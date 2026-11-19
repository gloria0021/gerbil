package jp.miyavi.android.gerbil.ui

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import jp.miyavi.android.gerbil.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import jp.miyavi.android.gerbil.core.designsystem.Spacing
import jp.miyavi.android.gerbil.core.network.ImageDownloadProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val LocalImageDownloads = staticCompositionLocalOf<StateFlow<Map<String, ImageDownloadProgress>>> {
    MutableStateFlow(emptyMap())
}

/** [url] の受信状況だけを監視する。ほかの画像の進捗が変わっても再構成しない。 */
@Composable
fun imageProgress(url: String): ImageDownloadProgress? {
    val downloads = LocalImageDownloads.current
    val flow = remember(downloads, url) { downloads.map { it[url] }.distinctUntilChanged() }
    // 最初の値は収集を始めた直後に届く（それまでは「読み込み中…」を出す）
    return flow.collectAsState(initial = null).value
}

/** Material 3 の進捗インジケータで画像の受信バイト数・割合を表示する。 */
@Composable
fun ImageLoadingBadge(
    progress: ImageDownloadProgress?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetFraction = progress?.fraction
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction ?: 0f,
        label = "imageDownloadProgress",
    )
    val label = when {
        progress == null -> stringResource(R.string.image_loading)
        progress.totalBytes > 0L -> progress.formatLabel()
        else -> stringResource(R.string.image_receiving, progress.formatLabel())
    }

    if (compact) {
        Column(
            modifier = modifier.padding(Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (targetFraction != null) {
                CircularProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier.size(Spacing.xl),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(Spacing.xl))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    } else {
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (targetFraction != null) {
                    CircularProgressIndicator(
                        progress = { animatedFraction },
                        modifier = Modifier.size(Spacing.xl),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(Spacing.xl))
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = stringResource(R.string.image_downloading),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
