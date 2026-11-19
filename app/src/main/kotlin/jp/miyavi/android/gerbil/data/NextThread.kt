package jp.miyavi.android.gerbil.data

import java.text.Normalizer

/**
 * 次スレを探す。スレタイから番号・記号・空白を除いた残りが似ていて、元のスレより後に立ったスレを選ぶ。
 * 番号（タイトルの最後の数字。part19 の 19 など）が両方にあれば、次スレの方が大きいものだけにする。
 */
object NextThread {
    /** 似ていると見なす下限（文字の2つ組の重なりの割合）。 */
    private const val MIN_SIMILARITY = 0.6

    data class Candidate(val threadKey: Long, val title: String)

    fun find(title: String, threadKey: Long, candidates: List<Candidate>): Candidate? {
        val base = bigrams(normalize(title))
        if (base.isEmpty()) return null
        val number = lastNumber(title)
        return candidates.asSequence()
            .filter { it.threadKey > threadKey }
            .filter { c -> number == null || lastNumber(c.title).let { it == null || it > number } }
            .map { it to similarity(base, bigrams(normalize(it.title))) }
            .filter { it.second >= MIN_SIMILARITY }
            // 最も似ているもの。同じなら先に立ったもの
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenBy { it.first.threadKey })
            .firstOrNull()?.first
    }

    private fun normalize(title: String): String =
        Normalizer.normalize(title, Normalizer.Form.NFKC).lowercase()
            .replace(Regex("""part|その|第|代目|スレ目""") , "")
            .filter { it.isLetter() }

    private fun lastNumber(title: String): Int? =
        Regex("""\d+""").findAll(Normalizer.normalize(title, Normalizer.Form.NFKC)).lastOrNull()?.value?.toIntOrNull()

    private fun bigrams(s: String): Set<String> =
        if (s.length < 2) setOfNotNull(s.takeIf { it.isNotEmpty() }) else (0 until s.length - 1).mapTo(HashSet()) { s.substring(it, it + 2) }

    private fun similarity(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / (a union b).size
}
