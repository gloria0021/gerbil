package jp.miyavi.android.gerbil.core.database

import androidx.room.OnConflictStrategy
import androidx.room.Insert
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY categoryOrder, boardOrder")
    fun observeAll(): Flow<List<BoardEntity>>

    @Query(
        "SELECT m.categoryName AS menuCategory, b.* FROM menu_entries m JOIN boards b ON b.id = m.boardId " +
            "ORDER BY m.categoryOrder, m.boardOrder",
    )
    fun observeMenu(): Flow<List<MenuRow>>

    @Transaction
    suspend fun replaceMenu(entries: List<MenuEntryEntity>) {
        deleteMenu()
        insertMenu(entries)
    }

    @Query("DELETE FROM menu_entries")
    suspend fun deleteMenu()

    @Upsert
    suspend fun insertMenu(entries: List<MenuEntryEntity>)

    @Query("SELECT * FROM boards WHERE id = :id")
    suspend fun get(id: String): BoardEntity?

    @Query("SELECT * FROM boards WHERE host = :host AND boardKey = :boardKey LIMIT 1")
    suspend fun findByHostKey(host: String, boardKey: String): BoardEntity?

    @Query("SELECT * FROM boards WHERE source = :source AND boardKey = :boardKey LIMIT 1")
    suspend fun findBySourceKey(source: String, boardKey: String): BoardEntity?

    @Query("SELECT * FROM boards WHERE source = :source")
    suspend fun getBySource(source: String): List<BoardEntity>

    @Query("DELETE FROM boards WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT * FROM sites ORDER BY sortOrder")
    fun observeSites(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites ORDER BY sortOrder")
    suspend fun getSites(): List<SiteEntity>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getSite(id: String): SiteEntity?

    @Upsert
    suspend fun upsertSite(site: SiteEntity)

    @Query("UPDATE sites SET label = :label WHERE id = :id")
    suspend fun renameSite(id: String, label: String)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun deleteSite(id: String)

    @Query("SELECT * FROM boards WHERE id = :id")
    fun observe(id: String): Flow<BoardEntity?>

    @Query("SELECT COUNT(*) FROM boards")
    suspend fun count(): Int

    @Query("SELECT * FROM boards")
    suspend fun getAll(): List<BoardEntity>

    @Upsert
    suspend fun upsertAll(boards: List<BoardEntity>)

    @Query("DELETE FROM boards WHERE id IN (:ids) AND isFavorite = 0")
    suspend fun deleteUnfavorited(ids: List<String>)

    @Query("UPDATE boards SET isFavorite = :favorite, favoriteOrder = :order WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, order: Int?)

    /** スター付きの板を開いたら、並び順（最後に開いた時刻）を更新する。 */
    @Query("UPDATE boards SET favoriteOrder = :order WHERE id = :id AND isFavorite = 1")
    suspend fun touchFavorite(id: String, order: Int)
}

@Dao
interface ThreadDao {
    @Query(
        """
        SELECT s.boardId, s.threadKey, s.title, s.resCount, s.rank,
               l.resCount AS readCount, l.state AS state, b.name AS boardName,
               IFNULL(l.isFavorite, 0) AS isFavorite, IFNULL(l.hasPosted, 0) AS hasPosted
        FROM thread_summaries s
        LEFT JOIN thread_logs l ON l.boardId = s.boardId AND l.threadKey = s.threadKey
        LEFT JOIN boards b ON b.id = s.boardId
        WHERE s.boardId = :boardId
        ORDER BY s.rank
        """,
    )
    fun observeBoardThreads(boardId: String): Flow<List<ThreadRow>>


    /** 閲覧タブ: 開いたことのあるスレ。スター付きを先に並べる。履歴を消してもスター付きは残す。 */
    @Query(
        """
        SELECT l.boardId, l.threadKey, l.title, MAX(l.resCount, IFNULL(s.resCount, 0)) AS resCount, 0 AS `rank`,
               l.resCount AS readCount, l.state AS state, b.name AS boardName, l.isFavorite AS isFavorite,
               l.hasPosted AS hasPosted
        FROM thread_logs l
        LEFT JOIN thread_summaries s ON s.boardId = l.boardId AND s.threadKey = l.threadKey
        LEFT JOIN boards b ON b.id = l.boardId
        WHERE l.lastViewedAt IS NOT NULL OR l.isFavorite = 1
        ORDER BY l.isFavorite DESC, l.lastViewedAt DESC
        """,
    )
    fun observeHistory(): Flow<List<ThreadRow>>

    @Query(
        """
        SELECT boardId, threadKey, title FROM thread_summaries
        UNION
        SELECT boardId, threadKey, title FROM thread_logs
        WHERE lastViewedAt IS NOT NULL OR isFavorite = 1
        ORDER BY threadKey DESC
        """,
    )
    suspend fun cachedThreadTitles(): List<CachedThreadTitle>

    @Transaction
    suspend fun replaceSummaries(boardId: String, rows: List<ThreadSummaryEntity>) {
        deleteSummaries(boardId)
        insertSummaries(rows)
    }

    @Query("DELETE FROM thread_summaries WHERE boardId = :boardId")
    suspend fun deleteSummaries(boardId: String)

    @Upsert
    suspend fun insertSummaries(rows: List<ThreadSummaryEntity>)

    @Query("SELECT * FROM thread_summaries WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun getSummary(boardId: String, threadKey: Long): ThreadSummaryEntity?

    @Query("SELECT * FROM ng_entries WHERE boardId = :boardId")
    fun observeNg(boardId: String): Flow<List<NgEntity>>

    @Upsert
    suspend fun addNg(entry: NgEntity)

    @Query("DELETE FROM ng_entries WHERE boardId = :boardId AND kind = :kind AND value = :value")
    suspend fun removeNg(boardId: String, kind: String, value: String)

    /** 期限を過ぎた NG を消す（ID はその日だけ、ワッチョイは約1週間で変わるため）。 */
    @Query("DELETE FROM ng_entries WHERE kind = :kind AND createdAt < :before")
    suspend fun purgeNg(kind: String, before: Long)

    @Query("SELECT * FROM thread_summaries WHERE boardId = :boardId")
    suspend fun getSummaries(boardId: String): List<ThreadSummaryEntity>

    /** 次スレを追尾しているスレ（スター付き）。 */
    @Query("SELECT * FROM thread_logs WHERE boardId = :boardId AND isFavorite = 1")
    suspend fun getFollowed(boardId: String): List<ThreadLogEntity>

    @Query("SELECT * FROM thread_logs WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun getLog(boardId: String, threadKey: Long): ThreadLogEntity?

    @Query("SELECT * FROM thread_logs WHERE boardId = :boardId AND threadKey = :threadKey")
    fun observeLog(boardId: String, threadKey: Long): Flow<ThreadLogEntity?>

    @Upsert
    suspend fun upsertLog(log: ThreadLogEntity)

    /**
     * 取得結果（タイトル・状態・サイズ・件数・新着位置）だけを書く。
     * 通信中に保存された既読位置・スター・書き込み済み・閲覧日時は、最新の行の値を残す。
     */
    @Transaction
    suspend fun saveFetchedLog(fetched: ThreadLogEntity) {
        val current = getLog(fetched.boardId, fetched.threadKey)
        upsertLog(
            current?.copy(
                title = fetched.title,
                state = fetched.state,
                byteSize = fetched.byteSize,
                lastModified = fetched.lastModified,
                lastFetchedAt = fetched.lastFetchedAt,
                resCount = fetched.resCount,
                newFrom = fetched.newFrom,
            ) ?: fetched,
        )
    }

    @Query(
        "UPDATE thread_logs SET readPosition = :position, readOffsetPx = :offset " +
            "WHERE boardId = :boardId AND threadKey = :threadKey",
    )
    suspend fun updateReadPosition(boardId: String, threadKey: Long, position: Int, offset: Int)

    @Query("UPDATE thread_logs SET lastViewedAt = :at WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun updateLastViewed(boardId: String, threadKey: Long, at: Long)

    @Query("UPDATE thread_logs SET isFavorite = :favorite WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun updateFavorite(boardId: String, threadKey: Long, favorite: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLogIfAbsent(log: ThreadLogEntity): Long

    /**
     * スターの列だけを書く。行がなければ（まだ開いていないスレ）スター付きの記録を作る。
     * 行全体を上書きしないので、同時に保存された取得結果・既読位置を巻き戻さない。
     */
    @Transaction
    suspend fun setFavorite(boardId: String, threadKey: Long, favorite: Boolean, titleIfNew: String) {
        if (updateFavorite(boardId, threadKey, favorite) == 0 && favorite) {
            insertLogIfAbsent(ThreadLogEntity(boardId = boardId, threadKey = threadKey, title = titleIfNew, isFavorite = true))
        }
    }

    @Query("UPDATE thread_logs SET hasPosted = 1 WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun markPosted(boardId: String, threadKey: Long)

    @Query("SELECT myPosts FROM thread_logs WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun myPosts(boardId: String, threadKey: Long): String?

    @Query("UPDATE thread_logs SET myPosts = :myPosts WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun setMyPosts(boardId: String, threadKey: Long, myPosts: String)

    /** 自分が書き込んだレスの番号を足す。 */
    @Transaction
    suspend fun addMyPost(boardId: String, threadKey: Long, number: Int) {
        val current = myPosts(boardId, threadKey)?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (number !in current) setMyPosts(boardId, threadKey, (current + number).sorted().joinToString(","))
    }

    @Query("UPDATE thread_logs SET lastViewedAt = NULL")
    suspend fun clearHistory()

    @Query("UPDATE thread_logs SET lastViewedAt = NULL, isFavorite = 0 WHERE boardId = :boardId AND threadKey = :threadKey")
    suspend fun removeFromHistory(boardId: String, threadKey: Long)
}
