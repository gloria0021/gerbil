package jp.miyavi.android.gerbil.feature.thread

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.animateBounds
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.key
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import com.mikepenz.markdown.model.markdownPadding
import kotlin.math.PI
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.ColumnScope
import jp.miyavi.android.gerbil.core.designsystem.sheetFieldColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.Markdown
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.chrisbanes.haze.HazeState
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.ui.BoardsBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadSummarySheet(state: SummaryUiState, modelStatus: ModelStatus, modelName: String?, hazeState: HazeState, onDismiss: () -> Unit, onDownload: () -> Unit, onRetry: () -> Unit, onStop: () -> Unit = {}, onBegin: () -> Unit = {}, onUpdate: () -> Unit = {}, question: QuestionUiState = QuestionUiState(), onQuestionChange: (String) -> Unit = {}, onAsk: () -> Unit = {}, onStopQuestion: () -> Unit = {}, onAnchorClick: (Int) -> Unit = {}, onResetQuestion: () -> Unit = {}, onJumpToPost: (IntRange) -> Unit = {}, newestFirst: Boolean = false, onNewestFirst: (Boolean) -> Unit = {}) {
    // シート全体はスクロールさせず、要約を載せた白い面の中だけをスクロールさせる
    // 質問の入力中にキーボードで入力欄が隠れないよう、シートは最初から全部引き上げ、中身をキーボードの分だけ上へ逃がす
    BoardsBottomSheet(
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentModifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        ThreadSummaryContent(state, modelStatus, modelName, onDismiss, onDownload, onRetry, onStop, onBegin, onUpdate, question, onQuestionChange, onAsk, onStopQuestion, onAnchorClick, onResetQuestion, onJumpToPost, newestFirst, onNewestFirst)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadSummaryContent(state: SummaryUiState, modelStatus: ModelStatus, modelName: String?, onDismiss: () -> Unit, onDownload: () -> Unit, onRetry: () -> Unit, onStop: () -> Unit = {}, onBegin: () -> Unit = {}, onUpdate: () -> Unit = {}, question: QuestionUiState = QuestionUiState(), onQuestionChange: (String) -> Unit = {}, onAsk: () -> Unit = {}, onStopQuestion: () -> Unit = {}, onAnchorClick: (Int) -> Unit = {}, onResetQuestion: () -> Unit = {}, onJumpToPost: (IntRange) -> Unit = {}, newestFirst: Boolean = false, onNewestFirst: (Boolean) -> Unit = {}) {
    // 質問の画面か（要約の画面の「質問」ボタンで切り替える）
    var questionMode by rememberSaveable { mutableStateOf(false) }
    // 分割要約の並び（基本は古い順）は設定に覚えておき、作っている途中とでき上がった後で共通にする
    // 上はドラッグハンドルの余白があるので詰める
    Column(Modifier.fillMaxWidth().padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val isModelSetup = state.stage == SummaryStage.DOWNLOADABLE || state.stage == SummaryStage.DOWNLOADING
        val modelText = when (modelStatus) {
            ModelStatus.UNKNOWN -> null
            ModelStatus.CHECKING -> stringResource(R.string.summary_model_status_checking)
            ModelStatus.DOWNLOADABLE -> stringResource(R.string.summary_model_status_downloadable)
            ModelStatus.DOWNLOADING -> stringResource(R.string.summary_model_status_downloading)
            ModelStatus.READY -> modelName?.takeIf { it.isNotBlank() }?.let {
                stringResource(R.string.summary_model_status_ready_named, it)
            } ?: stringResource(R.string.summary_model_status_ready)
            ModelStatus.UNAVAILABLE -> stringResource(R.string.summary_model_status_unavailable)
        }
        if (questionMode) {
            QuestionSection(question, modelText, onQuestionChange, onAsk, onStopQuestion, onAnchorClick, onResetQuestion, onModeChange = { questionMode = it })
            return@Column
        }
        // 見出しの行。右端に、要約中は停止、終わった後（完了・失敗）は再要約のアイコンを置く
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
            if (isModelSetup) {
                Text(stringResource(R.string.summary_model_title), style = MaterialTheme.typography.titleMedium)
            } else {
                SummaryModeSwitch(questionMode = false, onChange = { questionMode = it })
            }
            // モデルの状態が変わるときは、M3 のモーショントークンで文字を入れ替える（下から浮かび上がって前の文字は上へ抜ける）
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = modelText,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 }) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                },
                label = "summaryModel",
            ) { text ->
                if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (state.stage) {
            // 要約中・でき上がった後の停止と更新は、面のいちばん上の行の右端（並べ替えの右）に置く
            SummaryStage.CHECKING -> SummaryStopButton(onStop)
            SummaryStage.FAILED -> if (state.canRetry()) SummaryRegenerateButton(onRetry)
            else -> Unit
        }
        }
        when (state.stage) {
            // 準備中も要約中と同じ面で出す（一瞬だけ別の画面を挟まないように）。進捗の行には「準備中」が出る
            SummaryStage.CHECKING -> SummaryField { SummaryWaiting(progress = null) }
            SummaryStage.DOWNLOADABLE -> {
                Text(stringResource(R.string.summary_download_notice))
                Button(onClick = onDownload) { Text(stringResource(R.string.summary_download)) }
            }
            SummaryStage.DOWNLOADING -> {
                // 取得は端末（AICore）が裏で行う。進み具合が分かるのは、このアプリが最初に頼んだときだけ。
                // 分からないときは待たせる表示にせず、閉じて読んでよいことを伝える
                val total = state.downloadTotalBytes
                if (total != null && total > 0) {
                    val fraction = (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    CircularProgressIndicator(progress = { fraction })
                    Text(stringResource(R.string.summary_download_progress, state.downloadedBytes / 1_000_000, total / 1_000_000, (fraction * 100).toInt()))
                    Text(stringResource(R.string.summary_download_continue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.summary_download_waiting))
                    Text(stringResource(R.string.summary_download_background_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onDismiss) { Text(stringResource(R.string.summary_download_close)) }
            }
            SummaryStage.RUNNING -> {
                // 要約を待つあいだも、結果が出る白い面を先に出す。部分要約ができるたびに順に出して、待つあいだも読めるようにする。
                // まだ1つもなければプレースホルダー。全体のまとめができたら、COMPLETE で全体の要約に置き換わる
                SummaryField {
                    if (state.partials.isEmpty()) {
                        SummaryWaiting(state.progress, trailing = { SummaryStopButton(onStop) })
                    } else {
                        SummaryWaiting(state.progress, showSkeleton = false, trailing = {
                            SummaryTrailingButtons {
                                SummaryOrderButton(newestFirst) { onNewestFirst(!newestFirst) }
                                SummaryStopButton(onStop)
                            }
                        })
                        PartialSummaryList(state.partials, newestFirst, onJumpToPost)
                    }
                }
            }
            SummaryStage.COMPLETE -> {
                // 箇条書きなどの Markdown は M3 の文字・色で描く
                SummaryField {
                    // 要約の元になったレスの範囲は、要約中に進み具合が出る位置（面のいちばん上）に出す
                    Row(Modifier.fillMaxWidth().padding(bottom = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (state.partial) R.string.summary_partial else R.string.summary_range, state.first, state.last, state.count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTrailingButtons {
                            if (state.partials.isNotEmpty()) SummaryOrderButton(newestFirst) { onNewestFirst(!newestFirst) }
                            SummaryRegenerateButton(onRetry)
                        }
                    }
                    // 要約の後にレスが増えたら、進捗の行と同じ位置で知らせ、作り直せるようにする
                    if (state.newPostCount > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                pluralStringResource(R.plurals.summary_new_posts, state.newPostCount, state.newPostCount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(onClick = onUpdate) { Text(stringResource(R.string.summary_update)) }
                        }
                    }
                    // 要約は、レスの範囲ごとの一文を時系列に並べたもの（まとめ直しはしない）
                    if (state.partials.isNotEmpty()) {
                        PartialSummaryList(state.partials, newestFirst, onJumpToPost)
                    } else {
                        Markdown(
                            content = summaryMarkdown(state.result),
                            colors = markdownColor(),
                            typography = markdownTypography(),
                            padding = markdownPadding(listItemTop = Spacing.xs, listItemBottom = Spacing.s),
                        )
                    }
                }
                // 注意書きは要約の下に一段小さく
                Text(
                    stringResource(R.string.summary_caution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 止めた・アプリを離れたときは、説明を出さずにプレースホルダーを止めたまま残す（右上の再要約で続きから）
            SummaryStage.FAILED if state.error == SummaryError.CANCELLED || state.error == SummaryError.BACKGROUND -> {
                SummaryField { SummaryWaiting(progress = null, active = false) }
            }
            SummaryStage.FAILED -> {
                Text(when (state.error) {
                    SummaryError.EMPTY -> stringResource(R.string.summary_empty)
                    SummaryError.TOO_LARGE -> stringResource(R.string.summary_too_large, state.errorPost)
                    SummaryError.BUSY -> stringResource(R.string.summary_busy)
                    SummaryError.QUOTA -> stringResource(R.string.summary_quota)
                    SummaryError.CANCELLED -> stringResource(R.string.summary_interrupted)
                    SummaryError.BACKGROUND -> stringResource(R.string.summary_background)
                    SummaryError.DOWNLOAD_TIMEOUT -> stringResource(R.string.summary_download_timeout)
                    SummaryError.OUTPUT_UNAVAILABLE -> stringResource(R.string.summary_output_unavailable)
                    SummaryError.GENERIC -> stringResource(R.string.summary_failed)
                })
            }
            // 開いた直後: 説明と「要約」ボタン（要約は押してから始める）
            SummaryStage.IDLE -> SummaryField {
                SummaryWaiting(progress = null, active = false) {
                    FilledTonalButton(onClick = onBegin, modifier = Modifier.padding(top = Spacing.xl)) {
                        Text(stringResource(R.string.summary_start))
                    }
                }
            }
            SummaryStage.UNSUPPORTED -> Text(stringResource(R.string.summary_unsupported))
            SummaryStage.CLOSED -> Unit
        }
        // 閉じるボタンは置かない（シートは下へ引いて閉じる）
    }
}

/**
 * 要約の結果（とその待ち表示）を載せる面。書き込み欄と同じく、シートより明るい不透明な面にする。
 * 長いときはこの面の中だけがスクロールし、見出しやボタンは動かない。
 */
@Composable
private fun ColumnScope.SummaryField(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = sheetFieldColor(),
        shape = MaterialTheme.shapes.large,
        // 中身が少ないときも生成中と同じ高さを保ち、下側を空けておく（状態が変わってもシートの高さが変わらないように）
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.summaryFieldMinHeight).weight(1f, fill = false),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.l),
            content = content,
        )
    }
}

@Composable
private fun SummaryWaiting(
    progress: SummaryProgress?,
    active: Boolean = true,
    label: String? = null,
    showSkeleton: Boolean = true,
    /** 進み具合の行の右端に置くもの（並べ替えのボタン）。文字の入れ替わりの動きには巻き込まない。 */
    trailing: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val inspection = LocalInspectionMode.current
    // 止まっているときは光を流さず、進捗の行も出さない
    val shimmer = if (inspection || !active) remember { mutableFloatStateOf(STILL_SHIMMER) } else {
        rememberInfiniteTransition(label = "summaryShimmer").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 3_000, easing = LinearEasing), RepeatMode.Restart),
            label = "summaryShimmerPosition",
        )
    }
    // 止まっているときも進捗の行の高さは確保しておき（透明にするだけ）、シートの高さが変わらないようにする
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // 進捗の行は生成中だけ置く（高さは白い面の最低の高さで保つ）
    if (active) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AnimatedContent(
        modifier = Modifier.weight(1f),
        targetState = progress,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 }) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { -it / 2 })
        },
        label = "summaryProgress",
    ) { current ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            // AI が書いているように動くアイコン（文字に寄せて、少し大きめの 20dp）
            AiWritingIcon(color = primary, modifier = Modifier.size(Spacing.l + Spacing.xs))
            val text = when (current) {
                is SummaryProgress.Posts -> stringResource(R.string.summary_progress, current.first, current.last)
                SummaryProgress.Merging -> stringResource(R.string.summary_merging)
                null -> label ?: stringResource(R.string.summary_checking)
            }
            // AI が動いている感じを出すため、テーマカラーのグラデーションを文字の上で流す
            val sweep = with(LocalDensity.current) { PROGRESS_GRADIENT_WIDTH.toPx() }
            val gradient = Brush.linearGradient(
                colors = listOf(primary, tertiary, primary),
                start = Offset(shimmer.value * sweep * 2f, 0f),
                end = Offset(shimmer.value * sweep * 2f + sweep, 0f),
                tileMode = TileMode.Mirror,
            )
            Text(text, modifier = Modifier.weight(1f).then(if (inspection) Modifier else Modifier.basicMarquee()), maxLines = 1,
                style = MaterialTheme.typography.bodyMedium.copy(brush = gradient, fontWeight = FontWeight.Bold))
        }
    }
    trailing?.invoke()
    }
    // 載せている面（sheetFieldColor）の色から少しずらして、ライトでもダークでも棒が見えるようにする
    val field = sheetFieldColor()
    val base = lerp(field, MaterialTheme.colorScheme.onSurfaceVariant, SKELETON_TONE)
    val shine = lerp(field, MaterialTheme.colorScheme.onSurfaceVariant, SKELETON_SHINE_TONE)
    // 生成中は光る棒、止まっているときは棒の代わりに説明（何がここに出るか・レスを外部へ送らないこと）を出す
    Box {
    if (!active) {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.summary_placeholder_notice),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.invoke()
        }
    }
    if (active && showSkeleton) Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s).testTag("summarySkeleton"), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(0.94f, 1f, 0.88f, 0.97f, 0.76f).forEach { width ->
            Box(Modifier.fillMaxWidth(width).height(Spacing.m).clip(MaterialTheme.shapes.small).drawBehind {
                val center = size.width * (shimmer.value * 2f - 0.5f)
                drawRect(Brush.linearGradient(listOf(base, shine, base),
                    start = Offset(center - size.width * 0.4f, 0f), end = Offset(center + size.width * 0.4f, 0f)))
            })
        }
    }
    }
}

