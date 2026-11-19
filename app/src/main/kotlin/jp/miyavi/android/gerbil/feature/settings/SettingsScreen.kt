package jp.miyavi.android.gerbil.feature.settings

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import jp.miyavi.android.gerbil.navigation.rememberEnterSettled
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.rememberLazyListState
import jp.miyavi.android.gerbil.feature.thread.DEFAULT_MUTE_CONDITIONS
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.runtime.LaunchedEffect
import android.text.format.Formatter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.SnackbarHostState
import jp.miyavi.android.gerbil.ui.GlassSnackbarHost
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import jp.miyavi.android.gerbil.ui.rememberLinkOpener

import jp.miyavi.android.gerbil.core.settings.LinkOpenMode
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.gerbil.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import jp.miyavi.android.gerbil.BuildConfig
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.DefaultCustomSeed
import jp.miyavi.android.gerbil.core.settings.ColorTheme
import jp.miyavi.android.gerbil.core.settings.AppFont
import jp.miyavi.android.gerbil.core.settings.PostStyle
import jp.miyavi.android.gerbil.core.settings.HazeMode
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.core.settings.ThemeMode
import jp.miyavi.android.gerbil.ui.BoardsTopAppBar
import kotlin.math.roundToInt

/** 画面3: 設定。1画面に縦に並べる（表示 → スクロール → NG → 書き込み → データ → 情報）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    /** 開いたら「フィードバックを送信」（いちばん下）の位置にする。 */
    scrollToFeedback: Boolean = false,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onCustomColor: (Int) -> Unit,
    onFont: (AppFont) -> Unit,
    onPostStyle: (PostStyle) -> Unit = {},
    onGlassOpacity: (Int) -> Unit = {},
    onGlassRefraction: (Int) -> Unit = {},
    onHazeMode: (HazeMode) -> Unit = {},
    onBlurStrength: (Int) -> Unit = {},
    onBlurOpacity: (Int) -> Unit = {},
    onLinkOpenMode: (LinkOpenMode) -> Unit = {},
    onAutoSummary: (Boolean) -> Unit = {},
    onAiMute: (Boolean) -> Unit = {},
    onAutoMute: (Boolean) -> Unit = {},
    onMuteConditions: (String?) -> Unit = {},
    onOpenAiCore: (() -> Unit)? = null,
    /** この端末で AI が使えるか。非対応なら AI のセクションとローカルモデルの削除を出さない。 */
    aiSupported: Boolean = true,
    /** キャッシュ（スレの本文・画像・要約）の容量（バイト）。測る前は null。 */
    cacheBytes: Long? = null,
    onMeasureCache: () -> Unit = {},
    onClearCache: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.settings_ai_unavailable)
    val hazeState = rememberHazeState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val openFailed = stringResource(R.string.thread_url_open_failed)
    val openLink = rememberLinkOpener(settings.linkOpenMode) { scope.launch { snackbar.showSnackbar(openFailed) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { GlassSnackbarHost(snackbar) },
        topBar = {
            BoardsTopAppBar(
                hazeState = hazeState,
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.common_back_cd)) }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val listState = rememberLazyListState()
        // 案内から開いたときは、画面が入りきってから「フィードバックを送信」を2回ふわっと光らせて場所を示す
        val highlight = remember { Animatable(0f) }
        if (scrollToFeedback) {
            val settled = rememberEnterSettled()
            val glowIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val glowOut = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
            LaunchedEffect(settled) {
                if (!settled) return@LaunchedEffect
                repeat(FEEDBACK_HIGHLIGHT_PULSES) {
                    highlight.animateTo(1f, glowIn)
                    highlight.animateTo(0f, glowOut)
                }
            }
        }
        val highlightColor = MaterialTheme.colorScheme.primaryContainer
        // ジャービルの案内から開いたときは、いちばん下まで一瞬で送る（フィードバックの項目は情報セクションにあり、末尾の画面に入る）
        LaunchedEffect(scrollToFeedback) {
            if (!scrollToFeedback) return@LaunchedEffect
            val count = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            listState.scrollToItem(count - 1)
        }
        LazyColumn(Modifier.fillMaxSize().hazeSource(hazeState), state = listState, contentPadding = padding) {
            item { SectionHeader(stringResource(R.string.settings_section_display)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                    Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { onThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                                contentPadding = PaddingValues(horizontal = Spacing.xs),
                                icon = {},
                                label = { Text(stringResource(themeLabel(mode))) },
                            )
                        }
                    }
                }
            }
            item { ChoiceItem(stringResource(R.string.settings_color_theme), stringResource(colorThemeLabel(settings.colorTheme))) { dialog = "color" } }
            if (settings.colorTheme == ColorTheme.CUSTOM) item {
                ChoiceItem(stringResource(R.string.settings_custom_color), colorHex(settings.customColorArgb ?: DefaultCustomSeed.toArgb())) { dialog = "custom" }
            }
            item { ChoiceItem(stringResource(R.string.settings_font), stringResource(fontLabel(settings.font))) { dialog = "font" } }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                    Text(stringResource(R.string.settings_post_style), style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                        PostStyle.entries.forEachIndexed { index, style ->
                            SegmentedButton(
                                selected = settings.postStyle == style,
                                onClick = { onPostStyle(style) },
                                shape = SegmentedButtonDefaults.itemShape(index, PostStyle.entries.size),
                                contentPadding = PaddingValues(horizontal = Spacing.xs),
                                icon = {},
                                label = { Text(stringResource(postStyleLabel(style))) },
                            )
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
                    Text(stringResource(R.string.settings_haze_mode), style = MaterialTheme.typography.bodyLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
                        HazeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.hazeMode == mode,
                                onClick = { onHazeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, HazeMode.entries.size),
                                contentPadding = PaddingValues(horizontal = Spacing.xs),
                                icon = {},
                                label = { Text(stringResource(hazeModeLabel(mode))) },
                            )
                        }
                    }
                }
            }
            if (settings.hazeMode == HazeMode.BLUR) {
                item {
                    GlassLevelSlider(
                        title = stringResource(R.string.settings_blur_strength),
                        description = stringResource(R.string.settings_blur_strength_desc),
                        level = settings.blurStrengthLevel,
                        onLevelChange = onBlurStrength,
                    )
                }
            }
            if (settings.hazeMode == HazeMode.GLASS) item {
                GlassLevelSlider(
                    title = stringResource(R.string.settings_glass_refraction),
                    description = stringResource(R.string.settings_glass_refraction_desc),
                    level = settings.glassRefractionLevel,
                    onLevelChange = onGlassRefraction,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_features)) }
            item { ChoiceItem(stringResource(R.string.settings_link_open), stringResource(linkLabel(settings.linkOpenMode))) { dialog = "link" } }

            if (aiSupported) {
            item { SectionHeader(stringResource(R.string.settings_section_ai)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_summary)) },
                    supportingContent = { Text(stringResource(R.string.settings_auto_summary_desc)) },
                    trailingContent = { Switch(checked = settings.autoSummary, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = settings.autoSummary, role = Role.Switch, onValueChange = onAutoSummary),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_mute)) },
                    supportingContent = { Text(stringResource(R.string.settings_ai_mute_desc)) },
                    trailingContent = { Switch(checked = settings.aiMute, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = settings.aiMute, role = Role.Switch, onValueChange = onAiMute),
                )
            }
            item {
                // AI ミュートが OFF のあいだは選べない（入れても働かないため）
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_mute)) },
                    supportingContent = { Text(stringResource(R.string.settings_auto_mute_desc)) },
                    trailingContent = { Switch(checked = settings.aiMute && settings.autoMute, onCheckedChange = null, enabled = settings.aiMute) },
                    modifier = Modifier.toggleable(value = settings.autoMute, enabled = settings.aiMute, role = Role.Switch, onValueChange = onAutoMute),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_mute_conditions)) },
                    supportingContent = {
                        Text(settings.muteConditions ?: DEFAULT_MUTE_CONDITIONS, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.clickable(enabled = settings.aiMute) { dialog = "mute" },
                )
            }
            }

            // データ: 消しても取り直せるものだけ。容量を空けたいときに使う
            item { SectionHeader(stringResource(R.string.settings_section_data)) }
            item {
                LaunchedEffect(Unit) { onMeasureCache() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_clear_cache)) },
                    supportingContent = { Text(stringResource(R.string.settings_clear_cache_desc)) },
                    trailingContent = { cacheBytes?.let { Text(Formatter.formatShortFileSize(context, it)) } },
                    modifier = Modifier.clickable(role = Role.Button) { dialog = "clear_cache" },
                )
            }
            if (aiSupported) item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_ai_manage)) },
                    supportingContent = { Text(AnnotatedString.fromHtml(stringResource(R.string.settings_ai_manage_desc))) },
                    modifier = Modifier.clickable(role = Role.Button) {
                        if (onOpenAiCore != null) onOpenAiCore() else {
                            val opened = runCatching {
                                context.packageManager.getApplicationInfo(AICORE_PACKAGE, 0)
                                context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", AICORE_PACKAGE, null)))
                            }.isSuccess
                            if (!opened) scope.launch { snackbar.showSnackbar(unavailable) }
                        }
                    },
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feedback)) },
                    supportingContent = { Text(stringResource(R.string.settings_feedback_desc)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.drawBehind { drawRect(highlightColor.copy(alpha = highlight.value)) }.clickable { openLink(feedbackUrl(deviceInfo(context, settings, aiSupported))) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licenses)) },
                    supportingContent = { Text(stringResource(R.string.settings_licenses_desc)) },
                    modifier = Modifier.clickable { dialog = "licenses" },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                    modifier = Modifier.clickable { openLink(PRIVACY_POLICY_URL) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_version)) },
                    supportingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }
        }
    }

    when (dialog) {
        "color" -> ChoiceDialog(stringResource(R.string.settings_color_theme), ColorTheme.entries, settings.colorTheme, ::colorThemeLabel, { dialog = null }) {
            onColorTheme(it)
            dialog = null
        }
        "font" -> ChoiceDialog(stringResource(R.string.settings_font), AppFont.entries, settings.font, ::fontLabel, { dialog = null }) {
            onFont(it)
            dialog = null
        }
        "custom" -> CustomColorDialog(settings.customColorArgb ?: DefaultCustomSeed.toArgb(), { dialog = null }) {
            onCustomColor(it)
            dialog = null
        }
        "link" -> ChoiceDialog(stringResource(R.string.settings_link_open), LinkOpenMode.entries, settings.linkOpenMode, ::linkLabel, { dialog = null }) {
            onLinkOpenMode(it)
            dialog = null
        }
        "mute" -> MuteConditionsDialog(settings.muteConditions ?: DEFAULT_MUTE_CONDITIONS, { dialog = null }) {
            onMuteConditions(it.takeIf { c -> c.isNotBlank() && c.trim() != DEFAULT_MUTE_CONDITIONS })
            dialog = null
        }
        "licenses" -> LicensesDialog { dialog = null }
        "clear_cache" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm)) },
            confirmButton = { TextButton(onClick = { onClearCache(); dialog = null }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** AI ミュートの条件を書き換える。1行に1つ。空にするか初期値に戻すと初期値を使う。 */
@Composable
private fun MuteConditionsDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_mute_conditions)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.settings_mute_conditions_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { text = DEFAULT_MUTE_CONDITIONS }) { Text(stringResource(R.string.settings_mute_conditions_reset)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.s),
    )
}

