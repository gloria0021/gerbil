package jp.miyavi.android.gerbil.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.zIndex
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetState
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.HazeBlurStyle
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.settings.HazeMode
import jp.miyavi.android.gerbil.core.settings.Settings

/** アプリ全体で共有する透かし方の設定。各レベルは 0〜10 段階。 */
data class GlassEffectConfig(
    val opacityLevel: Int = Settings.DEFAULT_GLASS_OPACITY,
    val refractionLevel: Int = Settings.DEFAULT_GLASS_REFRACTION,
    val mode: HazeMode = Settings.DEFAULT_HAZE_MODE,
    val blurStrengthLevel: Int = Settings.DEFAULT_BLUR_STRENGTH,
    val blurOpacityLevel: Int = Settings.DEFAULT_BLUR_OPACITY,
)

/** 透過面の背景の不透明度（0〜1）。ガラスとぼかしで別の値を使う。設定では変えない固定値。 */
data class SurfaceOpacity(val glass: Float, val blur: Float)

/** タイトルバー・タブ・下部バー・メニュー・検索欄・吹き出しなど、ふつうの透過面。 */
val SURFACE_OPACITY = SurfaceOpacity(glass = 0.5f, blur = 0.3f)

/** メニュー（歯車・長押し・絞り込み）。文字を選ぶ面なのでガラスは濃くする。 */
private val MENU_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.3f)

/** ボトムシート（書き込み・要約・返信や ID の一覧）。読む量が多いのでガラスは濃くする。 */
private val SHEET_OPACITY = SurfaceOpacity(glass = 0.9f, blur = 0.3f)

/**
 * 設定の透かし方（ぼかし / ガラス）で面を描く。TopAppBar・浮いたバー・検索・シートで共通に使う。
 * 背景色は [opacity] の不透明度（ガラス / ぼかしで別の固定値）で乗せる。
 */
fun Modifier.hazeSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    /** false なら縁の影と光を付けない（横いっぱいの帯では、縁が線に見えるため）。 */
    rim: Boolean = true,
): Modifier = composed {
    when (LocalGlassEffect.current.mode) {
        HazeMode.BLUR -> clip(shape).blurSurface(hazeState, fixedOpacity)
        HazeMode.GLASS -> glassSurface(hazeState, shape, fixedOpacity, rim)
    }
}

val LocalGlassEffect = staticCompositionLocalOf { GlassEffectConfig() }

@Composable
fun ProvideGlassEnvironment(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val darkGlass = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val glassConfiguration = remember(configuration, darkGlass) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkGlass) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }
    CompositionLocalProvider(
        LocalConfiguration provides glassConfiguration,
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        content = content,
    )
}

/**
 * 透明ガラスに、テーマカラーの濃淡と屈折（各 0〜10）を適用する Modifier。
 *
 * 色付けは Haze のシェーダー内の tint で行う（屈折 → 色付け → 縁の光）。
 * Modifier.background などで上に色を重ねると屈折と縁の光を塗りつぶすので、重ねない。
 * 縁のくっきりした屈折の層（refractionDetailIntensity）は tint の後に色なしで重なり、
 * テーマカラーを打ち消すので使わない。
 */
private fun Modifier.glassSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    fixedOpacity: SurfaceOpacity,
    rim: Boolean = true,
): Modifier = composed {
    val colors = MaterialTheme.colorScheme
    val config = LocalGlassEffect.current
    val darkGlass = colors.surface.luminance() < 0.5f
    fun fraction(v: Int) = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) / 10f
    val opacity = fixedOpacity.glass
    val refraction = fraction(config.refractionLevel)
    val glassTint = colors.surface.copy(alpha = opacity)
    val rimShadow = colors.scrim.copy(alpha = if (!rim) 0f else if (darkGlass) 0.28f else 0.16f)
    val glassStyle = remember(shape, rimShadow, refraction, rim) {
        GlassStyle.clear.then {
            shape(shape)
            whitePoint(0f)
            // 縁と中央で色が変わらないよう、縁を白く持ち上げる光と色ずれを控えめにする
            surfaceProfile(SurfaceProfile.Squircle)
            ambientResponse(0.08f)
            specularIntensity(if (rim) 0.4f else 0f)
            edgeShadow(rimShadow)
            chromaticAberrationStrength(0.12f * refraction)
            optics {
                copy(
                    // 縁から屈折させる。ずれは Haze 側で短辺の半分までに抑えられる
                    refractionStrength = refraction,
                    refractionDisplacement = (refraction * 56f).dp,
                    refractionProfile = RefractionProfile.Edge((12f + 24f * refraction).dp),
                    refractionDetailIntensity = 0f,
                )
            }
        }
    // 背景色は不透明な surface を敷く。透明だと、文字のない所（下の像が透明）で tint が消える
    }.material3(containerColor = colors.surface, tint = glassTint)
    this.hazeGlass(input = HazeInput.Sources(hazeState), style = glassStyle)
}

