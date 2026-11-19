package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.source.PostFailure
import jp.miyavi.android.gerbil.core.text.TextDecoding
import jp.miyavi.android.gerbil.source.BoardRef
import jp.miyavi.android.gerbil.source.BoardSource
import jp.miyavi.android.gerbil.source.CategoryWithBoards
import jp.miyavi.android.gerbil.source.FetchResult
import jp.miyavi.android.gerbil.source.ParsedUrl
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.PostReplyRequest
import jp.miyavi.android.gerbil.source.PostReplyResult
import jp.miyavi.android.gerbil.source.SourceError
import jp.miyavi.android.gerbil.source.ThreadFetchResult
import jp.miyavi.android.gerbil.source.ThreadHeader
import jp.miyavi.android.gerbil.source.ThreadRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.io.IOException

/**
 * 2ch 互換の掲示板の汎用の実装。bbsmenu（JSON / HTML）・subject.txt・dat・bbs.cgi を扱う。
 * [menuUrl] は利用者が登録した bbsmenu の URL（なければ板一覧は空）。[urls] は板・スレの URL の読み方。
 * 文字コードは応答ごとに判定し（Shift_JIS / UTF-8）、書き込みは [postCharset] で送る。
 * [hostUrl] はテストで MockWebServer に差し替えるための口。
 */
