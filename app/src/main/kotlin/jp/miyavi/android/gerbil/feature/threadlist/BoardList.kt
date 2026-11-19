package jp.miyavi.android.gerbil.feature.threadlist

import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.platform.LocalContext
import android.content.ClipboardManager
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import jp.miyavi.android.gerbil.data.SiteAddResult
import androidx.compose.material3.Button
import jp.miyavi.android.gerbil.ui.GlassDropdownMenu
import jp.miyavi.android.gerbil.ui.motionItem
import jp.miyavi.android.gerbil.data.BoardRepository
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import jp.miyavi.android.gerbil.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.miyavi.android.gerbil.core.database.BoardEntity
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing

/** 板一覧の掲示板ごとのまとまり。 */
data class SiteSection(val siteId: String, val label: String, val categories: Map<String, List<BoardEntity>>)

/** 板一覧のグリッドの列数と列の間隔。LIST タブの一覧はすべてこのグリッドに並べる。 */
const val BoardGridColumns = 3
val BoardGridSpacing = Spacing.xs

/** 板一覧に並べる内容（スター付き・カテゴリ別の全板・板名の一致）。 */
class BoardListModel(
    val boards: List<BoardEntity>,
    val refreshing: Boolean,
    val favorites: List<BoardEntity>,
    /** 掲示板ごとのセクション（見出し＝ラベル）。中はカテゴリ名ごとの板。 */
    val sections: List<SiteSection>,
    val query: String,
    val matches: List<BoardEntity>?,
)

@Composable
fun rememberBoardListModel(state: BoardListUi, query: String): BoardListModel {
    val favorites = remember(state.boards) { state.boards.filter { it.isFavorite }.sortedByDescending { it.favoriteOrder ?: 0 } }
    val sections = remember(state.menu, state.sites) {
        val labels = state.sites.associate { it.id to it.label }
        state.menu.groupBy { it.board.source }.map { (siteId, rows) ->
            SiteSection(siteId, labels[siteId] ?: siteId, rows.groupBy({ it.menuCategory }, { it.board }).mapValues { (_, list) -> list.distinctBy { it.id } })
        }
    }
    val terms = remember(query) { TitleSearch.terms(query) }
    val matches = remember(state.boards, terms) {
        if (terms.isEmpty()) null else state.boards.filter { TitleSearch.matches(it.name, terms) }
    }
    return BoardListModel(state.boards, state.refreshing, favorites, sections, query, matches)
}

/**
 * 一覧タブの板一覧。上にスター付きの板（横スクロール）、下にカテゴリ別の全板。
 * 検索語があるときは、カテゴリの開閉に関係なく板名が一致する板を出す。
 */
@Composable
fun BoardListContent(
    state: BoardListUi,
    query: String,
    selectedBoardId: String?,
    onSelect: (BoardEntity) -> Unit,
    onToggleFavorite: (BoardEntity) -> Unit = {},
    modifier: Modifier = Modifier,
    topContentPadding: Dp = 0.dp,
) {
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedSite by rememberSaveable { mutableStateOf<String?>(null) }
    val model = rememberBoardListModel(state, query)
    LazyVerticalGrid(
        columns = GridCells.Fixed(BoardGridColumns),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(BoardGridSpacing),
        contentPadding = PaddingValues(top = topContentPadding, bottom = Dimens.floatingTabsContentPadding),
    ) {
        boardListItems(model, selectedBoardId, onSelect, onToggleFavorite, expanded, selectedSite, { selectedSite = it }) { name ->
            expanded = if (name in expanded) expanded - name else expanded + name
        }
    }
}

