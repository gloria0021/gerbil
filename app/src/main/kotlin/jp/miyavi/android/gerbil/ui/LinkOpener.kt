package jp.miyavi.android.gerbil.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import jp.miyavi.android.gerbil.core.settings.LinkOpenMode

/**
 * 本文の URL（スレ以外）を開く関数を返す。
 * [LinkOpenMode.IN_APP] は Partial Custom Tabs（下から出るシート型のアプリ内ブラウザ）。端末のブラウザが
 * シート表示に対応していなければ全画面のアプリ内ブラウザになる。[LinkOpenMode.BROWSER] はブラウザアプリで開く。
 * 開けなければ [onFailure] を呼ぶ。アプリ内ブラウザを開いているあいだは [onInAppOpenChange] に true を渡す
 * （シートの影はブラウザ側が描くので、後ろの画面を暗くして奥行きを出すため）。
 */
@Composable
fun rememberLinkOpener(
    mode: LinkOpenMode,
    onInAppOpenChange: (Boolean) -> Unit = {},
    /** false なら、ほかの画面（写真の選択など）へ移っても閉じない。アップロードのように途中で別の画面を挟むページ用。 */
    closeWhenLeft: Boolean = true,
    onFailure: () -> Unit,
): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    // シート表示は「結果を受け取る起動」でないと許可されない（ブラウザ側の制約）。閉じると結果が返る
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onInAppOpenChange(false) }
    val toolbar = MaterialTheme.colorScheme.surfaceContainer.toArgb()
    val heightPx = with(LocalDensity.current) { (LocalConfiguration.current.screenHeightDp * SHEET_HEIGHT_FRACTION).toInt() * density }.toInt()
    return { url ->
        runCatching {
            when (mode) {
                LinkOpenMode.BROWSER -> uriHandler.openUri(url)
                LinkOpenMode.IN_APP -> {
                    val tab = CustomTabsIntent.Builder()
                        .setInitialActivityHeightPx(heightPx, CustomTabsIntent.ACTIVITY_HEIGHT_ADJUSTABLE)
                        .setToolbarCornerRadiusDp(TOOLBAR_CORNER_DP)
                        .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(toolbar).build())
                        .setShowTitle(true)
                        .setUrlBarHidingEnabled(true)
                        .build()
                    onInAppOpenChange(true)
                    val intent = tab.intent.setData(Uri.parse(url))
                    if (closeWhenLeft) intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    launcher.launch(intent)
                }
            }
        }.onFailure {
            onInAppOpenChange(false)
            if (it is ActivityNotFoundException || it is IllegalArgumentException) onFailure() else throw it
        }
    }
}

/** シートの最初の高さ（画面の高さに対する割合）。上へ引き上げれば全画面にできる。 */
private const val SHEET_HEIGHT_FRACTION = 0.85f

/** シート上端の角丸（Custom Tabs が受け付ける上限は 16dp）。 */
private const val TOOLBAR_CORNER_DP = 16
