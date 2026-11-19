package jp.miyavi.android.gerbil

import jp.miyavi.android.gerbil.feature.thread.NanoSummaryClient
import jp.miyavi.android.gerbil.feature.thread.ModelDownloader
import jp.miyavi.android.gerbil.core.network.ImgurResolver
import android.content.Context
import jp.miyavi.android.gerbil.core.database.BoardsDatabase
import jp.miyavi.android.gerbil.core.network.HttpClientFactory
import jp.miyavi.android.gerbil.core.network.ImageDownloadTracker
import jp.miyavi.android.gerbil.core.network.PersistentCookieJar
import jp.miyavi.android.gerbil.core.settings.SettingsStore
import jp.miyavi.android.gerbil.data.BoardRepository
import jp.miyavi.android.gerbil.data.LogStorage
import jp.miyavi.android.gerbil.data.ThreadRepository
import jp.miyavi.android.gerbil.feature.thread.SummaryCache
import jp.miyavi.android.gerbil.source.BoardSource
import jp.miyavi.android.gerbil.source.dat.DatSource
import jp.miyavi.android.gerbil.source.shitaraba.ShitarabaSource
import jp.miyavi.android.gerbil.source.shitaraba.ShitarabaUrls
import java.io.File

/** 手動 DI。依存の組み立てはここだけで行う。 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settings = SettingsStore(context)
    val cookieJar = PersistentCookieJar(context)
    val httpClient = HttpClientFactory.create(BuildConfig.VERSION_NAME, cookieJar = cookieJar).newBuilder()
        // Debug 版だけ、架空の掲示板（スクリーンショット用）への通信を端末のファイルで返す。Release 版では何も足さない
        .apply { variantInterceptors(appContext).forEach(::addInterceptor) }
        .build()
    val imageDownloads = ImageDownloadTracker()
    val imageHttpClient = httpClient.newBuilder()
        .addNetworkInterceptor(imageDownloads.interceptor())
        .build()
    /** 端末内 AI のモデルの取得（アプリ全体で持ち、画面を閉じても続ける）。 */
    val modelDownloader = ModelDownloader { NanoSummaryClient() }

    /** 書き込み欄に貼った Imgur のページのリンクを、直リンクに変える。 */
    val imgur = ImgurResolver(httpClient)
    private val database = BoardsDatabase.create(context)
    /** 掲示板の ID（と bbsmenu の URL）から、2ch 互換の取得・書き込みの部品を作る。 */
    private fun sourceOf(siteId: String, menuUrl: String? = null): BoardSource =
        if (ShitarabaUrls.isShitaraba(siteId)) ShitarabaSource(httpClient) else DatSource(httpClient, siteId, menuUrl)
    val boards = BoardRepository(database.boardDao(), settings, ::sourceOf)
    /** 消しても取り直せるもの（画像・要約）の置き場。 */
    val cacheDir: File = context.cacheDir
    /** 開いたスレの本文（dat）の置き場。 */
    val logsDir = File(context.filesDir, "logs")
    val summaries = SummaryCache(File(context.cacheDir, "summaries"))
    val muteVerdicts = SummaryCache(File(context.cacheDir, "mute-verdicts"))
    val threads = ThreadRepository(
        threadDao = database.threadDao(),
        boardDao = database.boardDao(),
        sourceOf = { sourceOf(it) },
        storage = LogStorage(logsDir),
    )
}
