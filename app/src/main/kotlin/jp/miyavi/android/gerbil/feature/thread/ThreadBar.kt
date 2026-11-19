package jp.miyavi.android.gerbil.feature.thread

import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.Animatable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import jp.miyavi.android.gerbil.ui.GlassPill
import jp.miyavi.android.gerbil.ui.SURFACE_OPACITY
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.FilledTonalIconButton
import jp.miyavi.android.gerbil.ui.GlassDropdownMenu
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Spacing

/**
 * スレ画面の下部。右端に透過効果のピルを1つ置き、左に操作（新着）、縦線で区切って右に道具（絞り込み・検索）を並べる。
 * 検索中はピルが1本の検索欄（前後の送り付き）に形を変える。幅は本文と同じ最大幅に揃える。
 */
@Composable
internal fun BoxScope.ThreadBottomBar(
    hazeState: HazeState,
    refreshing: Boolean,
    /** 「新着」を押して取りに行っているあいだ（開いたときの自動の更新は含まない）。ベルを揺らす。 */
    manualRefreshing: Boolean = false,
    onRefreshToNew: () -> Unit,
    onSummary: () -> Unit,
    /** この端末で AI が使えるか（非対応の端末では要約の入口を出さない）。 */
    aiAvailable: Boolean = true,
    aiFilter: AiFilterUiState,
    onAiFilter: (AiFilterKind, Boolean) -> Unit,
    onReloadMute: () -> Unit,
    aiMuteAvailable: Boolean = true,
    filter: ThreadFilter?,
    filteredCount: Int,
    onFilter: (ThreadFilter?) -> Unit,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    matchPosition: Int,
    matchCount: Int,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    // 2つのピルと検索欄は、ばねで伸び縮みしながら入れ替わる（同じ面が姿を変えて見えるように）
    AnimatedContent(
        targetState = searchOpen,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = SWAP_SCALE)) togetherWith
                fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.Center,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .widthIn(max = Dimens.maxReadingWidth)
            .fillMaxWidth()
            .padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l),
        label = "threadBar",
    ) { searching ->
        if (searching) {
            // 検索欄は入力中の文字が下の本文と重なって読みにくいので、背景を 80% まで不透明にする
            GlassPill(hazeState = hazeState, modifier = Modifier.fillMaxWidth(), fixedOpacity = SURFACE_OPACITY) {
                SearchContent(
                    query = query,
                    onQueryChange = onQueryChange,
                    matchPosition = matchPosition,
                    matchCount = matchCount,
                    onPrev = onPrevMatch,
                    onNext = onNextMatch,
                    onClose = { onSearchOpenChange(false) },
                )
            }
        } else {
            // 1つのピルを右端に置く。左に操作（新着）、縦線で区切って右に道具（絞り込み・検索）
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                GlassPill(hazeState = hazeState, fixedOpacity = SURFACE_OPACITY) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onRefreshToNew,
                        enabled = !refreshing,
                        contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    ) {
                        RingingBell(ringing = manualRefreshing)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.thread_bar_new), maxLines = 1)
                    }
                    VerticalDivider(
                        modifier = Modifier.height(Spacing.xl).padding(horizontal = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    if (aiAvailable) IconButton(onClick = onSummary) {
                        Icon(painterResource(R.drawable.ic_huge_ai_magic), contentDescription = stringResource(R.string.summary_title))
                    }
                    FilterChipSlot(filter = filter, filteredCount = filteredCount, onFilter = onFilter)
                    FilterButton(
                        filter = filter, onFilter = onFilter, blur = aiFilter, onBlur = onAiFilter,
                        onReloadMute = onReloadMute, aiMuteAvailable = aiMuteAvailable,
                    )
                    IconButton(onClick = { onSearchOpenChange(true) }) {
                        Icon(painterResource(R.drawable.ic_huge_search), contentDescription = stringResource(R.string.thread_bar_search))
                    }
                    }
                }
            }
        }
    }
}


