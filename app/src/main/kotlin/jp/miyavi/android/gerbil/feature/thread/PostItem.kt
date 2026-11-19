package jp.miyavi.android.gerbil.feature.thread

import jp.miyavi.android.gerbil.ui.SkeletonColors
import jp.miyavi.android.gerbil.ui.skeletonBar
import jp.miyavi.android.gerbil.ui.skeletonColors
import jp.miyavi.android.gerbil.ui.rememberShimmer
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.OutlinedButton
import jp.miyavi.android.gerbil.data.NgKey
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import jp.miyavi.android.gerbil.ui.shrinkExit
import jp.miyavi.android.gerbil.ui.expandEnter
import jp.miyavi.android.gerbil.ui.imageProgress
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.staticCompositionLocalOf
import jp.miyavi.android.gerbil.core.settings.PostStyle
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.gerbil.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import jp.miyavi.android.gerbil.source.dat.BodySegmenter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.aaTextStyle
import jp.miyavi.android.gerbil.core.designsystem.bodyTextStyle
import jp.miyavi.android.gerbil.core.designsystem.postBodyContainerColor
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.Segment
import jp.miyavi.android.gerbil.ui.ImageLoadingBadge

/** レス表示の見た目（設定）。 */
val LocalPostStyle = staticCompositionLocalOf { PostStyle.MODERN }

/** レス1件。カードで囲まず、ヘッダは小さく控えめに、本文は大きく（AGENTS.md デザイン指南 3・8）。 */
@Composable
fun PostItem(
    post: Post,
    replyCount: Int,
    idTotal: Int,
    isNewStart: Boolean,
    modifier: Modifier = Modifier,
    isGoreWarned: Boolean = false,
    /** スレ内検索の語。本文中の一致を強調する。 */
    highlight: String? = null,
    /** AI フィルターで本文をぼかすか。 */
    bodyBlurred: Boolean = false,
    /** NG のレスか（本文を隠し、押すと見せる）。 */
    isNg: Boolean = false,
    /** レスの書き手を NG にする・外す（null なら出さない）。 */
    onToggleNg: ((Post) -> Unit)? = null,
    /** 自分が書き込んだレスか（ヘッダに「自分」のチップを出す）。 */
    isMine: Boolean = false,
    onAnchorClick: (List<IntRange>) -> Unit = {},
    onLinkClick: (String) -> Unit = {},
    onRepliesClick: (Int) -> Unit = {},
    onIdClick: (String) -> Unit = {},
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onReplyClick: ((Int) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val modern = LocalPostStyle.current == PostStyle.MODERN
    Column(modifier.fillMaxWidth()) {
        if (isNewStart) NewPostsBand()
        // レス間の余白: モダンは面の切れ目があるので 12dp、シンプルは余白だけで区切るので 20dp
        val gap = if (modern) Spacing.m else Dimens.postSpacing
        Column(Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = gap)) {
            PostHeader(
                post = post,
                replyCount = replyCount,
                idTotal = idTotal,
                isMine = isMine,
                onRepliesClick = onRepliesClick,
                onIdClick = onIdClick,
                onReplyClick = onReplyClick,
                isNg = isNg,
                onToggleNg = onToggleNg,
            )
            // モダン: 本文を薄い角丸の面に載せ、区切り線なしでもレスの切れ目を面の切れ目で示す。シンプル: 面なし
            val bodyModifier = if (modern) {
                Modifier
                    .fillMaxWidth()
                    .background(postBodyContainerColor(), MaterialTheme.shapes.large)
                    .padding(horizontal = Spacing.m, vertical = Spacing.s)
            } else {
                Modifier.fillMaxWidth()
            }
            // AI フィルターでぼかすレスは、本文をぼかし、タップで表示する（リンクや画像も押せないように覆う）
            // NG と AI ミュートは、押している間だけ見せる（離すとまた隠す）
            var holding by remember(post.number) { mutableStateOf(false) }
            val hidden = isNg || bodyBlurred
            val veiled = hidden && !holding
            // ぼかしの強さ（0〜1）をフェードで変える。見せるときは丁寧に、隠し直すときは手早く（effects のトークン）
            val motion = MaterialTheme.motionScheme
            val veil by animateFloatAsState(
                targetValue = if (veiled) 1f else 0f,
                animationSpec = if (veiled) motion.fastEffectsSpec() else motion.defaultEffectsSpec(),
                label = "postVeil",
            )
            val veilRadius = with(LocalDensity.current) { Dimens.imageBlurRadius.toPx() }
            val veilShape = MaterialTheme.shapes.large
            Box {
            // 毎フレーム変わるぼかしは graphicsLayer の中で読む（再構成しない）。縁は本文の面と同じ角丸で切る
            Column(
                Modifier.graphicsLayer {
                    val r = veil * veilRadius
                    renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
                    clip = r > 0.5f
                    shape = veilShape
                }.then(bodyModifier),
            ) {
            if (post.isAbone) {
                Text(
                    "あぼーん",
                    style = bodyTextStyle(),
                    color = colors.onSurfaceVariant.copy(alpha = DIM_ALPHA),
                )
            } else {
                val highlightStyle = SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)
                val body = remember(post, colors.primary, highlight, highlightStyle) {
                    buildBody(trimBlankEdges(post.body), colors.primary, onAnchorClick, onLinkClick, highlight, highlightStyle)
                }
                SelectionContainer {
                    if (post.isAA) {
                        Text(
                            body,
                            style = aaTextStyle(),
                            color = colors.onSurface,
                            softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    } else {
                        Text(body, style = bodyTextStyle(), color = colors.onSurface)
                    }
                }
                val imageUrls = remember(post.body) {
                    post.body.filterIsInstance<Segment.Link>().filter { it.isImage || it.isVideo }.map { it.url }.distinct()
                }
                if (imageUrls.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.s))
                    ImagePreviewRow(
                        postNumber = post.number,
                        urls = imageUrls,
                        isGoreWarned = isGoreWarned,
                        onImageClick = onImageClick,
                    )
                }
            }
            }
            if (hidden) {
                // 長押ししている間だけ見せる。指を離すまで押さえ続けるので、見せている間もこの面は残す
                val label = stringResource(if (isNg) R.string.post_ng_tap_to_show else R.string.ai_filter_tap_to_show)
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.large)
                        // 長押しで見せ始め、指を離すまで見せる（ふつうのタップやスクロールでは開かない）
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (awaitLongPressOrCancellation(down.id) != null) {
                                    holding = true
                                    try { waitForUpOrCancellation() } finally { holding = false }
                                }
                            }
                        }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.graphicsLayer { alpha = veil })
                }
            }
            }
        }
    }
}

