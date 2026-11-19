package jp.miyavi.android.gerbil.feature.thread

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.gerbil.R
import kotlin.math.PI
import androidx.compose.foundation.layout.fillMaxSize

/**
 * 要約中を示す動くアイコン（Hugeicons の Ai File を部品に分けて動かす）。
 * 書類の中に2本の線が順に書かれていき、右下の星がくるっと回りながら瞬く。読み込み表示のループ。
 */
@Composable
internal fun AiWritingIcon(color: Color, modifier: Modifier = Modifier) {
    val inspection = LocalInspectionMode.current
    val t by if (inspection) remember { androidx.compose.runtime.mutableFloatStateOf(STILL_FRAME) } else {
        rememberInfiniteTransition(label = "aiWriting").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "aiWritingProgress",
        )
    }
    val sparkle = remember { PathParser().parsePathString(SPARKLE_PATH).toPath() }
    Box(modifier) {
        Icon(painterResource(R.drawable.ic_huge_ai_file_doc), contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // 元のアイコンと同じ 24×24 の座標で描く
            scale(size.width / VIEWPORT, pivot = Offset.Zero) {
                // 1本目 → 2本目の順に書き、最後の 1/4 は書き終わった状態で少し止める
                val writing = (t / WRITE_PORTION).coerceAtMost(1f)
                listOf(LINE_1_Y, LINE_2_Y).forEachIndexed { i, y ->
                    val p = (writing * 2f - i).coerceIn(0f, 1f)
                    if (p > 0f) {
                        drawLine(color, Offset(LINE_START_X, y), Offset(LINE_START_X + LINE_LENGTH * p, y), strokeWidth = STROKE, cap = StrokeCap.Round)
                    }
                }
                // 星は1周期で 90 度回り、2回瞬く（縮んで戻る）
                val twinkle = 1f - SPARKLE_SHRINK * (1f - kotlin.math.cos(t * 4f * PI.toFloat())) / 2f
                rotate(t * SPARKLE_TURN, pivot = SPARKLE_CENTER) {
                    scale(twinkle, pivot = SPARKLE_CENTER) {
                        drawPath(sparkle, color, style = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

private const val VIEWPORT = 24f
private const val STROKE = 1.5f
private const val CYCLE_MS = 1_600
private const val WRITE_PORTION = 0.75f
private const val STILL_FRAME = 0.75f
private const val LINE_START_X = 7f
private const val LINE_LENGTH = 4f
private const val LINE_1_Y = 13f
private const val LINE_2_Y = 16.5f
private const val SPARKLE_TURN = 90f
private const val SPARKLE_SHRINK = 0.4f
private val SPARKLE_CENTER = Offset(17.5f, 18.5f)

/** Hugeicons Ai File の星の部分（ic_huge_ai_file.xml と同じ形）。 */
private const val SPARKLE_PATH = "M17.5 15.001L17.7579 15.698C18.0961 16.612 18.2652 17.069 18.5986 17.4024C18.932 17.7357 19.389 17.9048 20.303 18.2431L21 18.501L20.303 18.7589C19.389 19.0971 18.932 19.2662 18.5986 19.5996C18.2652 19.933 18.0961 20.39 17.7579 21.3039L17.5 22.001L17.2421 21.3039C16.9039 20.39 16.7348 19.933 16.4014 19.5996C16.068 19.2662 15.611 19.0971 14.697 18.7589L14 18.501L14.697 18.2431C15.611 17.9048 16.068 17.7357 16.4014 17.4024C16.7348 17.069 16.9039 16.612 17.2421 15.698L17.5 15.001Z"
