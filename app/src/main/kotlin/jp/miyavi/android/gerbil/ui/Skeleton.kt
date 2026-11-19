package jp.miyavi.android.gerbil.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.MaterialTheme

/**
 * プレースホルダーの帯に流す光の位置（0〜1 をくり返す）。要約の待ち表示と同じ流れ方。
 * 読み込みの表示なのでループしてよい（AGENTS 4.9）。スクリーンショットでは止める。
 */
@Composable
fun rememberShimmer(): State<Float> {
    if (LocalInspectionMode.current) return remember { mutableFloatStateOf(STILL) }
    return rememberInfiniteTransition(label = "skeletonShimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = SHIMMER_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "skeletonShimmerPosition",
    )
}

/** プレースホルダーの帯の色（下地 [on] の上で、ライトでもダークでも見える濃さ）。 */
data class SkeletonColors(val base: Color, val shine: Color)

@Composable
fun skeletonColors(on: Color = MaterialTheme.colorScheme.surface): SkeletonColors {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(on, ink) { SkeletonColors(lerp(on, ink, BASE_TONE), lerp(on, ink, SHINE_TONE)) }
}

/** 光の流れる帯。光の位置は描画の段階で読み、毎フレームの再構成を起こさない。 */
fun Modifier.skeletonBar(shimmer: State<Float>, colors: SkeletonColors, shape: Shape): Modifier =
    clip(shape).drawBehind {
        val center = size.width * (shimmer.value * 2f - 0.5f)
        drawRect(
            Brush.linearGradient(
                listOf(colors.base, colors.shine, colors.base),
                start = Offset(center - size.width * SHINE_WIDTH, 0f),
                end = Offset(center + size.width * SHINE_WIDTH, 0f),
            ),
        )
    }

private const val SHIMMER_MS = 3_000
private const val STILL = -1f
private const val BASE_TONE = 0.16f
private const val SHINE_TONE = 0.04f
private const val SHINE_WIDTH = 0.4f

/** プレースホルダーから中身への入れ替えのフェード（ミリ秒）。トークンの slowEffects では速すぎて見えないため、実機で決めた値。 */
const val CONTENT_SWAP_FADE_MS = 600
