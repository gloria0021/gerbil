package jp.miyavi.android.gerbil.source

/**
 * 掲示板サービスとの境界（contracts/board-source.md）。
 * サービス固有の通信・形式はこの内側に閉じ込める。スレ本文は取得した生バイト列を端末に保存し、
 * 表示時に [parseThread] で解析する（専ブラの伝統的なログ形式。差分追記が単純になる）。
 */
interface BoardSource {
    val id: String

    fun parseUrl(url: String): ParsedUrl?

    /** ブラウザで表示できるスレのページ。 */
    fun threadPageUrl(thread: ThreadRef): String? = null

    suspend fun fetchBoardMenu(): FetchResult<List<CategoryWithBoards>>

    suspend fun fetchThreadList(board: BoardRef, ifModifiedSince: String?): FetchResult<List<ThreadHeader>>

    /** [currentSize] は保存済みバイト数。0 なら全取得。 */
    suspend fun fetchThread(thread: ThreadRef, currentSize: Long, ifModifiedSince: String?): ThreadFetchResult

    /** 保存済みのレス数 [currentCount] も使って差分を取る（レス番号で続きを取る掲示板用）。既定はバイト数で取る。 */
    suspend fun fetchThreadAfter(thread: ThreadRef, currentSize: Long, currentCount: Int, ifModifiedSince: String?): ThreadFetchResult =
        fetchThread(thread, currentSize, ifModifiedSince)

    fun parseThread(bytes: ByteArray): List<Post>

    /** 保存済みバイト列からスレタイを取り出す（一覧にないスレを開いたとき用）。 */
    fun parseTitle(bytes: ByteArray): String?

    suspend fun postReply(request: PostReplyRequest): PostReplyResult = PostReplyResult.Failed(SourceError.SERVER)
}

data class PostReplyRequest(
    val thread: ThreadRef,
    val name: String,
    val mail: String,
    val body: String,
    val confirmationFields: Map<String, String> = emptyMap(),
    val submitLabel: String? = null,
)

sealed interface PostReplyResult {
    data object Success : PostReplyResult
    data class ConfirmationRequired(
        val message: String,
        val hiddenFields: Map<String, String>,
        val submitLabel: String,
    ) : PostReplyResult
    /** message はサーバの文だけ。ないときは reason か error から画面側が文を出す。 */
    data class Failed(val error: SourceError, val message: String? = null, val reason: PostFailure? = null) : PostReplyResult
}

enum class PostFailure {
    /** 掲示板が書き込みを拒否したが、理由の文がなかった。 */
    REJECTED,

    /** 書き込み先の板が保存されていない。 */
    BOARD_UNKNOWN,
}

sealed interface FetchResult<out T> {
    data class Success<T>(val value: T, val lastModified: String?) : FetchResult<T>
    data object NotModified : FetchResult<Nothing>
    data class Failed(val error: SourceError) : FetchResult<Nothing>
}

sealed interface ThreadFetchResult {
    /** 保存済みの末尾に追記するバイト列。 */
    data class Appended(val bytes: ByteArray, val lastModified: String?) : ThreadFetchResult
    data object NotModified : ThreadFetchResult
    /** 全体を置き換える（初回取得、あぼーん等で内容が改変された場合）。 */
    data class Replaced(val bytes: ByteArray, val lastModified: String?) : ThreadFetchResult
    /** DAT落ち。過去ログを取得できた場合は bytes に全体が入る。 */
    data class Archived(val bytes: ByteArray?) : ThreadFetchResult
    data object NotFound : ThreadFetchResult
    data class Failed(val error: SourceError) : ThreadFetchResult
}

enum class SourceError { NETWORK, RATE_LIMITED, FORBIDDEN, SERVER, PARSE }
