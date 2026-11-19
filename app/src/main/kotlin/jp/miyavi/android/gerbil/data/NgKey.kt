package jp.miyavi.android.gerbil.data

import jp.miyavi.android.gerbil.source.Post

/**
 * NG の対象。ワッチョイ（名前欄の `(ﾜｯﾁｮｲ ab12-cd34)` などの `ab12-cd34` 全体）があればそれ、なければ ID。
 * ワッチョイの後ろ4桁だけでは NG しない（同じ機種の人を巻き込むため）。
 */
data class NgKey(val kind: String, val value: String) {
    companion object {
        const val WACCHOI = "wacchoi"
        const val ID = "id"
        private val wacchoi = Regex("""\(\S+ ([0-9A-Za-z+/]{4}-[0-9A-Za-z+/]{4})\)""")

        fun of(post: Post): NgKey? {
            if (post.isAbone) return null
            wacchoi.find(post.name)?.let { return NgKey(WACCHOI, it.groupValues[1]) }
            return post.id?.takeIf { it.length >= MIN_ID_LENGTH && '?' !in it }?.let { NgKey(ID, it) }
        }

        /** [post] が NG か（ワッチョイか ID のどちらかが一致）。 */
        fun matches(post: Post, ng: Set<NgKey>): Boolean {
            if (ng.isEmpty() || post.isAbone) return false
            val w = wacchoi.find(post.name)?.groupValues?.get(1)
            return (w != null && NgKey(WACCHOI, w) in ng) || (post.id != null && NgKey(ID, post.id) in ng)
        }

        private const val MIN_ID_LENGTH = 8
    }
}
