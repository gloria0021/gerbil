package jp.miyavi.android.gerbil.source

/** 板の所在。boardKey が同じなら host が変わっても同じ板とみなす（移転追従）。 */
data class BoardRef(val source: String, val host: String, val boardKey: String) {
    val id: String get() = boardId(source, boardKey)

    companion object {
        fun boardId(source: String, boardKey: String) = "$source:$boardKey"
    }
}

data class ThreadRef(val board: BoardRef, val threadKey: Long)

data class BoardInfo(val name: String, val host: String, val boardKey: String)

data class CategoryWithBoards(val name: String, val boards: List<BoardInfo>)

/** スレ一覧の1行。threadKey はスレ作成時刻（UNIX 秒）。 */
data class ThreadHeader(val threadKey: Long, val title: String, val resCount: Int, val rank: Int)

data class Post(
    val number: Int,
    val name: String,
    val mail: String,
    val dateText: String,
    /** 吹き出し等に使う短い日時 "MM/dd HH:mm"。解析できなければ null。 */
    val shortDate: String?,
    val id: String?,
    val be: String?,
    val body: List<Segment>,
    val isAA: Boolean,
    val isAbone: Boolean,
) {
    val isSage: Boolean get() = mail.equals("sage", ignoreCase = true)
}

sealed interface Segment {
    val text: String

    data class Text(override val text: String) : Segment

    /** `>>1`, `>>1-5`, `>>1,3` 形式。targets は 1 始まりのレス番号の範囲。 */
    data class Anchor(override val text: String, val targets: List<IntRange>) : Segment

    /** URL。[isImage] / [isVideo] はプレビューできる画像・動画（拡張子で判定）。 */
    data class Link(
        override val text: String,
        val url: String,
        val isImage: Boolean,
        val isVideo: Boolean = false,
    ) : Segment
}

/** URL を解析した結果。 */
sealed interface ParsedUrl {
    data class Board(val board: BoardRef) : ParsedUrl
    data class Thread(val thread: ThreadRef, val resNumber: Int?) : ParsedUrl
}
