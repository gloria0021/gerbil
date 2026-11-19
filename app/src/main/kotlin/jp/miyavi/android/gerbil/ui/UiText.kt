package jp.miyavi.android.gerbil.ui

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/** ViewModel から画面へ渡す文言。言語を変えても正しく描けるよう、画面で解決する。 */
sealed interface UiText {
    /** 掲示板・サーバから届いた文など、訳さない文字列。 */
    data class Raw(val text: String) : UiText
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    fun asString(resources: Resources): String {
        fun resolve(args: List<Any>) = args.map { if (it is UiText) it.asString(resources) else it }.toTypedArray()
        return when (this) {
            is Raw -> text
            is Res -> resources.getString(id, *resolve(args))
            is Plural -> resources.getQuantityString(id, count, *resolve(args))
        }
    }
}

@Composable
fun UiText.asString(): String = asString(LocalResources.current)
