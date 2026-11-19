package jp.miyavi.android.gerbil.data

import jp.miyavi.android.gerbil.source.PostFailure
import jp.miyavi.android.gerbil.core.database.BoardDao
import jp.miyavi.android.gerbil.core.database.ThreadDao
import jp.miyavi.android.gerbil.core.database.NgEntity
import jp.miyavi.android.gerbil.core.database.ThreadLogEntity
import jp.miyavi.android.gerbil.core.database.ThreadRow
import jp.miyavi.android.gerbil.core.database.ThreadState
import jp.miyavi.android.gerbil.core.database.ThreadSummaryEntity
import jp.miyavi.android.gerbil.source.BoardRef
import jp.miyavi.android.gerbil.source.BoardSource
import jp.miyavi.android.gerbil.source.FetchResult
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.PostReplyRequest
import jp.miyavi.android.gerbil.source.PostReplyResult
import jp.miyavi.android.gerbil.source.SourceError
import jp.miyavi.android.gerbil.source.ThreadFetchResult
import jp.miyavi.android.gerbil.source.ThreadRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 表示用に読み込んだスレ。 */
data class LoadedThread(val log: ThreadLogEntity, val posts: List<Post>)

sealed interface RefreshOutcome {
    data class Updated(val thread: LoadedThread, val added: Int) : RefreshOutcome

    /** 本文は変わっていない。表示中の本文・索引をそのまま使い、[log]（新着の境目など）だけ反映する。 */
    data class Unchanged(val log: ThreadLogEntity) : RefreshOutcome
    data class Failed(val error: SourceError, val cached: LoadedThread?) : RefreshOutcome
    data object NotFound : RefreshOutcome
}

