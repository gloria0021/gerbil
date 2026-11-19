package jp.miyavi.android.gerbil.feature.threadlist

import jp.miyavi.android.gerbil.feature.aichat.ChatLink
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import jp.miyavi.android.gerbil.feature.aichat.AiChatWindow
import jp.miyavi.android.gerbil.feature.aichat.AiChatViewModel
import jp.miyavi.android.gerbil.ui.CONTENT_SWAP_FADE_MS
import androidx.compose.runtime.mutableFloatStateOf
import jp.miyavi.android.gerbil.ui.SkeletonColors
import jp.miyavi.android.gerbil.ui.skeletonBar
import jp.miyavi.android.gerbil.ui.skeletonColors
import jp.miyavi.android.gerbil.ui.rememberShimmer
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.clickable
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.snapshotFlow
import jp.miyavi.android.gerbil.ui.GlassScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import jp.miyavi.android.gerbil.ui.GlassPill
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.draw.clipToBounds
import jp.miyavi.android.gerbil.navigation.pushTransition
import jp.miyavi.android.gerbil.navigation.popTransition
import jp.miyavi.android.gerbil.navigation.rememberEnterSettled
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import jp.miyavi.android.gerbil.data.SiteAddResult
import jp.miyavi.android.gerbil.ui.SURFACE_OPACITY
import jp.miyavi.android.gerbil.ui.GlassDropdownMenu
import jp.miyavi.android.gerbil.ui.LocalScreenHazeState
import androidx.compose.runtime.CompositionLocalProvider
import jp.miyavi.android.gerbil.ui.motionItem
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import jp.miyavi.android.gerbil.ui.asString
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import jp.miyavi.android.gerbil.ui.GlassSnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.gerbil.ui.TopInsetPullToRefreshBox
import jp.miyavi.android.gerbil.R
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.gerbil.core.database.BoardEntity
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.designsystem.ThreadFastScroller
import jp.miyavi.android.gerbil.core.settings.ThreadSort
import jp.miyavi.android.gerbil.ui.FloatingGlassBar
import jp.miyavi.android.gerbil.ui.FontSizeMenuContent
import jp.miyavi.android.gerbil.ui.BoardsTopAppBar
import jp.miyavi.android.gerbil.ui.MessageEffect
import jp.miyavi.android.gerbil.ui.ReadingFontSize
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import jp.miyavi.android.gerbil.core.settings.Settings

