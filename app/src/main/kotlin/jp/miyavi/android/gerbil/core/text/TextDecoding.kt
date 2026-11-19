package jp.miyavi.android.gerbil.core.text

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** 掲示板のテキスト資源（subject.txt / dat / bbs.cgi の応答）を文字列にする。Shift_JIS は機種依存文字のため CP932 を使う。 */
object TextDecoding {
    val sjis: Charset = runCatching { Charset.forName("windows-31j") }
        .getOrElse { Charset.forName("Shift_JIS") }

    fun decodeSjis(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String =
        String(bytes, offset, length, sjis)

    /**
     * Shift_JIS（Windows-31J）か UTF-8 かを判定して文字列にする。UTF-8 として正しく読めれば UTF-8、読めなければ Shift_JIS。
     * 日本語の Shift_JIS が偶然 UTF-8 として正しく読めることはまずない。ASCII だけならどちらでも同じ。
     */
    fun decode(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): String =
        String(bytes, offset, length, charsetOf(bytes, offset, length))

    private val metaCharset = Regex("""charset\s*=\s*["']?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)

    /** HTML の先頭（ASCII の範囲）に書かれた charset。Shift_JIS 系なら CP932 にそろえる。 */
    private fun declaredCharset(bytes: ByteArray, offset: Int, length: Int): Charset? {
        val head = String(bytes, offset, minOf(length, HEAD_BYTES), Charsets.ISO_8859_1)
        val name = metaCharset.find(head)?.groupValues?.get(1) ?: return null
        if (name.lowercase().replace("_", "-") in setOf("shift-jis", "sjis", "x-sjis", "windows-31j", "cp932")) return sjis
        return runCatching { Charset.forName(name) }.getOrNull()
    }

    private const val HEAD_BYTES = 1024

    /** [decode] が選ぶ文字コード。 */
    fun charsetOf(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Charset {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, offset, length))
            Charsets.UTF_8
        } catch (_: CharacterCodingException) {
            // HTML なら meta の charset（EUC-JP 等）に従う。なければ Shift_JIS
            declaredCharset(bytes, offset, length) ?: sjis
        }
    }
}

/** dat 本文・名前に含まれる HTML を扱う最小限のユーティリティ。 */
object HtmlText {
    private val tag = Regex("<[^>]*>")
    private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hearts" to "♥", "copy" to "©", "reg" to "®", "times" to "×", "divide" to "÷",
    )

    fun stripTags(s: String): String = tag.replace(s, "")

    fun unescape(s: String): String {
        if ('&' !in s) return s
        return entity.replace(s) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") ->
                    body.substring(2).toIntOrNull(16)?.let(::codePointToString) ?: m.value
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePointToString) ?: m.value
                else -> named[body] ?: m.value
            }
        }
    }

    private fun codePointToString(cp: Int): String? =
        if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null
}
