package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.core.text.HtmlText
import jp.miyavi.android.gerbil.source.Segment

/** dat 本文の HTML を、表示用のセグメント（テキスト・アンカー・リンク）に分解する。 */
object BodySegmenter {
    private val br = Regex(""" ?<br> ?""", RegexOption.IGNORE_CASE)
    private val token = Regex(
        """(?<anchor>(?:>>|＞＞|≫)(?:[0-9０-９]+(?:[-－][0-9０-９]+)?)(?:[,、][0-9０-９]+(?:[-－][0-9０-９]+)?)*)""" +
            """|(?<url>h?ttps?://[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)""",
    )
    private val imageExt = Regex("""\.(jpe?g|png|gif|webp)$""", RegexOption.IGNORE_CASE)
    private val videoExt = Regex("""\.(mp4|webm|mov|m4v)$""", RegexOption.IGNORE_CASE)
    private val imageQueryFormat = Regex("""(?:^|[?&])format=(jpe?g|png|gif|webp)(?:&|$)""", RegexOption.IGNORE_CASE)
    private val aaChars = "＿￣／＼｜|∧∨´｀∀ﾟ・ω（）()ノヽ从⌒￤┃━┏┓┗┛"

    /** URL が動画（mp4 / webm / mov / m4v）を指すか。プレビューと全画面表示の切り替えに使う。 */
    fun isVideoUrl(url: String): Boolean = videoExt.containsMatchIn(url.substringBefore('?').substringBefore('#'))

    fun segment(html: String): List<Segment> {
        var s = html
        if (s.startsWith(" ")) s = s.substring(1)
        if (s.endsWith(" ")) s = s.dropLast(1)
        val text = HtmlText.unescape(HtmlText.stripTags(br.replace(s, "\n")))
        return tokenize(text)
    }

    fun tokenize(text: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var last = 0
        for (m in token.findAll(text)) {
            if (m.range.first > last) out += Segment.Text(text.substring(last, m.range.first))
            val anchor = m.groups["anchor"]
            if (anchor != null) {
                out += Segment.Anchor(m.value, parseTargets(m.value))
            } else {
                val raw = m.value
                val url = if (raw.startsWith("ttp")) "h$raw" else raw
                val path = url.substringBefore('?').substringBefore('#')
                val query = url.substringAfter('?', "").substringBefore('#')
                val isImage = imageExt.containsMatchIn(path) || imageQueryFormat.containsMatchIn(query)
                val isVideo = !isImage && isVideoUrl(url)
                out += Segment.Link(raw, url, isImage, isVideo)
            }
            last = m.range.last + 1
        }
        if (last < text.length) out += Segment.Text(text.substring(last))
        return mergeText(out)
    }

    private fun parseTargets(anchor: String): List<IntRange> {
        val digits = normalizeDigits(anchor).trimStart('>', '＞', '≫')
        return digits.split(',', '、').mapNotNull { part ->
            val ends = part.split('-', '－').mapNotNull { it.toIntOrNull() }
            when (ends.size) {
                1 -> ends[0]..ends[0]
                2 -> minOf(ends[0], ends[1])..maxOf(ends[0], ends[1])
                else -> null
            }
        }.filter { it.first >= 1 }
    }

    private fun normalizeDigits(s: String) = buildString {
        for (c in s) append(if (c in '０'..'９') '0' + (c - '０') else c)
    }

    private fun mergeText(list: List<Segment>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (seg in list) {
            val prev = out.lastOrNull()
            if (seg is Segment.Text && prev is Segment.Text) out[out.lastIndex] = Segment.Text(prev.text + seg.text)
            else out += seg
        }
        return out
    }

    /** AA らしさの判定。3行以上で、AA 特有の記号か全角スペースのインデントが多いもの。 */
    fun looksLikeAA(text: String): Boolean {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 3) return false
        val visible = text.count { !it.isWhitespace() }
        if (visible == 0) return false
        val symbolRatio = text.count { it in aaChars }.toDouble() / visible
        val indented = lines.count { it.startsWith("　") || it.startsWith("　 ") }.toDouble() / lines.size
        return symbolRatio >= 0.2 || (indented >= 0.6 && symbolRatio >= 0.08)
    }
}
