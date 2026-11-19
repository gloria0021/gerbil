package jp.miyavi.android.gerbil.core.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }
/** テーマ。並びは選ぶ画面の並び（Suna が先頭で初期値）。保存は名前なので、並べ替えても前の設定は読める。 */
enum class ColorTheme { SUNA, CANYON, FOG, OLIVE, OBSIDIAN, DYNAMIC, CUSTOM }
enum class AppFont { SYSTEM, LINE_SEED_JP }

/** レス表示の見た目。モダン = 本文を薄い角丸の面に載せる、シンプル = 面なし。 */
enum class PostStyle { MODERN, SIMPLE }

/** 本文の URL（スレ以外）の開き方。IN_APP = 下から出るアプリ内ブラウザ、BROWSER = ブラウザアプリ。 */
enum class LinkOpenMode { IN_APP, BROWSER }
enum class ThreadSort { RANK, MOMENTUM, NEWEST }

/** バーやシートの透かし方。BLUR = ぼかし、GLASS = 屈折するガラス。 */
enum class HazeMode { BLUR, GLASS }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.SUNA,
    val customColorArgb: Int? = null,
    val font: AppFont = DEFAULT_FONT,
    val boardFontSizeSp: Int = DEFAULT_FONT_SIZE,
    val threadFontSizeSp: Int = DEFAULT_FONT_SIZE,
    val postStyle: PostStyle = PostStyle.MODERN,
    val linkOpenMode: LinkOpenMode = LinkOpenMode.IN_APP,
    /** スレを開いたら、裏で要約を作り始める。 */
    val autoSummary: Boolean = true,
    /** スレを開いたら、ゴミレスの AI ミュートを始める。 */
    /** AI ミュート（実験中）を使うか。OFF のあいだは、レス表示のメニューにも出さず、自動 AI ミュートも働かない。 */
    val aiMute: Boolean = false,
    val autoMute: Boolean = false,
    /** AI ミュートの条件（利用者が書いたもの）。null なら初期値。 */
    val muteConditions: String? = null,
    /** 要約の分割要約を新しい順に並べるか（false = 古い順。並べ替えのボタンで変えて覚える）。 */
    val summaryNewestFirst: Boolean = false,
    val glassOpacityLevel: Int = DEFAULT_GLASS_OPACITY,
    val glassRefractionLevel: Int = DEFAULT_GLASS_REFRACTION,
    val hazeMode: HazeMode = DEFAULT_HAZE_MODE,
    val blurStrengthLevel: Int = DEFAULT_BLUR_STRENGTH,
    val blurOpacityLevel: Int = DEFAULT_BLUR_OPACITY,
) {
    companion object {
        const val MIN_FONT_SIZE = 14
        const val MAX_FONT_SIZE = 22
        const val DEFAULT_FONT_SIZE = 16
        const val MIN_GLASS_LEVEL = 0
        const val MAX_GLASS_LEVEL = 10
        const val DEFAULT_GLASS_OPACITY = 2
        const val DEFAULT_GLASS_REFRACTION = 6
        const val DEFAULT_BLUR_STRENGTH = 6
        const val DEFAULT_BLUR_OPACITY = 5
        val DEFAULT_FONT = AppFont.LINE_SEED_JP
        val DEFAULT_HAZE_MODE = HazeMode.GLASS
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")
/** 閲覧の状態（最後に開いた板・スレごとの AI ミュート）。バックアップに含める settings とは分けて、端末の外へ出さない。 */
private val Context.stateStore: DataStore<Preferences> by preferencesDataStore(
    "state",
    produceMigrations = { context -> listOf(LastBoardMigration(context.dataStore)) },
)

/** 以前は settings に入れていた最後の板を、最初に読むときに state へ移す（書き込めてから settings から消す）。 */
internal class LastBoardMigration(private val settings: DataStore<Preferences>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) =
        settings.data.first()[SettingsStore.LAST_BOARD] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        if (currentData[SettingsStore.LAST_BOARD] != null) return currentData
        val old = settings.data.first()[SettingsStore.LAST_BOARD] ?: return currentData
        return currentData.toMutablePreferences().apply { this[SettingsStore.LAST_BOARD] = old }
    }

    override suspend fun cleanUp() {
        settings.edit { it.remove(SettingsStore.LAST_BOARD) }
    }
}

