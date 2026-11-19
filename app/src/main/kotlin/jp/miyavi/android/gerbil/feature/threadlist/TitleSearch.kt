package jp.miyavi.android.gerbil.feature.threadlist

import java.text.Normalizer

/**
 * スレタイの絞り込み。空白で区切った語をすべて含むものに一致する（AND）。
 * 全角・半角と英字の大小は区別しない。
 */
object TitleSearch {
    fun terms(query: String): List<String> =
        normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(title: String, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val t = normalize(title)
        return terms.all { t.contains(it) }
    }

    private fun normalize(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
}
