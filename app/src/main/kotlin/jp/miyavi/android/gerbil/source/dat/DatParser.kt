package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.core.text.HtmlText
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.Segment

/** dat（`名前<>メール<>日付 ID<>本文<>タイトル`）を解析する。 */
object DatParser {
    private val shortDate = Regex("""\d{2,4}/(\d{2})/(\d{2})\S*\s+(\d{2}):(\d{2})""")

    fun parse(text: String): List<Post> {
        val lines = text.split('\n')
        val count = if (lines.lastOrNull().isNullOrEmpty()) lines.size - 1 else lines.size
        return List(count) { i -> parseLine(lines[i], i + 1) }
    }

    /** dat の1行目の5列目がスレタイ。 */
    fun parseTitle(firstLine: String): String? =
        firstLine.split("<>").getOrNull(4)?.let { HtmlText.unescape(HtmlText.stripTags(it)).trim() }?.ifEmpty { null }

    fun parseLine(line: String, number: Int): Post {
        val fields = line.trimEnd('\r').split("<>")
        if (fields.size < 4) return abone(number)
        val name = cleanInline(fields[0])
        val mail = cleanInline(fields[1])
        val dateField = HtmlText.unescape(HtmlText.stripTags(fields[2])).trim()
        if (name == "あぼーん" && mail == "あぼーん" && dateField == "あぼーん") return abone(number)

        val idIndex = dateField.indexOf(" ID:")
        val beIndex = dateField.indexOf(" BE:")
        val dateEnd = listOf(idIndex, beIndex).filter { it >= 0 }.minOrNull() ?: dateField.length
        val dateText = dateField.substring(0, dateEnd).trim()
        val id = if (idIndex >= 0) dateField.substring(idIndex + 4).substringBefore(' ').ifEmpty { null } else null
        val be = if (beIndex >= 0) dateField.substring(beIndex + 4).substringBefore(' ').ifEmpty { null } else null

        val body = BodySegmenter.segment(fields[3])
        return Post(
            number = number,
            name = name,
            mail = mail,
            dateText = dateText,
            shortDate = shortDate.find(dateText)?.let { m ->
                val (mo, d, h, mi) = m.destructured
                "$mo/$d $h:$mi"
            },
            id = id,
            be = be,
            body = body,
            isAA = BodySegmenter.looksLikeAA(body.joinToString("") { it.text }),
            isAbone = false,
        )
    }

    private fun cleanInline(s: String): String =
        HtmlText.unescape(HtmlText.stripTags(s)).replace(Regex("\\s+"), " ").trim()

    private fun abone(number: Int) = Post(
        number = number, name = "あぼーん", mail = "", dateText = "", shortDate = null, id = null, be = null,
        body = listOf(Segment.Text("あぼーん")), isAA = false, isAbone = true,
    )
}
