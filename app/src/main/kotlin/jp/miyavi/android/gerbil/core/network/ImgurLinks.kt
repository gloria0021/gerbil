package jp.miyavi.android.gerbil.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Imgur の投稿ページのリンク（imgur.com/a/… など）を、画像そのものの直リンク（i.imgur.com/….png）に変える。
 * 直リンクでないと、ほかの専ブラで画像をプレビューできないため。ページのプレビュー用の情報（og:image）から取る。
 */
object ImgurLinks {
    /** 投稿・アルバム・ギャラリーのページのリンク。直リンク（i.imgur.com）は含まない。 */
    private val pageLink = Regex("""https?://(?:www\.|m\.)?imgur\.com/(?:a/|gallery/)?[A-Za-z0-9]{5,}(?:[?#][^\s]*)?""")
    private val ogImage = Regex("<meta[^>]+property=\"og:image\"[^>]*content=\"([^\"]+)\"")

    private val directLink = Regex("""https://i\.imgur\.com/[A-Za-z0-9]{5,}\.[A-Za-z0-9]+""")

    /** クリップボードの文字から、Imgur のリンク（ページか直リンク）を1つ取り出す。なければ null。 */
    fun findAnyLink(text: String): String? = (pageLink.find(text) ?: directLink.find(text))?.value

    /** 本文の中の、直リンクに変えられる Imgur のページのリンク。 */
    fun findPageLinks(text: String): List<String> = pageLink.findAll(text).map { it.value }.distinct().toList()

    /** ページの HTML から直リンクを取り出す。i.imgur.com 以外や、取り出せないときは null。 */
    internal fun directFromHtml(html: String): String? {
        val url = ogImage.find(html)?.groupValues?.get(1)?.substringBefore('?') ?: return null
        return url.takeIf { it.startsWith("https://i.imgur.com/") }
    }
}

/** Imgur のページを1回読んで直リンクを返す。読めなければ null（本文はそのまま）。 */
class ImgurResolver(private val client: OkHttpClient) {
    suspend fun resolve(pageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(pageUrl).build()).execute().use { res ->
                if (!res.isSuccessful) null else ImgurLinks.directFromHtml(res.body.string())
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