/** 画像・動画のプレビュー。複数あるときは折り返さずに横へ並べ、横スクロールで見る。 */
@Composable
private fun ImagePreviewRow(
    postNumber: Int,
    urls: List<String>,
    isGoreWarned: Boolean,
    onImageClick: (List<String>, Int) -> Unit,
) {
    // 横に隠れた分まで読み込まないよう、見えている範囲とその近くだけを組み立てる
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        itemsIndexed(urls, key = { index, url -> "$index:$url" }) { index, url ->
            var revealed by rememberSaveable(postNumber, url) { mutableStateOf(false) }
            var aspectRatio by remember(url) { mutableStateOf<Float?>(null) }
            var loadState by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
            val blurred = isGoreWarned && !revealed
            val blurModifier = if (blurred) {
                Modifier.blur(Dimens.imageBlurRadius)
            } else {
                Modifier
            }
            val sizeModifier = aspectRatio?.let { ratio ->
                Modifier.height(Dimens.imagePreviewHeight).aspectRatio(ratio)
            } ?: Modifier.size(width = Dimens.imagePreviewWidth, height = Dimens.imagePreviewHeight)
            val failed = loadState is AsyncImagePainter.State.Error
            val loading = loadState is AsyncImagePainter.State.Loading
            Box(
                modifier = Modifier
                    .then(sizeModifier)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        if (blurred) revealed = true else onImageClick(urls, index)
                    },
            ) {
                val isVideo = BodySegmenter.isVideoUrl(url)
                var videoFailed = false
                var videoLoading = false
                if (isVideo) {
                    when (val frame = rememberVideoFrame(url)) {
                        is VideoFrameState.Ready -> {
                            LaunchedEffect(frame) {
                                aspectRatio = (frame.frame.width.toFloat() / frame.frame.height.coerceAtLeast(1).toFloat())
                                    .coerceIn(MIN_IMAGE_ASPECT, MAX_IMAGE_ASPECT)
                            }
                            VideoFrameImage(
                                frame = frame.frame,
                                contentDescription = stringResource(if (blurred) R.string.post_video_gore_cd else R.string.post_video_cd),
                                modifier = Modifier.fillMaxSize().then(blurModifier),
                            )
                        }
                        VideoFrameState.Loading -> videoLoading = true
                        VideoFrameState.Failed -> videoFailed = true
                    }
                    // 最初のコマが取れなくても、タップで全画面の再生は試せるように再生アイコンは出す
                    if (!blurred) {
                        Icon(
                            painterResource(R.drawable.ic_huge_play),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = PLAY_BADGE_ALPHA))
                                .padding(Spacing.s),
                        )
                    }
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = stringResource(if (blurred) R.string.post_image_gore_cd else R.string.image_preview_cd),
                        contentScale = ContentScale.Crop,
                        onState = { state ->
                            loadState = state
                            if (state is AsyncImagePainter.State.Success) {
                                val image = state.result.image
                                if (image.width > 0 && image.height > 0) {
                                    aspectRatio = (image.width.toFloat() / image.height.toFloat())
                                        .coerceIn(MIN_IMAGE_ASPECT, MAX_IMAGE_ASPECT)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().then(blurModifier),
                    )
                }
                if (!blurred && isVideo && videoLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(Spacing.l))
                } else if (!blurred && isVideo && videoFailed) {
                    Text(
                        stringResource(R.string.post_video_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.xs),
                    )
                } else if (!blurred && loading) {
                    ImageLoadingBadge(
                        progress = imageProgress(url),
                        compact = true,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (!blurred && failed) {
                    Text(
                        stringResource(R.string.post_media_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center).padding(Spacing.xs),
                    )
                }
                if (blurred) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.scrim.copy(
                                    alpha = GORE_BLUR_SCRIM,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.post_gore_tap_to_show),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** 「ここから新着」の境目。primary の細い線に見出しを添える。末尾では「新着なし」に使う。 */
@Composable
internal fun NewPostsBand(text: String = stringResource(R.string.thread_new_posts_from)) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        HorizontalDivider(Modifier.weight(1f), color = primary)
        Text(text, style = MaterialTheme.typography.labelMedium, color = primary)
        HorizontalDivider(Modifier.weight(1f), color = primary)
    }
}

/**
 * レスのヘッダ。普段は「番号・日時・返信数・ID の投稿数」だけのコンパクト表示。
 * 長押しで名前・メール・ID と「>>N にレス」ボタンを広げ、もう一度長押しで閉じる。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostHeader(
    post: Post,
    replyCount: Int,
    idTotal: Int,
    isMine: Boolean,
    onRepliesClick: (Int) -> Unit,
    onIdClick: (String) -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    isNg: Boolean = false,
    onToggleNg: ((Post) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by rememberSaveable(post.number) { mutableStateOf(false) }
    val interactive = !post.isAbone
    val detailLabel = stringResource(if (expanded) R.string.post_detail_hide else R.string.post_detail_show)
    val headerShape = MaterialTheme.shapes.large
    Column(
        Modifier
            // 押した反応は本文の面と同じ角丸にする。左右に少し広げ、自分のレスの番号のピル（左へはみ出す）も収める
            .layout { measurable, constraints ->
                val extra = Spacing.s.roundToPx()
                val placeable = measurable.measure(constraints.copy(
                    minWidth = constraints.minWidth + 2 * extra,
                    maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * extra else constraints.maxWidth,
                ))
                layout(placeable.width - 2 * extra, placeable.height) { placeable.place(-extra, 0) }
            }
            .fillMaxWidth()
            .clip(headerShape)
            .then(
                if (interactive) {
                    // タップで詳細（名前・メール・ID・「>>N にレス」）を開閉する。チップは自分のタップを優先する
                    Modifier.clickable(onClickLabel = detailLabel) { expanded = !expanded }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.s),
    ) {
        // チップの有無に関わらず、ヘッダは一定の高さの中で縦中央に置く。チップのタップ領域もこの高さに揃える
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dimens.postHeaderHeight) {
        Box(Modifier.fillMaxWidth().heightIn(min = Dimens.postHeaderHeight), contentAlignment = Alignment.CenterStart) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMine) {
                    // 自分のレスは、番号をピルで囲み、番号の色（primary）を面にして文字を反転させる
                    val mineLabel = stringResource(R.string.post_mine_cd)
                    // ピルの左の余白ぶん左へずらし、番号の位置をほかのレスの番号と揃える（上下の余白は付けず、行の高さを変えない）
                    Surface(
                        shape = CircleShape,
                        color = colors.primary,
                        contentColor = colors.onPrimary,
                        modifier = Modifier
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val shift = Spacing.xs.roundToPx()
                                layout(placeable.width - shift, placeable.height) { placeable.place(-shift, 0) }
                            }
                            .semantics { contentDescription = mineLabel },
                    ) {
                        Text(
                            post.number.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.xs),
                        )
                    }
                } else {
                    Text(
                        post.number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                    )
                }
                if (interactive) {
                    val date = remember(post.dateText) { fullDate(post.dateText) }
                    if (date.isNotEmpty()) {
                        Text(date, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    if (replyCount > 0) {
                        CountChip(
                            label = pluralStringResource(R.plurals.post_replies_count, replyCount, replyCount),
                            description = pluralStringResource(R.plurals.post_replies_cd, replyCount, replyCount),
                            container = colors.secondaryContainer,
                            content = colors.onSecondaryContainer,
                            onClick = { onRepliesClick(post.number) },
                        )
                    }
                    val id = post.id
                    if (id != null && idTotal > 1) {
                        CountChip(
                            label = pluralStringResource(R.plurals.post_id_count, idTotal, idTotal),
                            description = pluralStringResource(R.plurals.post_id_cd, idTotal, idTotal),
                            container = if (idTotal >= PostIndex.MANY_POSTS) colors.errorContainer else colors.secondaryContainer,
                        content = if (idTotal >= PostIndex.MANY_POSTS) colors.onErrorContainer else colors.onSecondaryContainer,
                            onClick = { onIdClick(id) },
                        )
                    }
                }
            }
        }
        }
        AnimatedVisibility(visible = expanded && interactive, enter = expandEnter(), exit = shrinkExit()) {
            Column(
                modifier = Modifier.padding(bottom = Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                val details = remember(post, colors.tertiary) { buildDetails(post, colors.tertiary) }
                Text(details, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                // 「>>N にレス」の横に、書き手の NG の追加・解除（ワッチョイがあればワッチョイ、なければ ID）
                val canNg = onToggleNg != null && remember(post) { NgKey.of(post) } != null
                if (onReplyClick != null || canNg) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (onReplyClick != null) FilledTonalButton(onClick = { onReplyClick(post.number) }) {
                        Icon(
                            painterResource(R.drawable.ic_huge_edit),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.post_reply_to, post.number))
                    }
                    if (canNg) OutlinedButton(onClick = { onToggleNg?.invoke(post) }) {
                        Text(stringResource(if (isNg) R.string.post_ng_remove else R.string.post_ng_add))
                    }
                }
            }
        }
    }
}

/** 返信数・ID の投稿数を示す小さな丸いチップ。見た目は小さく、タップ領域は 48dp を確保する。 */
@Composable
private fun CountChip(label: String, description: String, container: Color, content: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

/** "2026/11/14(土) 22:28:29.33" → "2026/11/14 22:28:29.33"（曜日だけ除く）。 */
internal fun fullDate(dateText: String): String =
    dateText.replace(WEEKDAY, " ").replace(SPACES, " ").trim()

private val WEEKDAY = Regex("\\([^)]*\\)")
private val SPACES = Regex("\\s+")

/** 広げたときの詳細。名前（ワッチョイ等を含む）・メール・ID・BE。日時はヘッダに出すので含めない。 */
private fun buildDetails(post: Post, tertiary: Color): AnnotatedString = buildAnnotatedString {
    append(post.name)
    val second = buildList {
        if (post.mail.isNotEmpty()) add(post.mail)
        post.id?.let { add("ID:$it") }
        post.be?.let { add("BE:$it") }
    }
    if (second.isEmpty()) return@buildAnnotatedString
    append('\n')
    second.forEachIndexed { i, part ->
        if (i > 0) append(" · ")
        if (i == 0 && post.isSage && part == post.mail) withStyle(SpanStyle(color = tertiary)) { append(part) } else append(part)
    }
}

/**
 * 本文の先頭・末尾の空行を取り除く（行間をわざと空けた投稿が続くと、レスの切れ目が分かりにくくなるため）。
 * 途中の空行と、AA の行頭の字下げはそのまま残す。
 */
internal fun trimBlankEdges(segments: List<Segment>): List<Segment> {
    if (segments.isEmpty()) return segments
    val out = segments.toMutableList()
    (out.first() as? Segment.Text)?.let { out[0] = Segment.Text(it.text.replace(LEADING_BLANK_LINES, "")) }
    (out.last() as? Segment.Text)?.let { out[out.lastIndex] = Segment.Text(it.text.replace(TRAILING_BLANK_LINES, "")) }
    return out.filterNot { it is Segment.Text && it.text.isEmpty() }
}

private val LEADING_BLANK_LINES = Regex("^(?:[ \\t]*\\n)+")
private val TRAILING_BLANK_LINES = Regex("(?:\\n[ \\t]*)+$")

private fun buildBody(
    segments: List<Segment>,
    primary: Color,
    onAnchorClick: (List<IntRange>) -> Unit,
    onLinkClick: (String) -> Unit,
    highlight: String? = null,
    highlightStyle: SpanStyle = SpanStyle(),
): AnnotatedString = buildAnnotatedString {
    val anchorStyles = TextLinkStyles(SpanStyle(color = primary))
    val urlLinkStyles = TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline))
    segments.forEachIndexed { idx, seg ->
        when (seg) {
            is Segment.Text -> appendHighlighted(seg.text, highlight, highlightStyle)
            is Segment.Anchor -> withLink(LinkAnnotation.Clickable("anchor_$idx", anchorStyles) { onAnchorClick(seg.targets) }) {
                append(seg.text)
            }
            is Segment.Link -> withLink(LinkAnnotation.Clickable("link_$idx", urlLinkStyles) { onLinkClick(seg.url) }) {
                append(seg.text)
            }
        }
    }
}