class SettingsStore private constructor(
    private val store: DataStore<Preferences>,
    private val stateStore: DataStore<Preferences> = store,
) {
    constructor(context: Context) : this(context.applicationContext.dataStore, context.applicationContext.stateStore)

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            themeMode = legacyThemeMode(p[THEME]),
            colorTheme = p[COLOR_THEME].toEnum(if (p[DYNAMIC] == false) ColorTheme.FOG else ColorTheme.SUNA),
            customColorArgb = p[CUSTOM_COLOR],
            font = p[APP_FONT].toEnum(Settings.DEFAULT_FONT),
            boardFontSizeSp = (p[BOARD_FONT] ?: Settings.DEFAULT_FONT_SIZE)
                .coerceIn(Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE),
            threadFontSizeSp = (p[THREAD_FONT] ?: p[FONT] ?: Settings.DEFAULT_FONT_SIZE)
                .coerceIn(Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE),
            postStyle = p[POST_STYLE].toEnum(PostStyle.MODERN),
            linkOpenMode = p[LINK_OPEN].toEnum(LinkOpenMode.IN_APP),
            autoSummary = p[AUTO_SUMMARY] ?: true,
            aiMute = p[AI_MUTE] ?: false,
            autoMute = p[AUTO_MUTE] ?: false,
            muteConditions = p[MUTE_CONDITIONS],
            summaryNewestFirst = p[SUMMARY_NEWEST_FIRST] ?: false,
            glassOpacityLevel = (p[GLASS_OPACITY] ?: Settings.DEFAULT_GLASS_OPACITY)
                .coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL),
            glassRefractionLevel = (p[GLASS_REFRACTION] ?: Settings.DEFAULT_GLASS_REFRACTION)
                .coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL),
            hazeMode = p[HAZE_MODE].toEnum(Settings.DEFAULT_HAZE_MODE),
            blurStrengthLevel = (p[BLUR_STRENGTH] ?: Settings.DEFAULT_BLUR_STRENGTH)
                .coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL),
            blurOpacityLevel = (p[BLUR_OPACITY] ?: Settings.DEFAULT_BLUR_OPACITY)
                .coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL),
        )
    }

    val lastBoardId: Flow<String?> = stateStore.data.map { it[LAST_BOARD] }
    val lastTab: Flow<Int> = store.data.map { it[LAST_TAB] ?: 0 }

    suspend fun menuFetchedAt(): Long = store.data.first()[MENU_FETCHED] ?: 0

    suspend fun setThemeMode(v: ThemeMode) = store.edit { it[THEME] = v.name }
    suspend fun setColorTheme(v: ColorTheme) = store.edit { it[COLOR_THEME] = v.name }
    suspend fun setCustomColor(v: Int) = store.edit {
        it[CUSTOM_COLOR] = v or (0xFF shl 24)
        it[COLOR_THEME] = ColorTheme.CUSTOM.name
    }
    suspend fun setFont(v: AppFont) = store.edit { it[APP_FONT] = v.name }
    suspend fun setBoardFontSize(v: Int) =
        store.edit { it[BOARD_FONT] = v.coerceIn(Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE) }
    suspend fun setThreadFontSize(v: Int) =
        store.edit { it[THREAD_FONT] = v.coerceIn(Settings.MIN_FONT_SIZE, Settings.MAX_FONT_SIZE) }
    suspend fun setPostStyle(v: PostStyle) = store.edit { it[POST_STYLE] = v.name }
    suspend fun setLinkOpenMode(v: LinkOpenMode) = store.edit { it[LINK_OPEN] = v.name }
    suspend fun setAutoSummary(v: Boolean) = store.edit { it[AUTO_SUMMARY] = v }
    suspend fun setAiMute(v: Boolean) = store.edit { it[AI_MUTE] = v }
    suspend fun setAutoMute(v: Boolean) = store.edit { it[AUTO_MUTE] = v }
    suspend fun setSummaryNewestFirst(v: Boolean) = store.edit { it[SUMMARY_NEWEST_FIRST] = v }
    /** null か空なら初期値に戻す。 */
    suspend fun setMuteConditions(v: String?) = store.edit { if (v.isNullOrBlank()) it.remove(MUTE_CONDITIONS) else it[MUTE_CONDITIONS] = v }
    suspend fun setGlassOpacityLevel(v: Int) =
        store.edit { it[GLASS_OPACITY] = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) }
    suspend fun setGlassRefractionLevel(v: Int) =
        store.edit { it[GLASS_REFRACTION] = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) }
    suspend fun setHazeMode(v: HazeMode) = store.edit { it[HAZE_MODE] = v.name }
    suspend fun setBlurStrengthLevel(v: Int) =
        store.edit { it[BLUR_STRENGTH] = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) }
    suspend fun setBlurOpacityLevel(v: Int) =
        store.edit { it[BLUR_OPACITY] = v.coerceIn(Settings.MIN_GLASS_LEVEL, Settings.MAX_GLASS_LEVEL) }
    suspend fun setLastBoard(id: String) = stateStore.edit { it[LAST_BOARD] = id }
    suspend fun setLastTab(tab: Int) = store.edit { it[LAST_TAB] = tab }
    suspend fun setMenuFetchedAt(epochMs: Long) = store.edit { it[MENU_FETCHED] = epochMs }

    private val threadMuteLock = Mutex()

    suspend fun threadMute(boardId: String, threadKey: Long): Boolean? = threadMuteLock.withLock {
        try {
            stateStore.data.first()[threadMuteKey(boardId, threadKey)]
        } catch (_: IOException) {
            null
        }
    }

    suspend fun setThreadMute(boardId: String, threadKey: Long, enabled: Boolean) {
        threadMuteLock.withLock {
            try {
                stateStore.edit { it[threadMuteKey(boardId, threadKey)] = enabled }
            } catch (_: IOException) {
                // 保存できなくても閲覧中の選択は維持する。
            }
        }
    }

    private fun threadMuteKey(boardId: String, threadKey: Long) =
        booleanPreferencesKey("thread_mute:$boardId:$threadKey")

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    internal companion object {
        fun fromDataStore(store: DataStore<Preferences>) = SettingsStore(store)
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val CUSTOM_COLOR = intPreferencesKey("custom_color")
        val APP_FONT = stringPreferencesKey("app_font")
        val FONT = intPreferencesKey("body_font_size")
        val BOARD_FONT = intPreferencesKey("board_font_size")
        val THREAD_FONT = intPreferencesKey("thread_font_size")
        val POST_STYLE = stringPreferencesKey("post_style")
        val LINK_OPEN = stringPreferencesKey("link_open_mode")
        val AUTO_SUMMARY = booleanPreferencesKey("auto_summary")
        val AI_MUTE = booleanPreferencesKey("ai_mute")
        val AUTO_MUTE = booleanPreferencesKey("auto_mute")
        val MUTE_CONDITIONS = stringPreferencesKey("mute_conditions")
        val SUMMARY_NEWEST_FIRST = booleanPreferencesKey("summary_newest_first")
        val LAST_BOARD = stringPreferencesKey("last_board")
        val LAST_TAB = intPreferencesKey("last_tab")
        val MENU_FETCHED = longPreferencesKey("menu_fetched_at")
        val GLASS_OPACITY = intPreferencesKey("glass_opacity_level")
        val GLASS_REFRACTION = intPreferencesKey("glass_blur_level")
        val HAZE_MODE = stringPreferencesKey("haze_mode")
        val BLUR_STRENGTH = intPreferencesKey("blur_strength_level")
        val BLUR_OPACITY = intPreferencesKey("blur_opacity_level")
    }

}

private fun legacyThemeMode(value: String?): ThemeMode = when (value) {
    "LIGHT", "LIGHT_MUTED" -> ThemeMode.LIGHT
    "DARK", "DARK_MUTED" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}