/** スレ一覧とスレ本文。Room と保存ファイルを唯一の正とし、取得結果はここでマージする。 */
class ThreadRepository(
    private val threadDao: ThreadDao,
    private val boardDao: BoardDao,
    /** 掲示板の ID から、取得・書き込みの部品を作る。 */
    private val sourceOf: (siteId: String) -> BoardSource,
    private val storage: LogStorage,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val listModified = HashMap<String, String?>()
    private val mutex = Mutex()

    fun observeBoardThreads(boardId: String): Flow<List<ThreadRow>> = threadDao.observeBoardThreads(boardId)

    /** その板の NG（期限を過ぎたものは先に消す）。 */
    fun observeNg(boardId: String): Flow<List<NgEntity>> = threadDao.observeNg(boardId)

    suspend fun purgeExpiredNg() {
        threadDao.purgeNg(NgKey.ID, now() - NG_ID_TTL_MS)
        threadDao.purgeNg(NgKey.WACCHOI, now() - NG_WACCHOI_TTL_MS)
    }

    suspend fun setNg(boardId: String, key: NgKey, ng: Boolean) =
        if (ng) threadDao.addNg(NgEntity(boardId, key.kind, key.value, now())) else threadDao.removeNg(boardId, key.kind, key.value)

    fun observeHistory(): Flow<List<ThreadRow>> = threadDao.observeHistory()

    suspend fun cachedThreadTitles() = threadDao.cachedThreadTitles()
    fun observeLog(boardId: String, threadKey: Long): Flow<ThreadLogEntity?> = threadDao.observeLog(boardId, threadKey)

    suspend fun refreshThreadList(boardId: String): SourceError? {
        // 板がない（掲示板を消した、前の版の板を覚えていた等）なら取りに行かない。画面は板一覧に戻る
        val board = boardDao.get(boardId) ?: return null
        val ref = BoardRef(board.source, board.host, board.boardKey)
        return when (val r = sourceOf(board.source).fetchThreadList(ref, synchronized(listModified) { listModified[boardId] })) {
            is FetchResult.Success -> {
                threadDao.replaceSummaries(
                    boardId,
                    r.value.map { ThreadSummaryEntity(boardId, it.threadKey, it.title, it.resCount, it.rank) },
                )
                synchronized(listModified) { listModified[boardId] = r.lastModified }
                followNextThreads(boardId)
                null
            }
            FetchResult.NotModified -> null
            is FetchResult.Failed -> r.error
        }
    }

    /**
     * 次スレを追尾しているスレが終わりに近い（[NEXT_THREAD_FROM] レス以上・DAT落ち・一覧から消えた）なら、板のスレ一覧から
     * 次スレを探す。見つかったら次スレを追尾にして閲覧中の先頭に出し、古いスレの追尾は外す。
     */
    private suspend fun followNextThreads(boardId: String) {
        val followed = threadDao.getFollowed(boardId)
        if (followed.isEmpty()) return
        val summaries = threadDao.getSummaries(boardId)
        val listed = summaries.associateBy { it.threadKey }
        val candidates = summaries.map { NextThread.Candidate(it.threadKey, it.title) }
        for (log in followed) {
            val count = maxOf(log.resCount, listed[log.threadKey]?.resCount ?: 0)
            val ending = log.state != ThreadState.ACTIVE || count >= NEXT_THREAD_FROM || log.threadKey !in listed
            if (!ending) continue
            val next = NextThread.find(log.title, log.threadKey, candidates) ?: continue
            if (threadDao.getLog(boardId, next.threadKey)?.isFavorite == true) continue
            threadDao.setFavorite(boardId, next.threadKey, true, next.title)
            threadDao.updateLastViewed(boardId, next.threadKey, now())
            threadDao.updateFavorite(boardId, log.threadKey, false)
        }
    }

    /** 閲覧中タブ: 開いたスレがある板の一覧をすべて取り直す。失敗があれば最初のものを返す。 */
    suspend fun refreshHistoryLists(): SourceError? = coroutineScope {
        threadDao.observeHistory().first()
            .map { it.boardId }
            .distinct()
            .map { id -> async { refreshThreadList(id) } }
            .awaitAll()
            .firstOrNull { it != null }
    }

    suspend fun threadPageUrl(boardId: String, threadKey: Long): String? {
        val board = boardDao.get(boardId) ?: return null
        return sourceOf(board.source).threadPageUrl(ThreadRef(BoardRef(board.source, board.host, board.boardKey), threadKey))
    }

    /** 保存済みの本文を読む。保存がなければ null。 */
    suspend fun loadCached(boardId: String, threadKey: Long): LoadedThread? = withContext(Dispatchers.Default) {
        val log = threadDao.getLog(boardId, threadKey) ?: return@withContext null
        val bytes = withContext(Dispatchers.IO) { storage.read(boardId, threadKey) } ?: return@withContext null
        LoadedThread(log, sourceOf(siteOf(boardId)).parseThread(bytes))
    }

    /**
     * 差分を取得して保存し、全体を読み直す。
     * [shownCount] は画面に出ていたレス数。渡すと「ここから新着」をその次のレスに置く。
     * 増えていなければ末尾の次（= 新着なし）になる。
     */
    suspend fun refreshThread(boardId: String, threadKey: Long, shownCount: Int? = null): RefreshOutcome = mutex.withLock {
        val board = boardDao.get(boardId) ?: return RefreshOutcome.NotFound
        val ref = ThreadRef(BoardRef(board.source, board.host, board.boardKey), threadKey)
        val old = threadDao.getLog(boardId, threadKey)
        val size = if (old != null) withContext(Dispatchers.IO) { storage.size(boardId, threadKey) } else 0
        val result = sourceOf(board.source).fetchThreadAfter(ref, size, old?.resCount ?: 0, old?.lastModified.takeIf { size > 0 })
        val oldCount = old?.resCount ?: 0

        val (state, lastModified) = when (result) {
            is ThreadFetchResult.Appended -> {
                withContext(Dispatchers.IO) { storage.append(boardId, threadKey, result.bytes) }
                ThreadState.ACTIVE to result.lastModified
            }
            is ThreadFetchResult.Replaced -> {
                withContext(Dispatchers.IO) { storage.replace(boardId, threadKey, result.bytes) }
                ThreadState.ACTIVE to result.lastModified
            }
            is ThreadFetchResult.Archived -> {
                if (result.bytes != null) withContext(Dispatchers.IO) { storage.replace(boardId, threadKey, result.bytes) }
                else if (size == 0L) return RefreshOutcome.NotFound
                ThreadState.ARCHIVED to old?.lastModified
            }
            ThreadFetchResult.NotModified -> {
                // 保存済みの本文があれば読み直さない（解析・索引の作り直しを省く）
                if (old != null && size > 0) {
                    val newFrom = if (shownCount != null && old.resCount >= shownCount) shownCount + 1 else old.newFrom
                    threadDao.saveFetchedLog(old.copy(lastFetchedAt = now(), newFrom = newFrom))
                    return RefreshOutcome.Unchanged(threadDao.getLog(boardId, threadKey) ?: old)
                }
                (old?.state ?: ThreadState.ACTIVE) to old?.lastModified
            }
            ThreadFetchResult.NotFound -> return RefreshOutcome.NotFound
            is ThreadFetchResult.Failed -> return RefreshOutcome.Failed(result.error, if (shownCount == null) loadCached(boardId, threadKey) else null)
        }

        val bytes = withContext(Dispatchers.IO) { storage.read(boardId, threadKey) } ?: return RefreshOutcome.NotFound
        val posts = withContext(Dispatchers.Default) { sourceOf(board.source).parseThread(bytes) }
        val title = old?.title?.takeIf { it.isNotEmpty() }
            ?: threadDao.getSummary(boardId, threadKey)?.title
            ?: sourceOf(board.source).parseTitle(bytes)
            ?: threadKey.toString()
        val base = shownCount ?: oldCount
        val added = (posts.size - base).coerceAtLeast(0)
        // 画面に出ていた件数が分かるときは、増えていなくても帯をその次へ動かす（末尾の次なら「新着なし」）
        val newFrom = when {
            shownCount != null && posts.size >= shownCount -> shownCount + 1
            old != null && base > 0 && added > 0 -> base + 1
            else -> old?.newFrom
        }
        val log = (old ?: ThreadLogEntity(boardId = boardId, threadKey = threadKey, title = title)).copy(
            title = title,
            state = state,
            byteSize = bytes.size.toLong(),
            lastModified = lastModified,
            lastFetchedAt = now(),
            resCount = posts.size,
            newFrom = newFrom,
        )
        threadDao.saveFetchedLog(log)
        RefreshOutcome.Updated(LoadedThread(threadDao.getLog(boardId, threadKey) ?: log, posts), added)
    }

    suspend fun markViewed(boardId: String, threadKey: Long) {
        threadDao.updateLastViewed(boardId, threadKey, now())
    }

    suspend fun saveReadPosition(boardId: String, threadKey: Long, position: Int, offsetPx: Int) =
        threadDao.updateReadPosition(boardId, threadKey, position, offsetPx)

    /** スターを付け外しする。まだ開いていないスレは、本文なしの記録を作ってスターを付ける。 */
    suspend fun setFavorite(boardId: String, threadKey: Long, favorite: Boolean, title: String? = null) {
        // 新しく記録を作るときだけ使うタイトル（トランザクションの外で先に決める）
        val newTitle = if (favorite) title ?: threadDao.getSummary(boardId, threadKey)?.title ?: threadKey.toString() else ""
        threadDao.setFavorite(boardId, threadKey, favorite, newTitle)
    }

    suspend fun removeFromHistory(boardId: String, threadKey: Long) =
        threadDao.removeFromHistory(boardId, threadKey)

    suspend fun addMyPost(boardId: String, threadKey: Long, number: Int) = threadDao.addMyPost(boardId, threadKey, number)

    suspend fun postReply(
        boardId: String,
        threadKey: Long,
        name: String,
        mail: String,
        body: String,
        confirmationFields: Map<String, String> = emptyMap(),
        submitLabel: String? = null,
    ): PostReplyResult {
        val board = boardDao.get(boardId) ?: return PostReplyResult.Failed(SourceError.PARSE, reason = PostFailure.BOARD_UNKNOWN)
        val ref = ThreadRef(BoardRef(board.source, board.host, board.boardKey), threadKey)
        val result = sourceOf(board.source).postReply(
            PostReplyRequest(
                thread = ref,
                name = name,
                mail = mail,
                body = body,
                confirmationFields = confirmationFields,
                submitLabel = submitLabel,
            ),
        )
        if (result is PostReplyResult.Success) threadDao.markPosted(boardId, threadKey)
        return result
    }
}

/** 板の ID（"掲示板の ID:板キー"）から掲示板の ID を取り出す。 */
private fun siteOf(boardId: String): String = boardId.substringBeforeLast(':')

/** このレス数を超えたら、次スレを探し始める（次スレは 950〜980 前後で立つことが多い）。 */
private const val NEXT_THREAD_FROM = 900

/** NG の期限。ID は日付で変わるので2日、ワッチョイは約1週間で変わるので8日。 */
private const val NG_ID_TTL_MS = 2L * 24 * 60 * 60 * 1000
private const val NG_WACCHOI_TTL_MS = 8L * 24 * 60 * 60 * 1000