@Composable
private fun ChoiceItem(title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(stringResource(label(option))) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

@Composable
private fun CustomColorDialog(initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable(initial) { mutableStateOf(colorHex(initial).removePrefix("#")) }
    val color = parseHexColor(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_custom_color)) },
        text = { CustomColorForm(value = value, onValueChange = { value = it }, color = color) },
        confirmButton = { TextButton(onClick = { color?.let(onSave) }, enabled = color != null) { Text(stringResource(R.string.common_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun CustomColorForm(value: String, onValueChange: (String) -> Unit, color: Int?) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(sanitizeHexInput(it)) },
            label = { Text(stringResource(R.string.settings_color_code)) },
            singleLine = true,
            isError = value.length != 6,
            modifier = Modifier.fillMaxWidth(),
        )
        if (color != null) Column(Modifier.heightIn(max = Dimens.colorControlsMaxHeight).verticalScroll(rememberScrollState())) {
            Surface(color = Color(color), shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).height(Dimens.minTouchTarget)) {}
            listOf(R.string.settings_color_red to 16, R.string.settings_color_green to 8, R.string.settings_color_blue to 0).forEach { (label, shift) ->
                val channel = (color shr shift) and 0xFF
                Text(stringResource(R.string.settings_color_channel, stringResource(label), channel), style = MaterialTheme.typography.labelMedium)
                Slider(value = channel.toFloat(), valueRange = 0f..255f, onValueChange = { new ->
                    val changed = (color and (0xFF shl shift).inv()) or (new.roundToInt() shl shift)
                    onValueChange(colorHex(changed).removePrefix("#"))
                })
            }
        }
    }
}