/** 画面1: スレ選択（contracts/ui-thread-list-screen.md）。下部タブで 閲覧 / 一覧 を切り替える。 */
@Composable
fun ThreadListScreen(
    viewModel: ThreadListViewModel,
    fontSize: Int,
    onFontSize: (Int) -> Unit,
    onOpenThread: (boardId: String, threadKey: Long) -> Unit,
    onOpenSettings: () -> Unit,
    /** ジャービルの案内から、設定の「フィードバックを送信」を開く。 */
    onOpenFeedback: () -> Unit = onOpenSettings,
    onOpenSites: () -> Unit = {},
    chatViewModel: AiChatViewModel? = null,
    /** ジャービルの文字の大きさ（スレ表示と同じ設定）。 */
    chatFontSize: Int = jp.miyavi.android.gerbil.core.settings.Settings.DEFAULT_FONT_SIZE,
    onAddFirstBoard: ((url: String, onResult: (SiteAddResult) -> Unit) -> Unit)? = null,
) {
    var chatAnchor by remember(chatViewModel) { mutableStateOf<Offset?>(null) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(viewModel.messages, snackbar)
    ThreadListContent(
        state = state,
        onOpenChat = chatViewModel?.let { { anchor -> chatAnchor = anchor } },
        chatActive = chatAnchor != null,
        snackbar = snackbar,
        fontSize = fontSize,
        onFontSize = onFontSize,
        onOpenThread = onOpenThread,
        onOpenSettings = onOpenSettings,
        onOpenSites = onOpenSites,
        onAddFirstBoard = onAddFirstBoard,
        onSelectTab = viewModel::selectTab,
        onSort = viewModel::setSort,
        onRefresh = { if (state.showBoards) viewModel.refreshMenu() else viewModel.refresh() },
        onQueryChange = viewModel::setQuery,
        onSelectBoard = { viewModel.selectBoard(it.id) },
        onBackToBoards = viewModel::showBoardList,
        onToggleFavorite = { viewModel.toggleFavorite(it) },
        onRemoveFromBrowse = { viewModel.removeFromBrowse(it) },
        onToggleBoardFavorite = { viewModel.toggleBoardFavorite(it) },
    )
    chatAnchor?.let { anchor ->
        if (chatViewModel != null) AiChatWindow(chatViewModel, anchor, chatFontSize) { destination ->
            chatAnchor = null
            if (destination != null) {
                val threadKey = destination.threadKey
                if (destination.link == ChatLink.FEEDBACK) {
                    onOpenFeedback()
                } else if (destination.link == ChatLink.SITES) {
                    onOpenSites()
                } else if (threadKey != null) {
                    onOpenThread(destination.boardId, threadKey)
                } else {
                    viewModel.selectTab(ThreadListTab.LIST)
                    viewModel.selectBoard(destination.boardId)
                }
            }
        }
    }
}

/** 状態を受け取って描画するだけの部分（スクリーンショットテスト用に分けてある）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListContent(
    state: ThreadListUiState,
    snackbar: SnackbarHostState,
    onOpenThread: (boardId: String, threadKey: Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSites: () -> Unit = {},
    onAddFirstBoard: ((url: String, onResult: (SiteAddResult) -> Unit) -> Unit)? = null,
    onSelectTab: (ThreadListTab) -> Unit,
    onSort: (ThreadSort) -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectBoard: (BoardEntity) -> Unit,
    onBackToBoards: () -> Unit,
    onToggleFavorite: (ThreadRowUi) -> Unit,
    onRemoveFromBrowse: (ThreadRowUi) -> Unit = {},
    onToggleBoardFavorite: (BoardEntity) -> Unit = {},
    onOpenChat: ((Offset) -> Unit)? = null,
    chatActive: Boolean = false,
    fontSize: Int = Settings.DEFAULT_FONT_SIZE,
    onFontSize: (Int) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val inBoard = state.tab == ThreadListTab.LIST && !state.showBoards
    var searchOpen by rememberSaveable(inBoard, state.boardId) { mutableStateOf(state.query.isNotBlank()) }
    val searching = inBoard && searchOpen
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeSearch = {
        focus.clearFocus()
        keyboard?.hide()
        searchOpen = false
        onQueryChange("")
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
                            when {
                                state.tab == ThreadListTab.BROWSE -> stringResource(ThreadListTab.BROWSE.label)
                                state.showBoards -> stringResource(R.string.board_list_title)
                                else -> state.boardName.orEmpty()
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (inBoard) {
                            IconButton(onClick = if (searching) closeSearch else onBackToBoards) {
                                Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(if (searching) R.string.thread_search_close_cd else R.string.board_list_back_cd))
                            }
                        }
                    },
                    actions = {
                        // 確かめ終わって使えると分かったら、フェードで出す
                        AnimatedVisibility(
                            visible = onOpenChat != null,
                            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                        ) {
                            var anchor by remember { mutableStateOf(Offset.Zero) }
                            IconButton(
                                onClick = { onOpenChat?.invoke(anchor) },
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    anchor = coordinates.positionOnScreen() + Offset(
                                        coordinates.size.width / 2f,
                                        coordinates.size.height / 2f,
                                    )
                                },
                            ) {
                                Icon(
                                    painterResource(R.drawable.gerbil_chat_peek),
                                    contentDescription = stringResource(R.string.ai_chat_title),
                                    modifier = Modifier.size(Spacing.xl).graphicsLayer { alpha = if (chatActive) 0f else 1f },
                                )
                            }
                        }
                        OverflowMenu(onOpenSettings, onOpenSites, fontSize, onFontSize)
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val topPadding = padding.calculateTopPadding()
            Box(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize()) {
                ReadingFontSize(fontSize) {
                    TopInsetPullToRefreshBox(
                        isRefreshing = if (state.showBoards) state.boardList.refreshing else state.refreshing,
                        onRefresh = onRefresh,
                        topPadding = topPadding,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    ) {
                        // タブを切り替えたときだけ、新しい中身をフェードで出す（起動時は出さない）
                        val firstTab = remember { booleanArrayOf(true) }
                        val contentAlpha = remember(state.tab) {
                            Animatable(if (firstTab[0]) 1f else 0f).also { firstTab[0] = false }
                        }
                        val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                        LaunchedEffect(contentAlpha) { contentAlpha.animateTo(1f, fadeSpec) }
                        Box(Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
                        when {
                            state.tab == ThreadListTab.LIST -> ListTabGrid(
                                state = state,
                                topPadding = topPadding,
                                onQueryChange = onQueryChange,
                                onSort = onSort,
                                searching = searching,
                                onSearchToggle = { if (searching) closeSearch() else searchOpen = true },
                                // 検索欄を閉じてから、予測型「戻る」で板一覧へ戻る
                                backToBoards = onBackToBoards,
                                onSelectBoard = onSelectBoard,
                                onToggleBoardFavorite = onToggleBoardFavorite,
                                onOpenRow = { row -> onOpenThread(row.boardId, row.threadKey) },
                                onToggleFavorite = onToggleFavorite,
                                onAddFirstBoard = onAddFirstBoard,
                            )
                            state.rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = topPadding)) { EmptyState(state) }
                            else -> BrowseTabList(
                                state = state,
                                topPadding = topPadding,
                                onOpenThread = { row -> onOpenThread(row.boardId, row.threadKey) },
                                onToggleFavorite = onToggleFavorite,
                                onRemoveFromBrowse = onRemoveFromBrowse,
                            )
                        }
                        }
                    }
                }
                FloatingGlassBar(hazeState = hazeState, fixedOpacity = SURFACE_OPACITY) {
                    GlassTabs(ThreadListTab.entries, state.tab, onSelectTab, ThreadListTab::label)
                }
            }
        }
    }
    BackHandler(enabled = searching, onBack = closeSearch)
}

/** 選択中を示す面は、選んだタブへばねで滑って移る。 */
@Composable
private fun <T> GlassTabs(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> Int,
    fillWidth: Boolean = false,
) {
    val motion = MaterialTheme.motionScheme
    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    // 各タブの左端と幅（px）。置かれてから分かる
    val bounds = remember { mutableStateMapOf<T, Pair<Float, Float>>() }
    val target = bounds[selected]
    // 位置が分かった最初の回はその場に出し、以後の変更だけを動かす
    val indicator = if (target != null) {
        val x by animateFloatAsState(target.first, motion.defaultSpatialSpec(), label = "tabIndicatorX")
        val width by animateFloatAsState(target.second, motion.defaultSpatialSpec(), label = "tabIndicatorWidth")
        Modifier.drawBehind {
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2f),
            )
        }
    } else {
        Modifier
    }
    Row(indicator.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            val isSelected = selected == tab
            val textColor by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                motion.fastEffectsSpec(),
                label = "tabTextColor",
            )
            Box(
                modifier = (if (fillWidth) Modifier.weight(1f) else Modifier)
                    .onPlaced { bounds[tab] = it.positionInParent().x to it.size.width.toFloat() }
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) }
                    .heightIn(min = Dimens.minTouchTarget)
                    .padding(horizontal = if (fillWidth) Spacing.xs else Spacing.xxl, vertical = Spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label(tab)), style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_huge_search), contentDescription = null)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier.weight(1f),
            singleLine = true,
            textStyle = textStyle.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.title_search_placeholder),
                            style = textStyle,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
    }
}

