package jp.miyavi.android.gerbil.source.shitaraba

import jp.miyavi.android.gerbil.core.text.HtmlText
import jp.miyavi.android.gerbil.source.ThreadHeader
import jp.miyavi.android.gerbil.source.dat.ThreadUrl
import jp.miyavi.android.gerbil.source.dat.UrlRules
import java.nio.charset.Charset

/**
 * したらば掲示板の URL の読み方（spec 006）。板はカテゴリと番号の2段なので、板キーを「カテゴリ/番号」にする。
 * 板 = `https://jbbs.shitaraba.net/カテゴリ/番号/`、スレ = `https://jbbs.shitaraba.net/bbs/read.cgi/カテゴリ/番号/スレ番号/[範囲]`。
 */
object ShitarabaUrls : UrlRules {
    const val HOST = "jbbs.shitaraba.net"
    private val boardUrl = Regex("""^https?://jbbs\.shitaraba\.net/([a-z]+)/(\d+)/?$""")
    private val threadUrl = Regex("""^https?://jbbs\.shitaraba\.net/bbs/(?:read|rawmode)\.cgi/([a-z]+)/(\d+)/(\d+)(?:/(?:l\d+|(\d+)(?:-\d*)?)?)?.*$""")

    override fun parseBoardUrl(url: String): Pair<String, String>? {
        val m = boardUrl.find(url.trim()) ?: return null
        return HOST to "${m.groupValues[1]}/${m.groupValues[2]}"
    }

    override fun parseThreadUrl(url: String): ThreadUrl? {
        val m = threadUrl.find(url.trim()) ?: return null
        return ThreadUrl(HOST, "${m.groupValues[1]}/${m.groupValues[2]}", m.groupValues[3].toLongOrNull() ?: return null, m.groupValues[4].toIntOrNull())
    }

    fun isShitaraba(host: String) = host == HOST
}

/** したらばの一覧と本文の形を、アプリの形（2ch 互換の dat の行）に直す。 */
object ShitarabaFormat {
    /** したらばの文字コード。①などの NEC の特殊文字を読めるよう、使えれば eucJP-open を使う。 */
    val charset: Charset = runCatching { Charset.forName("x-eucJP-Open") }.getOrElse { Charset.forName("EUC-JP") }

    private val subjectLine = Regex("""^(\d+)\.cgi,(.*)\((\d+)\)\s*$""")

    /** subject.txt（`スレ番号.cgi,スレタイ(レス数)`）。最後の行が先頭のスレの繰り返しになることがあるので、同じスレは1つにする。 */
    fun parseSubject(text: String): List<ThreadHeader> {
        val seen = HashSet<Long>()
        var rank = 0
        return text.lineSequence().mapNotNull { raw ->
            val m = subjectLine.find(raw.trimEnd('\r')) ?: return@mapNotNull null
            val key = m.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            if (!seen.add(key)) return@mapNotNull null
            rank++
            ThreadHeader(key, HtmlText.unescape(m.groupValues[2]).trim(), m.groupValues[3].toInt(), rank)
        }.toList()
    }

    /**
     * rawmode の行（`番号<>名前<>メール<>日付<>本文<>スレタイ<>ID`）を、2ch 互換の dat の行（`名前<>メール<>日付 ID:xxx<>本文<>スレタイ`）に直す。
     * 欠けた番号（削除されたレス）は「あぼーん」の行で埋め、行の位置とレス番号をそろえる。[after] はすでに保存しているレス数。
     * 直した行と、最後のレス番号を返す。
     */
    fun toDat(raw: String, after: Int): Pair<String, Int> {
        var last = after
        val out = StringBuilder()
        for (line in raw.lineSequence()) {
            val fields = line.trimEnd('\r').split("<>")
            val number = fields.firstOrNull()?.toIntOrNull() ?: continue
            if (number <= last || fields.size < 6) continue
            while (last + 1 < number) {
                out.append(ABONE_LINE).append('\n')
                last++
            }
            val id = fields.getOrNull(6).orEmpty()
            val date = if (id.isEmpty()) fields[3] else "${fields[3]} ID:$id"
            out.append(fields[1]).append("<>").append(fields[2]).append("<>").append(date).append("<>")
                .append(fields[4]).append("<>").append(fields[5]).append('\n')
            last = number
        }
        return out.toString() to last
    }

    /** 削除されたレスの行（列が足りないので、表示ではあぼーんになる）。 */
    private const val ABONE_LINE = "あぼーん"
}
