package jp.miyavi.android.gerbil.feature.thread

import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 動画の最初のコマの読み込み状態。 */
sealed interface VideoFrameState {
    data object Loading : VideoFrameState
    data class Ready(val frame: ImageBitmap) : VideoFrameState
    data object Failed : VideoFrameState
}

/** 一覧のサムネイル用に、取得した最初のコマを覚えておく（スクロールで戻っても取り直さない）。 */
private val frameCache = LruCache<String, ImageBitmap>(FRAME_CACHE_SIZE)

/**
 * 動画 URL の最初のコマを読み込む。MediaMetadataRetriever はファイル全体ではなく必要な部分だけを取りに行く。
 */
@Composable
fun rememberVideoFrame(url: String): VideoFrameState {
    val state by produceState<VideoFrameState>(
        initialValue = frameCache.get(url)?.let { VideoFrameState.Ready(it) } ?: VideoFrameState.Loading,
        key1 = url,
    ) {
        if (value is VideoFrameState.Ready) return@produceState
        val frame = withContext(Dispatchers.IO) { loadFirstFrame(url) }
        value = if (frame != null) {
            frameCache.put(url, frame)
            VideoFrameState.Ready(frame)
        } else {
            VideoFrameState.Failed
        }
    }
    return state
}

private fun loadFirstFrame(url: String): ImageBitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(url, HashMap())
        val bitmap = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_MAX_PX, FRAME_MAX_PX)
        bitmap?.asImageBitmap()
    } finally {
        retriever.release()
    }
}.getOrNull()

/** 動画の最初のコマを表示する。 */
@Composable
fun VideoFrameImage(frame: ImageBitmap, contentDescription: String, modifier: Modifier = Modifier) {
    Image(bitmap = frame, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
}

/**
 * 全画面オーバーレイの動画ページ。表示中のページだけ再生し、離れたら止める。操作部は Media3 標準の PlayerView。
 */
@Composable
fun VideoPlayerPage(url: String, isCurrentPage: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    // ホームに戻るなど画面が見えなくなったら止める（ループ再生なので放っておくと音が続く）
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(player, isCurrentPage, visible) {
        player.playWhenReady = isCurrentPage && visible
    }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier,
    )
}

private const val FRAME_CACHE_SIZE = 32
private const val FRAME_MAX_PX = 480
