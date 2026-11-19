package jp.miyavi.android.gerbil.feature.thread

import androidx.compose.foundation.Image
import androidx.compose.material3.IconToggleButton
import jp.miyavi.android.gerbil.core.designsystem.sheetFieldColor
import jp.miyavi.android.gerbil.ui.shrinkExit
import jp.miyavi.android.gerbil.ui.expandEnter
import jp.miyavi.android.gerbil.ui.BoardsBottomSheet
import jp.miyavi.android.gerbil.source.dat.BodySegmenter
import jp.miyavi.android.gerbil.source.Segment
import jp.miyavi.android.gerbil.ui.asString
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.core.designsystem.Dimens
import jp.miyavi.android.gerbil.core.designsystem.Spacing

/** スレッド書き込み用のモダン Material 3 コンポーザー ModalBottomSheet。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplySheet(
    reply: ReplyUiState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onMailChange: (String) -> Unit,
    onToggleSage: () -> Unit,
    onBodyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancelConfirmation: () -> Unit,
    hazeState: HazeState,
    onOpenImgur: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val confirmation = reply.confirmation
    val isSage = reply.mail.equals("sage", ignoreCase = true)
    var showMetaFields by rememberSaveable { mutableStateOf(false) }
    // 画像（Imgur）の説明とボタンを開いているか
    var showImageHelp by rememberSaveable { mutableStateOf(false) }
    val attachedImages = remember(reply.body) {
        // レス表示と同じ判定（クエリ付き・format= 指定も含む）で画像 URL を拾う
        BodySegmenter.tokenize(reply.body).mapNotNull { (it as? Segment.Link)?.takeIf { l -> l.isImage }?.url }.distinct()
    }

    // 長文を書きやすいよう、書き込みシートだけはタイトルバーの位置まで広げられる
    BoardsBottomSheet(
        coverTopBar = true,
        onDismissRequest = onDismiss,
        hazeState = hazeState,
        sheetState = sheetState,
        // キーボードとナビゲーションバー分の余白は透かした面の内側で取り、透けた帯を作らない
        contentModifier = Modifier
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .verticalScroll(rememberScrollState()),
    ) {
        // トップアクションバー（閉じる + タイトル + ピル型送信ボタン）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_huge_close), contentDescription = stringResource(R.string.common_close))
            }
            Text(
                stringResource(if (confirmation != null) R.string.reply_confirm_title else R.string.reply_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (confirmation == null) {
                Button(
                    onClick = onSubmit,
                    enabled = reply.body.isNotBlank() && !reply.sending,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    if (reply.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.l),
                            strokeWidth = Dimens.scrollbarTickThickness,
                        )
                    } else {
                        Text(stringResource(R.string.reply_submit))
                    }
                }
            }
        }
        HorizontalDivider(thickness = Dimens.hairline, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (confirmation != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = sheetFieldColor(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        confirmation.message.ifEmpty { stringResource(R.string.reply_confirm_required) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Spacing.l),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                ) {
                    TextButton(onClick = onCancelConfirmation, enabled = !reply.sending) {
                        Text(stringResource(R.string.reply_back_to_edit))
                    }
                    Button(
                        onClick = onSubmit,
                        enabled = !reply.sending,
                        shape = CircleShape,
                    ) {
                        if (reply.sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Spacing.l),
                                strokeWidth = Dimens.scrollbarTickThickness,
                            )
                        } else {
                            Text(confirmation.submitLabel)
                        }
                    }
                }
            } else {
                reply.error?.let { err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            err.asString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.m),
                        )
                    }
                }

                // 主役の本文入力エリア（ボーダーレス tonal surface）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = sheetFieldColor(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    TextField(
                        value = reply.body,
                        onValueChange = onBodyChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.reply_body_placeholder)) },
                        // 行数の上限を設けず、書くほど伸ばす（はみ出した分はシートの中でスクロール）
                        minLines = 6,
                        enabled = !reply.sending,
                        colors = borderlessFieldColors(),
                    )
                }

                // 本文中に含まれる画像 URL のサムネイルプレビュー
                if (attachedImages.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(attachedImages, key = { it }) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = stringResource(R.string.reply_attached_image_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(Dimens.imageThumbSize)
                                    .clip(MaterialTheme.shapes.medium),
                            )
                        }
                    }
                }

                // コンパクトなツールバー（sage / 名前・メール展開 / 画像添付）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    FilterChip(
                        selected = isSage,
                        onClick = onToggleSage,
                        enabled = !reply.sending,
                        leadingIcon = if (isSage) {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_huge_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(Spacing.l),
                                )
                            }
                        } else {
                            null
                        },
                        label = { Text(stringResource(R.string.reply_sage)) },
                    )
                    FilterChip(
                        selected = showMetaFields || reply.name.isNotBlank(),
                        onClick = { showMetaFields = !showMetaFields },
                        enabled = !reply.sending,
                        label = {
                            Text(if (reply.name.isNotBlank()) stringResource(R.string.reply_name_value, reply.name) else stringResource(R.string.reply_name_mail))
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    // 画像のアイコン。押すと下に、Imgur の削除についての注意と「Imgur を開く」をアコーディオンで開く
                    IconToggleButton(checked = showImageHelp, onCheckedChange = { showImageHelp = it }, enabled = !reply.sending) {
                        Icon(painterResource(R.drawable.ic_huge_image), contentDescription = stringResource(R.string.reply_image_cd))
                    }
                }
                AnimatedVisibility(visible = showImageHelp, enter = expandEnter(), exit = shrinkExit()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(stringResource(R.string.reply_imgur_title), style = MaterialTheme.typography.titleSmall)
                        // 箇条書きは1行ずつ分けて、項目のあいだを空ける
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            stringResource(R.string.reply_imgur_notice).lines().forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // どのボタンを押せばよいかを、Imgur の画面の切り抜きで示す
                        Image(
                            painterResource(R.drawable.imgur_hint),
                            contentDescription = stringResource(R.string.reply_imgur_hint_cd),
                            modifier = Modifier.fillMaxWidth(IMGUR_HINT_WIDTH_FRACTION).clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.FillWidth,
                        )
                        Button(
                            onClick = onOpenImgur,
                            enabled = !reply.sending,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_huge_image),
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.l),
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.reply_open_imgur))
                        }
                    }
                }

                // 必要なときだけ開く名前・メール欄
                AnimatedVisibility(visible = showMetaFields, enter = expandEnter(), exit = shrinkExit()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = sheetFieldColor(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            TextField(
                                value = reply.name,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.reply_name_label)) },
                                singleLine = true,
                                enabled = !reply.sending,
                                colors = borderlessFieldColors(),
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = sheetFieldColor(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            TextField(
                                value = reply.mail,
                                onValueChange = onMailChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.reply_mail_label)) },
                                singleLine = true,
                                enabled = !reply.sending,
                                colors = borderlessFieldColors(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/** Imgur のヒント画像の幅（シートの幅に対する割合）。左に寄せる。 */
private const val IMGUR_HINT_WIDTH_FRACTION = 0.8f
