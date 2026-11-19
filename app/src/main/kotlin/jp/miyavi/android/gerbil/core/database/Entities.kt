package jp.miyavi.android.gerbil.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** 板。id = "<掲示板の ID>:<boardKey>"（source = 掲示板の ID）。カテゴリは板の属性として持つ（一覧表示にしか使わないため）。 */
@Entity(tableName = "boards", primaryKeys = ["id"], indices = [Index("categoryOrder", "boardOrder")])
data class BoardEntity(
    val id: String,
    val source: String,
    val boardKey: String,
    val host: String,
    val name: String,
    val categoryName: String,
    val categoryOrder: Int,
    val boardOrder: Int,
    val isFavorite: Boolean = false,
    val favoriteOrder: Int? = null,
)

/**
 * 利用者が登録した掲示板（spec 003）。[id] は登録した URL のホスト（板の ID の前半に使う）。
 * [menuUrl] は bbsmenu の URL。板の URL で登録したときは null で、その板だけを持つ。
 */
@Entity(tableName = "sites", primaryKeys = ["id"])
data class SiteEntity(
    val id: String,
    val label: String,
    val url: String,
    val menuUrl: String?,
    val sortOrder: Int,
)

/**
 * NG（端末の中だけで隠す。掲示板にもほかの人にも送らない）。板ごとに、ワッチョイ（約1週間同じ）か ID（その日だけ）で持つ。
 * [kind] は "wacchoi" か "id"。期限を過ぎたものは消す。
 */
@Entity(tableName = "ng_entries", primaryKeys = ["boardId", "kind", "value"])
data class NgEntity(
    val boardId: String,
    val kind: String,
    val value: String,
    val createdAt: Long,
)

/** 板一覧の並び。同じ板が複数のカテゴリに載ることがあるため、板とは別に持つ。 */
@Entity(tableName = "menu_entries", primaryKeys = ["categoryOrder", "boardOrder"])
data class MenuEntryEntity(
    val categoryOrder: Int,
    val boardOrder: Int,
    val categoryName: String,
    val boardId: String,
)

/** ドロワーの1行（カテゴリ名＋板）。 */
data class MenuRow(
    val menuCategory: String,
    @androidx.room.Embedded val board: BoardEntity,
)

/** スレ一覧のキャッシュ。 */
@Entity(tableName = "thread_summaries", primaryKeys = ["boardId", "threadKey"])
data class ThreadSummaryEntity(
    val boardId: String,
    val threadKey: Long,
    val title: String,
    val resCount: Int,
    val rank: Int,
)

enum class ThreadState { ACTIVE, ARCHIVED, DELETED }

/** 端末に保存したスレ。本文は dat ファイル（[jp.miyavi.android.gerbil.data.LogStorage]）に置く。 */
@Entity(tableName = "thread_logs", primaryKeys = ["boardId", "threadKey"], indices = [Index("lastViewedAt")])
data class ThreadLogEntity(
    val boardId: String,
    val threadKey: Long,
    val title: String,
    val state: ThreadState = ThreadState.ACTIVE,
    val byteSize: Long = 0,
    val lastModified: String? = null,
    val lastFetchedAt: Long = 0,
    val resCount: Int = 0,
    /** 最後に画面の先頭にあったレス番号（1 始まり）。 */
    val readPosition: Int = 0,
    val readOffsetPx: Int = 0,
    /** 直近の更新で増えた最初のレス番号。 */
    val newFrom: Int? = null,
    val isFavorite: Boolean = false,
    val lastViewedAt: Long? = null,
    /** このスレに自分が書き込んだことがあるか。 */
    @ColumnInfo(defaultValue = "0") val hasPosted: Boolean = false,
    /** 自分が書き込んだレスの番号（カンマ区切り）。書き込み後の更新で、送った本文と一致するレスを探して足す。 */
    @ColumnInfo(defaultValue = "") val myPosts: String = "",
) {
    fun myPostNumbers(): Set<Int> = myPosts.split(',').mapNotNullTo(HashSet()) { it.trim().toIntOrNull() }
}

data class CachedThreadTitle(val boardId: String, val threadKey: Long, val title: String)

/** スレ選択の各行に、一覧情報と保存状態をまとめたもの。 */
data class ThreadRow(
    val boardId: String,
    val threadKey: Long,
    val title: String,
    val resCount: Int,
    val rank: Int,
    val readCount: Int?,
    val state: ThreadState?,
    val boardName: String?,
    val isFavorite: Boolean,
    val hasPosted: Boolean,
)
