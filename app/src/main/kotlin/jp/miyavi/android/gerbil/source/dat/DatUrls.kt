package jp.miyavi.android.gerbil.source.dat

/** 板・スレの URL の読み方。掲示板ごとに規則を差し替えられるようにする。 */
interface UrlRules {
    /** 板 URL を (host, boardKey) にする。 */
    fun parseBoardUrl(url: String): Pair<String, String>?

    /** スレ URL を読む。 */
    fun parseThreadUrl(url: String): ThreadUrl?
}

data class ThreadUrl(val host: String, val boardKey: String, val threadKey: Long, val resNumber: Int?)

/**
 * 2ch 互換の掲示板の一般的な URL 規則（ホストは問わない）。
 * 板 = `https://host/板キー/`、スレ = `https://host/test/read.cgi/板キー/スレキー/[レス番号]`。
 * スマホ向けのページのように `/test/` の前にもう一段ある形（`https://host/何か/test/read.cgi/…`）もスレとして読む
 * （そのホストは板のサーバーとは限らないので、板は登録した板一覧から板キーで探す）。
 */
object DatUrls : UrlRules {
    private const val HOST = """([A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+)"""
    private val boardUrl = Regex("""^https?://$HOST/([A-Za-z0-9_]+)/?$""")
    private val threadUrl = Regex("""^https?://$HOST/(?:[A-Za-z0-9_-]+/)?test/read\.cgi/([A-Za-z0-9_]+)/(\d+)(?:/(?:l\d+|(\d+)(?:-\d*)?)?)?.*$""")

    override fun parseBoardUrl(url: String): Pair<String, String>? {
        val m = boardUrl.find(url.trim()) ?: return null
        // 「test」は read.cgi などの置き場で、板ではない
        if (m.groupValues[2] == "test") return null
        return m.groupValues[1].lowercase() to m.groupValues[2]
    }

    override fun parseThreadUrl(url: String): ThreadUrl? {
        val m = threadUrl.find(url.trim()) ?: return null
        return ThreadUrl(
            host = m.groupValues[1].lowercase(),
            boardKey = m.groupValues[2],
            threadKey = m.groupValues[3].toLongOrNull() ?: return null,
            resNumber = m.groupValues[4].toIntOrNull(),
        )
    }
}
