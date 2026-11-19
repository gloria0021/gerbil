package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.core.text.HtmlText
import jp.miyavi.android.gerbil.source.ThreadHeader

/** subject.txt（`<key>.dat<>タイトル (レス数)`）を解析する。不正な行は読み飛ばす。 */
object SubjectParser {
    private val line = Regex("""^(\d+)\.(?:dat|cgi)<>(.*)\((\d+)\)\s*$""")

    fun parse(text: String): List<ThreadHeader> {
        var rank = 0
        return text.lineSequence().mapNotNull { raw ->
            val m = line.find(raw.trimEnd('\r')) ?: return@mapNotNull null
            val key = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            rank++
            ThreadHeader(
                threadKey = key,
                title = HtmlText.unescape(m.groupValues[2]).trim(),
                resCount = m.groupValues[3].toInt(),
                rank = rank,
            )
        }.toList()
    }

    /** 勢い（1日あたりのレス数）。作成時刻に使えない未来のスレ番号は不明とする。 */
    fun momentum(resCount: Int, threadKey: Long, nowEpochSeconds: Long): Double? {
        if (threadKey > nowEpochSeconds) return null
        val elapsed = (nowEpochSeconds - threadKey).coerceAtLeast(60)
        return resCount * 86_400.0 / elapsed
    }
}