internal fun sanitizeHexInput(input: String) =
    input.uppercase().filter { ch -> ch in '0'..'9' || ch in 'A'..'F' }.take(6)

internal fun parseHexColor(value: String): Int? =
    value.takeIf { it.length == 6 }?.toIntOrNull(16)?.or(0xFF shl 24)

private fun colorHex(value: Int) = "#%06X".format(value and 0xFFFFFF)

private fun colorThemeLabel(v: ColorTheme) = when (v) {
    ColorTheme.SUNA -> R.string.theme_suna
    ColorTheme.DYNAMIC -> R.string.theme_dynamic
    ColorTheme.CANYON -> R.string.theme_canyon
    ColorTheme.FOG -> R.string.theme_fog
    ColorTheme.OLIVE -> R.string.theme_olive
    ColorTheme.OBSIDIAN -> R.string.theme_obsidian
    ColorTheme.CUSTOM -> R.string.theme_custom
}

private fun postStyleLabel(v: PostStyle) = when (v) {
    PostStyle.MODERN -> R.string.post_style_modern
    PostStyle.SIMPLE -> R.string.post_style_simple
}

private fun fontLabel(v: AppFont) = when (v) {
    AppFont.SYSTEM -> R.string.font_system
    AppFont.LINE_SEED_JP -> R.string.font_line_seed_jp
}

