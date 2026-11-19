package jp.miyavi.android.gerbil.feature.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import jp.miyavi.android.gerbil.BuildConfig
import jp.miyavi.android.gerbil.core.settings.Settings

/** フィードバックの Google フォーム。端末情報の欄（[DEVICE_ENTRY]）に [deviceInfo] を入れた状態で開く。 */
private const val FORM_URL = "https://docs.google.com/forms/d/e/1FAIpQLSePt9T6wp-DhSvaJT81a-UibuM70mzQNsgGwtghrbl6hI8t4g/viewform"
private const val DEVICE_ENTRY = "entry.692300470"

internal fun feedbackUrl(info: String): String =
    Uri.parse(FORM_URL).buildUpon()
        .appendQueryParameter("usp", "pp_url")
        .appendQueryParameter(DEVICE_ENTRY, info)
        .build().toString()

/**
 * 不具合の切り分けに使う端末の情報（権限なしで取れるものだけ）。閲覧先（登録した掲示板・スレ）は入れない。
 * 短文の欄にも収まるよう1行にする。
 */
internal fun deviceInfo(context: Context, settings: Settings, aiSupported: Boolean): String {
    val config = context.resources.configuration
    val metrics = context.resources.displayMetrics
    return listOf(
        "Gerbil ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "Locale ${config.locales[0].toLanguageTag()}",
        "Screen ${config.screenWidthDp}x${config.screenHeightDp}dp ${metrics.density}x font ${config.fontScale}",
        "AI ${if (aiSupported) "supported" else "unsupported"}",
        "Theme ${settings.themeMode}/${settings.colorTheme} ${settings.hazeMode} ${settings.postStyle} ${settings.font}",
    ).joinToString(" / ")
}