/** 絞り込み中だけ、何で絞っているか・何件かをチップで示す。タップで解除。消える途中も直前の内容を出す。 */
@Composable
private fun FilterChipSlot(filter: ThreadFilter?, filteredCount: Int, onFilter: (ThreadFilter?) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val lastFilter = remember { arrayOfNulls<ThreadFilter>(1) }
    if (filter != null) lastFilter[0] = filter
    AnimatedVisibility(
        visible = filter != null,
        enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.fastSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.fastSpatialSpec()),
    ) {
        val shown = lastFilter[0] ?: return@AnimatedVisibility
        InputChip(
            selected = true,
            onClick = { onFilter(null) },
            label = { Text("${stringResource(shown.label)}  $filteredCount", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_huge_close),
                    contentDescription = stringResource(R.string.thread_filter_clear_cd),
                    modifier = Modifier.size(InputChipDefaults.IconSize),
                )
            },
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/**
 * 絞り込みとぼかしのボタン。押すとメニューを出す。
 * 「絞り込み」は該当するレスだけを表示する（1つだけ選ぶ。選ぶとメニューを閉じる。選んでいるものをもう一度押すと解除）。
 * 「ぼかし」は該当するレスの本文をぼかす（複数選べる。選んでもメニューは閉じない）。どちらかを使っているあいだは選択状態の色になる。
 */
@Composable
private fun FilterButton(
    filter: ThreadFilter?,
    onFilter: (ThreadFilter?) -> Unit,
    blur: AiFilterUiState,
    onBlur: (AiFilterKind, Boolean) -> Unit,
    onReloadMute: () -> Unit,
    aiMuteAvailable: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconToggleButton(checked = filter != null || blur.kinds.isNotEmpty(), onCheckedChange = { open = true }) {
            Icon(painterResource(R.drawable.ic_huge_filter), contentDescription = stringResource(R.string.thread_bar_filter))
        }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuHeading(stringResource(R.string.thread_bar_filter))
            HorizontalDivider()
            ThreadFilter.entries.forEach { f ->
                DropdownMenuItem(
                    text = { Text(stringResource(f.label)) },
                    onClick = {
                        open = false
                        onFilter(if (f == filter) null else f)
                    },
                    leadingIcon = {
                        if (f == filter) Icon(painterResource(R.drawable.ic_huge_check), contentDescription = stringResource(R.string.common_selected_cd))
                    },
                )
            }
            // AI ミュートは見出しを立てず、この1項目のチェックで入り切りする（今はゴミレスだけ）。設定で無効なら出さない
            if (aiMuteAvailable) {
            HorizontalDivider()
            val selected = AiFilterKind.JUNK in blur.kinds
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.ai_filter_title))
                        if (selected && blur.judging) {
                            Text(
                                stringResource(R.string.ai_filter_judging, blur.judged, blur.total),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (selected && blur.applied) {
                            Text(
                                stringResource(R.string.ai_filter_applied),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (selected && blur.unavailable) {
                            Text(
                                stringResource(R.string.ai_filter_unavailable),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                onClick = { onBlur(AiFilterKind.JUNK, !selected) },
                leadingIcon = {
                    if (selected) Icon(painterResource(R.drawable.ic_huge_check), contentDescription = stringResource(R.string.common_selected_cd))
                },
                trailingIcon = {
                    if (selected && blur.applied) {
                        IconButton(onClick = onReloadMute) {
                            Icon(
                                painterResource(R.drawable.ic_huge_refresh),
                                contentDescription = stringResource(R.string.ai_filter_reload_cd),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(Spacing.l),
                            )
                        }
                    } else {
                        Icon(painterResource(R.drawable.ic_huge_ai_magic), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.l))
                    }
                },
            )
            }
        }
    }
}

/** メニューの中の見出し。[icon] があれば文字の左に小さく添える（AI の機能の目印など）。 */
@Composable
private fun MenuHeading(text: String, @DrawableRes icon: Int? = null) {
    Row(
        Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Spacing.l))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    matchPosition: Int,
    matchCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            keyboard?.hide()
            onClose()
        }) {
            Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.thread_search_close_cd))
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // キーボードの検索キーで次の一致へ送る
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.thread_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    field()
                }
            },
        )
        if (query.isNotBlank()) {
            Text(
                if (matchCount > 0) stringResource(R.string.thread_search_count, matchPosition + 1, matchCount)
                else stringResource(R.string.thread_search_no_match),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
        IconButton(onClick = onPrev, enabled = matchCount > 0) {
            Icon(painterResource(R.drawable.ic_huge_arrow_up), contentDescription = stringResource(R.string.thread_search_prev_cd))
        }
        IconButton(onClick = onNext, enabled = matchCount > 0) {
            Icon(painterResource(R.drawable.ic_huge_arrow_down), contentDescription = stringResource(R.string.thread_search_next_cd))
        }
    }
}

/** 普段と検索中を入れ替えるとき、新しい中身が少し小さい状態から広がる割合。 */
private const val SWAP_SCALE = 0.92f

/**
 * 「新着」のベル。取りに行っているあいだは、上を支点に小刻みに揺らす（読み込みの表示なのでくり返してよい）。
 * 更新が一瞬で終わっても1回分は揺らし切り、揺れの終わり（0 度）で止める。角度は描画の段階で読む。
 */
@Composable
private fun RingingBell(ringing: Boolean) {
    val angle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val stillRinging by rememberUpdatedState(ringing)
    var job by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(ringing) {
        if (ringing && job?.isActive != true) {
            job = scope.launch { do angle.animateTo(0f, BELL_RING) while (stillRinging) }
        }
    }
    Icon(
        painterResource(R.drawable.ic_huge_notification),
        contentDescription = null,
        modifier = Modifier
            .size(ButtonDefaults.IconSize)
            .graphicsLayer {
                transformOrigin = BELL_PIVOT
                rotationZ = angle.value
            },
    )
}

/** リンリンリン: 大きく振ってから小さくし、少し休む（1回分）。 */
private val BELL_RING = keyframes {
    durationMillis = 720
    0f at 0
    16f at 60
    -16f at 140
    12f at 220
    -12f at 300
    6f at 380
    -6f at 450
    0f at 520
}
/** ベルは上の吊り下げ部分を支点に揺れる。 */
private val BELL_PIVOT = TransformOrigin(0.5f, 0.1f)
