package jp.miyavi.android.gerbil.feature.threadlist

import jp.miyavi.android.gerbil.core.database.SiteEntity
import jp.miyavi.android.gerbil.core.settings.SettingsStore
import jp.miyavi.android.gerbil.data.ThreadRepository
import jp.miyavi.android.gerbil.data.BoardRepository
import kotlinx.coroutines.job
import kotlinx.coroutines.Job
import androidx.annotation.StringRes
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.ui.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.gerbil.core.database.BoardEntity
import jp.miyavi.android.gerbil.core.database.MenuRow
import jp.miyavi.android.gerbil.core.database.ThreadRow
import jp.miyavi.android.gerbil.core.database.ThreadState
import jp.miyavi.android.gerbil.core.settings.ThreadSort
import jp.miyavi.android.gerbil.source.dat.SubjectParser
import jp.miyavi.android.gerbil.source.BoardRef
import jp.miyavi.android.gerbil.ui.UserMessage
import jp.miyavi.android.gerbil.ui.messageFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToInt

/** 下部タブ。閲覧 = 開いたことのあるスレ（スター付きが先頭）、一覧 = 板一覧→板のスレ。 */
enum class ThreadListTab(@StringRes val label: Int) { BROWSE(R.string.thread_list_tab_browse), LIST(R.string.thread_list_tab_all) }

data class ThreadRowUi(
    val boardId: String,
    val threadKey: Long,
    val title: String,
    /** メタ行の部品。画面で解決して「 · 」で結合する。 */
    val meta: List<UiText>,
    val newCount: Int,
    val hasUnread: Boolean,
    val isArchived: Boolean,
    val isFavorite: Boolean = false,
    /** 自分が書き込んだスレ。 */
    val hasPosted: Boolean = false,
    val searchBoard: BoardRef? = null,
    val searchBoardName: String? = null,
)

/** 一覧タブで板一覧を出しているときの中身。 */
data class BoardListUi(
    val boards: List<BoardEntity> = emptyList(),
    val menu: List<MenuRow> = emptyList(),
    val refreshing: Boolean = false,
    /** 登録した掲示板（板一覧を掲示板ごとのセクションに分け、見出しにラベルを出す）。 */
    val sites: List<SiteEntity> = emptyList(),
)