/** [text] を追加し、[query] に一致する部分（大文字・小文字を区別しない）に [style] を重ねる。 */
private fun AnnotatedString.Builder.appendHighlighted(text: String, query: String?, style: SpanStyle) {
    if (query.isNullOrEmpty()) return append(text)
    var from = 0
    while (true) {
        val hit = text.indexOf(query, from, ignoreCase = true)
        if (hit < 0) break
        append(text.substring(from, hit))
        withStyle(style) { append(text.substring(hit, hit + query.length)) }
        from = hit + query.length
    }
    append(text.substring(from))
}


private const val DIM_ALPHA = 0.6f
private const val GORE_BLUR_SCRIM = 0.45f
private const val MIN_IMAGE_ASPECT = 0.4f
/** 動画サムネイルの再生アイコンの下地の不透明度。 */
private const val PLAY_BADGE_ALPHA = 0.7f
private const val MAX_IMAGE_ASPECT = 3.0f

/** 取得中・画面が入ってくる途中に、レスの形だけを先に見せる（ヘッダと本文の行を帯で描き、要約の待ちと同じ光を流す）。 */
@Composable
internal fun PostSkeletonList(modifier: Modifier = Modifier) {
    val shimmer = rememberShimmer()
    val onList = skeletonColors()
    val onBody = skeletonColors(postBodyContainerColor())
    Column(modifier.fillMaxWidth()) {
        SKELETON_LINES.forEach { lines -> PostSkeleton(lines, shimmer, onList, onBody) }
    }
}

