package jp.miyavi.android.gerbil.feature.thread

import jp.miyavi.android.gerbil.ui.BoardsBottomSheet
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.ui.ReadingFontSize

sealed interface ThreadSheet {
    data class Anchor(val label: String, val postNumbers: List<Int>) : ThreadSheet
    data class Replies(val targetNumber: Int, val postNumbers: List<Int>) : ThreadSheet
    data class ById(val id: String, val postNumbers: List<Int>) : ThreadSheet
}

/** アンカー参照先・被参照一覧・同 ID 投稿一覧の多段 ModalBottomSheet（設定の透かし方で描く。下に引いて閉じる）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListSheet(
    stack: List<ThreadSheet>,
    posts: List<Post>,
    index: PostIndex,
    hazeState: HazeState,
    fontSize: Int = Settings.DEFAULT_FONT_SIZE,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onAnchorClick: (List<IntRange>) -> Unit,
    onLinkClick: (String) -> Unit,
    onRepliesClick: (Int) -> Unit,
    onIdClick: (String) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    marks: (Post) -> PostMarks = { PostMarks() },
    onToggleNg: ((Post) -> Unit)? = null,
) {
    val current = stack.lastOrNull() ?: return
    val sheetPosts = remember(current, posts) {
        val numbers = when (current) {
            is ThreadSheet.Anchor -> current.postNumbers
            is ThreadSheet.Replies -> current.postNumbers
            is ThreadSheet.ById -> current.postNumbers
        }
        numbers.mapNotNull { n -> posts.getOrNull(n - 1) }
    }
    val title = when (current) {
        is ThreadSheet.Anchor -> if (sheetPosts.size == 1) ">>${current.label}" else ">>${current.label} (${sheetPosts.size})"
        is ThreadSheet.Replies -> stringResource(R.string.sheet_replies_title, current.targetNumber, sheetPosts.size)
        is ThreadSheet.ById -> stringResource(R.string.sheet_id_title, current.id, sheetPosts.size)
    }

    // ナビゲーションバー分の余白はガラスの内側（リスト）で取る
    BoardsBottomSheet(onDismissRequest = onDismiss, hazeState = hazeState) {
        ReadingFontSize(fontSize) {
            Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (stack.size > 1) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_huge_arrow_left), contentDescription = stringResource(R.string.sheet_back_cd))
                    }
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Spacing.s),
                )
            }
            HorizontalDivider(thickness = Dimens.hairline, color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                items(sheetPosts, key = { it.number }) { post ->
                    val postMarks = marks(post)
                    PostItem(
                        post = post,
                        replyCount = index.replyCount(post.number),
                        idTotal = index.idTotal(post.id),
                        isNewStart = false,
                        isGoreWarned = index.isGoreWarned(post.number),
                        bodyBlurred = postMarks.bodyBlurred,
                        isMine = postMarks.isMine,
                        isNg = postMarks.isNg,
                        onToggleNg = onToggleNg,
                        onAnchorClick = onAnchorClick,
                        onLinkClick = onLinkClick,
                        onRepliesClick = onRepliesClick,
                        onIdClick = onIdClick,
                        onImageClick = onImageClick,
                    )
                }
            }
            }
        }
    }
}
