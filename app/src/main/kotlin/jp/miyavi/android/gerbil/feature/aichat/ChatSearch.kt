package jp.miyavi.android.gerbil.feature.aichat

import androidx.annotation.StringRes
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.database.BoardEntity
import jp.miyavi.android.gerbil.data.BoardRepository
import jp.miyavi.android.gerbil.data.ThreadRepository
import jp.miyavi.android.gerbil.feature.threadlist.TitleSearch
import jp.miyavi.android.gerbil.source.dat.SubjectParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal data class ChatDestination(
    val boardId: String,
    val title: String,
    val threadKey: Long? = null,
    val boardName: String? = null,
    /** 板やスレではなく、アプリの画面へ案内するボタン（題名は画面で文言から付ける）。 */
    val link: ChatLink? = null,
)

/** 答えに添える、アプリの画面へのボタン。 */
internal enum class ChatLink { FEEDBACK, SITES }

internal data class ChatSearchResult(
    @StringRes val message: Int,
    val destinations: List<ChatDestination> = emptyList(),
)

internal sealed interface ChatSearchRequest {
    data class Board(val query: String) : ChatSearchRequest
    data class Thread(val query: String, val board: String? = null) : ChatSearchRequest
    data class Popular(val board: String) : ChatSearchRequest
}

// よく使う「〜スレを開きたい」などは、そのまま検索する。機能の使い方を尋ねる質問は会話に渡す。
internal fun directChatSearch(question: String): ChatSearchRequest? {
    val input = question.trim()
    POPULAR_SEARCH.matchEntire(input)?.let { match ->
        return ChatSearchRequest.Popular(match.groupValues[1].trim())
    }
    val match = DIRECT_SEARCH.matchEntire(input) ?: return null
    val query = match.groupValues[1].trim()
    if (query.isEmpty() || "の" in query || "って" in query || "板で" in query) return null
    return when (match.groupValues[2]) {
        "板" -> ChatSearchRequest.Board(query)
        else -> ChatSearchRequest.Thread(query)
    }
}

internal fun parseChatSearch(response: String, question: String): ChatSearchRequest? {
    if ('\n' in response || '\r' in response) return null
    val fields = response.split('|').map(String::trim)
    val query = fields.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
    val terms = TitleSearch.terms(question).joinToString("")
    if (!matches(terms, query)) return null
    return when (fields.first()) {
        "FIND_BOARD" -> if (fields.size == 2) ChatSearchRequest.Board(query) else null
        "POPULAR_THREADS" -> if (fields.size == 2) ChatSearchRequest.Popular(query) else null
        "FIND_THREAD" -> when (fields.size) {
            2 -> ChatSearchRequest.Thread(query)
            3 -> fields[2].takeIf { it.isNotBlank() && matches(terms, it) }
                ?.let { ChatSearchRequest.Thread(query, board = it) }
            else -> null
        }
        else -> null
    }
}

internal suspend fun findChatDestinations(
    request: ChatSearchRequest,
    boards: BoardRepository,
    threads: ThreadRepository,
): ChatSearchResult = withContext(Dispatchers.Default) {
    val registered = boards.boards.first()
    if (request is ChatSearchRequest.Board) {
        val destinations = findBoards(registered, request.query).take(RESULT_LIMIT).map(::boardDestination)
        return@withContext ChatSearchResult(
            if (destinations.isEmpty()) R.string.ai_chat_no_boards else R.string.ai_chat_search_pick,
            destinations,
        )
    }

    val boardQuery = when (request) {
        is ChatSearchRequest.Popular -> request.board
        is ChatSearchRequest.Thread -> request.board
        is ChatSearchRequest.Board -> error("Board searches are handled above")
    }
    val titleQuery = (request as? ChatSearchRequest.Thread)?.query
    if (boardQuery == null && titleQuery != null) {
        val eligible = registered.associateBy { it.id }
        val destinations = threads.cachedThreadTitles().asSequence()
            .filter { row ->
                ensureActive()
                row.boardId in eligible && matches(row.title, titleQuery)
            }
            .distinctBy { it.boardId to it.threadKey }
            .take(RESULT_LIMIT)
            .map { ChatDestination(it.boardId, it.title, it.threadKey, eligible.getValue(it.boardId).name) }
            .toList()
        return@withContext ChatSearchResult(
            if (destinations.isEmpty()) R.string.ai_chat_no_threads else R.string.ai_chat_search_pick,
            destinations,
        )
    }

    val matching = findBoards(registered, requireNotNull(boardQuery))
    val board = matching.singleOrNull() ?: return@withContext ChatSearchResult(
        if (matching.isEmpty()) R.string.ai_chat_no_boards else R.string.ai_chat_multiple_boards,
        matching.take(RESULT_LIMIT).map(::boardDestination),
    )

    // 板が特定できたときだけ、通常のスレ一覧と同じ経路で更新する。
    val failed = threads.refreshThreadList(board.id) != null
    val rows = threads.observeBoardThreads(board.id).first()
    val resultRows = if (request is ChatSearchRequest.Popular) {
        val now = System.currentTimeMillis() / 1000
        rows.sortedByDescending { SubjectParser.momentum(it.resCount, it.threadKey, now) }
    } else {
        rows.filter { matches(it.title, requireNotNull(titleQuery)) }
    }
    val destinations = resultRows.take(RESULT_LIMIT).map { row ->
        ChatDestination(board.id, row.title, row.threadKey, board.name)
    }
    val message = when {
        failed && destinations.isEmpty() -> R.string.ai_chat_fetch_failed
        failed -> R.string.ai_chat_cached_results
        destinations.isEmpty() -> R.string.ai_chat_board_no_threads
        request is ChatSearchRequest.Popular -> R.string.ai_chat_popular_results
        else -> R.string.ai_chat_search_pick
    }
    ChatSearchResult(message, destinations)
}

private fun boardDestination(board: BoardEntity) = ChatDestination(board.id, board.name)

private fun findBoards(boards: List<BoardEntity>, query: String): List<BoardEntity> {
    val normalized = normalizeBoardName(query)
    val exact = boards.filter {
        normalizeBoardName(it.name) == normalized || normalizeBoardName(it.boardKey) == normalized
    }
    return exact.ifEmpty {
        boards.filter { matches(normalizeBoardName(it.name), normalized) || matches(it.boardKey, normalized) }
    }
}

private fun normalizeBoardName(name: String) =
    TitleSearch.terms(name).joinToString("").replace("ニュー速", "ニュース速報")

private fun matches(title: String, query: String): Boolean {
    val terms = TitleSearch.terms(query)
    return terms.isNotEmpty() && (
        TitleSearch.matches(title, terms) ||
            TitleSearch.matches(TitleSearch.terms(title).joinToString(""), terms)
        )
}

private val POPULAR_SEARCH =
    Regex("""(.+?)(?:板)?(?:で|の)(?:今|いま)?人気(?:の|な)?スレ(?:ッド)?(?:を)?(?:教えて|見せて|探して|出して|知りたい)?[。！!？?]*""")
private val DIRECT_SEARCH = Regex("""(.+?)(?:の)?(スレッド|スレ|板)(?:を)?(?:開きたい|開いて|開く|探したい|探して|検索して)[。！!？?]*""")
private const val RESULT_LIMIT = 8
