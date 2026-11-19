package jp.miyavi.android.gerbil.core.designsystem

import jp.miyavi.android.gerbil.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.gerbil.ui.ProvideGlassEnvironment
import jp.miyavi.android.gerbil.ui.hazeSurface
import androidx.compose.animation.core.animateDpAsState
import jp.miyavi.android.gerbil.ui.SURFACE_OPACITY
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** スクロールバー上の目印（新着の開始・前回の読了位置）。 */
data class ScrollTick(val index: Int, val kind: Kind) {
    enum class Kind { NEW, LAST_READ }
}

/**
 * レス表示の高速スクロールバー（contracts/ui-thread-screen.md）。
 * つまみの位置はピクセルではなくレスの index に対応させる（中央 ≒ 全体の半分のレス）。
 * 普段は隠し、スクロール中は細く、ドラッグ中は太くして移動先の番号と日時を吹き出しで出す。
 * 本文の操作と干渉しないよう、つまみの周辺を掴んだときだけ反応する。
 * [topInset] は TopAppBar の高さで、つまみの可動域をバーの下端から始める（バーの裏に隠れない）。
 * [hazeState] を渡すと、吹き出しを設定の透かし方（ぼかし / ガラス）で描く。
 */
/** 高速スクロールバーを置く側。アプリでは右に固定している。 */
enum class ScrollbarSide { RIGHT, LEFT }

