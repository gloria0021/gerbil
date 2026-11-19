package jp.miyavi.android.gerbil.feature.thread

import com.google.mlkit.genai.common.FeatureStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 端末内 AI のモデルの取得の状態。 */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    /** [total] は最初に頼んだときだけ分かる（AICore が取得中のところへ頼み直すと、数値は届かない）。分からなければ null。 */
    data class Downloading(val bytes: Long = 0, val total: Long? = null) : ModelDownloadState
    data object Done : ModelDownloadState
    data class Failed(val timedOut: Boolean) : ModelDownloadState
}

/**
 * モデル（数 GB）の取得を見守る。取得そのものは端末の AICore が行い、アプリを閉じても続く（アプリから止める手段はない）。
 * ここでは取得を頼み、AICore が「取得中」のあいだは使えるようになるまで状態を見続ける（アプリ全体で1つ）。
 */
class ModelDownloader(private val clientFactory: () -> SummaryClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    val isDownloading: Boolean get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) return
        _state.value = ModelDownloadState.Downloading()
        job = scope.launch {
            val client = clientFactory()
            try {
                var failure: Exception? = null
                try {
                    client.download { bytes, total -> _state.value = ModelDownloadState.Downloading(bytes, total) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e
                }
                // 進み具合の知らせが途切れても、AICore が取得中なら終わるまで待つ（知らせの終わり＝使える、とは限らない）
                // 頼み直したときは数値が届かず、すぐ「完了」だけが来る（2026-11 に実機で確認）。そのあとは数値なしで待つ
                var status = statusOrNull(client)
                if (status == FeatureStatus.DOWNLOADING) _state.value = ModelDownloadState.Downloading()
                while (status == FeatureStatus.DOWNLOADING) {
                    delay(STATUS_POLL_MS)
                    status = statusOrNull(client)
                }
                _state.value = if (status == FeatureStatus.AVAILABLE) ModelDownloadState.Done
                    else ModelDownloadState.Failed(timedOut = failure is SummaryDownloadTimeout)
            } finally {
                client.close()
            }
        }
    }

    private suspend fun statusOrNull(client: SummaryClient): Int? = try {
        client.status()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private const val STATUS_POLL_MS = 3_000L
