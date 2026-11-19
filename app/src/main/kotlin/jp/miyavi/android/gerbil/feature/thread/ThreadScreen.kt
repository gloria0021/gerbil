package jp.miyavi.android.gerbil.feature.thread

import jp.miyavi.android.gerbil.ui.CONTENT_SWAP_FADE_MS
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import jp.miyavi.android.gerbil.navigation.rememberEnterSettled
import jp.miyavi.android.gerbil.data.NgKey
import androidx.compose.foundation.background
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import jp.miyavi.android.gerbil.ui.GlassDropdownMenu
import jp.miyavi.android.gerbil.ui.LocalScreenHazeState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.WindowInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableIntStateOf
import jp.miyavi.android.gerbil.ui.rememberLinkOpener
import jp.miyavi.android.gerbil.core.settings.LinkOpenMode
import jp.miyavi.android.gerbil.source.Segment
import jp.miyavi.android.gerbil.source.Post
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.gerbil.ui.FloatingGlassBar
import jp.miyavi.android.gerbil.ui.BoardsTopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import jp.miyavi.android.gerbil.ui.GlassSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.gerbil.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import jp.miyavi.android.gerbil.core.network.ImgurLinks
import android.content.ClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.ThreadFastScroller
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.ui.FontSizeMenuContent
import jp.miyavi.android.gerbil.ui.ReadingFontSize
import jp.miyavi.android.gerbil.ui.MessageEffect
import kotlinx.coroutines.launch

/** 画面2: レス表示（contracts/ui-thread-screen.md）。 */
@Composable
fun ThreadScreen(
    viewModel: ThreadViewModel,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    onBack: () -> Unit,
    linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    onOpenSettings: () -> Unit,
    onOpenThread: (boardId: String, threadKey: Long, resNumber: Int?) -> Unit = { _, _, _ -> },
    autoSummary: Boolean = false,
    aiMute: Boolean = false,
    autoMute: Boolean = false,
    muteConditions: String = DEFAULT_MUTE_CONDITIONS,
    aiAvailable: Boolean = true,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 設定で自動要約が ON なら、レスが揃ったところで裏で要約を作り始める（シートは開かない）
    val hasPosts = state.posts.isNotEmpty()
    // AI の準備・自動の要約とミュートは、画面が入り終わってから始める（遷移の動きをもたつかせない）
    val settled = rememberEnterSettled()
    LaunchedEffect(settled) { if (settled) viewModel.refreshModelStatus() }
    LaunchedEffect(autoSummary, hasPosts, settled) {
        if (settled && autoSummary && hasPosts) viewModel.autoSummary(viewModel.state.value.posts)
    }
    LaunchedEffect(aiMute, autoMute, muteConditions, settled) {
        if (!aiMute || settled) viewModel.configureJunk(aiMute, autoMute, muteConditions)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    viewModel.interruptSummary()
                    viewModel.pauseJunk()
                    viewModel.flushPosition()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.resumeSummary()
                    viewModel.resumeJunk()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); viewModel.closeSummary() }
    }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    ThreadScreenContent(
        holdContent = !settled,
        state = state,
        snackbar = snackbar,
        onSummary = viewModel::startSummary,
        onSummaryOpen = viewModel::openSummary,
        onSummaryBegin = viewModel::beginSummary,
        onSummaryUpdate = viewModel::updateSummary,
        onAiFilter = viewModel::setAiFilter,
        onReloadMute = viewModel::reloadJunk,
        aiMuteAvailable = aiMute,
        aiAvailable = aiAvailable,
        onToggleNg = viewModel::toggleNg,
        onQuestionChange = viewModel::updateQuestion,
        onAsk = viewModel::askQuestion,
        onStopQuestion = viewModel::stopQuestion,
        onSummaryClose = viewModel::hideSummary,
        onResetQuestion = viewModel::resetQuestion,
        onSummaryDownload = viewModel::downloadSummaryModel,
        onSummaryStop = viewModel::interruptSummary,
        linkOpenMode = linkOpenMode,
        fontSize = fontSize,
        onFontSize = onFontSize,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onRefresh = { viewModel.refresh(jumpToNew = true) },
        onScrolled = viewModel::onScrolled,
        onInitialPositionConsumed = viewModel::consumeInitialPosition,
        onScrollConsumed = viewModel::consumeScrollToIndex,
        onOpenReply = viewModel::openReply,
        onDismissReply = viewModel::dismissReply,
        onReplyNameChange = viewModel::updateReplyName,
        onReplyMailChange = viewModel::updateReplyMail,
        onToggleSage = viewModel::toggleSage,
        onReplyBodyChange = viewModel::updateReplyBody,
        onSubmitReply = viewModel::submitReply,
        onCancelReplyConfirmation = viewModel::cancelConfirmation,
        onOpenLink = { url, openExternally -> viewModel.openLink(url, onOpenThread, openExternally) },
        currentThreadUrl = viewModel::currentThreadUrl,
    )
}