@Composable
fun ThreadFastScroller(
    listState: LazyListState,
    itemCount: Int,
    label: (index: Int) -> String,
    modifier: Modifier = Modifier,
    side: ScrollbarSide = ScrollbarSide.RIGHT,
    ticks: List<ScrollTick> = emptyList(),
    topInset: Dp = 0.dp,
    hazeState: HazeState? = null,
    bottomInset: Dp = 0.dp,
    threadList: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var heightPx by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    // ドラッグ中は最新の位置だけを反映する。
    LaunchedEffect(listState) {
        snapshotFlow { if (dragging) dragIndex else -1 }
            .filter { it >= 0 }
            .collectLatest { listState.scrollToItem(it) }
    }

    val listIndex by remember(listState, itemCount) {
        derivedStateOf {
            if (!listState.canScrollForward && itemCount > 0) itemCount - 1 else listState.firstVisibleItemIndex
        }
    }
    val currentIndex = if (dragging) dragIndex else listIndex
    val fraction = FastScrollerMath.fractionForIndex(currentIndex, itemCount)

    val thumbLengthPx = with(density) { Dimens.scrollbarThumbLength.toPx() }
    val paddingPx = with(density) { Spacing.xs.toPx() }
    val topPx = with(density) { topInset.toPx() } + paddingPx
    val bottomPx = with(density) { bottomInset.toPx() } + paddingPx
    val trackPx = (heightPx - thumbLengthPx - topPx - bottomPx).coerceAtLeast(0f)
    val thumbTopPx = topPx + fraction * trackPx

    val shown = (visible || dragging) && itemCount > 1 && trackPx > 0f
    val latestShown by rememberUpdatedState(shown)
    val latestThumbTop by rememberUpdatedState(thumbTopPx)
    val latestTrack by rememberUpdatedState(trackPx)
    val latestTrackTop by rememberUpdatedState(topPx)
    val latestCount by rememberUpdatedState(itemCount)
    val latestListIndex by rememberUpdatedState(listIndex)

    // 出入りは不透明度（effects）、つかんだときの太さは大きさ（spatial）のトークンで動かす
    val motion = MaterialTheme.motionScheme
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) motion.fastEffectsSpec() else motion.slowEffectsSpec(),
        label = "scrollbarAlpha",
    )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) Dimens.scrollbarThick else Dimens.scrollbarThin,
        animationSpec = motion.fastSpatialSpec(),
        label = "scrollbarWidth",
    )
    val alignment = if (side == ScrollbarSide.RIGHT) Alignment.TopEnd else Alignment.TopStart

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height }
            .pointerInput(side) {
                val touchWidth = Dimens.minTouchTarget.toPx()
                val grab = Dimens.minTouchTarget.toPx() / 2
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val inStrip = if (side == ScrollbarSide.RIGHT) {
                        down.position.x >= size.width - touchWidth
                    } else {
                        down.position.x <= touchWidth
                    }
                    val top = latestThumbTop
                    val nearThumb = down.position.y in (top - grab)..(top + thumbLengthPx + grab)
                    if (!latestShown || !inStrip || !nearThumb) return@awaitEachGesture

                    down.consume()
                    val grabOffset = down.position.y - top
                    dragIndex = latestListIndex
                    dragging = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val f = FastScrollerMath.fractionForOffset(
                                change.position.y - grabOffset - latestTrackTop,
                                latestTrack,
                            )
                            val index = FastScrollerMath.indexForFraction(f, latestCount)
                            if (FastScrollerMath.crossedSegment(dragIndex, index)) {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            dragIndex = index
                            change.consume()
                        }
                    } finally {
                        dragging = false
                    }
                }
            },
    ) {
        content()

        // 目印とつまみ
        val positionDescription = stringResource(if (threadList) R.string.thread_list_scroller_cd else R.string.thread_scroller_cd)
        val positionState = stringResource(if (threadList) R.string.thread_list_scroller_state else R.string.thread_scroller_state, currentIndex + 1, itemCount)
        val moveLabel = stringResource(if (threadList) R.string.thread_list_scroller_move else R.string.thread_scroller_move)
        Box(
            modifier = Modifier
                .align(alignment)
                .fillMaxHeight()
                .width(Dimens.minTouchTarget)
                .alpha(alpha)
                .semantics {
                    contentDescription = positionDescription
                    stateDescription = positionState
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (currentIndex + 1).toFloat(),
                        range = 1f..itemCount.coerceAtLeast(1).toFloat(),
                    )
                    setProgress(label = moveLabel) { value ->
                        val target = value.roundToInt().coerceIn(1, itemCount.coerceAtLeast(1)) - 1
                        scope.launch { listState.scrollToItem(target) }
                        true
                    }
                },
        ) {
            val edge = if (side == ScrollbarSide.RIGHT) Alignment.TopEnd else Alignment.TopStart
            ticks.forEach { tick ->
                val y = topPx + FastScrollerMath.fractionForIndex(tick.index, itemCount) * trackPx +
                    thumbLengthPx / 2
                Box(
                    Modifier
                        .align(edge)
                        .offset { IntOffset(0, y.roundToInt()) }
                        .size(Dimens.scrollbarTickLength, Dimens.scrollbarTickThickness)
                        .background(tickColor(tick.kind)),
                )
            }
            Box(
                Modifier
                    .align(edge)
                    .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                    .padding(horizontal = Spacing.xs)
                    .size(thumbWidth, Dimens.scrollbarThumbLength)
                    .background(
                        color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.extraLarge,
                    ),
            )
        }

        if (dragging) {
            val bubbleOffset = with(density) { Dimens.minTouchTarget.roundToPx() }
            val bubbleModifier = Modifier
                .align(alignment)
                .offset {
                    val x = if (side == ScrollbarSide.RIGHT) -bubbleOffset else bubbleOffset
                    IntOffset(x, (thumbTopPx + thumbLengthPx / 2).roundToInt() - bubbleOffset / 2)
                }
                .height(Dimens.minTouchTarget)
            val bubbleText = @Composable {
                Text(label(dragIndex), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (hazeState != null) {
                ProvideGlassEnvironment {
                    Box(
                        modifier = bubbleModifier
                            .hazeSurface(hazeState, CircleShape, fixedOpacity = SURFACE_OPACITY)
                            .clip(CircleShape)
                            .padding(horizontal = Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) { bubbleText() }
                }
            } else {
                Surface(
                    modifier = bubbleModifier,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = Spacing.l)) {
                        bubbleText()
                    }
                }
            }
        }
    }
}

@Composable
private fun tickColor(kind: ScrollTick.Kind): Color = when (kind) {
    ScrollTick.Kind.NEW -> MaterialTheme.colorScheme.primary
    ScrollTick.Kind.LAST_READ -> MaterialTheme.colorScheme.tertiary
}

private const val HIDE_DELAY_MS = 1_500L

