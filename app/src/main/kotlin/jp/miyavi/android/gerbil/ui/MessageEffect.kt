package jp.miyavi.android.gerbil.ui

import jp.miyavi.android.gerbil.R
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow

/** ViewModel からの通知を Snackbar で出す。再試行があれば操作を付ける。 */
@Composable
fun MessageEffect(messages: Flow<UserMessage>, host: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(messages, host) {
        messages.collect { m ->
            val result = host.showSnackbar(
                message = m.text.asString(resources),
                actionLabel = if (m.retry != null) resources.getString(R.string.common_retry) else null,
                duration = if (m.retry != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.retry?.invoke()
        }
    }
}
