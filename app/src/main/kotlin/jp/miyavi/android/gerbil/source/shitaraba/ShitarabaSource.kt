package jp.miyavi.android.gerbil.source.shitaraba

import jp.miyavi.android.gerbil.source.BoardRef
import jp.miyavi.android.gerbil.source.BoardSource
import jp.miyavi.android.gerbil.source.CategoryWithBoards
import jp.miyavi.android.gerbil.source.FetchResult
import jp.miyavi.android.gerbil.source.ParsedUrl
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.PostFailure
import jp.miyavi.android.gerbil.source.PostReplyRequest
import jp.miyavi.android.gerbil.source.PostReplyResult
import jp.miyavi.android.gerbil.source.SourceError
import jp.miyavi.android.gerbil.source.ThreadFetchResult
import jp.miyavi.android.gerbil.source.ThreadHeader
import jp.miyavi.android.gerbil.source.ThreadRef
import jp.miyavi.android.gerbil.source.dat.BbsCgiParser
import jp.miyavi.android.gerbil.source.dat.BbsCgiResponse
import jp.miyavi.android.gerbil.source.dat.DatParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * したらば掲示板（spec 006）。スレ一覧は subject.txt、本文は rawmode.cgi（レス番号で続きを取る）、書き込みは write.cgi。
 * 本文は保存のときに 2ch 互換の dat の行（UTF-8）に直し、表示は 2ch 互換と同じ解析に任せる。
 * 全体の板一覧は持たない（利用者が URL で足した板だけ）。[hostUrl] はテストで MockWebServer に差し替えるための口。
 */
