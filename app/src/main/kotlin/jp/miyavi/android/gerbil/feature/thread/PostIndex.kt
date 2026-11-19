package jp.miyavi.android.gerbil.feature.thread

import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.Segment

/** 被参照数と ID 内の序数（n/m）。DB には保存せず、表示時に計算する。 */
class PostIndex(posts: List<Post>) {
    private val replies = HashMap<Int, MutableList<Int>>()
    private val idPosts = HashMap<String, MutableList<Int>>()
    private val idOrdinals = IntArray(posts.size)
    private val goreWarned = HashSet<Int>()

    init {
        posts.forEachIndexed { i, post ->
            val hasGoreKeyword = post.body.any { seg ->
                seg is Segment.Text && (seg.text.contains("グロ") || seg.text.contains("ぐろ"))
            }
            post.body.forEach { seg ->
                if (seg is Segment.Anchor) {
                    seg.targets.forEach { range ->
                        // 巨大な範囲アンカーで計算量が膨らまないよう上限を設ける
                        val last = minOf(range.last, range.first + MAX_RANGE, posts.size)
                        for (n in range.first..last) {
                            if (n != post.number) {
                                replies.getOrPut(n) { mutableListOf() }.let {
                                    if (it.lastOrNull() != post.number) it += post.number
                                }
                                if (hasGoreKeyword) goreWarned += n
                            }
                        }
                    }
                }
            }
            post.id?.takeIf { it.length >= MIN_ID_LENGTH && !it.startsWith("???") }?.let { id ->
                val list = idPosts.getOrPut(id) { mutableListOf() }
                list += post.number
                idOrdinals[i] = list.size
            }
        }
    }

    fun repliesTo(number: Int): List<Int> = replies[number].orEmpty()
    fun replyCount(number: Int): Int = replies[number]?.size ?: 0
    fun postsById(id: String?): List<Int> = id?.let { idPosts[it] }.orEmpty()
    fun idTotal(id: String?): Int = id?.let { idPosts[it]?.size } ?: 0
    fun idOrdinal(index: Int): Int = idOrdinals.getOrElse(index) { 0 }
    fun isGoreWarned(number: Int): Boolean = number in goreWarned

    /** 書き込みの多い ID の上位 [n] 件（2件以上書いた ID だけ。同数なら先に書いた方が上）。 */
    fun topIds(n: Int): List<String> = idPosts.entries
        .filter { it.value.size >= 2 }
        .sortedWith(compareByDescending<Map.Entry<String, MutableList<Int>>> { it.value.size }.thenBy { it.value.first() })
        .take(n)
        .map { it.key }

    companion object {
        private const val MAX_RANGE = 50
        private const val MIN_ID_LENGTH = 4
        const val MANY_POSTS = 5
    }
}

/** アンカーの範囲を、実在するレス番号（1..[postCount]）に絞って展開する。>>1-2147483647 のような範囲でも件数はレス数まで。 */
internal fun anchorNumbers(targets: List<IntRange>, postCount: Int): List<Int> =
    targets.flatMap { range -> maxOf(range.first, 1)..minOf(range.last, postCount) }.distinct()