/** 状態を受け取って描画するだけの部分（スクリーンショットテスト用に分けてある）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreenContent(
    state: ThreadUiState,
    /** 画面が入ってくる途中は一覧を組み立てない（遷移の動きをもたつかせない）。入り終わったらフェードで出す。 */
    holdContent: Boolean = false,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 下部バーの「新着」。更新して、新着の先頭へ移動する（引っ張って更新はしない）。 */
    onRefresh: () -> Unit,
    linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    onScrolled: (index: Int, offsetPx: Int) -> Unit,
    onInitialPositionConsumed: () -> Unit,
    onScrollConsumed: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    fontSize: Int = Settings.DEFAULT_FONT_SIZE,
    onFontSize: (Int) -> Unit = {},
    onOpenReply: (Int?) -> Unit = {},
    onDismissReply: () -> Unit = {},
    onReplyNameChange: (String) -> Unit = {},
    onReplyMailChange: (String) -> Unit = {},
    onToggleSage: () -> Unit = {},
    onReplyBodyChange: (String) -> Unit = {},
    onSubmitReply: () -> Unit = {},
    onCancelReplyConfirmation: () -> Unit = {},
    onOpenImgur: (() -> Unit)? = null,
    onSummary: (List<Post>, Boolean) -> Unit = { _, _ -> },
    onSummaryOpen: (List<Post>, Boolean) -> Unit = onSummary,
    onSummaryBegin: () -> Unit = {},
    onSummaryUpdate: (List<Post>, Boolean) -> Unit = { _, _ -> },
    onAiFilter: (AiFilterKind, Boolean) -> Unit = { _, _ -> },
    onReloadMute: () -> Unit = {},
    /** 設定で AI ミュートが有効か。無効ならメニューに出さない。 */
    aiMuteAvailable: Boolean = true,
    /** この端末で AI が使えるか（非対応の端末では要約の入口を出さない）。 */
    aiAvailable: Boolean = true,
    onToggleNg: ((Post) -> Unit)? = null,
    onQuestionChange: (String) -> Unit = {},
    onAsk: (List<Post>) -> Unit = {},
    onStopQuestion: () -> Unit = {},
    onResetQuestion: () -> Unit = {},
    onSummaryClose: () -> Unit = {},
    onSummaryDownload: () -> Unit = {},
    onSummaryStop: () -> Unit = {},
    currentThreadUrl: suspend () -> String? = { null },
    /** 本文のリンク。スレへのリンクはアプリ内で開き、それ以外は2つ目の引数（ブラウザで開く）に任せる。 */
    onOpenLink: (url: String, openExternally: (String) -> Unit) -> Unit = { url, openExternally -> openExternally(url) },
) {
    val wasHeld = remember { holdContent }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val resources = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val hazeState = rememberHazeState()
    var restored by rememberSaveable { mutableStateOf(false) }
    var sheetStack by remember { mutableStateOf<List<ThreadSheet>>(emptyList()) }

    // 絞り込み（本文の一覧そのものを絞る）とスレ内検索
    var filter by rememberSaveable { mutableStateOf<ThreadFilter?>(null) }
    var appliedFilter by rememberSaveable { mutableStateOf<ThreadFilter?>(null) }
    var positionBeforeFilter by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val shown = remember(state.posts, state.index, filter) { state.posts.filtered(filter, state.index) }
    val latestShown by rememberUpdatedState(shown)
    val latestFilter by rememberUpdatedState(filter)
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(shown, query) { searchMatches(shown, query) }
    var matchCursor by remember { mutableIntStateOf(0) }
    val highlight = query.trim().takeIf { searchOpen && it.isNotEmpty() }
    var previewGallery by remember { mutableStateOf<ImageGallery?>(null) }

    // 開いたときの位置（前回の読了位置・新着・指定レス）へ一度だけ移動する
    LaunchedEffect(state.initialIndex, state.posts.size) {
        val target = state.initialIndex ?: return@LaunchedEffect
        if (state.posts.isEmpty()) return@LaunchedEffect
        if (!restored) listState.scrollToItem(target, state.initialOffset)
        restored = true
        onInitialPositionConsumed()
    }
    // 読了位置を保存する
    LaunchedEffect(listState, restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> if (latestFilter == null) onScrolled(i, o) }
    }
    LaunchedEffect(state.scrollToIndex, state.posts.size) {
        val target = state.scrollToIndex ?: return@LaunchedEffect
        if (state.posts.isNotEmpty()) {
            if (filter != null) {
                filter = null
                appliedFilter = null
                positionBeforeFilter = null
                snapshotFlow { latestShown.size }.first { it == state.posts.size }
            }
            val last = if (state.newFrom == state.posts.size + 1) state.posts.size else state.posts.lastIndex
            // 利用者が押して移動するときは、どちらへ動いたか分かるようにスクロールで移る（開いたときの復元は一瞬で行う）
            val destination = target.coerceIn(0, last)
            listState.swiftScrollToItem(destination)
        }
        onScrollConsumed()
    }

    fun openUrl(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.thread_url_open_failed)) }
        }
    }

    // 本文の URL は設定（アプリ内ブラウザ / ブラウザ）で開く。画像の「ブラウザで開く」は常にブラウザ
    // アプリ内ブラウザを開いているあいだは、後ろのこの画面にスクリムをかけて奥行きを出す
    var inAppBrowserOpen by remember { mutableStateOf(false) }
    val openInAppBrowser = rememberLinkOpener(LinkOpenMode.IN_APP, onInAppOpenChange = { inAppBrowserOpen = it }) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.thread_url_open_failed)) }
    }
    val openLinkExternally = if (linkOpenMode == LinkOpenMode.IN_APP) openInAppBrowser else ::openUrl

    // Imgur から戻ったら、クリップボードに「Copy link」で写したリンクがあれば書き込み欄に入れる。
    // 読むのは戻ったときの1回だけ。開く前からあった文字（前に写したリンク）は入れない
    var clipBeforeImgur by remember { mutableStateOf<String?>(null) }
    var imgurReturned by remember { mutableStateOf(false) }
    fun readClip(): String? = runCatching {
        context.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    }.getOrNull()
    val openImgurTab = rememberLinkOpener(LinkOpenMode.IN_APP, onInAppOpenChange = { open ->
        inAppBrowserOpen = open
        if (!open) imgurReturned = true
    }, closeWhenLeft = false) {
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.thread_url_open_failed)) }
    }
    val openImgur: (String) -> Unit = { url ->
        clipBeforeImgur = readClip()
        openImgurTab(url)
    }
    LaunchedEffect(imgurReturned) {
        if (!imgurReturned) return@LaunchedEffect
        imgurReturned = false
        // クリップボードは、アプリの窓がフォーカスを得てからでないと読めない（読めなければ null）。
        // 書き込みシートは別の窓なので画面の窓のフォーカスは待てず、少しのあいだ読み直す
        var clip: String? = null
        repeat(CLIP_READ_ATTEMPTS) {
            clip = readClip()
            if (clip != null) return@repeat
            delay(CLIP_READ_INTERVAL_MS)
        }
        val copied = clip?.takeIf { it != clipBeforeImgur } ?: return@LaunchedEffect
        val link = ImgurLinks.findAnyLink(copied) ?: return@LaunchedEffect
        val body = state.reply.body
        if (link in body) return@LaunchedEffect
        onReplyBodyChange(if (body.isEmpty() || body.endsWith("\n")) body + link else body + "\n" + link)
    }

    fun openLink(url: String) = onOpenLink(url, openLinkExternally)

    fun applyFilter(next: ThreadFilter?) {
        // 絞り込む直前の位置を覚えておき、解除したらそこへ戻す
        if (filter == null && next != null) {
            positionBeforeFilter = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
        filter = next
    }

    // 絞り込みを変えたら先頭から、解除したら絞り込む前の位置から見せる
    LaunchedEffect(filter) {
        if (filter == appliedFilter) return@LaunchedEffect
        appliedFilter = filter
        if (filter != null) {
            listState.scrollToItem(0)
        } else {
            positionBeforeFilter?.let { (i, o) -> listState.scrollToItem(i, o) }
            positionBeforeFilter = null
        }
    }

    fun goToMatch(cursor: Int) {
        if (matches.isEmpty()) return
        matchCursor = cursor.mod(matches.size)
        scope.launch { listState.swiftScrollToItem(matches[matchCursor]) }
    }

    // 検索語が変わったら、今の位置から下で最初の一致へ移る（なければ先頭の一致）
    LaunchedEffect(matches) {
        if (!searchOpen || matches.isEmpty()) return@LaunchedEffect
        val from = listState.firstVisibleItemIndex
        goToMatch(matches.indexOfFirst { it >= from }.takeIf { it >= 0 } ?: 0)
    }

    fun openAnchor(targets: List<IntRange>) {
        val numbers = anchorNumbers(targets, state.posts.size)
        if (numbers.isNotEmpty()) {
            val label = targets.joinToString(",") { range ->
                if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"
            }
            sheetStack = sheetStack + ThreadSheet.Anchor(label, numbers)
        } else {
            val missing = targets.firstOrNull()?.first ?: return
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.thread_post_not_found, missing)) }
        }
    }

    fun openReplies(number: Int) {
        val list = state.index.repliesTo(number)
        if (list.isNotEmpty()) sheetStack = sheetStack + ThreadSheet.Replies(number, list)
    }

    fun openById(id: String) {
        val list = state.index.postsById(id)
        if (list.isNotEmpty()) sheetStack = sheetStack + ThreadSheet.ById(id, list)
    }

    // メニュー（ポップアップ）もこの画面の内容を透かせるよう、透かす元を渡す
    CompositionLocalProvider(LocalScreenHazeState provides hazeState) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { GlassSnackbarHost(snackbar, Modifier.padding(bottom = Dimens.floatingTabsContentPadding)) },
            topBar = {
                BoardsTopAppBar(
                    hazeState = hazeState,
                    title = {
                        Text(
                            if (state.posts.isEmpty()) state.title else "${state.title} (${state.posts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.common_back_cd))
                        }
                    },
                    actions = {
                        ThreadMenu(
                            onOpenSettings = onOpenSettings,
                            fontSize = fontSize,
                            onFontSize = onFontSize,
                            replyEnabled = !state.archived,
                            onOpenReply = { onOpenReply(null) },
                            onOpenThreadUrl = {
                                scope.launch {
                                    val url = currentThreadUrl()
                                    if (url != null) openInAppBrowser(url)
                                    else snackbar.showSnackbar(resources.getString(R.string.thread_url_open_failed))
                                }
                            },
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val topPadding = padding.calculateTopPadding()
            Box(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()) {
                ReadingFontSize(fontSize) {
                    // 更新は下部バーの「新着」で行う（引っ張って更新はしない。押すとベルが回る）
                    Box(Modifier.fillMaxSize()) {
                        if (holdContent || (state.posts.isEmpty() && (state.loading || state.refreshing))) {
                            // 入ってくる途中・取得中は、レスの形だけを先に見せる
                            PostSkeletonList(Modifier.widthIn(max = Dimens.maxReadingWidth).align(Alignment.TopCenter).padding(top = topPadding))
                        } else if (state.posts.isEmpty()) {
                            Box(Modifier.fillMaxSize())
                        } else if (shown.isEmpty()) {
                            Box(Modifier.fillMaxSize().padding(top = topPadding), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(R.string.thread_filter_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // 待たせていた一覧だけを、プレースホルダーの上にフェードで重ねる（最初から見えている一覧は動かさない）
                            // 本文の入れ替えは、M3 のトークン（slowEffects ≒ 0.3 秒）では速すぎて見えないので 0.6 秒にする
                            val fadeSpec = androidx.compose.animation.core.tween<Float>(CONTENT_SWAP_FADE_MS)
                            val listAlpha = remember { Animatable(if (wasHeld) 0f else 1f) }
                            var fading by remember { mutableStateOf(wasHeld) }
                            LaunchedEffect(Unit) {
                                listAlpha.animateTo(1f, fadeSpec)
                                fading = false
                            }
                            // プレースホルダーは本文と入れ替わるように薄くしてから外す
                            if (fading) {
                                PostSkeletonList(
                                    Modifier.widthIn(max = Dimens.maxReadingWidth).align(Alignment.TopCenter).padding(top = topPadding)
                                        .graphicsLayer { alpha = 1f - listAlpha.value },
                                )
                            }
                            ThreadFastScroller(
                                listState = listState,
                                itemCount = shown.size,
                                label = { i ->
                                    val post = shown[i]
                                    listOfNotNull(">>${post.number}", post.shortDate).joinToString(" · ")
                                },
                                topInset = topPadding,
                                hazeState = hazeState,
                                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = listAlpha.value },
                            ) {
                                LazyColumn(
                                    state = listState,
                                    // ぼかす元はレスの一覧だけにする。外側の高速スクロールの吹き出しが一覧を透かせるように
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .widthIn(max = Dimens.maxReadingWidth)
                                        .fillMaxWidth()
                                        .hazeSource(hazeState),
                                    contentPadding = PaddingValues(top = topPadding, bottom = Dimens.floatingTabsContentPadding),
                                ) {
                                    // 画像つきと文字だけのレスで中身の構造が違うので、種類を分けて再利用しやすくする
                                    items(shown, key = { it.number }, contentType = { postContentType(it) }) { post ->
                                        val marks = state.marksOf(post)
                                        PostItem(
                                            post = post,
                                            replyCount = state.index.replyCount(post.number),
                                            idTotal = state.index.idTotal(post.id),
                                            isNewStart = filter == null && post.number == state.newFrom,
                                            highlight = highlight,
                                            bodyBlurred = marks.bodyBlurred,
                                            isGoreWarned = state.index.isGoreWarned(post.number),
                                            isMine = marks.isMine,
                                            isNg = marks.isNg,
                                            onToggleNg = onToggleNg,
                                            onAnchorClick = ::openAnchor,
                                            onLinkClick = ::openLink,
                                            onRepliesClick = ::openReplies,
                                            onIdClick = ::openById,
                                            onImageClick = { urls, index -> previewGallery = ImageGallery(urls, index) },
                                            onReplyClick = { number -> onOpenReply(number) },
                                        )
                                    }
                                    // 最後に見ていたレスより後に新着がないときは、末尾に「新着なし」を出す
                                    if (filter == null && state.newFrom == state.posts.size + 1) {
                                        item(key = "no-new") { NewPostsBand(stringResource(R.string.thread_no_new_posts)) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.loading || (state.refreshing && (state.posts.isEmpty() || state.automaticRefresh))) {
                    val loadingLabel = stringResource(R.string.thread_loading)
                    LinearProgressIndicator(
                        Modifier.align(Alignment.TopCenter).padding(top = topPadding).fillMaxWidth()
                            .semantics { contentDescription = loadingLabel },
                    )
                }
                // 検索中はキーボードの上へ持ち上げる（ナビゲーションバー分は Scaffold が空けている）
                ThreadBottomBar(
                    hazeState = hazeState,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
                    refreshing = state.refreshing,
                    manualRefreshing = state.refreshing && !state.automaticRefresh,
                    onRefreshToNew = onRefresh,
                    onSummary = { onSummaryOpen(shown, filter != null) },
                    aiFilter = state.aiFilter,
                    onAiFilter = onAiFilter,
                    onReloadMute = onReloadMute,
                    aiMuteAvailable = aiMuteAvailable,
                    aiAvailable = aiAvailable,
                    filter = filter,
                    filteredCount = shown.size,
                    onFilter = ::applyFilter,
                    searchOpen = searchOpen,
                    onSearchOpenChange = { open ->
                        searchOpen = open
                        if (!open) query = ""
                    },
                    query = query,
                    onQueryChange = { query = it },
                    matchPosition = matchCursor.coerceAtMost((matches.size - 1).coerceAtLeast(0)),
                    matchCount = matches.size,
                    onPrevMatch = { goToMatch(matchCursor - 1) },
                    onNextMatch = { goToMatch(matchCursor + 1) },
                )
                // アプリ内ブラウザのシートの後ろを暗くする（M3 のスクリム。出入りはフェード）
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = inAppBrowserOpen,
                    enter = fadeIn(motion.defaultEffectsSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()),
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = IN_APP_BROWSER_SCRIM_ALPHA)))
                }
            }
        }
    }

    if (state.summarySheetOpen) {
        ThreadSummarySheet(
            state.summary, state.modelStatus, state.modelName, hazeState, onSummaryClose, onSummaryDownload,
            onRetry = { onSummary(shown, filter != null) },
            onStop = onSummaryStop,
            onBegin = onSummaryBegin,
            onUpdate = { onSummaryUpdate(shown, filter != null) },
            question = state.question,
            onQuestionChange = onQuestionChange,
            onAsk = { onAsk(shown) },
            onStopQuestion = onStopQuestion,
            onAnchorClick = { n -> openAnchor(listOf(n..n)) },
            onResetQuestion = onResetQuestion,
            // 要約の範囲のアンカーは、シートを閉じてスレの範囲の先頭へ直接移る（アンカーのシートでは開かない）
            onJumpToPost = { range ->
                onSummaryClose()
                val index = shown.indexOfFirst { it.number >= range.first }
                if (index >= 0) scope.launch { listState.swiftScrollToItem(index) }
            },
        )
    }
    if (sheetStack.isNotEmpty()) {
        PostListSheet(
            stack = sheetStack,
            posts = state.posts,
            index = state.index,
            hazeState = hazeState,
            fontSize = fontSize,
            onBack = { sheetStack = sheetStack.dropLast(1) },
            onDismiss = { sheetStack = emptyList() },
            onAnchorClick = ::openAnchor,
            onLinkClick = ::openLink,
            onRepliesClick = ::openReplies,
            onIdClick = ::openById,
            onImageClick = { urls, index -> previewGallery = ImageGallery(urls, index) },
            // 返信や ID の一覧でも、ミュートしたレスはぼかす
            marks = { post -> state.marksOf(post) },
            onToggleNg = onToggleNg,
        )
    }
    previewGallery?.let { gallery ->
        ImageOverlay(
            gallery = gallery,
            onDismiss = { previewGallery = null },
            onOpenInBrowser = ::openUrl,
        )
    }
    if (state.reply.open) {
        ReplySheet(
            reply = state.reply,
            onDismiss = onDismissReply,
            onNameChange = onReplyNameChange,
            onMailChange = onReplyMailChange,
            onToggleSage = onToggleSage,
            onBodyChange = onReplyBodyChange,
            onSubmit = onSubmitReply,
            onCancelConfirmation = onCancelReplyConfirmation,
            hazeState = hazeState,
            // Imgur も、ほかの URL と同じ下から出るアプリ内ブラウザで開く（設定によらず）
            onOpenImgur = onOpenImgur ?: { openImgur(IMGUR_UPLOAD_URL) },
        )
    }

}

private fun postContentType(post: Post): Int =
    if (post.body.any { it is Segment.Link && (it.isImage || it.isVideo) }) 1 else 0

/**
 * 遠い位置へは、目的地の少し手前まで一瞬で飛び、最後の数レス分だけをスクロールで滑らせて着く。
 * 長い距離をアニメーションでたどると、途中のレスを測りながら進むので途切れ途切れに見えるため。
 */
private suspend fun LazyListState.swiftScrollToItem(index: Int) {
    val distance = index - firstVisibleItemIndex
    if (kotlin.math.abs(distance) > SWIFT_SCROLL_RUNWAY) {
        scrollToItem(if (distance > 0) index - SWIFT_SCROLL_RUNWAY else index + SWIFT_SCROLL_RUNWAY)
    }
    animateScrollToItem(index)
}

/** 最後に滑らせる距離（レス数）。 */
private const val SWIFT_SCROLL_RUNWAY = 6

/** アプリ内ブラウザを開いているあいだ、後ろの画面にかけるスクリムの濃さ（M3 のモーダルの標準と同じ 32%）。 */
private const val IN_APP_BROWSER_SCRIM_ALPHA = 0.32f

private const val CLIP_READ_ATTEMPTS = 10
private const val CLIP_READ_INTERVAL_MS = 200L
private const val IMGUR_UPLOAD_URL = "https://imgur.com/upload"


@Composable
private fun ThreadMenu(
    onOpenSettings: () -> Unit,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    replyEnabled: Boolean,
    onOpenReply: () -> Unit,
    onOpenThreadUrl: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_huge_settings), contentDescription = stringResource(R.string.common_more_cd)) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.thread_bar_reply)) },
                enabled = replyEnabled,
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_edit), contentDescription = null) },
                onClick = {
                    open = false
                    onOpenReply()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.thread_open_url_title)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_huge_link), contentDescription = null) },
                onClick = {
                    open = false
                    onOpenThreadUrl()
                },
            )
            HorizontalDivider()
            FontSizeMenuContent(fontSize, onFontSize)
            HorizontalDivider()
            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { open = false; onOpenSettings() })
        }
    }
}
