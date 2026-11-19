package jp.miyavi.android.gerbil.core.designsystem

import androidx.compose.runtime.remember
import androidx.compose.material3.ColorScheme
import jp.miyavi.android.gerbil.ui.AppMotionScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.rememberDynamicColorScheme
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.settings.AppFont
import jp.miyavi.android.gerbil.core.settings.ColorTheme
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.core.settings.ThemeMode

// Dynamic Color が使えないとき用の固定スキーム（AGENTS.md デザイン指南 2）。色の直書きはこのファイルだけ。
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F5AA9), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF), onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF595E72), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE1F9), onSecondaryContainer = Color(0xFF161B2C),
    tertiary = Color(0xFF745470), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F6), onTertiaryContainer = Color(0xFF2C122A),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC), onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F3FA),
    surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E2E9),
    outline = Color(0xFF767680), outlineVariant = Color(0xFFC6C5D0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB4C5FF), onPrimary = Color(0xFF0E2C78),
    primaryContainer = Color(0xFF27428F), onPrimaryContainer = Color(0xFFDBE1FF),
    secondary = Color(0xFFC1C5DD), onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF41465A), onSecondaryContainer = Color(0xFFDDE1F9),
    tertiary = Color(0xFFE2BBDB), onTertiary = Color(0xFF432740),
    tertiaryContainer = Color(0xFF5B3D57), onTertiaryContainer = Color(0xFFFFD7F6),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
)

// Pixel 11 Pro 本体の色を参考にした種色。配色全体は Material の色生成に任せる。
private val CanyonSeed = Color(0xFFF07160)
private val FogSeed = Color(0xFFB3E0F5)
private val OliveSeed = Color(0xFF717C5C)
private val ObsidianSeed = Color(0xFF162138)
val DefaultCustomSeed = Color(0xFF6750A4)

private val LineSeedFamily = FontFamily(
    Font(R.font.line_seed_jp_regular, FontWeight.Normal),
    Font(R.font.line_seed_jp_bold, FontWeight.Bold),
)
private val LineSeedTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = LineSeedFamily),
        displayMedium = t.displayMedium.copy(fontFamily = LineSeedFamily),
        displaySmall = t.displaySmall.copy(fontFamily = LineSeedFamily),
        headlineLarge = t.headlineLarge.copy(fontFamily = LineSeedFamily),
        headlineMedium = t.headlineMedium.copy(fontFamily = LineSeedFamily),
        headlineSmall = t.headlineSmall.copy(fontFamily = LineSeedFamily),
        titleLarge = t.titleLarge.copy(fontFamily = LineSeedFamily),
        titleMedium = t.titleMedium.copy(fontFamily = LineSeedFamily),
        titleSmall = t.titleSmall.copy(fontFamily = LineSeedFamily),
        bodyLarge = t.bodyLarge.copy(fontFamily = LineSeedFamily),
        bodyMedium = t.bodyMedium.copy(fontFamily = LineSeedFamily),
        bodySmall = t.bodySmall.copy(fontFamily = LineSeedFamily),
        labelLarge = t.labelLarge.copy(fontFamily = LineSeedFamily),
        labelMedium = t.labelMedium.copy(fontFamily = LineSeedFamily),
        labelSmall = t.labelSmall.copy(fontFamily = LineSeedFamily),
    )
}

/** このテーマが暗い配色か（システムバーのアイコン色の判定にも使う）。 */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** 本文の文字サイズ（ユーザー設定）。 */
val LocalBodyFontSize = staticCompositionLocalOf { Settings.DEFAULT_FONT_SIZE }