/** 板一覧と板内のスレ一覧。スレ一覧では検索と並び順を先頭に固定する。 */
@Composable
private fun ListTabGrid(
    state: ThreadListUiState,
    topPadding: Dp,
    onQueryChange: (String) -> Unit,
    onSort: (ThreadSort) -> Unit,
    searching: Boolean,
    onSearchToggle: () -> Unit,
    backToBoards: () -> Unit,
    onSelectBoard: (BoardEntity) -> Unit,
    onToggleBoardFavorite: (BoardEntity) -> Unit,
    onOpenRow: (ThreadRowUi) -> Unit,
    onToggleFavorite: (ThreadRowUi) -> Unit,
    onAddFirstBoard: ((url: String, onResult: (SiteAddResult) -> Unit) -> Unit)? = null,
) {
    val gridState = rememberLazyGridState()
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    // 「掲示板」のセクションで選んでいる掲示板
    var selectedSite by rememberSaveable { mutableStateOf<String?>(null) }
    val boardModel = rememberBoardListModel(state.boardList, "")
    // NavDisplay は履歴が同じ間はエントリを再利用する。描画時に最新の一覧と検索状態を読む。
    val renderPage by rememberUpdatedState<@Composable (String) -> Unit>({ page ->
        val showingBoards = page == "boards"
        val settled = rememberEnterSettled()
        val holdRows = !settled
        val rowsAlpha by animateFloatAsState(
            if (settled) 1f else 0f,
            androidx.compose.animation.core.tween(CONTENT_SWAP_FADE_MS),
            label = "boardRowsAlpha",
        )
        val rowsFading = settled && rowsAlpha < 1f
        val shimmer = if (holdRows || rowsFading || (state.rows.isEmpty() && state.refreshing)) rememberShimmer() else remember { mutableFloatStateOf(-1f) }
        val threadsState = key(state.boardId) { rememberLazyListState() }
        val pinned = showingBoards && state.boardList.boards.isNotEmpty() && boardModel.matches == null
        val switchHaze = rememberHazeState()
        var switchHeight by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        // 掲示板ごとの板一覧を横のページにし、スワイプかタブで切り替える（いくつ増えてもタブが横に流れる）
        val sections = boardModel.sections
        val pagerState = rememberPagerState(initialPage = sections.indexOfFirst { it.siteId == selectedSite }.coerceAtLeast(0)) { sections.size }
        if (showingBoards) LaunchedEffect(pagerState, sections) {
            snapshotFlow { pagerState.currentPage }.collect { page -> sections.getOrNull(page)?.let { selectedSite = it.siteId } }
        }
        val tabScope = rememberCoroutineScope()
        Column(
            Modifier.fillMaxSize()
                // 下にのぞかせる板一覧が透けないよう、いつも画面の背景色で塗る
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (showingBoards && holdRows) {
                Column(Modifier.fillMaxSize().padding(top = topPadding)) {
                    repeat(THREAD_SKELETON_ROWS) { ThreadRowSkeleton(shimmer) }
                }
            } else {
                if (pinned) {
                    Spacer(Modifier.height(topPadding))
                    BoardListPinnedHeader(boardModel, state.boardId, onSelectBoard, onToggleBoardFavorite)
                }
                Box(Modifier.weight(1f)) {
                    if (pinned) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().hazeSource(switchHaze), key = { sections[it].siteId }) { page ->
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(BoardGridColumns),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(BoardGridSpacing),
                                contentPadding = PaddingValues(top = with(density) { switchHeight.toDp() }, bottom = Dimens.floatingTabsContentPadding),
                            ) {
                                boardListItems(
                                    boardModel, state.boardId, onSelectBoard, onToggleBoardFavorite, expanded,
                                    sections[page].siteId, {}, null, categoriesOnly = true,
                                ) { name -> expanded = if (name in expanded) expanded - name else expanded + name }
                            }
                        }
                        // 掲示板が1つだけなら、押しても変わらないのでタブは出さない
                        if (sections.size > 1) GlassScrollableTabRow(
                            hazeState = switchHaze,
                            selectedTabIndex = pagerState.currentPage,
                            tabCount = sections.size,
                            modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { switchHeight = it.height },
                        ) {
                            sections.forEachIndexed { index, site ->
                                // 押した反応（ripple）は出さない。選択のピルがページに合わせて滑る動きだけで、押したことを伝える
                                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = { tabScope.launch { pagerState.animateScrollToPage(index) } },
                                        text = { Text(site.label, maxLines = 1) },
                                        selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    } else if (showingBoards) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(BoardGridColumns),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(BoardGridSpacing),
                            contentPadding = PaddingValues(top = topPadding, bottom = Dimens.floatingTabsContentPadding),
                        ) {
                            boardListItems(
                                boardModel, state.boardId, onSelectBoard, onToggleBoardFavorite, expanded,
                                selectedSite, { selectedSite = it }, onAddFirstBoard,
                            ) { name ->
                                expanded = if (name in expanded) expanded - name else expanded + name
                            }
                        }
                    } else {
                        val sortScope = rememberCoroutineScope()
                        val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
                        var previousSort by remember(threadsState) { mutableStateOf(state.sort) }
                        SideEffect {
                            if (previousSort != state.sort) {
                                threadsState.requestScrollToItem(0)
                                previousSort = state.sort
                            }
                        }
                        var controlsHeight by remember { mutableStateOf(0) }
                        val listTop = topPadding + with(density) { controlsHeight.toDp() }
                        ThreadFastScroller(
                            listState = threadsState,
                            itemCount = if (holdRows) 0 else state.rows.size,
                            label = { index -> state.rows.getOrNull(index)?.title.orEmpty() },
                            modifier = Modifier.fillMaxSize(),
                            topInset = listTop,
                            bottomInset = Dimens.floatingTabsContentPadding,
                            hazeState = switchHaze,
                            threadList = true,
                        ) {
                            LazyColumn(
                                state = threadsState,
                                modifier = Modifier.fillMaxSize().hazeSource(switchHaze),
                                contentPadding = PaddingValues(top = listTop, bottom = Dimens.floatingTabsContentPadding),
                            ) {
                                when {
                                    state.rows.isEmpty() && state.query.isNotBlank() -> item(key = "nomatch") {
                                        Text(
                                            stringResource(R.string.title_search_no_match, state.query.trim()),
                                            modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    holdRows || (state.rows.isEmpty() && state.refreshing) ->
                                        items(THREAD_SKELETON_ROWS, key = { "skeleton-$it" }) { ThreadRowSkeleton(shimmer) }
                                    state.rows.isEmpty() -> item(key = "empty") { EmptyMessage(state) }
                                    else -> items(state.rows, key = { "${it.boardId}/${it.threadKey}" }) { row ->
                                        Box(Modifier.animateItem(fadeInSpec = null, placementSpec = placementSpec, fadeOutSpec = null)) {
                                            if (rowsFading) ThreadRowSkeleton(shimmer, Modifier.graphicsLayer { alpha = 1f - rowsAlpha })
                                            ThreadRowWithMenu(
                                                row,
                                                modifier = Modifier.graphicsLayer { alpha = rowsAlpha },
                                                onOpen = { onOpenRow(row) },
                                                onToggleFavorite = { onToggleFavorite(row) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val motion = MaterialTheme.motionScheme
                        val tabLineHeight = MaterialTheme.typography.labelLarge.lineHeight
                        val controlHeight = with(density) { tabLineHeight.toDp() + Spacing.s * 2 }
                            .coerceAtLeast(Dimens.minTouchTarget)
                        var focusSearch by remember { mutableStateOf(false) }
                        Row(
                            Modifier.align(Alignment.TopCenter).padding(top = topPadding)
                                .onSizeChanged { controlsHeight = it.height }
                                .padding(horizontal = Spacing.l, vertical = Spacing.xs)
                                .heightIn(min = Dimens.searchBarReservedHeight - Spacing.s),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GlassPill(hazeState = switchHaze, modifier = Modifier.weight(1f), rim = !searching) {
                                AnimatedContent(
                                    targetState = searching,
                                    transitionSpec = {
                                        val direction = if (targetState) 1 else -1
                                        slideInHorizontally(motion.defaultSpatialSpec()) { direction * it } togetherWith
                                            slideOutHorizontally(motion.fastSpatialSpec()) { -direction * it }
                                    },
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth().height(controlHeight),
                                    label = "titleSearch",
                                ) { open ->
                                    if (open) {
                                        val requester = remember { FocusRequester() }
                                        if (searching && focusSearch) LaunchedEffect(requester) {
                                            requester.requestFocus()
                                            focusSearch = false
                                        }
                                        SearchField(state.query, onQueryChange, Modifier.focusRequester(requester))
                                    } else {
                                        GlassTabs(
                                            listOf(ThreadSort.MOMENTUM, ThreadSort.NEWEST, ThreadSort.RANK),
                                            state.sort,
                                            { sort ->
                                                if (sort != state.sort) sortScope.launch {
                                                    // 先頭にある行の位置を基準にして並べ替える。
                                                    threadsState.scrollToItem(0)
                                                    onSort(sort)
                                                }
                                            },
                                            ::sortLabel,
                                            fillWidth = true,
                                        )
                                    }
                                }
                            }
                            GlassPill(hazeState = switchHaze) {
                                IconButton(onClick = {
                                    focusSearch = !searching
                                    onSearchToggle()
                                }) {
                                    Icon(
                                        painterResource(if (searching) R.drawable.ic_huge_close else R.drawable.ic_huge_search),
                                        contentDescription = stringResource(if (searching) R.string.thread_search_close_cd else R.string.thread_bar_search),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    })
    NavDisplay(
        backStack = if (state.showBoards) listOf("boards") else listOf("boards", "threads"),
        onBack = backToBoards,
        modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface),
        transitionSpec = { pushTransition() },
        popTransitionSpec = { popTransition() },
        predictivePopTransitionSpec = { _ -> popTransition() },
        entryProvider = { page -> NavEntry(page) { renderPage(page) } },
    )
}

/** 取得中・板を開いて流れているあいだに、スレの行の形だけを先に見せる（タイトル2行とメタ行を帯で描き、光を流す）。 */
@Composable
private fun ThreadRowSkeleton(shimmer: State<Float>, modifier: Modifier = Modifier) {
    val colors = skeletonColors()
    val shape = MaterialTheme.shapes.extraSmall
    val density = LocalDensity.current
    val title = MaterialTheme.typography.titleMedium
    val label = MaterialTheme.typography.labelMedium
    val titleHeight = with(density) { title.fontSize.toDp() }
    val titleGap = with(density) { (title.lineHeight.takeIf { it.isSp }?.toDp() ?: titleHeight) - titleHeight }
    val labelHeight = with(density) { label.fontSize.toDp() }
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = Spacing.xs + Dimens.unreadBar + Spacing.s, end = Spacing.l, top = Spacing.m, bottom = Spacing.m),
    ) {
        Column(Modifier.padding(vertical = titleGap / 2), verticalArrangement = Arrangement.spacedBy(titleGap)) {
            Box(Modifier.fillMaxWidth().height(titleHeight).skeletonBar(shimmer, colors, shape))
            Box(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION).height(titleHeight).skeletonBar(shimmer, colors, shape))
        }
        Spacer(Modifier.height(Spacing.xs))
        Box(Modifier.fillMaxWidth(SKELETON_META_FRACTION).height(labelHeight).skeletonBar(shimmer, colors, shape))
    }
}

private const val THREAD_SKELETON_ROWS = 10
private const val SKELETON_TITLE_FRACTION = 0.55f
private const val SKELETON_META_FRACTION = 0.4f

/** 閲覧中タブ: 開いたスレとスター付きのスレ（スター付きが先頭）。 */
@Composable
private fun BrowseTabList(
    state: ThreadListUiState,
    topPadding: Dp,
    onOpenThread: (ThreadRowUi) -> Unit,
    onToggleFavorite: (ThreadRowUi) -> Unit,
    onRemoveFromBrowse: (ThreadRowUi) -> Unit,
) {
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    ThreadFastScroller(
        listState = listState,
        itemCount = state.rows.size,
        label = { index -> state.rows.getOrNull(index)?.title.orEmpty() },
        modifier = Modifier.fillMaxSize(),
        topInset = topPadding,
        bottomInset = Dimens.floatingTabsContentPadding,
        hazeState = hazeState,
        threadList = true,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            contentPadding = PaddingValues(top = topPadding, bottom = Dimens.floatingTabsContentPadding),
        ) {
            items(state.rows, key = { "${it.boardId}/${it.threadKey}" }) { row ->
                ThreadRowWithMenu(
                    row,
                    modifier = motionItem(),
                    onOpen = { onOpenThread(row) },
                    onToggleFavorite = { onToggleFavorite(row) },
                    onDelete = { onRemoveFromBrowse(row) },
                )
            }
        }
    }
}

/** スレ1行＋長押しメニュー（次スレの追尾、閲覧中タブでは削除も）。板のスターは全体タブの板一覧で付け外しする。 */
@Composable
private fun ThreadRowWithMenu(
    row: ThreadRowUi,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 長押しした位置。メニューは、その位置がメニューの右下に来るよう指の左上に出す（指で隠れないように）。
    // DropdownMenu は項目の左下からのずれで置くので、測ったメニューの大きさを引いて合わせる
    var touch by remember { mutableStateOf(Offset.Zero) }
    var rowHeight by remember { mutableStateOf(0) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(
        modifier
            .onSizeChanged { rowHeight = it.height }
            .pointerInput(Unit) {
                awaitEachGesture { touch = awaitFirstDown(requireUnconsumed = false).position }
            },
    ) {
        ThreadListItem(row, onClick = onOpen, onLongClick = { menu = true })
        GlassDropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            offset = with(density) { DpOffset((touch.x - menuSize.width).toDp(), (touch.y - rowHeight - menuSize.height).toDp()) },
            // 大きさを測るまでは見せない（最初の1回だけ、測ってから正しい位置に出す）
            modifier = Modifier
                .onSizeChanged { menuSize = it }
                .graphicsLayer { alpha = if (menuSize == IntSize.Zero) 0f else 1f },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(if (row.isFavorite) R.string.thread_unstar else R.string.thread_star)) },
                onClick = {
                    menu = false
                    onToggleFavorite()
                },
            )
            if (onDelete != null) DropdownMenuItem(
                text = { Text(stringResource(R.string.common_delete)) },
                onClick = {
                    menu = false
                    // 次スレを追尾しているスレは、うっかり消さないよう確かめる
                    if (row.isFavorite) confirmDelete = true else onDelete()
                },
            )
        }
        if (confirmDelete && onDelete != null) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.thread_delete_following)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.common_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun OverflowMenu(onOpenSettings: () -> Unit, onOpenSites: () -> Unit, fontSize: Int, onFontSize: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(painterResource(R.drawable.ic_huge_settings), contentDescription = stringResource(R.string.common_more_cd)) }
        GlassDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FontSizeMenuContent(fontSize, onFontSize)
            HorizontalDivider()
            // 掲示板の登録・ラベルの変更・削除（めったに触らないので、設定の中ではなくここから開く）
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_sites)) },
                onClick = {
                    open = false
                    onOpenSites()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    open = false
                    onOpenSettings()
                },
            )
        }
    }
}

