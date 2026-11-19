package jp.miyavi.android.gerbil

import android.content.Context
import okhttp3.Interceptor

/** Release 版では通信に何も足さない（架空の掲示板は Debug 版だけ。src/debug の同名ファイルを参照）。 */
internal fun variantInterceptors(context: Context): List<Interceptor> = emptyList()

/** Release 版では何もしない（架空の掲示板は Debug 版だけ）。 */
@Suppress("UNUSED_PARAMETER")
internal suspend fun registerDemoBoard(container: AppContainer) = Unit