/** プレースホルダーの棒と、その上を流れる光の、面の色に混ぜる文字色の割合。 */
private const val SKELETON_TONE = 0.16f
private const val SKELETON_SHINE_TONE = 0.04f

/**
 * AI の出力を Markdown の箇条書きに揃える。「・」「•」「*」で始まる行は「- 」に直し、
 * 項目どうしが1つの段落につながらないよう各行を独立させる。
 */
internal fun summaryMarkdown(text: String): String =
    text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        // 部分要約がそのまま完成になったとき（スレが1つのまとまりに収まるとき）は、箇条書きの前の一文を外す
        .let { lines ->
            val firstBullet = lines.indexOfFirst { it.startsWith("- ") || it.startsWith("・") || it.startsWith("•") || it.startsWith("* ") }
            if (firstBullet > 0) lines.drop(firstBullet) else lines
        }
        .joinToString("\n") { line ->
            when {
                line.startsWith("- ") -> line
                line.startsWith("・") || line.startsWith("•") -> "- " + line.drop(1).trim()
                line.startsWith("* ") -> "- " + line.drop(2)
                else -> line
            }
        }

/** 光を流さないときの位置（棒の外に置き、棒を下地の色のまま見せる）。 */
private const val STILL_SHIMMER = -1f

/** 再要約しても結果が変わらない失敗（本文がない・出力枠が足りない）では、再要約を出さない。 */
private fun SummaryUiState.canRetry() = error != SummaryError.EMPTY && error != SummaryError.OUTPUT_UNAVAILABLE

