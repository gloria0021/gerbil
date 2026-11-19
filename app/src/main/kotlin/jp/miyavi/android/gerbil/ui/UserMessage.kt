package jp.miyavi.android.gerbil.ui

import androidx.annotation.StringRes
import jp.miyavi.android.gerbil.R
import jp.miyavi.android.gerbil.source.SourceError

/** Snackbar で出す通知。retry があれば「再試行」を付ける。 */
data class UserMessage(val text: UiText, val retry: (() -> Unit)? = null)

fun UserMessage(@StringRes text: Int, retry: (() -> Unit)? = null) = UserMessage(UiText.Res(text), retry)

fun messageFor(error: SourceError, @StringRes what: Int): UiText = UiText.Res(
    when (error) {
        SourceError.NETWORK -> R.string.error_network
        SourceError.RATE_LIMITED -> R.string.error_rate_limited
        SourceError.FORBIDDEN -> R.string.error_forbidden
        SourceError.SERVER -> R.string.error_server
        SourceError.PARSE -> R.string.error_parse
    },
    listOf(UiText.Res(what)),
)
