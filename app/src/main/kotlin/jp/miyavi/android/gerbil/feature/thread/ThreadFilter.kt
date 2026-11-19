package jp.miyavi.android.gerbil.feature.thread

import androidx.annotation.StringRes
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.Segment

/** スレ本文の絞り込み。 */
enum class ThreadFilter(@StringRes val label: Int) {
    /** リンクがあるレス。名前は保存済み画面状態の復元のため維持する。 */
    MEDIA(R.string.thread_filter_links),

    /** 返信を多く受けたレスの上位5件（多い順）。 */
    TOP_REPLIES(R.string.thread_filter_top_replies),

    /** 書き込みの多い ID 上位5人のレス（スレの順）。 */
    TOP_POSTERS(R.string.thread_filter_top_posters),
}

private const val TOP_COUNT = 5

/** 絞り込んだレス。[filter] が null ならそのまま。 */
internal fun List<Post>.filtered(filter: ThreadFilter?, index: PostIndex): List<Post> = when (filter) {
    null -> this
    ThreadFilter.MEDIA -> filter { post -> post.body.any { it is Segment.Link } }
    ThreadFilter.TOP_REPLIES -> filter { index.replyCount(it.number) > 0 }
        .sortedWith(compareByDescending<Post> { index.replyCount(it.number) }.thenBy { it.number })
        .take(TOP_COUNT)
    // 同じ人は最初の書き込みだけを多い順に出す（ほかの書き込みは「レス m」のチップで見られる）
    ThreadFilter.TOP_POSTERS -> index.topIds(TOP_COUNT).mapNotNull { id -> firstOrNull { it.id == id } }
}

/** 本文に [query] を含むレスの位置（[posts] の中の添字）。大文字・小文字は区別しない。空なら空。 */
internal fun searchMatches(posts: List<Post>, query: String): List<Int> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return posts.indices.filter { i -> posts[i].body.any { it.text.contains(q, ignoreCase = true) } }
}