private fun themeLabel(v: ThemeMode) = when (v) {
    ThemeMode.SYSTEM -> R.string.theme_mode_system
    ThemeMode.LIGHT -> R.string.theme_mode_light
    ThemeMode.DARK -> R.string.theme_mode_dark
}

@Composable
private fun GlassLevelSlider(
    title: String,
    description: String,
    level: Int,
    onLevelChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "$level / ${Settings.MAX_GLASS_LEVEL}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevelChange(it.roundToInt()) },
            valueRange = Settings.MIN_GLASS_LEVEL.toFloat()..Settings.MAX_GLASS_LEVEL.toFloat(),
            steps = Settings.MAX_GLASS_LEVEL - Settings.MIN_GLASS_LEVEL - 1,
        )
    }
}

private fun hazeModeLabel(v: HazeMode) = when (v) {
    HazeMode.BLUR -> R.string.haze_blur
    HazeMode.GLASS -> R.string.haze_glass
}

private fun linkLabel(v: LinkOpenMode) = when (v) {
    LinkOpenMode.IN_APP -> R.string.link_open_in_app
    LinkOpenMode.BROWSER -> R.string.link_open_browser
}

private const val AICORE_PACKAGE = "com.google.android.aicore"

/** 案内から開いたときに「フィードバックを送信」を光らせる回数。 */
private const val FEEDBACK_HIGHLIGHT_PULSES = 2

/** 同梱しているオープンソースのライセンス（assets/licenses の一覧と全文）。APK に添える義務を満たすために出す。 */
@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val assets = LocalContext.current.assets
    val text by produceState("") {
        value = withContext(Dispatchers.IO) {
            LICENSE_FILES.joinToString("\n\n----------\n\n") { name ->
                assets.open("licenses/$name").bufferedReader().use { it.readText().trim() }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_licenses)) },
        text = {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** 表示する順（一覧 → 各ライセンスの全文）。 */
private val LICENSE_FILES = listOf("third-party.txt", "mit.txt", "hugeicons-MIT.txt", "line-seed-jp-OFL.txt", "apache-2.0.txt")

/** プライバシーポリシー（公開のレポジトリの docs/privacy-policy.md）。Google Play の掲載にも同じ URL を使う。 */
private const val PRIVACY_POLICY_URL = "https://github.com/gloria0021/gerbil/blob/main/docs/privacy-policy.md"