data class ThreadListUiState(
    val boardId: String? = null,
    val boardName: String? = null,
    val tab: ThreadListTab = ThreadListTab.BROWSE,
    /** 一覧タブで、板のスレではなく板一覧を出しているか。 */
    val showBoards: Boolean = false,
    val boardList: BoardListUi = BoardListUi(),
    val sort: ThreadSort = ThreadSort.MOMENTUM,
    val rows: List<ThreadRowUi> = emptyList(),
    val refreshing: Boolean = false,
    val query: String = "",
)

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadListViewModel(
    private val boards: BoardRepository,
    private val threads: ThreadRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val tab = MutableStateFlow(ThreadListTab.BROWSE)
    // 全体タブは起動するたびに板一覧から始める
    private val showBoards = MutableStateFlow(true)
    private val refreshing = MutableStateFlow(false)
    private val menuRefreshing = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val boardId = settings.lastBoardId.distinctUntilChanged()
    private val sort = MutableStateFlow(ThreadSort.MOMENTUM)

    // 行には、どのタブの行かを付けて流す。タブを切り替えた直後に前のタブの行が新しいタブに一瞬出て、
    // 行の出入りの動きで並び替わったように見えるのを防ぐ（タブは行が届いてから切り替える）
    private val allRows = combine(tab, boardId) { t, id -> t to id }.flatMapLatest { (t, id) ->
        when (t) {
            ThreadListTab.LIST -> if (id == null) flowOf(emptyList()) else threads.observeBoardThreads(id)
            ThreadListTab.BROWSE -> threads.observeHistory()
        }.map { t to it }
    }

    private val rows = combine(allRows, query) { r, q -> q to r }

    private val board = boardId.flatMapLatest { id -> if (id == null) flowOf(null) else boards.observe(id) }

    private val boardList = combine(boards.boards, boards.menu, menuRefreshing, boards.sites, ::BoardListUi)

    private val view = combine(tab, showBoards, boardList) { t, sb, bl -> Triple(t, sb, bl) }

    val state: StateFlow<ThreadListUiState> = combine(view, board, sort, rows, refreshing) { (_, sb, bl), b, s, (q, tagged), busy ->
        val (t, r) = tagged
        val now = System.currentTimeMillis() / 1000
        val isList = t == ThreadListTab.LIST
        val sorted = if (isList) sortRows(r, s, now) else r
        ThreadListUiState(
            boardId = b?.id,
            boardName = b?.name,
            tab = t,
            showBoards = isList && (sb || b == null),
            boardList = bl,
            sort = s,
            // 板の中の検索欄: その板のスレタイだけで絞る（通信しない）
            rows = sorted.filterByTitle(q.takeIf { isList }).map { it.toUi(now, showBoard = !isList) },
            refreshing = busy,
            query = q,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThreadListUiState())

    init {
        viewModelScope.launch {
            // 板をまだ選んでいなければ、一覧タブの板一覧から始める
            val hasBoard = settings.lastBoardId.first() != null
            tab.value = if (!hasBoard) ThreadListTab.LIST
            else ThreadListTab.entries.getOrElse(settings.lastTab.first()) { ThreadListTab.BROWSE }
        }
        refreshMenu(force = false)
        // 板が変わったら一覧を取り直す
        viewModelScope.launch { boardId.filterNotNull().collect { refresh() } }
    }

    fun selectTab(t: ThreadListTab) {
        // 全体タブを開いた状態でもう一度押したら板一覧へ戻す
        if (t == ThreadListTab.LIST && tab.value == ThreadListTab.LIST) showBoardList()
        tab.value = t
        viewModelScope.launch { settings.setLastTab(t.ordinal) }
    }

    /** 一覧タブ: 板を選んでその板のスレ一覧へ。 */
    fun selectBoard(id: String) {
        query.value = ""
        viewModelScope.launch {
            settings.setLastBoard(id)
            sort.value = ThreadSort.MOMENTUM
            showBoards.value = false
            boards.markOpened(id)
        }
    }

    /** 一覧タブ: 板のスレ一覧から板一覧へ戻る。 */
    fun showBoardList() {
        query.value = ""
        showBoards.value = true
    }

    fun refreshMenu(force: Boolean = true) {
        if (menuRefreshing.value) return
        viewModelScope.launch {
            menuRefreshing.value = true
            val error = if (force) boards.refresh() else boards.refreshIfStale()
            menuRefreshing.value = false
            if (error != null) _messages.send(UserMessage(messageFor(error, R.string.error_target_board_list), retry = { refreshMenu(true) }))
        }
    }

    /** 長押しメニューからスレのスターを付け外しする（まだ開いていないスレにも付けられる）。 */
    fun toggleFavorite(row: ThreadRowUi) = viewModelScope.launch {
        // 検索結果のスレは、板がまだ保存されていなければ先に登録する
        row.searchBoard?.let { ref ->
            if (boards.get(ref.id) == null) boards.ensure(ref, row.searchBoardName ?: ref.boardKey)
        }
        threads.setFavorite(row.boardId, row.threadKey, !row.isFavorite, row.title)
    }

    /** 長押しメニューから板のスターを付け外しする。 */
    fun toggleBoardFavorite(board: BoardEntity) = viewModelScope.launch {
        boards.setFavorite(board.id, !board.isFavorite)
    }

    /** 閲覧中タブ: 長押しメニューから閲覧履歴・スターを削除する。 */
    fun removeFromBrowse(row: ThreadRowUi) = viewModelScope.launch {
        threads.removeFromHistory(row.boardId, row.threadKey)
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun openThread(row: ThreadRowUi, onOpen: (String, Long) -> Unit) {
        val ref = row.searchBoard
        if (ref == null) {
            onOpen(row.boardId, row.threadKey)
        } else {
            viewModelScope.launch {
                if (boards.get(ref.id) == null) boards.ensure(ref, row.searchBoardName ?: ref.boardKey)
                onOpen(ref.id, row.threadKey)
            }
        }
    }

    fun setSort(s: ThreadSort) {
        sort.value = s
    }

    private var refreshJob: Job? = null
    private var refreshTarget: String? = null

    /** 同じ一覧の取得中は重ねない。別の板・タブへの要求なら、進行中の取得を取り消して新しい方を取る。 */
    fun refresh() {
        viewModelScope.launch {
            val browse = tab.value == ThreadListTab.BROWSE
            val id = if (browse) null else settings.lastBoardId.first() ?: return@launch
            val target = id ?: BROWSE_TARGET
            if (refreshJob?.isActive == true) {
                if (target == refreshTarget) return@launch
                refreshJob?.cancel()
            }
            refreshTarget = target
            refreshJob = launch {
                val self = coroutineContext.job
                refreshing.value = true
                try {
                    // 閲覧中タブは複数の板にまたがるので、開いたスレがある板をすべて取り直す
                    val error = if (id == null) threads.refreshHistoryLists() else threads.refreshThreadList(id)
                    if (error != null) _messages.send(UserMessage(messageFor(error, R.string.error_target_thread_list), retry = ::refresh))
                } finally {
                    // 取り消された古い取得が、新しい取得の表示を消さないようにする
                    if (refreshJob === self) refreshing.value = false
                }
            }
        }
    }

    private fun sortRows(rows: List<ThreadRow>, sort: ThreadSort, now: Long): List<ThreadRow> = when (sort) {
        ThreadSort.RANK -> rows
        ThreadSort.MOMENTUM -> rows.sortedByDescending { SubjectParser.momentum(it.resCount, it.threadKey, now) }
        ThreadSort.NEWEST -> rows.sortedByDescending { row -> row.threadKey.takeIf { it <= now } }
    }

    private fun ThreadRow.toUi(now: Long, showBoard: Boolean): ThreadRowUi {
        val archived = state == ThreadState.ARCHIVED
        val parts = buildList {
            if (showBoard) boardName?.let { add(UiText.Raw(it)) }
            add(UiText.Raw(resCount.toString()))
            if (!archived) {
                val momentum = SubjectParser.momentum(resCount, threadKey, now)?.roundToInt()
                add(if (momentum == null) UiText.Res(R.string.thread_meta_momentum_unknown)
                    else UiText.Res(R.string.thread_meta_momentum, listOf(momentum)))
            }
        }
        val read = readCount
        return ThreadRowUi(
            boardId = boardId,
            threadKey = threadKey,
            title = title,
            meta = parts,
            newCount = if (read != null) (resCount - read).coerceAtLeast(0) else 0,
            hasUnread = read != null && resCount > read,
            isArchived = archived,
            isFavorite = isFavorite,
            hasPosted = hasPosted,
        )
    }

}

/** 閲覧中タブ（複数の板）の取得を表す識別子。板 ID（"サイト:板"）とは重ならない。 */
private const val BROWSE_TARGET = "browse"

/** スレタイに検索語がすべて含まれる行だけにする（検索語がなければそのまま）。 */
private fun List<ThreadRow>.filterByTitle(query: String?): List<ThreadRow> {
    val terms = TitleSearch.terms(query.orEmpty())
    return if (terms.isEmpty()) this else filter { TitleSearch.matches(it.title, terms) }
}
