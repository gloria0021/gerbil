package jp.miyavi.android.gerbil

import android.content.Context
import kotlinx.coroutines.flow.first
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File

/**
 * Debug 版（Gerbil Dev）だけ: 架空の掲示板（`*.gerbil.example`。例として予約されたドメインで実在しない）への通信を、
 * 端末の `files/demo/<ホスト>/<パス>` に置いたファイルで返す。スクリーンショット用に本物の掲示板を使わないため。
 * データは `scripts/demo-board.py` で作って流し込む。
 */
internal fun variantInterceptors(context: Context): List<Interceptor> =
    listOf(DemoBoardInterceptor(File(context.filesDir, "demo")))

private class DemoBoardInterceptor(private val root: File) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host != DEMO_DOMAIN && !url.host.endsWith(".$DEMO_DOMAIN")) return chain.proceed(request)
        val file = File(root, url.host + url.encodedPath)
        val bytes = file.takeIf { it.isFile && it.canonicalPath.startsWith(root.canonicalPath) }?.readBytes()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (bytes != null) 200 else 404)
            .message(if (bytes != null) "OK" else "Not Found")
            .body((bytes ?: ByteArray(0)).toResponseBody(contentType(file.name).toMediaType()))
            .build()
    }

    private fun contentType(name: String) = when (name.substringAfterLast('.')) {
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "text/plain; charset=utf-8"
    }
}

private const val DEMO_DOMAIN = "gerbil.example"

/**
 * Debug 版だけ: 架空の掲示板のデータ（files/demo）が流し込まれていれば、まだ登録していなければ登録し、登録済みなら板一覧を読み直す
 * （scripts/demo-board.py のあとに起動するだけで、スクリーンショットの準備ができるように）。
 */
internal suspend fun registerDemoBoard(container: AppContainer) {
    val demo = File(container.appContext.filesDir, "demo/menu.$DEMO_DOMAIN/bbsmenu.json")
    if (!demo.isFile) return
    // 登録済みなら、流し込み直したデータ（板一覧）を読み直す
    if (container.boards.sites.first().any { it.id.endsWith(DEMO_DOMAIN) }) container.boards.refresh()
    else container.boards.addSite(DEMO_LABEL, "https://bbs.$DEMO_DOMAIN/wheel/")
}

private const val DEMO_LABEL = "Gerbil Demo"
