package jp.miyavi.android.gerbil.source.dat

import jp.miyavi.android.gerbil.core.text.HtmlText

sealed interface BbsCgiResponse {
    data object Success : BbsCgiResponse
    data class Confirmation(
        val message: String,
        val hiddenFields: Map<String, String>,
        val submitValue: String,
    ) : BbsCgiResponse
    data class Error(val message: String) : BbsCgiResponse
}

/** 投稿先の `bbs.cgi` が返す HTML 応答を判定し、確認フォームやエラーメッセージを抽出する。 */
object BbsCgiParser {
    private val titleRegex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val bodyRegex = Regex("""<body[^>]*>(.*?)</body>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val stripScriptStyle = Regex("""<(script|style|head)[^>]*>.*?</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val stripForm = Regex("""<form[^>]*>.*?</form>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val brTag = Regex("""<br\s*/?>|</p>|</div>|</li>""", RegexOption.IGNORE_CASE)
    private val inputTag = Regex("""<input\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

    fun parse(html: String): BbsCgiResponse {
        val title = titleRegex.find(html)?.groupValues?.get(1)?.let { HtmlText.unescape(HtmlText.stripTags(it)).trim() }.orEmpty()
        return when {
            isSuccess(html, title) -> BbsCgiResponse.Success
            isConfirmation(html, title) -> parseConfirmation(html, title)
            else -> BbsCgiResponse.Error(extractMessage(html, stripFormBlock = false).ifEmpty { title })
        }
    }

    private fun isSuccess(html: String, title: String): Boolean =
        html.contains("2ch_X:true", ignoreCase = true) || title.contains("書きこみました") || title.contains("書き込みました")

    private fun isConfirmation(html: String, title: String): Boolean =
        html.contains("2ch_X:cookie", ignoreCase = true) ||
            html.contains("2ch_X:check", ignoreCase = true) ||
            title.contains("書き込み確認") ||
            title.contains("投稿確認")

    private fun parseConfirmation(html: String, title: String): BbsCgiResponse.Confirmation {
        val hidden = LinkedHashMap<String, String>()
        var submitValue = DEFAULT_CONFIRM_SUBMIT
        inputTag.findAll(html).forEach { match ->
            val attrs = parseAttrs(match.groupValues[1])
            val type = attrs["type"]?.lowercase().orEmpty()
            val name = attrs["name"].orEmpty()
            val value = HtmlText.unescape(attrs["value"].orEmpty())
            when {
                type == "hidden" && name.isNotEmpty() -> hidden[name] = value
                type == "submit" && (name == "submit" || name.isEmpty()) && value.isNotEmpty() -> submitValue = value
            }
        }
        val message = extractMessage(html, stripFormBlock = true).ifEmpty { title }
        return BbsCgiResponse.Confirmation(message = message, hiddenFields = hidden, submitValue = submitValue)
    }

    private fun parseAttrs(raw: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        attrRegex.findAll(raw).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            map[key] = value
        }
        return map
    }

    private fun extractMessage(html: String, stripFormBlock: Boolean): String {
        val body = bodyRegex.find(html)?.groupValues?.get(1) ?: html
        var cleaned = stripScriptStyle.replace(body, "")
        if (stripFormBlock) cleaned = stripForm.replace(cleaned, "")
        cleaned = brTag.replace(cleaned, "\n")
        cleaned = HtmlText.unescape(HtmlText.stripTags(cleaned))
        return cleaned.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private const val DEFAULT_CONFIRM_SUBMIT = "上記全てを承諾して書き込む"
}