class ShitarabaSource(
    private val client: OkHttpClient,
    override val id: String = ShitarabaUrls.HOST,
    private val hostUrl: () -> HttpUrl = { "https://${ShitarabaUrls.HOST}/".toHttpUrl() },
) : BoardSource {
    override fun parseUrl(url: String): ParsedUrl? {
        ShitarabaUrls.parseThreadUrl(url)?.let {
            return ParsedUrl.Thread(ThreadRef(BoardRef(id, it.host, it.boardKey), it.threadKey), it.resNumber)
        }
        return ShitarabaUrls.parseBoardUrl(url)?.let { (host, key) -> ParsedUrl.Board(BoardRef(id, host, key)) }
    }

    override fun threadPageUrl(thread: ThreadRef): String =
        url("bbs/read.cgi/${thread.board.boardKey}/${thread.threadKey}/").toString()

    override suspend fun fetchBoardMenu(): FetchResult<List<CategoryWithBoards>> = FetchResult.Success(emptyList(), null)

    override suspend fun fetchThreadList(board: BoardRef, ifModifiedSince: String?): FetchResult<List<ThreadHeader>> = io {
        val request = Request.Builder().url(url("${board.boardKey}/subject.txt"))
            .apply { ifModifiedSince?.let { header("If-Modified-Since", it) } }
            .build()
        execute(request) { res ->
            when {
                res.code == 304 -> FetchResult.NotModified
                res.isSuccessful -> FetchResult.Success(
                    ShitarabaFormat.parseSubject(String(res.body.bytes(), ShitarabaFormat.charset)),
                    res.header("Last-Modified"),
                )
                else -> FetchResult.Failed(errorOf(res.code))
            }
        } ?: FetchResult.Failed(SourceError.NETWORK)
    }

    override suspend fun fetchThread(thread: ThreadRef, currentSize: Long, ifModifiedSince: String?): ThreadFetchResult =
        fetchThreadAfter(thread, currentSize, 0, ifModifiedSince)

    /** 保存しているレス数 [currentCount] の次から取る。0 なら全体を取る。 */
    override suspend fun fetchThreadAfter(thread: ThreadRef, currentSize: Long, currentCount: Int, ifModifiedSince: String?): ThreadFetchResult = io {
        val after = if (currentSize > 0) currentCount else 0
        val request = Request.Builder()
            .url(url("bbs/rawmode.cgi/${thread.board.boardKey}/${thread.threadKey}/${after + 1}-"))
            .build()
        execute(request) { res ->
            if (!res.isSuccessful) return@execute if (res.code == 404) ThreadFetchResult.NotFound else ThreadFetchResult.Failed(errorOf(res.code))
            val raw = String(res.body.bytes(), ShitarabaFormat.charset)
            val (dat, last) = ShitarabaFormat.toDat(raw, after)
            val lastModified = res.header("Last-Modified")
            when {
                last > after && after > 0 -> ThreadFetchResult.Appended(dat.toByteArray(), lastModified)
                last > after -> ThreadFetchResult.Replaced(dat.toByteArray(), lastModified)
                after > 0 -> ThreadFetchResult.NotModified
                // 全体を頼んで何も返らないのは、スレがない（消えた・番号違い）
                else -> ThreadFetchResult.NotFound
            }
        } ?: ThreadFetchResult.Failed(SourceError.NETWORK)
    }

    override fun parseThread(bytes: ByteArray): List<Post> = DatParser.parse(String(bytes, Charsets.UTF_8))

    override fun parseTitle(bytes: ByteArray): String? {
        val end = bytes.indexOf('\n'.code.toByte()).let { if (it < 0) bytes.size else it }
        return DatParser.parseTitle(String(bytes, 0, end, Charsets.UTF_8))
    }

    override suspend fun postReply(request: PostReplyRequest): PostReplyResult = io {
        val (dir, bbs) = request.thread.board.boardKey.split('/').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        val form = FormBody.Builder(ShitarabaFormat.charset).apply {
            val fields = LinkedHashMap<String, String>()
            fields.putAll(request.confirmationFields)
            fields.putIfAbsent("DIR", dir)
            fields.putIfAbsent("BBS", bbs)
            fields.putIfAbsent("KEY", request.thread.threadKey.toString())
            fields.putIfAbsent("TIME", ((System.currentTimeMillis() / 1_000L) - POST_TIME_OFFSET_SEC).toString())
            fields.putIfAbsent("NAME", request.name)
            fields.putIfAbsent("MAIL", request.mail)
            fields.putIfAbsent("MESSAGE", request.body)
            fields["submit"] = request.submitLabel ?: DEFAULT_SUBMIT
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
        val httpRequest = Request.Builder()
            .url(url("bbs/write.cgi/${request.thread.board.boardKey}/${request.thread.threadKey}/"))
            .header("Referer", threadPageUrl(request.thread))
            .post(form)
            .build()
        execute(httpRequest) { res ->
            if (!res.isSuccessful) return@execute PostReplyResult.Failed(errorOf(res.code))
            when (val parsed = BbsCgiParser.parse(String(res.body.bytes(), ShitarabaFormat.charset))) {
                BbsCgiResponse.Success -> PostReplyResult.Success
                is BbsCgiResponse.Confirmation -> PostReplyResult.ConfirmationRequired(parsed.message, parsed.hiddenFields, parsed.submitValue)
                is BbsCgiResponse.Error -> PostReplyResult.Failed(SourceError.SERVER, parsed.message.ifEmpty { null }, PostFailure.REJECTED)
            }
        } ?: PostReplyResult.Failed(SourceError.NETWORK)
    }

    private fun url(path: String): HttpUrl = hostUrl().newBuilder().addPathSegments(path).build()

    private inline fun <T> execute(request: Request, block: (Response) -> T): T? = try {
        client.newCall(request).execute().use(block)
    } catch (_: IOException) {
        null
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun errorOf(code: Int) = when (code) {
        429, 503 -> SourceError.RATE_LIMITED
        401, 403 -> SourceError.FORBIDDEN
        else -> SourceError.SERVER
    }

    companion object {
        private const val POST_TIME_OFFSET_SEC = 60L
        private const val DEFAULT_SUBMIT = "書き込む"
    }
}
