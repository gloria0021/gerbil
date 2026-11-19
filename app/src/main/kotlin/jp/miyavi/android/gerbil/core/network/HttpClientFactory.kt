package jp.miyavi.android.gerbil.core.network

import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** 2ch 互換の掲示板の慣行に沿った HTTP クライアント（専ブラ用 UA、同一ホストへの同時接続 2 本、429/503 は間隔を空けて再試行）。 */
object HttpClientFactory {
    fun userAgent(versionName: String) = "Monazilla/1.00 Gerbil/$versionName"

    fun create(
        versionName: String,
        retryBaseDelayMs: Long = 1_000,
        cookieJar: CookieJar = CookieJar.NO_COOKIES,
    ): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 2 })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(UserAgentInterceptor(userAgent(versionName)))
        .addInterceptor(BackoffInterceptor(retryBaseDelayMs))
        .build()
}

private class UserAgentInterceptor(private val ua: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build())
}

/** 429 / 503 のとき最大 2 回、指数的に間隔を空けて再試行する。 */
private class BackoffInterceptor(private val baseDelayMs: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        while ((response.code == 429 || response.code == 503) && attempt < 2) {
            response.close()
            Thread.sleep(baseDelayMs shl attempt)
            attempt++
            response = chain.proceed(chain.request())
        }
        return response
    }
}
