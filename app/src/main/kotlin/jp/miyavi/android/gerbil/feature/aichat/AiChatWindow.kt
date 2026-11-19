package jp.miyavi.android.gerbil.feature.aichat

import androidx.compose.ui.text.style.TextAlign
import jp.miyavi.android.gerbil.core.designsystem.ThreadFastScroller
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import jp.miyavi.android.gerbil.ui.ReadingFontSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.Markdown
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.genai.common.FeatureStatus
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.sheetFieldColor
import jp.miyavi.android.gerbil.feature.thread.ModelDownloadState
import jp.miyavi.android.gerbil.ui.ScreenMotionScheme
import kotlinx.coroutines.withContext

@Composable
internal fun AiChatWindow(viewModel: AiChatViewModel, anchor: Offset, fontSize: Int, onClose: (ChatDestination?) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val download by viewModel.downloadState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle) {
        viewModel.open()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.close()
        }
    }
    val language = LocalConfiguration.current.locales[0].language
    val listState = rememberLazyListState()
    LaunchedEffect(state.turns.size, state.turns.lastOrNull()?.answer, state.turns.lastOrNull()?.searchMessage) {
        // 一覧の先頭はリセットの行なので、最後のやり取りは turns.size 番目
        if (state.turns.isNotEmpty()) listState.scrollToItem(state.turns.size)
    }
    val modelReady = state.modelStatus == FeatureStatus.AVAILABLE
    val sendingEnabled = modelReady && !state.generating && state.draft.isNotBlank()
    Dialog(
        onDismissRequest = { onClose(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        val keyboard = LocalSoftwareKeyboardController.current
        val focus = LocalFocusManager.current
        val send = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.send(language)
        }
        var closing by remember { mutableStateOf(false) }
        var selectedDestination by remember { mutableStateOf<ChatDestination?>(null) }
        val dismiss = {
            focus.clearFocus()
            keyboard?.hide()
            viewModel.stop()
            closing = true
        }
        androidx.activity.compose.BackHandler(onBack = dismiss)
        val progress = remember { Animatable(0f) }
        // 開き切ってから候補を作らせる（開く動きと AI の処理を重ねない）
        val fullyOpened by remember { derivedStateOf { progress.value >= 1f && !progress.isRunning } }
        LaunchedEffect(fullyOpened, modelReady, language) {
            if (fullyOpened && modelReady && !closing) viewModel.suggestQuestion(language)
        }
        val scrim = MaterialTheme.colorScheme.scrim
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { alpha = progress.value * CHAT_SCRIM_ALPHA }
                    .background(scrim)
                    .clickable(interactionSource = null, indication = null, onClick = dismiss),
            )
            BoxWithConstraints(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(Spacing.l),
            ) {
                val density = LocalDensity.current
                var windowOrigin by remember { mutableStateOf(Offset.Unspecified) }
                val laidOut = windowOrigin.isSpecified
                LaunchedEffect(laidOut, closing) {
                    if (closing) {
                        progress.animateTo(0f, ScreenMotionScheme.fastSpatialSpec())
                        onClose(selectedDestination)
                    } else if (laidOut) {
                        progress.animateTo(1f, ScreenMotionScheme.defaultSpatialSpec())
                    }
                }
                val start = if (laidOut && anchor.isSpecified) anchor - windowOrigin else Offset.Zero
                val width = minOf(maxWidth, CHAT_WINDOW_MAX_WIDTH)
                val top = with(density) { start.y.toDp() }.coerceIn(0.dp, maxHeight / 4)
                val height = minOf(maxHeight - top, CHAT_WINDOW_MAX_HEIGHT)
                val left = maxWidth - width
                val iconSize = Spacing.xl
                var headerAvatarCenter by remember { mutableStateOf(Offset.Unspecified) }
                Box(
                    Modifier.matchParentSize().onGloballyPositioned { windowOrigin = it.positionOnScreen() },
                    contentAlignment = AbsoluteAlignment.TopRight,
                ) {
                    if (laidOut && width > 0.dp && height > 0.dp) {
                        val pivot = with(density) {
                            TransformOrigin((start.x - left.toPx()) / width.toPx(), (start.y - top.toPx()) / height.toPx())
                        }
                        Surface(
                            modifier = Modifier.padding(top = top).width(width).height(height).graphicsLayer {
                                val fraction = progress.value.coerceIn(0f, 1f)
                                val startWidth = (iconSize.toPx() / size.width).coerceAtMost(1f)
                                val startHeight = (iconSize.toPx() / size.height).coerceAtMost(1f)
                                scaleX = startWidth + (1f - startWidth) * fraction
                                scaleY = startHeight + (1f - startHeight) * fraction
                                transformOrigin = pivot
                                alpha = fraction
                            },
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(Modifier.fillMaxSize().padding(Spacing.l)) {
                                // 文字の大きさは、スレ表示と同じ設定に合わせる
                                ReadingFontSize(fontSize) {
                                ChatContent(
                                    state, download, viewModel, language, listState, sendingEnabled, send, dismiss,
                                    onAvatarPlaced = { headerAvatarCenter = it },
                                    onOpenDestination = { destination ->
                                        if (!closing) {
                                            selectedDestination = destination
                                            dismiss()
                                        }
                                    },
                                )
                                }
                            }
                        }
                        val avatarCenter = with(density) {
                            val panelContentStart = Offset((left + Spacing.l).toPx(), (top + Spacing.l).toPx())
                            val innerCenter = if (headerAvatarCenter.isSpecified) headerAvatarCenter else {
                                val radius = (Dimens.minTouchTarget / 2).toPx()
                                Offset(radius, radius)
                            }
                            panelContentStart + innerCenter
                        }
                        Icon(
                            painterResource(R.drawable.gerbil_chat_peek),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.align(AbsoluteAlignment.TopLeft).size(iconSize).graphicsLayer {
                                val center = start + (avatarCenter - start) * progress.value
                                // 絵はしっぽの分だけ右に伸びていて、頭は絵の中心より左にある。丸に収まったら頭の中心を丸の中心に合わせる
                                translationX = center.x - size.width / 2f + size.width * PEEK_HEAD_OFFSET * progress.value
                                translationY = center.y - size.height / 2f
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatContent(
    state: ChatState,
    download: ModelDownloadState,
    viewModel: AiChatViewModel,
    language: String,
    listState: LazyListState,
    sendingEnabled: Boolean,
    send: () -> Unit,
    onClose: () -> Unit,
    onAvatarPlaced: (Offset) -> Unit,
    onOpenDestination: (ChatDestination) -> Unit,
) {
    val modelReady = state.modelStatus == FeatureStatus.AVAILABLE
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        ChatAvatarFrame(
            modifier = Modifier.onPlaced { coordinates ->
                val center = Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                onAvatarPlaced(coordinates.positionInParent() + center)
            },
        )
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ai_chat_title), style = MaterialTheme.typography.titleLarge)
            val modelText = when (state.modelStatus) {
                FeatureStatus.AVAILABLE -> state.modelName?.let {
                    stringResource(R.string.summary_model_status_ready_named, it)
                } ?: stringResource(R.string.summary_model_status_ready)
                FeatureStatus.DOWNLOADABLE -> stringResource(R.string.summary_model_status_downloadable)
                FeatureStatus.DOWNLOADING -> stringResource(R.string.summary_model_status_downloading)
                FeatureStatus.UNAVAILABLE -> stringResource(R.string.summary_model_status_unavailable)
                else -> stringResource(R.string.summary_model_status_checking)
            }
            Text(modelText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_huge_close), stringResource(R.string.common_close))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = Spacing.s),
        color = sheetFieldColor(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        val downloading = download is ModelDownloadState.Downloading || state.modelStatus == FeatureStatus.DOWNLOADING
        val needsModel = state.modelStatus == FeatureStatus.DOWNLOADABLE || download is ModelDownloadState.Failed
        if (!modelReady && (downloading || needsModel)) {
            // モデルの取得の案内は、要約と同じ形でチャットの面の中に出す
            ChatModelSetup(download, downloading, onDownload = viewModel::download)
        } else if (state.turns.isEmpty()) {
            ChatWelcome(
                // 押したら、入力欄に入れてそのまま送る
                onPrompt = { prompt ->
                    viewModel.updateDraft(prompt)
                    send()
                },
                enabled = modelReady,
                suggestion = state.suggestion,
            )
        } else {
            // やり取りが面の高さを超えたら、スクロール中だけ右端にスクロールバーを出す（止まると消える。スレ表示と同じ部品）
            ThreadFastScroller(
                listState = listState,
                itemCount = state.turns.size + 1,
                label = { i -> state.turns.getOrNull(i - 1)?.question?.take(CHAT_SCROLL_LABEL_CHARS).orEmpty() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    // 会話をはじめからにするボタンは、要約・質問と同じく表示エリアの中の右上に置く（一覧と一緒に流れる）
                    item(key = "reset") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = viewModel::reset) {
                                Icon(painterResource(R.drawable.ic_huge_reset), stringResource(R.string.ai_chat_reset_cd))
                            }
                        }
                    }
                    itemsIndexed(state.turns, key = { index, _ -> index }) { index, turn ->
                        ChatMessage(turn, typing = state.generating && index == state.turns.lastIndex, onOpenDestination)
                    }
                }
            }
        }
    }
    // AI が答えたやり取りがあるときは、要約と同じ注意書きを一段小さく添える（検索の結果だけのときは出さない）
    if (state.turns.any { it.answer != null }) {
        Text(
            stringResource(R.string.summary_caution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // 上はチャットの面の下の余白（8dp）がそのまま空くので、下だけ空けて上下を 8dp に揃える
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
        )
    }
    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    when {
        // 考えている間は、チャットの中に入力中の「…」を出す
        state.generating -> Unit
        modelReady -> {
            if (state.turns.lastOrNull()?.completed == false && state.error != R.string.ai_chat_too_long) {
                TextButton(onClick = { viewModel.send(language, retry = true) }) {
                    Text(stringResource(R.string.ai_chat_retry))
                }
            }
        }
        // 取得前・取得中・失敗は、上のチャットの面の中で案内する
        download is ModelDownloadState.Downloading || state.modelStatus == FeatureStatus.DOWNLOADING ||
            state.modelStatus == FeatureStatus.DOWNLOADABLE || download is ModelDownloadState.Failed -> Unit
        state.modelStatus == FeatureStatus.UNAVAILABLE -> Text(stringResource(R.string.ai_chat_unavailable))
        state.error != null -> TextButton(onClick = viewModel::checkModel) { Text(stringResource(R.string.ai_chat_retry)) }
        else -> LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    val inputLabel = stringResource(R.string.ai_chat_input)
    val colors = MaterialTheme.colorScheme
    val inputStyle = MaterialTheme.typography.bodyLarge
    var inputFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.extraLarge,
            color = sheetFieldColor(),
            border = BorderStroke(1.dp, if (inputFocused) colors.primary else colors.outline),
        ) {
            BasicTextField(
                value = state.draft,
                onValueChange = viewModel::updateDraft,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget)
                    .onFocusChanged { inputFocused = it.isFocused }
                    .semantics { contentDescription = inputLabel },
                textStyle = inputStyle.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (sendingEnabled) send() }),
                decorationBox = { textField ->
                    Box(
                        Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (state.draft.isEmpty()) Text(inputLabel, color = colors.onSurfaceVariant, style = inputStyle)
                        textField()
                    }
                },
            )
        }
        FilledIconButton(
            onClick = if (state.generating) viewModel::stop else send,
            enabled = state.generating || sendingEnabled,
            modifier = Modifier.size(Dimens.minTouchTarget),
        ) {
            Icon(
                painterResource(if (state.generating) R.drawable.ic_huge_stop else R.drawable.ic_huge_arrow_up),
                stringResource(if (state.generating) R.string.ai_chat_stop else R.string.ai_chat_send),
            )
        }
    }
}

