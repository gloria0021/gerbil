package jp.miyavi.android.gerbil.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.util.Locale

data class ImageDownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
) {
    val fraction: Float?
        get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null

    /** 全体サイズが分からないときは受信量だけを返す（「受信中」は画面側で付ける）。 */
    fun formatLabel(): String {
        val read = formatByteSize(bytesRead)
        return if (totalBytes > 0L) {
            val pct = ((fraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
            "$read / ${formatByteSize(totalBytes)} ($pct%)"
        } else {
            read
        }
    }
}

/** 画像ダウンロードの受信バイト数・全体サイズを URL ごとに追跡する。 */
class ImageDownloadTracker {
    private val _progress = MutableStateFlow<Map<String, ImageDownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ImageDownloadProgress>> = _progress.asStateFlow()

    /** 表示が変わる単位（全体サイズが分かれば 1%、分からなければ 64 KB）ごとにだけ通知する。 */
    fun update(url: String, bytesRead: Long, totalBytes: Long) {
        _progress.update { current ->
            val prev = current[url]
            val next = ImageDownloadProgress(bytesRead, totalBytes)
            if (prev != null && prev.totalBytes == totalBytes && !visiblyChanged(prev, next)) current
            else current + (url to next)
        }
    }

    private fun visiblyChanged(prev: ImageDownloadProgress, next: ImageDownloadProgress): Boolean =
        if (next.totalBytes > 0L) percent(prev) != percent(next)
        else next.bytesRead - prev.bytesRead >= UNKNOWN_SIZE_STEP

    private fun percent(p: ImageDownloadProgress) = ((p.fraction ?: 0f) * 100f).toInt()

    fun clear(url: String) {
        _progress.update { current ->
            if (url in current) current - url else current
        }
    }

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val response: Response = chain.proceed(request)
        val body = response.body
        val total = body.contentLength()
        update(url, 0L, total)
        response.newBuilder()
            .body(ProgressResponseBody(url, body, total))
            .build()
    }

    private inner class ProgressResponseBody(
        private val url: String,
        private val delegate: ResponseBody,
        private val totalBytes: Long,
    ) : ResponseBody() {
        private var bufferedSource: BufferedSource? = null

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = totalBytes

        override fun source(): BufferedSource {
            return bufferedSource ?: wrapSource(delegate.source()).buffer().also { bufferedSource = it }
        }

        private fun wrapSource(source: Source): Source = object : ForwardingSource(source) {
            private var totalRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                if (bytesRead != -1L) {
                    totalRead += bytesRead
                    update(url, totalRead, totalBytes)
                } else {
                    clear(url)
                }
                return bytesRead
            }

            override fun close() {
                clear(url)
                super.close()
            }
        }
    }

    private companion object {
        const val UNKNOWN_SIZE_STEP = 64L * 1024
    }
}

internal fun formatByteSize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes.coerceAtLeast(0L)} B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}