/** 更新アイコンを停止アイコンと同じ見た目の大きさにする倍率（描画範囲 16 / 20）。 */
private const val REFRESH_TO_STOP_SCALE = 0.8f

/** 進捗の文字の上を流すグラデーションの1周期の幅。 */
private val PROGRESS_GRADIENT_WIDTH = 160.dp

/**
 * スレへの質問。入力欄に質問を書いて送ると、スレのレスをもとに端末の AI が答える。答えは要約と同じ白い面に出す。
 * 見出しの右端は、回答中は停止、答えた後はもう一度答えるアイコン。
 */
@Composable
private fun ColumnScope.QuestionSection(
    question: QuestionUiState,
    modelText: String?,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onStop: () -> Unit,
    onAnchorClick: (Int) -> Unit,
    onReset: () -> Unit,
    onModeChange: (Boolean) -> Unit,
) {
    val running = question.stage == QuestionStage.RUNNING
    // シートを開いた時点の回答。これと違う回答が届いたときだけ、上から流れるように出す
    val answerAtOpen = remember { question.answer }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.s), itemVerticalAlignment = Alignment.CenterVertically) {
            SummaryModeSwitch(questionMode = true, onChange = onModeChange)
            if (modelText != null) Text(modelText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // 質問の入力欄（書き込み欄と同じく、シートより明るい面）。送信はボタンかキーボードの送信キー
    Surface(color = sheetFieldColor(), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = question.question,
            onValueChange = onQuestionChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.question_placeholder)) },
            maxLines = 3,
            enabled = !running,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onAsk() }),
            trailingIcon = {
                IconButton(onClick = onAsk, enabled = !running && question.question.isNotBlank()) {
                    Icon(painterResource(R.drawable.ic_huge_sent), contentDescription = stringResource(R.string.question_send))
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
    SummaryField {
        // 停止とリセットは、要約と同じく面の中の右上に置く。答えた後・失敗した後は、質問と回答を消して最初に戻すリセット
        if (question.stage == QuestionStage.COMPLETE || question.stage == QuestionStage.FAILED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onReset) {
                    Icon(painterResource(R.drawable.ic_huge_reset), contentDescription = stringResource(R.string.question_reset))
                }
            }
        }
        when (question.stage) {
            // 「要約があると精度が上がる」の部分は太字（文言の <b> を活かす）
            QuestionStage.IDLE -> Text(
                AnnotatedString.fromHtml(stringResource(R.string.question_notice)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuestionStage.RUNNING -> SummaryWaiting(progress = null, label = stringResource(R.string.question_thinking), trailing = {
                IconButton(onClick = onStop) {
                    Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.question_stop))
                }
            })
            QuestionStage.COMPLETE -> AnswerMarkdown(question.answer, onAnchorClick, animate = question.answer != answerAtOpen)
            QuestionStage.FAILED -> Text(
                stringResource(
                    when (question.error) {
                        QuestionError.MODEL_NOT_READY -> R.string.question_error_model
                        QuestionError.TOO_LARGE -> R.string.question_error_too_large
                        QuestionError.BUSY -> R.string.question_error_busy
                        QuestionError.QUOTA -> R.string.question_error_quota
                        QuestionError.GENERIC -> R.string.question_error_generic
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (question.stage == QuestionStage.COMPLETE) {
        Text(stringResource(R.string.summary_caution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** シートの上部の「要約 ｜ 質問」の切り替え（M3 のセグメントボタン）。 */
@Composable
private fun SummaryModeSwitch(questionMode: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        listOf(false to R.string.summary_start, true to R.string.question_open).forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = questionMode == mode,
                onClick = { onChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
                icon = {},
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** 回答の中のアンカーをリンクにするときの URI の頭。 */
private const val ANCHOR_SCHEME = "boards-anchor:"

/** Markdown の中の >>番号 を、押せるリンク（[>>番号](boards-anchor:番号)）に置き換える。 */
internal fun anchorLinks(text: String): String =
    Regex(""">>(\d+)""").replace(text) { m -> "[>>${m.groupValues[1]}]($ANCHOR_SCHEME${m.groupValues[1]})" }

/** 要約中にできた部分要約。元のレスの範囲を小さく添えて、約50字の一文だけを出す（箇条書きは全体のまとめに使う）。 */
@Composable
private fun PartialSummary(part: SummaryPartial, animate: Boolean, onJumpToPost: (IntRange) -> Unit, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    // 範囲の見出しはフェードで出す（effects のトークン）
    val labelAlpha = remember(part) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(labelAlpha) { labelAlpha.animateTo(1f, motion.defaultEffectsSpec()) }
    Column(modifier.fillMaxWidth().padding(top = Spacing.m)) {
        // 「>>1-95のまとめ」（2ch の範囲アンカーの書き方）。アンカーを押すと、シートを閉じてスレの範囲の先頭へ移る
        val anchor = ">>${part.first}-${part.last}"
        val label = stringResource(R.string.summary_partial_range, anchor)
        val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
        val jump by rememberUpdatedState(onJumpToPost)
        val annotated = remember(label, linkStyles) {
            buildAnnotatedString {
                append(label)
                val start = label.indexOf(anchor)
                if (start >= 0) addLink(LinkAnnotation.Clickable("range", linkStyles) { jump(part.first..part.last) }, start, start + anchor.length)
            }
        }
        Text(
            annotated,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha.value },
        )
        val headline = remember(part.text) { partialHeadline(part.text) }
        StreamingText(headline, animate = animate, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/**
 * 部分要約を並べる。シートを開いているあいだに届いたものだけを、文字が流れるように出す
 * （開いた時点ですでにあるものはそのまま）。増えて面が伸びるときは、M3 のばねでなめらかに伸ばす。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PartialSummaryList(partials: List<SummaryPartial>, newestFirst: Boolean, onJumpToPost: (IntRange) -> Unit) {
    val shownAtOpen = remember { partials.size }
    val moveSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    // 並べ替えたときは、スレ一覧の並べ替えと同じく、各まとめが新しい位置へ滑って入れ替わる
    LookaheadScope {
        Column(Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            // 並びはレス番号で決める（作った順に頼らない）
            val order = partials.indices.sortedBy { partials[it].first }.let { if (newestFirst) it.reversed() else it }
            order.forEach { index ->
                val part = partials[index]
                key(part.first, part.last) {
                    PartialSummary(
                        part,
                        animate = index >= shownAtOpen,
                        onJumpToPost = onJumpToPost,
                        modifier = Modifier.animateBounds(this@LookaheadScope, boundsTransform = { _, _ -> moveSpec }),
                    )
                }
            }
        }
    }
}

/**
 * 文字が左から流れるように現れるテキスト。先頭の数文字はぼかすように薄くし、その先はまだ透明にしておく。
 * 透明な文字も最初から場所を取るので、現れる途中で折り返しが変わらない。
 */
@Composable
private fun StreamingText(text: String, animate: Boolean, style: TextStyle, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    val progress = remember(text) { Animatable(if (animate) 0f else text.length + STREAM_FADE_CHARS) }
    LaunchedEffect(progress) {
        // shortcut: 文字の出る速さは固定（M3 のトークンに文字送りの速さがないため）。読み込み表示のループと同じ例外扱い
        progress.animateTo(
            text.length + STREAM_FADE_CHARS,
            tween(durationMillis = (text.length * STREAM_MS_PER_CHAR).coerceAtLeast(STREAM_MIN_MS), easing = LinearEasing),
        )
    }
    val shown = buildAnnotatedString {
        val p = progress.value
        val solid = (p - STREAM_FADE_CHARS).toInt().coerceIn(0, text.length)
        withStyle(SpanStyle(color = color)) { append(text.substring(0, solid)) }
        for (i in solid until text.length) {
            val a = ((p - i) / STREAM_FADE_CHARS).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = color.copy(alpha = a))) { append(text[i]) }
        }
    }
    Text(shown, style = style, modifier = modifier)
}

/**
 * 部分要約から画面に出す一文を取り出す。1行目の箇条書きでない行。なければ最初の箇条書きの中身。
 */
internal fun partialHeadline(text: String): String {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullet = Regex("""^([-*・•]\s*)""")
    return lines.firstOrNull { !bullet.containsMatchIn(it) }?.removePrefix("#")?.trim()
        ?: lines.firstOrNull()?.let { bullet.replace(it, "") }.orEmpty()
}

/**
 * 質問の回答。>>番号 は押せるリンクにし、押したらそのレスをアンカーのシートでプレビューする
 * （質問のシートの上に重なって開くので、閉じれば質問に戻る）。入力中に作り直さないよう、回答が変わったときだけ組み直す。
 */
@Composable
private fun AnswerMarkdown(answer: String, onAnchorClick: (Int) -> Unit, animate: Boolean = false) {
    val latestOnAnchor by rememberUpdatedState(onAnchorClick)
    val fallback = LocalUriHandler.current
    val handler = remember(fallback) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val n = uri.removePrefix(ANCHOR_SCHEME).toIntOrNull()
                if (uri.startsWith(ANCHOR_SCHEME) && n != null) latestOnAnchor(n) else fallback.openUri(uri)
            }
        }
    }
    val content = remember(answer) { anchorLinks(answer) }
    // 上から下へ、境目をぼかした幕を下ろすように現れる（Markdown は1文字ずつにはできないため）
    val reveal = remember(answer) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(reveal) {
        reveal.animateTo(1f, tween(durationMillis = (answer.length * ANSWER_MS_PER_CHAR).coerceIn(STREAM_MIN_MS, ANSWER_MAX_MS), easing = LinearEasing))
    }
    val fadePx = with(LocalDensity.current) { Spacing.xxl.toPx() }
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Markdown(
            content = content,
            colors = markdownColor(),
            typography = markdownTypography(),
            padding = markdownPadding(listItemTop = Spacing.xs, listItemBottom = Spacing.s),
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (reveal.value < 1f) {
                        val edge = (size.height + fadePx) * reveal.value
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = edge - fadePx, endY = edge),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        )
    }
}

/** 文字が流れるように出るときの、1文字あたりの時間・先頭のぼかしの幅（文字数）・最短の時間。 */
private const val STREAM_MS_PER_CHAR = 28
private const val STREAM_FADE_CHARS = 6f
private const val STREAM_MIN_MS = 400

/** 回答が上から現れるときの、1文字あたりの時間と最長の時間。 */
private const val ANSWER_MS_PER_CHAR = 10
private const val ANSWER_MAX_MS = 2_000

/** 分割要約の並べ替え（古い順 1→9 / 新しい順 9→1）。要約の範囲・進み具合の行の右端に置く。 */
@Composable
private fun SummaryOrderButton(newestFirst: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            painterResource(if (newestFirst) R.drawable.ic_huge_sort_nine_one else R.drawable.ic_huge_sort_one_nine),
            contentDescription = stringResource(if (newestFirst) R.string.summary_order_newest_cd else R.string.summary_order_oldest_cd),
        )
    }
}

@Composable
private fun SummaryStopButton(onStop: () -> Unit) {
    IconButton(onClick = onStop) {
        Icon(painterResource(R.drawable.ic_huge_stop), contentDescription = stringResource(R.string.summary_stop))
    }
}

@Composable
private fun SummaryRegenerateButton(onRetry: () -> Unit) {
    IconButton(onClick = onRetry) {
        // 更新アイコンは描画範囲が停止アイコンより大きい（20 対 16）ので、縮めて見た目の大きさを揃える
        Icon(
            painterResource(R.drawable.ic_huge_refresh),
            contentDescription = stringResource(R.string.summary_regenerate),
            modifier = Modifier.scale(REFRESH_TO_STOP_SCALE),
        )
    }
}

/**
 * 行の右端に並べるアイコンのボタン。押せる範囲（48dp）は保ったまま、隣どうしの範囲を少し重ねて
 * アイコンの間を詰める（そのままだとアイコンの間が 24dp 空いて間延びするため）。
 */
@Composable
private fun SummaryTrailingButtons(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(-Spacing.m), verticalAlignment = Alignment.CenterVertically, content = content)
}