/** スクロール内容の上に浮くピル型バー。設定の透かし方（ぼかし / ガラス）で描く。 */
@Composable
fun BoxScope.FloatingGlassBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    /** 渡すと設定の不透明度の代わりに使う（文字を入力する面など、読みやすさを優先したいとき）。 */
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    content: @Composable RowScope.() -> Unit,
) {
    GlassPill(
        hazeState = hazeState,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l)
            .widthIn(max = Dimens.floatingTabsMaxWidth),
        fixedOpacity = fixedOpacity,
        content = content,
    )
}

/**
 * 横にスクロールするタブの帯（M3 の SecondaryScrollableTabRow）を、透過効果の面にして一覧の上に重ねる。
 * 帯そのものを透かし、下を流れる一覧が見えるようにする。タブがいくつ増えても横に流れる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassScrollableTabRow(
    hazeState: HazeState,
    selectedTabIndex: Int,
    tabCount: Int,
    modifier: Modifier = Modifier,
    tabs: @Composable () -> Unit,
) {
    val indicator: @Composable TabIndicatorScope.() -> Unit = {
        // 下線の代わりに、選んだタブの文字の後ろへピル（secondaryContainer）を敷き、スワイプに合わせて滑らせる。
        // 下部の「閲覧中 / 全体」のタブと同じ見せ方
        Box(
            Modifier
                .zIndex(-1f)
                .tabIndicatorOffset(selectedTabIndex, matchContentSize = true)
                // 文字の幅に左右 12dp ずつ足して、ピルに余白を持たせる
                .layout { measurable, constraints ->
                    val extra = Spacing.m.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = constraints.minWidth + 2 * extra, maxWidth = constraints.maxWidth + 2 * extra),
                    )
                    layout(constraints.minWidth, placeable.height) { placeable.place(-extra, 0) }
                }
                // 押した反応のピル（タブの文字の後ろ）と同じ高さにそろえて、縦の中央に置く
                .fillMaxHeight()
                .wrapContentHeight(Alignment.CenterVertically)
                .height(Dimens.siteTabPillHeight)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        )
    }
    ProvideGlassEnvironment {
        // 帯は横幅いっぱいにガラスで描く（縁の線は付けない）。帯の下の区切り線も引かない
        BoxWithConstraints(modifier.fillMaxWidth().hazeSurface(hazeState, RoundedCornerShape(0.dp), rim = false), contentAlignment = Alignment.Center) {
            val fixedWidth = Dimens.siteTabWidth * tabCount
            if (fixedWidth <= maxWidth) {
                // 収まるうちは、同じ幅のタブを中央に並べる
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.width(fixedWidth),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicator = indicator,
                    divider = {},
                    tabs = tabs,
                )
            } else {
                // 収まらないほど増えたら、横にスクロールする
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    edgePadding = Spacing.l,
                    indicator = indicator,
                    divider = {},
                    tabs = tabs,
                )
            }
        }
    }
}

/** 透過効果のピル型の面（ボタンやボタンの組を載せる）。置き場所は呼び出し側で決める。 */
@Composable
fun GlassPill(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    fixedOpacity: SurfaceOpacity = SURFACE_OPACITY,
    rim: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val glassShape = CircleShape
    ProvideGlassEnvironment {
        Row(
            modifier = modifier
                .hazeSurface(hazeState, glassShape, fixedOpacity, rim)
                .clip(glassShape)
                .padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** 屈折のないぼかし（Haze Blur）。強さ 0〜10 → 0〜48dp、不透明度 0〜10 → 背景色 0〜100%。 */
private fun Modifier.blurSurface(hazeState: HazeState, fixedOpacity: SurfaceOpacity): Modifier = composed {
    val surface = MaterialTheme.colorScheme.surface
    val config = LocalGlassEffect.current
    fun fraction(v: Int) = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) / 10f
    val radius = (fraction(config.blurStrengthLevel) * 48f).dp
    val alpha = fixedOpacity.blur
    val style = remember(surface, radius, alpha) {
        HazeBlurStyle {
            blurRadius(radius)
            backgroundColor(surface)
            colorEffects(listOf(HazeColorEffect.tint(surface.copy(alpha = alpha))))
        }
    }
    hazeBlur(input = HazeInput.Sources(hazeState), style = style)
}

/**
 * アプリ共通のボトムシートの外枠（書き込み・要約・返信や ID の一覧）。面は設定の透かし方（ぼかし / ガラス）で描き、
 * 背景の不透明度はシート専用の固定値（90%）にする。上側だけ角丸（`shapes.extraLarge`）。
 * 高さはヘッダー（TopAppBar）より上に伸びないよう抑える。システムバー・キーボードの余白は [contentModifier] か中身で取る。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsBottomSheet(
    onDismissRequest: () -> Unit,
    hazeState: HazeState,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentModifier: Modifier = Modifier,
    /** true ならヘッダー（タイトルバー）の位置まで広げられる（長文を書く書き込みシート用）。 */
    coverTopBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val corner = MaterialTheme.shapes.extraLarge
    val sheetShape = remember(corner) {
        RoundedCornerShape(corner.topStart, corner.topEnd, CornerSize(0.dp), CornerSize(0.dp))
    }
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val statusBar = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    // 画面の高さからステータスバーとヘッダーの高さを引いた分まで（ヘッダーは隠さない）
    val maxHeight = windowHeight - statusBar - if (coverTopBar) 0.dp else TopAppBarDefaults.TopAppBarExpandedHeight
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .hazeSurface(hazeState, sheetShape, fixedOpacity = SHEET_OPACITY)
                .clip(sheetShape)
                .then(contentModifier),
        ) {
            BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

/** 画面の透かす元。メニュー（ポップアップ）が下の内容を透かすために、各画面で渡す。 */
val LocalScreenHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * 透過効果のメニュー。面は設定の透かし方（ぼかし / ガラス）で描き、背景の不透明度は文字を読みやすくするため
 * 80% に固定する。透かす元（[LocalScreenHazeState]）がない画面では M3 標準のメニューの色で描く。
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    /** 項目の左下からのずれ。長押しした位置の近くに出すときに使う。 */
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalScreenHazeState.current
    val shape = MaterialTheme.shapes.large as? RoundedCornerShape ?: RoundedCornerShape(CornerSize(0.dp))
    if (hazeState == null) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier, offset = offset, shape = shape, content = content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        // DropdownMenu の modifier は面の内側の Column に掛かる。面は透明にして、Column を透かして描く
        modifier = modifier.hazeSurface(hazeState, shape, fixedOpacity = MENU_OPACITY),
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** 各画面共通の TopAppBar。設定の透かし方（ぼかし / ガラス）で、下を流れる内容を透かす。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsTopAppBar(
    hazeState: HazeState,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val barShape = remember { RoundedCornerShape(0.dp) }
    TopAppBar(
        title = title,
        // タイトルバーも読みやすさを優先して、背景の不透明度を 80% に固定する
        // 画面の幅いっぱいの帯なので、縁の光（rim）を付けない（左右と下の縁が白い線に見えるため）
        modifier = modifier.hazeSurface(hazeState, barShape, fixedOpacity = SURFACE_OPACITY, rim = false),
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 通知（Snackbar）。M3 標準の反転色の帯の代わりに、下部のバーと同じ透過効果のピルで出す。表示と再試行の仕組みは [SnackbarHostState] のまま。
 * 透かす元（[LocalScreenHazeState]）がない画面では、M3 の面の色で描く。
 */
@Composable
fun GlassSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier) { data ->
        val hazeState = LocalScreenHazeState.current
        val shape = CircleShape
        ProvideGlassEnvironment {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.l, vertical = Spacing.s)
                    .widthIn(max = Dimens.maxReadingWidth)
                    .then(
                        if (hazeState != null) Modifier.hazeSurface(hazeState, shape)
                        else Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
                    )
                    .clip(shape)
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(start = Spacing.xl, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    data.visuals.message,
                    modifier = Modifier.weight(1f, fill = false).padding(vertical = Spacing.m),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val action = data.visuals.actionLabel
                if (action != null) {
                    TextButton(onClick = data::performAction) { Text(action) }
                } else {
                    Spacer(Modifier.width(Spacing.m))
                }
            }
        }
    }
}