@Composable
fun BoardsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    customColorArgb: Int? = null,
    font: AppFont = Settings.DEFAULT_FONT,
    bodyFontSizeSp: Int = Settings.DEFAULT_FONT_SIZE,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val dynamicAvailable = colorTheme == ColorTheme.DYNAMIC
    val baseScheme = when {
        dynamicAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        colorTheme != ColorTheme.DYNAMIC -> {
            val seed = when (colorTheme) {
                ColorTheme.CANYON -> CanyonSeed
                ColorTheme.FOG -> FogSeed
                ColorTheme.OLIVE -> OliveSeed
                ColorTheme.OBSIDIAN -> ObsidianSeed
                ColorTheme.CUSTOM -> customColorArgb?.let(::Color) ?: DefaultCustomSeed
                ColorTheme.DYNAMIC -> DefaultCustomSeed
            }
            val base = rememberDynamicColorScheme(seedColor = seed, isDark = dark, style = paletteStyleFor(colorTheme))
            if (colorTheme == ColorTheme.CUSTOM) {
                val contrasted = rememberDynamicColorScheme(
                    seedColor = seed, isDark = dark, style = paletteStyleFor(colorTheme), contrastLevel = CUSTOM_SECONDARY_CONTRAST,
                )
                remember(base, contrasted) { withContrastedSecondary(base, contrasted) }
            } else {
                base
            }
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    val tintWeight = if (dark) 0.22f else 0.18f
    val tinted = dynamicAvailable || colorTheme != ColorTheme.DYNAMIC
    val background = if (tinted) lerp(baseScheme.surface, baseScheme.primaryContainer, tintWeight) else baseScheme.surface
    val vividPrimary = vividLinkColor(baseScheme.primary, background, dark)
    val scheme = if (tinted) {
        baseScheme.copy(
            surface = background,
            background = background,
            surfaceContainerLow = lerp(baseScheme.surfaceContainerLow, baseScheme.primaryContainer, tintWeight * 0.75f),
            surfaceContainer = lerp(baseScheme.surfaceContainer, baseScheme.primaryContainer, tintWeight * 0.75f),
            surfaceContainerHigh = lerp(baseScheme.surfaceContainerHigh, baseScheme.primaryContainer, tintWeight * 0.6f),
            primary = vividPrimary,
        )
    } else {
        baseScheme.copy(surface = background, background = background, primary = vividPrimary)
    }
    CompositionLocalProvider(LocalBodyFontSize provides bodyFontSizeSp) {
        MaterialTheme(colorScheme = scheme, motionScheme = AppMotionScheme, typography = if (font == AppFont.LINE_SEED_JP) LineSeedTypography else Typography(), content = content)
    }
}

/**
 * カスタム色の配色スタイル。標準の TonalSpot は種色の色相・彩度しか使わず明るさを捨てる
 * （白と灰色が同じ配色になり、白は水色寄りに色が付く）。Fidelity は種色をそのまま
 * primaryContainer に使うので、種色の明るさが背景やチップに反映され、無彩色は無彩色のままになる。
 */
internal fun paletteStyleFor(colorTheme: ColorTheme): PaletteStyle =
    if (colorTheme == ColorTheme.CUSTOM) PaletteStyle.Fidelity else PaletteStyle.TonalSpot

/**
 * Fidelity は淡い種色だと補助の色（secondary 系）まで淡くなり、スター付きの板のボタンやチップが背景と
 * 見分けにくくなる。全体のコントラストを上げると種色の明るさが消える（白と灰色が同じになる）ので、
 * 補助の面と文字だけを、コントラストを上げた配色（[contrasted]）から取る。
 */
internal fun withContrastedSecondary(base: ColorScheme, contrasted: ColorScheme): ColorScheme = base.copy(
    secondaryContainer = contrasted.secondaryContainer,
    onSecondaryContainer = contrasted.onSecondaryContainer,
)

/** 補助の色に使うコントラスト（0 = 標準、0.5 = 中、1 = 高）。 */
internal const val CUSTOM_SECONDARY_CONTRAST = 0.5

private fun vividLinkColor(base: Color, background: Color, isDark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    if (hsl[1] > 0.08f) hsl[1] = maxOf(hsl[1], 0.75f).coerceAtMost(0.92f)
    hsl[2] = if (isDark) maxOf(hsl[2], 0.68f) else minOf(hsl[2], 0.44f)
    val bgArgb = background.toArgb()
    var argb = ColorUtils.HSLToColor(hsl)
    repeat(16) {
        if (ColorUtils.calculateContrast(argb, bgArgb) >= 4.6) return Color(argb)
        hsl[2] = (if (isDark) hsl[2] + 0.03f else hsl[2] - 0.03f).coerceIn(0.15f, 0.92f)
        argb = ColorUtils.HSLToColor(hsl)
    }
    return Color(argb)
}

/**
 * レス本文の下地の色。どのテーマでも「背景（surface）に本文色（onSurface）を薄く重ねた色」で作る。
 * テーマ色や背景の色寄せに左右されず、背景との差が常に一定になる。
 */
@Composable
@ReadOnlyComposable
fun postBodyContainerColor(): Color {
    val c = MaterialTheme.colorScheme
    val alpha = if (c.surface.luminance() < 0.5f) POST_BODY_OVERLAY_DARK else POST_BODY_OVERLAY_LIGHT
    return c.onSurface.copy(alpha = alpha).compositeOver(c.surface)
}

/**
 * ボトムシートの中で、書き込み欄や要約など「読む・書く」部分の面の色。透かしたシートより明確に明るくする
 * （ライトは白に近い `surfaceContainerLowest`、ダークは最も明るい `surfaceContainerHighest`）。
 */
@Composable
@ReadOnlyComposable
fun sheetFieldColor(): Color {
    val c = MaterialTheme.colorScheme
    return if (c.surface.luminance() < 0.5f) c.surfaceContainerHighest else c.surfaceContainerLowest
}

/** レス本文の下地で、背景に重ねる本文色の不透明度（ライト / ダーク）。 */
private const val POST_BODY_OVERLAY_LIGHT = 0.03f
private const val POST_BODY_OVERLAY_DARK = 0.05f

/** レス本文のスタイル。bodyLarge を元に、行の高さは文字サイズ × 1.5。 */
@Composable
@ReadOnlyComposable
fun bodyTextStyle(): TextStyle {
    val size = LocalBodyFontSize.current
    return MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp)
}

/** AA のスタイル。折り返さず、行の高さは文字サイズ × 1.0。 */
@Composable
@ReadOnlyComposable
fun aaTextStyle(): TextStyle {
    val size = LocalBodyFontSize.current
    return MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontSize = size.sp, lineHeight = size.sp)
}

/** タイトルバー配色。「ここから新着」帯と同じ primaryContainer / onPrimaryContainer を使う。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun boardsTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