@Composable
private fun ChatAvatarFrame(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(Dimens.minTouchTarget),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
    ) {}
}

@Composable
private fun ChatWelcome(onPrompt: (String) -> Unit, enabled: Boolean, suggestion: String?) {
    Column(
        modifier = Modifier.padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(stringResource(R.string.ai_chat_intro), style = MaterialTheme.typography.bodyLarge)
        // 質問の候補は、決まった3つと AI が作る1つ。幅に合わせて折り返し、2行までに収める（入りきらない分は出さない）。押すとそのまま送る
        // 短い2つを先に並べて1行目に収め、長い1つを2行目に置く（2行に収まらないと後ろの候補から落ちるため）
        val fixed = listOf(R.string.ai_chat_starter_abilities, R.string.ai_chat_starter_bug, R.string.ai_chat_starter_limits)
            .map { stringResource(it) }
        FlowRow(maxLines = 2, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        fixed.forEach { prompt ->
            SuggestionChip(onClick = { onPrompt(prompt) }, label = { Text(prompt, style = MaterialTheme.typography.labelMedium) }, enabled = enabled)
        }
        // AI が作った候補を1つ、最後に左から流れるように出す（位置は固定したまま、マスクを右へ広げる）
        if (suggestion != null) {
            val reveal = remember { Animatable(0f) }
            val revealSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
            LaunchedEffect(Unit) {
                val systemScale = coroutineContext[MotionDurationScale]
                val revealScale = object : MotionDurationScale {
                    override val scaleFactor get() = (systemScale?.scaleFactor ?: 1f) * SUGGESTION_REVEAL_DURATION_SCALE
                }
                withContext(revealScale) { reveal.animateTo(1f, revealSpec) }
            }
            SuggestionChip(
                onClick = { onPrompt(suggestion) },
                label = { Text(suggestion, style = MaterialTheme.typography.labelMedium) },
                enabled = enabled,
                modifier = Modifier.graphicsLayer {
                    compositingStrategy = if (reveal.value < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
                }.drawWithContent {
                    drawContent()
                    if (reveal.value < 1f) {
                        val feather = Spacing.xl.toPx()
                        val edge = (size.width + feather) * reveal.value.coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = edge - feather, endX = edge),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
            )
        }
        }
    }
}

@Composable
private fun ChatMessage(turn: ChatTurn, typing: Boolean, onOpenDestination: (ChatDestination) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Surface(
            modifier = Modifier.align(Alignment.End).padding(start = Spacing.xl),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(turn.question, Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m), style = MaterialTheme.typography.bodyLarge)
        }
        turn.answer?.let { ChatMarkdown(it) }
        turn.searchMessage?.let { ChatMarkdown(stringResource(it)) }
        if (typing && !turn.completed) TypingIndicator()
        if (turn.destinations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                turn.destinations.forEach { destination ->
                    Surface(
                        onClick = { onOpenDestination(destination) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(Spacing.m),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (destination.link) {
                                        ChatLink.FEEDBACK -> stringResource(R.string.ai_chat_open_feedback)
                                        ChatLink.SITES -> stringResource(R.string.ai_chat_open_sites)
                                        null -> destination.title
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                destination.boardName?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                            }
                            Icon(painterResource(R.drawable.ic_huge_arrow_right), contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

private val CHAT_WINDOW_MAX_WIDTH = 560.dp
private val CHAT_WINDOW_MAX_HEIGHT = 640.dp

private const val CHAT_SCRIM_ALPHA = 0.32f

/** ジャービルの答え。太字・箇条書きなどの Markdown で描く（要約と同じ部品）。 */
@Composable
private fun ChatMarkdown(text: String) {
    Markdown(
        content = text,
        colors = markdownColor(),
        typography = markdownTypography(),
        padding = markdownPadding(listItemTop = Spacing.xs, listItemBottom = Spacing.xs),
    )
}

/** 候補が流れて出る動きを、トークンの slowEffects より少しゆっくりにする倍率。 */
private const val SUGGESTION_REVEAL_DURATION_SCALE = 2f

/**
 * モデルの取得の案内（要約と同じ内容と並び）。取得前は注意とボタン、取得中は進み具合（分かるときだけ）と
 * 閉じてもよいこと、失敗したら再試行。取得は端末（AICore）が裏で行う。
 */
@Composable
private fun ChatModelSetup(download: ModelDownloadState, downloading: Boolean, onDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val muted = MaterialTheme.colorScheme.onSurfaceVariant
        when {
            download is ModelDownloadState.Failed -> {
                Text(stringResource(R.string.ai_chat_download_failed), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onDownload) { Text(stringResource(R.string.ai_chat_retry)) }
            }
            downloading -> {
                val progress = download as? ModelDownloadState.Downloading
                val total = progress?.total
                if (progress != null && total != null && total > 0) {
                    val fraction = (progress.bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    CircularProgressIndicator(progress = { fraction })
                    Text(
                        stringResource(R.string.summary_download_progress, progress.bytes / 1_000_000, total / 1_000_000, (fraction * 100).toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(stringResource(R.string.summary_download_continue), style = MaterialTheme.typography.bodyMedium, color = muted)
                } else {
                    Text(stringResource(R.string.summary_download_waiting), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.ai_chat_download_background_notice), style = MaterialTheme.typography.bodyMedium, color = muted)
                }
            }
            else -> {
                Text(stringResource(R.string.ai_chat_download_notice), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onDownload) { Text(stringResource(R.string.summary_download)) }
            }
        }
    }
}

/** ジャービルが答えを考えている間の「…」。メッセージアプリの入力中のように、3つの点が順に弾む（読み込みの表示なのでくり返す）。 */
@Composable
private fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "chatTyping")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = TYPING_CYCLE_MS, easing = LinearEasing)),
        label = "chatTypingPhase",
    )
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    // 吹き出しの面は付けず、点だけを答えが出る位置に置く
    Row(
            Modifier.padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            repeat(TYPING_DOTS) { i ->
                Box(
                    Modifier
                        .size(Spacing.s)
                        .graphicsLayer {
                            // 点ごとに少しずつ遅らせて、前半だけ上に跳ねる
                            val t = (phase.value - i * TYPING_DOT_DELAY).mod(1f)
                            val bounce = if (t < 0.5f) kotlin.math.sin(t * 2f * kotlin.math.PI.toFloat()) else 0f
                            translationY = -bounce * Spacing.xs.toPx()
                            alpha = 0.5f + 0.5f * bounce
                        }
                        .background(dot, CircleShape),
                )
            }
        }
}

private const val TYPING_DOTS = 3
private const val TYPING_CYCLE_MS = 1_200
private const val TYPING_DOT_DELAY = 0.15f

/** スクロールバーの吹き出しに出す、質問の書き出しの文字数。 */
private const val CHAT_SCROLL_LABEL_CHARS = 16

/** ジャービルの絵（gerbil_chat_peek、幅 160）で、頭の中心（66）が絵の中心（80）から左にずれている割合。 */
private const val PEEK_HEAD_OFFSET = 14f / 160f
