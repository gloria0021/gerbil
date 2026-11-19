package jp.miyavi.android.gerbil

import jp.miyavi.android.gerbil.core.settings.LinkOpenMode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.gerbil.core.settings.HazeMode
import jp.miyavi.android.gerbil.core.settings.ColorTheme
import jp.miyavi.android.gerbil.core.settings.AppFont
import jp.miyavi.android.gerbil.core.settings.PostStyle
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.core.settings.ThemeMode
import jp.miyavi.android.gerbil.core.database.SiteEntity
import jp.miyavi.android.gerbil.data.SiteAddResult
import jp.miyavi.android.gerbil.source.ThreadRef
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** アプリ全体の状態（設定）。Activity 単位で1つ。板一覧はスレ選択の一覧タブが持つ。 */
class AppViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * この端末で AI（要約・質問・AI ミュート）が使えるか。起動のたびに確かめ、非対応とはっきり分かったときだけ false にして入口を隠す。
     * 確かめる前と、確かめられなかったときは true（入口を出す）。
     */
    val aiSupported = kotlinx.coroutines.flow.MutableStateFlow(true)
    /** 起動時の AI の確認が終わったか。AI チャットの入口は、終わって使えると分かってから出す（非対応の端末で一瞬出て消えないように）。 */
    val aiChecked = kotlinx.coroutines.flow.MutableStateFlow(false)

    init {
        // Debug 版だけ、架空の掲示板のデータがあれば登録する（Release 版では何もしない）
        viewModelScope.launch { registerDemoBoard(container) }
        viewModelScope.launch {
            val client = jp.miyavi.android.gerbil.feature.thread.NanoSummaryClient()
            try {
                aiSupported.value = !client.deviceUnsupported()
            } finally {
                aiChecked.value = true
                client.close()
            }
        }
    }

    /** 登録した掲示板（設定の「掲示板」）。 */
    val sites: StateFlow<List<SiteEntity>> = container.boards.sites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addSite(label: String, url: String, onResult: (SiteAddResult) -> Unit, onOpenThread: (ThreadRef) -> Unit) = viewModelScope.launch {
        val added = container.boards.addSite(label, url)
        onResult(added.result)
        added.thread?.let(onOpenThread)
    }
    /** 設定の「キャッシュを削除」に出す容量（スレの本文・画像・要約）。開いたときに測る。 */
    val cacheBytes = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    fun measureCache() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        cacheBytes.value = sizeOf(container.cacheDir) + sizeOf(container.logsDir)
    }

    /** スレの本文・画像・要約を消す（閲覧中の一覧・追尾・読んだ位置は残し、見るときに取り直す）。 */
    fun clearCache() = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        coil3.SingletonImageLoader.get(container.appContext).let { it.memoryCache?.clear(); it.diskCache?.clear() }
        container.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        container.logsDir.deleteRecursively()
        measureCache()
    }

    private fun sizeOf(dir: java.io.File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun renameSite(id: String, label: String) = viewModelScope.launch { container.boards.renameSite(id, label) }
    fun deleteSite(id: String) = viewModelScope.launch { container.boards.deleteSite(id) }

    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { container.settings.setThemeMode(v) }
    fun setColorTheme(v: ColorTheme) = viewModelScope.launch { container.settings.setColorTheme(v) }
    fun setCustomColor(v: Int) = viewModelScope.launch { container.settings.setCustomColor(v) }
    fun setFont(v: AppFont) = viewModelScope.launch { container.settings.setFont(v) }
    fun setBoardFontSize(v: Int) = viewModelScope.launch { container.settings.setBoardFontSize(v) }
    fun setThreadFontSize(v: Int) = viewModelScope.launch { container.settings.setThreadFontSize(v) }
    fun setPostStyle(v: PostStyle) = viewModelScope.launch { container.settings.setPostStyle(v) }
    fun setLinkOpenMode(v: LinkOpenMode) = viewModelScope.launch { container.settings.setLinkOpenMode(v) }
    fun setAutoSummary(v: Boolean) = viewModelScope.launch { container.settings.setAutoSummary(v) }
    fun setAiMute(v: Boolean) = viewModelScope.launch { container.settings.setAiMute(v) }
    fun setAutoMute(v: Boolean) = viewModelScope.launch { container.settings.setAutoMute(v) }
    fun setMuteConditions(v: String?) = viewModelScope.launch { container.settings.setMuteConditions(v) }
    fun setSummaryNewestFirst(v: Boolean) = viewModelScope.launch { container.settings.setSummaryNewestFirst(v) }
    fun setGlassOpacityLevel(v: Int) = viewModelScope.launch { container.settings.setGlassOpacityLevel(v) }
    fun setGlassRefractionLevel(v: Int) = viewModelScope.launch { container.settings.setGlassRefractionLevel(v) }
    fun setHazeMode(v: HazeMode) = viewModelScope.launch { container.settings.setHazeMode(v) }
    fun setBlurStrengthLevel(v: Int) = viewModelScope.launch { container.settings.setBlurStrengthLevel(v) }
    fun setBlurOpacityLevel(v: Int) = viewModelScope.launch { container.settings.setBlurOpacityLevel(v) }
}