@Composable
private fun PostSkeleton(lines: Int, shimmer: State<Float>, onList: SkeletonColors, onBody: SkeletonColors) {
    val modern = LocalPostStyle.current == PostStyle.MODERN
    val gap = if (modern) Spacing.m else Dimens.postSpacing
    val bodyColors = if (modern) onBody else onList
    val shape = MaterialTheme.shapes.extraSmall
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    val labelHeight = with(density) { typography.labelMedium.fontSize.toDp() }
    val bodyHeight = with(density) { typography.bodyLarge.fontSize.toDp() }
    // 本文の行の高さは文字の 1.5 倍なので、帯と帯の間は文字の半分
    val lineGap = bodyHeight / 2
    Column(Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = gap)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Dimens.postHeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(Modifier.size(width = Spacing.xl, height = labelHeight).skeletonBar(shimmer, onList, shape))
            Box(Modifier.fillMaxWidth(SKELETON_DATE_FRACTION).height(labelHeight).skeletonBar(shimmer, onList, shape))
        }
        val body = if (modern) {
            Modifier.fillMaxWidth().background(postBodyContainerColor(), MaterialTheme.shapes.large).padding(horizontal = Spacing.m, vertical = Spacing.s)
        } else {
            Modifier.fillMaxWidth()
        }
        Column(body.padding(vertical = lineGap / 2), verticalArrangement = Arrangement.spacedBy(lineGap)) {
            repeat(lines) { i ->
                val width = if (i == lines - 1 && lines > 1) SKELETON_LAST_LINE_FRACTION else 1f
                Box(Modifier.fillMaxWidth(width).height(bodyHeight).skeletonBar(shimmer, bodyColors, shape))
            }
        }
    }
}

/** プレースホルダーの各レスの行数（長さがそろって見えないよう、ばらつかせる）。 */
private val SKELETON_LINES = listOf(3, 1, 2, 4, 1, 2, 3)
private const val SKELETON_DATE_FRACTION = 0.45f
private const val SKELETON_LAST_LINE_FRACTION = 0.6f
