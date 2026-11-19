package jp.miyavi.android.gerbil.feature.thread

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class SummaryPost(val number: Int, val text: String)
sealed interface SummaryProgress {
    data class Posts(val first: Int, val last: Int, val completed: Int, val total: Int) : SummaryProgress
    data object Merging : SummaryProgress
}
class SummaryTooLarge : Exception()
class SummaryPostTooLarge(val number: Int) : Exception()
class SummaryMergeFailed : Exception()

/** 入力上限を超えた範囲だけ二分し、部分要約を時系列に統合する。 */
class ThreadSummarizer(
    private val fits: suspend (List<String>, Boolean) -> Boolean,
    private val generate: suspend (List<String>, Boolean) -> String,
) {
    /**
     * [onPartial] は部分要約ができるたびに、その元のレス番号の範囲と内容を渡す（待つあいだに読めるよう、画面に順に出すため）。
     */
    suspend fun summarize(
        posts: List<SummaryPost>,
        onPartial: (first: Int, last: Int, text: String) -> Unit = { _, _, _ -> },
        /** false なら部分要約をまとめ直さず、そのまま（時系列に）つないで返す。 */
        merge: Boolean = true,
        progress: (SummaryProgress) -> Unit,
    ): String {
        require(posts.isNotEmpty())
        var completed = 0
        suspend fun parts(group: List<SummaryPost>): List<String> {
            currentCoroutineContext().ensureActive()
            progress(SummaryProgress.Posts(group.first().number, group.last().number, completed, posts.size))
            return try {
                val result = generate(group.map { "${it.number}: ${it.text}" }, false)
                currentCoroutineContext().ensureActive()
                if (result.isBlank()) throw SummaryMergeFailed()
                completed += group.size
                onPartial(group.first().number, group.last().number, result)
                listOf(result)
            } catch (_: SummaryTooLarge) {
                if (group.size == 1) throw SummaryPostTooLarge(group.single().number)
                val mid = (group.size + 1) / 2
                parts(group.take(mid)) + parts(group.drop(mid))
            }
        }
        val summaries = mutableListOf<String>()
        var start = 0
        while (start < posts.size) {
            val remaining = posts.size - start
            suspend fun fitsCount(count: Int): Boolean {
                currentCoroutineContext().ensureActive()
                val group = posts.subList(start, start + count)
                // 入る件数を測るだけの段階は一瞬で終わるので、進捗には出さない（範囲が高速で切り替わって見えるため）
                return try {
                    fits(group.map { "${it.number}: ${it.text}" }, false)
                } catch (_: SummaryTooLarge) {
                    false
                }
            }
            if (!fitsCount(1)) throw SummaryPostTooLarge(posts[start].number)
            var lower = 1
            var upper = minOf(2, remaining)
            while (upper > lower && fitsCount(upper)) {
                lower = upper
                upper = minOf(remaining.toLong(), upper.toLong() * 2).toInt()
            }
            if (upper > lower) {
                while (upper - lower > 1) {
                    val middle = lower + (upper - lower) / 2
                    if (fitsCount(middle)) lower = middle else upper = middle
                }
            }
            summaries += parts(posts.subList(start, start + lower))
            start += lower
        }
        if (!merge) return summaries.joinToString("\n\n")
        var pending = summaries.toList()
        while (pending.size > 1) {
            currentCoroutineContext().ensureActive()
            progress(SummaryProgress.Merging)
            suspend fun compress(text: String): List<String> {
                currentCoroutineContext().ensureActive()
                return try {
                    if (!fits(listOf(text), true)) throw SummaryTooLarge()
                    val result = generate(listOf(text), true)
                    currentCoroutineContext().ensureActive()
                    if (result.isBlank()) throw SummaryMergeFailed()
                    listOf(result)
                } catch (_: SummaryTooLarge) {
                    val count = text.codePointCount(0, text.length)
                    if (count <= 1) throw SummaryMergeFailed()
                    val middle = text.offsetByCodePoints(0, count / 2)
                    compress(text.substring(0, middle)) + compress(text.substring(middle))
                }
            }
            suspend fun merge(group: List<String>): List<String> {
                if (group.size == 1) return group
                return try {
                    val result = generate(group, true)
                    currentCoroutineContext().ensureActive()
                    if (result.isBlank()) throw SummaryMergeFailed()
                    listOf(result)
                } catch (_: SummaryTooLarge) {
                    if (group.size == 2) return compress(group[0]) + compress(group[1])
                    val mid = (group.size + 1) / 2
                    merge(group.take(mid)) + merge(group.drop(mid))
                }
            }
            val next = merge(pending)
            if (next.size >= pending.size && next.sumOf { it.length.toLong() } >= pending.sumOf { it.length.toLong() }) throw SummaryMergeFailed()
            pending = next
        }
        return pending.single()
    }
}
