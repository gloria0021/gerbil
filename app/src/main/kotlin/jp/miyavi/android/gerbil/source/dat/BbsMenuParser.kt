package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.core.text.HtmlText
import jp.miyavi.android.gerbil.source.BoardInfo
import jp.miyavi.android.gerbil.source.CategoryWithBoards
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

/**
 * bbsmenu を解析する。JSON 形式（`menu_list`）と旧来の HTML 形式（`<B>カテゴリ</B>` と `<A HREF=板URL>板名</A>`）を読む。
 * 板の URL として読めないもの（外部リンク等）は [urls] の規則で除く。
 */
object BbsMenuParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val htmlItem = Regex(
        """<B>(.*?)</B>|<A\s+HREF\s*=\s*["']?([^"'\s>]+)["']?[^>]*>(.*?)</A>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(text: String, urls: UrlRules = DatUrls): List<CategoryWithBoards> =
        if (text.trimStart().startsWith("{")) parseJson(text, urls) else parseHtml(text, urls)

    private fun parseJson(text: String, urls: UrlRules): List<CategoryWithBoards> {
        val menu = json.decodeFromString<BbsMenu>(text)
        return menu.menuList.mapNotNull { category ->
            val boards = category.content.mapNotNull { entry ->
                val parsed = urls.parseBoardUrl(entry.url) ?: return@mapNotNull null
                BoardInfo(name = entry.boardName, host = parsed.first, boardKey = parsed.second)
            }
            if (boards.isEmpty()) null else CategoryWithBoards(category.name, boards)
        }
    }

    private fun parseHtml(text: String, urls: UrlRules): List<CategoryWithBoards> {
        val result = mutableListOf<CategoryWithBoards>()
        var category: String? = null
        var boards = mutableListOf<BoardInfo>()
        fun flush() {
            if (category != null && boards.isNotEmpty()) result += CategoryWithBoards(category!!, boards)
            boards = mutableListOf()
        }
        htmlItem.findAll(text).forEach { m ->
            if (m.groupValues[1].isNotEmpty()) {
                flush()
                category = clean(m.groupValues[1])
            } else {
                val parsed = urls.parseBoardUrl(m.groupValues[2]) ?: return@forEach
                if (category != null) boards += BoardInfo(name = clean(m.groupValues[3]), host = parsed.first, boardKey = parsed.second)
            }
        }
        flush()
        return result
    }

    private fun clean(s: String) = HtmlText.unescape(HtmlText.stripTags(s)).trim()

    @Serializable
    private data class BbsMenu(@SerialName("menu_list") val menuList: List<Category> = emptyList())

    @Serializable
    private data class Category(
        @SerialName("category_name") val name: String = "",
        @SerialName("category_content") val content: List<Entry> = emptyList(),
    )

    @Serializable
    private data class Entry(
        @SerialName("board_name") val boardName: String = "",
        val url: String = "",
    )
}
