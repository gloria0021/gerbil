package jp.miyavi.android.gerbil.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import jp.miyavi.android.gerbil.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.settings.Settings
import kotlin.math.roundToInt

/** メニュー内に置く標準 Slider。変更はドラッグを終えたときに保存する。 */
@Composable
fun FontSizeMenuContent(value: Int, onChange: (Int) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    // メニューの項目と左端・文字の大きさを揃え（左右 12dp、labelLarge）、上下と文字とスライダーの間に余白を取る
    Column(
        Modifier.width(Dimens.fontSizeControlWidth).padding(horizontal = Spacing.m, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            stringResource(R.string.font_size_value, draft.roundToInt()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val sliderDescription = stringResource(R.string.font_size)
        Slider(
            modifier = Modifier.semantics { contentDescription = sliderDescription },
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft.roundToInt()) },
            valueRange = Settings.MIN_FONT_SIZE.toFloat()..Settings.MAX_FONT_SIZE.toFloat(),
            steps = Settings.MAX_FONT_SIZE - Settings.MIN_FONT_SIZE - 1,
        )
    }
}

/** 端末の文字倍率を尊重しながら、各画面の読み物部分だけに倍率を適用する。 */
@Composable
fun ReadingFontSize(size: Int, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val adjustedDensity = remember(density, size) {
        Density(density.density, density.fontScale * size / Settings.DEFAULT_FONT_SIZE)
    }
    CompositionLocalProvider(LocalDensity provides adjustedDensity, content = content)
}