class DatSource(
    private val client: OkHttpClient,
    override val id: String,
    private val menuUrl: String?,
    private val urls: UrlRules = DatUrls,
    private val postCharset: Charset = TextDecoding.sjis,
    private val hostUrl: (host: String) -> HttpUrl = { "https://$it/".toHttpUrl() },
) : BoardSource {

    override fun parseUrl(url: String): ParsedUrl? {
        urls.parseThreadUrl(url)?.let {
            return ParsedUrl.Thread(ThreadRef(BoardRef(id, it.host, it.boardKey), it.threadKey), it.resNumber)
        }
        urls.parseBoardUrl(url)?.let { (host, key) -> return ParsedUrl.Board(BoardRef(id, host, key)) }
        return null
    }

    override fun threadPageUrl(thread: ThreadRef): String =
        url(thread.board.host, "test/read.cgi/${thread.board.boardKey}/${thread.threadKey}/").toString()

    override suspend fun fetchBoardMenu(): FetchResult<List<CategoryWithBoards>> = io {
        val menu = menuUrl ?: return@io FetchResult.Success(emptyList(), null)
        execute(Request.Builder().url(menu).build()) { res ->
            when {
                res.isSuccessful -> runCatching { BbsMenuParser.parse(TextDecoding.decode(res.body.bytes()), urls) }
                    .fold({ FetchResult.Success(it, res.header("Last-Modified")) }, { FetchResult.Failed(SourceError.PARSE) })
                else -> FetchResult.Failed(errorOf(res.code))
            }
        } ?: FetchResult.Failed(SourceError.NETWORK)
    }

    override suspend fun fetchThreadList(board: BoardRef, ifModifiedSince: String?): FetchResult<List<ThreadHeader>> = io {
        val request = Request.Builder().url(url(board.host, "${board.boardKey}/subject.txt"))
            .apply { ifModifiedSince?.let { header("If-Modified-Since", it) } }
            .build()
        execute(request) { res ->
            when {
                res.code == 304 -> FetchResult.NotModified
                res.isSuccessful -> FetchResult.Success(
                    SubjectParser.parse(TextDecoding.decode(res.body.bytes())),
                    res.header("Last-Modified"),
                )
                else -> FetchResult.Failed(errorOf(res.code))
            }
        } ?: FetchResult.Failed(SourceError.NETWORK)
    }

    override suspend fun fetchThread(thread: ThreadRef, currentSize: Long, ifModifiedSince: String?): ThreadFetchResult = io {
        if (currentSize <= 0) fetchFull(thread) else fetchDiff(thread, currentSize, ifModifiedSince)
    }

    override fun parseThread(bytes: ByteArray): List<Post> = DatParser.parse(TextDecoding.decode(bytes))

    override fun parseTitle(bytes: ByteArray): String? {
        val end = bytes.indexOf('\n'.code.toByte()).let { if (it < 0) bytes.size else it }
        return DatParser.parseTitle(TextDecoding.decode(bytes, 0, end))
    }

    override suspend fun postReply(request: PostReplyRequest): PostReplyResult = io {
        val postUrl = url(request.thread.board.host, "test/bbs.cgi").newBuilder()
            .addQueryParameter("guid", "ON")
            .build()
        val form = buildReplyForm(request)
        val httpRequest = Request.Builder()
            .url(postUrl)
            .header("Referer", threadPageUrl(request.thread))
            .post(form)
            .build()
        execute(httpRequest) { res ->
            if (!res.isSuccessful) return@execute PostReplyResult.Failed(errorOf(res.code))
            val html = TextDecoding.decode(res.body.bytes())
            when (val parsed = BbsCgiParser.parse(html)) {
                BbsCgiResponse.Success -> PostReplyResult.Success
                is BbsCgiResponse.Confirmation -> PostReplyResult.ConfirmationRequired(
                    message = parsed.message,
                    hiddenFields = parsed.hiddenFields,
                    submitLabel = parsed.submitValue,
                )
                is BbsCgiResponse.Error -> PostReplyResult.Failed(SourceError.SERVER, parsed.message.ifEmpty { null }, PostFailure.REJECTED)
            }
        } ?: PostReplyResult.Failed(SourceError.NETWORK)
    }

    private fun buildReplyForm(request: PostReplyRequest): FormBody {
        val fields = LinkedHashMap<String, String>()
        if (request.confirmationFields.isNotEmpty()) {
            fields.putAll(request.confirmationFields)
        }
        fields.putIfAbsent("bbs", request.thread.board.boardKey)
        fields.putIfAbsent("key", request.thread.threadKey.toString())
        fields.putIfAbsent("time", ((System.currentTimeMillis() / 1_000L) - POST_TIME_OFFSET_SEC).toString())
        fields.putIfAbsent("FROM", request.name)
        fields.putIfAbsent("mail", request.mail)
        fields.putIfAbsent("MESSAGE", request.body)
        fields["submit"] = request.submitLabel ?: DEFAULT_SUBMIT

        return FormBody.Builder(postCharset).apply {
            fields.forEach { (k, v) -> add(k, v) }
        }.build()
    }

    private fun fetchFull(thread: ThreadRef): ThreadFetchResult {
        val request = Request.Builder().url(datUrl(thread)).build()
        return execute(request) { res ->
            when {
                res.code == 200 -> replaced(res.body.bytes(), res.header("Last-Modified"))
                res.code == 404 || res.code == 302 -> fetchArchive(thread)
                else -> ThreadFetchResult.Failed(errorOf(res.code))
            }
        } ?: ThreadFetchResult.Failed(SourceError.NETWORK)
    }

    private fun fetchDiff(thread: ThreadRef, currentSize: Long, ifModifiedSince: String?): ThreadFetchResult {
        // 1 バイト手前から取り、末尾の改行と一致するかで改変（あぼーん等）を検出する。
        // 圧縮されると Range の位置がずれるため identity を明示する。
        val request = Request.Builder().url(datUrl(thread))
            .header("Range", "bytes=${currentSize - 1}-")
            .header("Accept-Encoding", "identity")
            .apply { ifModifiedSince?.let { header("If-Modified-Since", it) } }
            .build()
        return execute(request) { res ->
            when (res.code) {
                304 -> ThreadFetchResult.NotModified
                206 -> {
                    val bytes = res.body.bytes()
                    when {
                        bytes.isEmpty() || bytes[0] != '\n'.code.toByte() -> fetchFull(thread)
                        bytes.size == 1 -> ThreadFetchResult.NotModified
                        else -> ThreadFetchResult.Appended(bytes.copyOfRange(1, bytes.size), res.header("Last-Modified"))
                    }
                }
                200 -> replaced(res.body.bytes(), res.header("Last-Modified"))
                416 -> fetchFull(thread)
                404, 302 -> fetchArchive(thread)
                else -> ThreadFetchResult.Failed(errorOf(res.code))
            }
        } ?: ThreadFetchResult.Failed(SourceError.NETWORK)
    }

    private fun fetchArchive(thread: ThreadRef): ThreadFetchResult {
        val key = thread.threadKey.toString()
        val request = Request.Builder()
            .url(url(thread.board.host, "${thread.board.boardKey}/oyster/${key.take(4)}/$key.dat"))
            .build()
        return execute(request) { res ->
            when (res.code) {
                200 -> ThreadFetchResult.Archived(res.body.bytes().takeIf(::looksLikeDat))
                404, 302 -> ThreadFetchResult.Archived(null)
                else -> ThreadFetchResult.Failed(errorOf(res.code))
            }
        } ?: ThreadFetchResult.Failed(SourceError.NETWORK)
    }

    /** 案内・エラーの HTML が 200 で返っても保存済みログを置き換えないよう、DAT の形をしているときだけ採用する。 */
    private fun replaced(bytes: ByteArray, lastModified: String?): ThreadFetchResult =
        if (looksLikeDat(bytes)) ThreadFetchResult.Replaced(bytes, lastModified) else ThreadFetchResult.Failed(SourceError.PARSE)

    private fun datUrl(thread: ThreadRef) =
        url(thread.board.host, "${thread.board.boardKey}/dat/${thread.threadKey}.dat")

    private fun url(host: String, path: String): HttpUrl =
        hostUrl(host).newBuilder().addPathSegments(path).build()

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

/**
 * DAT の1行目は「名前<>メール<>日付 ID<>本文<>スレタイ」で、区切りの "<>" が4つ以上ある。
 * Shift_JIS の2バイト目に '<'(0x3C) と '>'(0x3E) は現れないので、バイト列のまま数えてよい。
 */
internal fun looksLikeDat(bytes: ByteArray): Boolean {
    var count = 0
    for (i in 0 until bytes.size - 1) {
        val b = bytes[i]
        if (b == '\n'.code.toByte()) break
        if (b == '<'.code.toByte() && bytes[i + 1] == '>'.code.toByte()) count++
    }
    return count >= 4
}
