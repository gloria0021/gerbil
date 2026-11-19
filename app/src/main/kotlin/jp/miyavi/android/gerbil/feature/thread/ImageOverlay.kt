package jp.miyavi.android.gerbil.feature.thread

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import jp.miyavi.android.gerbil.ui.imageProgress
import androidx.compose.ui.res.stringResource
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.source.dat.BodySegmenter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.ui.FloatingGlassBar
import jp.miyavi.android.gerbil.ui.ImageLoadingBadge

data class ImageGallery(val urls: List<String>, val initialIndex: Int = 0)

/** 画像・動画の全画面オーバーレイ（横スライド、画像はピンチズームとダブルタップ切替、動画は再生、ブラウザで開く）。 */
@Composable
fun ImageOverlay(
    gallery: ImageGallery,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
) {
    val urls = gallery.urls
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = gallery.initialIndex.coerceIn(0, urls.lastIndex)) { urls.size }
    val hazeState = rememberHazeState()
    var currentScaled by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        currentScaled = false
    }

    // 背景はフェード、画像は少し小さい状態からばねで広がって出る。閉じるときは逆の動きを終えてから閉じる
    val motion = MaterialTheme.motionScheme
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val dismiss = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !currentScaled,
                modifier = Modifier
                    .fillMaxSize()
                    .animateEnterExit(
                        enter = scaleIn(motion.defaultSpatialSpec(), initialScale = OPEN_SCALE),
                        exit = scaleOut(motion.fastSpatialSpec(), targetScale = OPEN_SCALE),
                    )
                    .hazeSource(hazeState),
            ) { page ->
                val url = urls[page]
                if (BodySegmenter.isVideoUrl(url)) {
                    // 操作部（シークバー）が下の浮いたバーに隠れないよう、その分を空ける
                    VideoPlayerPage(
                        url = url,
                        isCurrentPage = page == pagerState.currentPage,
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(bottom = Dimens.floatingTabsContentPadding),
                    )
                } else {
                    ZoomableImagePage(
                        url = url,
                        isCurrentPage = page == pagerState.currentPage,
                        onScaledChange = { scaled ->
                            if (page == pagerState.currentPage) currentScaled = scaled
                        },
                    )
                }
            }
            FloatingGlassBar(
                hazeState = hazeState,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = dismiss) {
                        Icon(
                            painterResource(R.drawable.ic_huge_close),
                            contentDescription = stringResource(R.string.common_close),
                        )
                    }
                    if (urls.size > 1) {
                        Text(
                            "${pagerState.currentPage + 1} / ${urls.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Spacing.s),
                        )
                    }
                    TextButton(onClick = { onOpenInBrowser(urls[pagerState.currentPage]) }) {
                        Text(stringResource(R.string.image_open_in_browser), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ZoomableImagePage(
    url: String,
    isCurrentPage: Boolean,
    onScaledChange: (Boolean) -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var loadState by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }

    val scope = rememberCoroutineScope()
    val zoomSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // ダブルタップの拡大の途中で指が触れたら止め、今の倍率・位置から指の操作に引き継ぐ
    var zoomJob by remember(url) { mutableStateOf<Job?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            zoomJob?.cancel()
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = {
                        // 拡大・等倍をばねで行き来する（位置も一緒に中央へ戻す）
                        val fromScale = scale
                        val toScale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        val fromOffset = offset
                        onScaledChange(toScale > 1f)
                        zoomJob?.cancel()
                        zoomJob = scope.launch {
                            animate(0f, 1f, animationSpec = zoomSpec) { t, _ ->
                                scale = fromScale + (toScale - fromScale) * t
                                offset = fromOffset * (1f - t)
                            }
                        }
                    },
                )
            }
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    zoomJob?.cancel()
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        // 1倍のまま1本指のときは横スワイプを HorizontalPager へ譲る
                        if (pointerCount > 1 || scale > 1f) {
                            val nextScale = (scale * event.calculateZoom()).coerceIn(1f, MAX_SCALE)
                            scale = nextScale
                            offset = if (nextScale > 1f) offset + event.calculatePan() else Offset.Zero
                            onScaledChange(nextScale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = url,
            contentDescription = stringResource(R.string.image_preview_cd),
            contentScale = ContentScale.Fit,
            onState = { loadState = it },
            modifier = Modifier
                .fillMaxSize()
                // 倍率・位置は描画の段階で読み、ズーム中に再構成を起こさない
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        if (loadState is AsyncImagePainter.State.Loading) {
            ImageLoadingBadge(
                progress = imageProgress(url),
                compact = false,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.92f
/** 開くとき・閉じるときの画像の大きさ（等倍に対する割合）。 */
private const val OPEN_SCALE = 0.92f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2f