private fun sortLabel(s: ThreadSort) = when (s) {
    ThreadSort.RANK -> R.string.sort_rank
    ThreadSort.MOMENTUM -> R.string.sort_momentum
    ThreadSort.NEWEST -> R.string.sort_newest
}

/** 1行: タイトル（2行まで）＋メタ行＋新着数バッジ。左端に未読（primary）/ 書き込み済み（tertiary）の短い角丸ピル。 */
@Composable
fun ThreadListItem(row: ThreadRowUi, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val dim = row.isArchived
    val titleColor = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClick?.let { stringResource(R.string.common_open_menu) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左端の目印: 端から少し離した、短い角丸のピル（タイトルの左端は 16dp のまま）。
        // スター付き = primary（自分の書き込みはスレではなくレスに印を付ける。未読は右端の新着数バッジで示す）
        val marker = if (row.isFavorite) MaterialTheme.colorScheme.primary else null
        Box(
            Modifier
                .padding(start = Spacing.xs)
                .size(width = Dimens.unreadBar, height = Dimens.unreadBarHeight)
                .then(if (marker != null) Modifier.background(marker, CircleShape) else Modifier),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = Spacing.s, end = Spacing.l, top = Spacing.m, bottom = Spacing.m),
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            val meta = row.meta.map { it.asString() }.joinToString(" · ")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (row.isArchived) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) {
                        Text(
                            stringResource(R.string.thread_meta_archived),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
        if (row.newCount > 0) {
            Surface(
                modifier = Modifier.padding(end = Spacing.l),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape, // バッジ = full
            ) {
                Text(
                    row.newCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(state: ThreadListUiState) {
    val text = when {
        state.refreshing -> ""
        state.tab == ThreadListTab.BROWSE -> stringResource(R.string.thread_list_empty_browse)
        else -> stringResource(R.string.thread_list_empty)
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyState(state: ThreadListUiState) {
    // PullToRefreshBox が反応するよう、空でもスクロール可能なリストにする
    LazyColumn(Modifier.fillMaxSize()) {
        item { EmptyMessage(state) }
    }
}
