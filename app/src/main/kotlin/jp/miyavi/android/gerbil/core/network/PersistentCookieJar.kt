package jp.miyavi.android.gerbil.core.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 掲示板から受け取った Cookie（書き込みの確認など） を端末に保持する [CookieJar]。
 * 同名・同ドメイン・同パスの Cookie は新しい値で上書きし、期限切れは送信しない。
 */
class PersistentCookieJar(
    private val store: CookieStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : CookieJar {

    constructor(context: Context) : this(
        SharedPrefsCookieStore(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)),
    )

    private val lock = Any()
    private val cookies = LinkedHashMap<CookieKey, StoredCookie>().apply {
        store.loadAll().forEach { entry ->
            this[entry.key] = entry.value
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = nowMs()
        synchronized(lock) {
            cookies.forEach { cookie ->
                val key = CookieKey(cookie.name, cookie.domain, cookie.path)
                if (cookie.expiresAt <= now) {
                    this.cookies.remove(key)
                } else {
                    this.cookies[key] = StoredCookie(url.toString(), cookie.toString(), cookie)
                }
            }
            pruneExpiredLocked(now)
            store.saveAll(this.cookies.values.map { it.url to it.raw })
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = nowMs()
        synchronized(lock) {
            val expiredRemoved = pruneExpiredLocked(now)
            if (expiredRemoved) {
                store.saveAll(cookies.values.map { it.url to it.raw })
            }
            return cookies.values
                .map { it.cookie }
                .filter { it.matches(url) }
        }
    }

    private fun pruneExpiredLocked(now: Long): Boolean {
        val before = cookies.size
        cookies.entries.removeAll { it.value.cookie.expiresAt <= now }
        return cookies.size != before
    }

    data class CookieKey(val name: String, val domain: String, val path: String)

    data class StoredCookie(val url: String, val raw: String, val cookie: Cookie)

    interface CookieStore {
        fun loadAll(): Map<CookieKey, StoredCookie>
        fun saveAll(entries: List<Pair<String, String>>)
    }

    private class SharedPrefsCookieStore(private val prefs: SharedPreferences) : CookieStore {
        override fun loadAll(): Map<CookieKey, StoredCookie> {
            val result = LinkedHashMap<CookieKey, StoredCookie>()
            val rawSet = prefs.getStringSet(KEY_COOKIES, emptySet()).orEmpty()
            rawSet.forEach { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                val urlStr = line.substring(0, tab)
                val raw = line.substring(tab + 1)
                val parsedUrl = runCatching { okhttp3.HttpUrl.Builder().apply {
                    val u = java.net.URI(urlStr)
                    scheme(u.scheme ?: "https")
                    host(u.host ?: return@forEach)
                }.build() }.getOrNull() ?: return@forEach
                val cookie = Cookie.parse(parsedUrl, raw) ?: return@forEach
                result[CookieKey(cookie.name, cookie.domain, cookie.path)] = StoredCookie(urlStr, raw, cookie)
            }
            return result
        }

        override fun saveAll(entries: List<Pair<String, String>>) {
            val encoded = entries.mapTo(HashSet()) { (u, raw) -> "$u\t$raw" }
            prefs.edit().putStringSet(KEY_COOKIES, encoded).apply()
        }
    }

    private companion object {
        const val PREFS_NAME = "boards_cookies"
        const val KEY_COOKIES = "cookies"
    }
}