/** 板一覧の項目をグリッドに追加する。検索欄など、ほかの項目と同じグリッドに並べるために分けている。 */
fun LazyGridScope.boardListItems(
    model: BoardListModel,
    selectedBoardId: String?,
    onSelect: (BoardEntity) -> Unit,
    onToggleFavorite: (BoardEntity) -> Unit,
    expanded: Set<String>,
    /** 「掲示板」のセクションで選んでいる掲示板（null なら最初の掲示板）。 */
    selectedSite: String? = null,
    onSelectSite: (String) -> Unit = {},
    /** 板が1つもないときに出す「見たいスレの URL」の入力欄から登録する（スレの URL ならその板を足してスレを開く）。 */
    onAddFirstBoard: ((url: String, onResult: (SiteAddResult) -> Unit) -> Unit)? = null,
    /** true なら、選んだ掲示板のカテゴリと板だけを出す（スター付きと見出し・切り替えは、上に固定して別に描く）。 */
    categoriesOnly: Boolean = false,
    onToggleCategory: (String) -> Unit,
) {
    val matches = model.matches
    if (matches != null) {
        items(matches, key = { "m:" + it.id }) { board ->
            BoardItem(board, onSelect, onToggleFavorite, supporting = categoryLabel(board.categoryName))
        }
        if (matches.isEmpty()) {
            item(key = "nomatch", span = { GridItemSpan(maxLineSpan) }) { Note(stringResource(R.string.board_list_no_match, model.query.trim())) }
        }
        return
    }
    if (model.favorites.isNotEmpty() && !categoriesOnly) {
        item(key = "fav-h", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.board_list_starred)) }
        item(key = "fav-row", span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(model.favorites, key = { it.id }) { board ->
                    StarredBoardButton(board, board.id == selectedBoardId, onSelect, onToggleFavorite)
                }
            }
        }
        item(key = "fav-d", span = { GridItemSpan(maxLineSpan) }) {
            HorizontalDivider(
                modifier = Modifier.padding(top = Spacing.s),
                thickness = Dimens.hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
    if (model.boards.isEmpty() && !model.refreshing) {
        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            if (onAddFirstBoard != null) FirstBoardInput(onAddFirstBoard) else Note(stringResource(R.string.board_list_empty))
        }
    }
    // 「掲示板」のセクション。登録した掲示板はセグメントボタンで切り替え、選んだ掲示板の板だけを出す（一覧を短く保つ）
    val section = model.sections.firstOrNull { it.siteId == selectedSite } ?: model.sections.firstOrNull()
    if (section != null && !categoriesOnly) {
        item(key = "sites-h", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(stringResource(R.string.board_list_sites)) }
        if (model.sections.size > 1) item(key = "sites-switch", span = { GridItemSpan(maxLineSpan) }) {
            SiteSwitch(model.sections, section.siteId, onSelectSite)
        }
    }
    if (section != null) {
        section.categories.forEach { (name, list) ->
            val key = "${section.siteId}/$name"
            val open = key in expanded
            // カテゴリを開閉したとき、中の板はフェードで出入りし、下の見出しはばねで位置を移す
            item(key = "c:$key", span = { GridItemSpan(maxLineSpan) }) {
                Box(motionItem()) { CategoryHeader(name, list.size, open) { onToggleCategory(key) } }
            }
            if (open) {
                items(list, key = { "b:$key:" + it.id }) { board ->
                    Box(motionItem()) { BoardItem(board, onSelect, onToggleFavorite) }
                }
            }
            item(key = "d:$key", span = { GridItemSpan(maxLineSpan) }) {
                HorizontalDivider(motionItem(), thickness = Dimens.hairline, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * 板一覧の上に固定する部分（スター付きの板の並び）。その下は掲示板ごとのページで、カテゴリと板だけがスクロールする。
 */
@Composable
internal fun BoardListPinnedHeader(model: BoardListModel, selectedBoardId: String?, onSelect: (BoardEntity) -> Unit, onToggleFavorite: (BoardEntity) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (model.favorites.isNotEmpty()) {
            SectionHeader(stringResource(R.string.board_list_starred))
            // 下の掲示板のタブとの間をあける（いきなりタブが来ないように）
            LazyRow(Modifier.padding(bottom = Spacing.m), contentPadding = PaddingValues(horizontal = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                items(model.favorites, key = { it.id }) { board -> StarredBoardButton(board, board.id == selectedBoardId, onSelect, onToggleFavorite) }
            }
        }
    }
}

/**
 * 掲示板の切り替え。4つまでは M3 のセグメントボタン、それより多いときは横にスクロールするチップ。
 */
@Composable
internal fun SiteSwitch(sections: List<SiteSection>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    if (sections.size <= SEGMENTED_SITES_MAX) {
        // 一覧の行は横幅いっぱいの大きさを渡すので、中身の幅に縮めて中央に置く
        SingleChoiceSegmentedButtonRow(modifier.wrapContentWidth(Alignment.CenterHorizontally).padding(horizontal = Spacing.l, vertical = Spacing.s)) {
            sections.forEachIndexed { index, site ->
                SegmentedButton(
                    selected = site.siteId == selected,
                    onClick = { onSelect(site.siteId) },
                    shape = SegmentedButtonDefaults.itemShape(index, sections.size),
                    icon = {},
                    label = { Text(site.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    } else {
        LazyRow(modifier, contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(sections, key = { it.siteId }) { site ->
                FilterChip(selected = site.siteId == selected, onClick = { onSelect(site.siteId) }, label = { Text(site.label) })
            }
        }
    }
}

/** セグメントボタンで並べる掲示板の数の上限（これより多いとボタンが狭くなりすぎる）。 */
private const val SEGMENTED_SITES_MAX = 4

/** スター付きの板。角丸の色付きボタンを横に並べる。タップで開き、長押しでスターを外すメニュー。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StarredBoardButton(
    board: BoardEntity,
    selected: Boolean,
    onSelect: (BoardEntity) -> Unit,
    onToggleFavorite: (BoardEntity) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                // 最後に開いた板も含め、スター付きの板はすべて同じ色にする
                .background(colors.secondaryContainer)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.common_open_menu),
                    onLongClick = { menu = true },
                    onClick = { onSelect(board) },
                )
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                board.name,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSecondaryContainer,
                maxLines = 1,
            )
        }
        GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.board_unstar)) },
                onClick = {
                    menu = false
                    onToggleFavorite(board)
                },
            )
        }
    }
}

/** 板1つ。タップで開き、長押しでスターの付け外しメニューを出す。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BoardItem(
    board: BoardEntity,
    onSelect: (BoardEntity) -> Unit,
    onToggleFavorite: (BoardEntity) -> Unit,
    supporting: String? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs)
            .clip(MaterialTheme.shapes.large)
            .heightIn(min = Dimens.minTouchTarget)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.common_open_menu),
                onLongClick = { menu = true },
                onClick = { onSelect(board) },
            )
            // 外側 4dp ＋ 内側 4dp で、文字を画面の端から 8dp に揃える（レス表示と同じ）
            .padding(horizontal = Spacing.xs, vertical = Spacing.s),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            board.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    GlassDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(if (board.isFavorite) R.string.board_unstar else R.string.board_star)) },
            onClick = {
                menu = false
                onToggleFavorite(board)
            },
        )
    }
    }
}

/** 一覧にない板を保存したときのカテゴリ名だけを訳す。掲示板から届いたカテゴリ名はそのまま。 */
@Composable
private fun categoryLabel(name: String) =
    if (name == BoardRepository.OTHER_CATEGORY) stringResource(R.string.board_category_other) else name

@Composable
private fun CategoryHeader(name: String, count: Int, open: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (open) Modifier.background(colors.surfaceContainerLow) else Modifier)
            .heightIn(min = Dimens.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (open) R.string.common_collapse else R.string.common_expand), onClick = onToggle)
            .padding(horizontal = Spacing.s, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            categoryLabel(name),
            style = MaterialTheme.typography.titleSmall,
            color = if (open) colors.primary else colors.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Icon(
                if (open) painterResource(R.drawable.ic_huge_arrow_down) else painterResource(R.drawable.ic_huge_arrow_right),
                contentDescription = null,
                tint = if (open) colors.primary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 板が1つもないときの入口。丸いアイコンと見出し・説明を中央に置き、その下に見たいスレの URL を貼るピル型の入力欄と「追加」。
 * 入力欄の右端は、空なら「貼り付け」（クリップボードから）、入力があれば「消す」。追加の確認中はボタンの中で回る。
 */
@Composable
private fun FirstBoardInput(onAdd: (url: String, onResult: (SiteAddResult) -> Unit) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<SiteAddResult?>(null) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    fun submit() {
        if (checking || url.isBlank()) return
        checking = true
        onAdd(url) { result ->
            checking = false
            error = result.takeIf { it != SiteAddResult.ADDED }
            if (result == SiteAddResult.ADDED) url = ""
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Box(Modifier.size(Dimens.emptyStateIcon).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_huge_link), contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(Spacing.xl + Spacing.s))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.board_list_first_url_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.settings_site_add_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        // 下線のないピル型の入力欄（M3 の検索欄と同じ形）
        TextField(
            value = url,
            onValueChange = { url = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            enabled = !checking,
            singleLine = true,
            shape = CircleShape,
            placeholder = { Text(stringResource(R.string.board_list_first_url_example), maxLines = 1) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_huge_link), contentDescription = null) },
            trailingIcon = {
                if (url.isEmpty()) {
                    IconButton(onClick = {
                        val clip = runCatching {
                            context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                        }.getOrNull()
                        if (!clip.isNullOrBlank()) { url = clip.trim(); error = null }
                    }) { Icon(painterResource(R.drawable.ic_huge_clipboard), contentDescription = stringResource(R.string.board_list_paste_cd)) }
                } else {
                    IconButton(onClick = { url = ""; error = null }) {
                        Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.board_list_clear_cd))
                    }
                }
            },
            isError = error != null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                disabledContainerColor = colors.surfaceContainerHigh,
                errorContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        val message = when (error) {
            SiteAddResult.INVALID_URL -> R.string.settings_site_error_invalid
            SiteAddResult.DUPLICATE -> R.string.settings_site_error_duplicate
            SiteAddResult.FETCH_FAILED -> R.string.settings_site_error_fetch
            SiteAddResult.EMPTY -> R.string.settings_site_error_empty
            SiteAddResult.UNSUPPORTED -> R.string.settings_site_error_unsupported
            SiteAddResult.ADDED, null -> null
        }
        if (message != null) Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = colors.error, textAlign = TextAlign.Center)
        Button(
            onClick = ::submit,
            enabled = !checking && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
        ) {
            if (checking) {
                CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = Dimens.hairline * 2, color = LocalContentColor.current)
            } else {
                Text(stringResource(R.string.board_list_add_first))
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
