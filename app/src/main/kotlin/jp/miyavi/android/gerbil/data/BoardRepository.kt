package jp.miyavi.android.gerbil.data

import jp.miyavi.android.gerbil.source.ParsedUrl
import jp.miyavi.android.gerbil.core.database.BoardDao
import jp.miyavi.android.gerbil.core.database.BoardEntity
import jp.miyavi.android.gerbil.core.database.MenuEntryEntity
import jp.miyavi.android.gerbil.core.database.MenuRow
import jp.miyavi.android.gerbil.core.settings.SettingsStore
import jp.miyavi.android.gerbil.source.BoardRef
import jp.miyavi.android.gerbil.source.BoardSource
import jp.miyavi.android.gerbil.source.FetchResult
import jp.miyavi.android.gerbil.source.SourceError
import jp.miyavi.android.gerbil.source.BoardInfo
import jp.miyavi.android.gerbil.source.CategoryWithBoards
import jp.miyavi.android.gerbil.source.ThreadRef
import jp.miyavi.android.gerbil.source.dat.DatUrls
import jp.miyavi.android.gerbil.source.shitaraba.ShitarabaUrls
import jp.miyavi.android.gerbil.core.database.SiteEntity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.flow.Flow

/** 掲示板の登録の結果。 */
enum class SiteAddResult { ADDED, INVALID_URL, DUPLICATE, FETCH_FAILED, EMPTY, UNSUPPORTED }

/** 登録の結果と、スレの URL で登録したときに開くスレ。 */
data class SiteAdded(val result: SiteAddResult, val thread: ThreadRef? = null)

/**
 * 掲示板と板一覧。利用者が登録した掲示板（bbsmenu の URL か板の URL）ごとに板一覧を取得する（spec 003）。
 * 本体には特定の掲示板を持たない。登録がなければ板一覧は空。24 時間ごとに起動時に更新する。
 * 同じ掲示板・同じ boardKey の host が変わったら上書きし、移転に追従する。
 */
class BoardRepository(
    private val dao: BoardDao,
    private val settings: SettingsStore,
    /** 掲示板の ID と bbsmenu の URL から、取得の部品を作る。 */
    private val sourceOf: (siteId: String, menuUrl: String?) -> BoardSource,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val boards: Flow<List<BoardEntity>> = dao.observeAll()
    val menu: Flow<List<MenuRow>> = dao.observeMenu()
    val sites: Flow<List<SiteEntity>> = dao.observeSites()

    fun observe(id: String): Flow<BoardEntity?> = dao.observe(id)

    suspend fun get(id: String): BoardEntity? = dao.get(id)

    suspend fun refreshIfStale(): SourceError? {
        val stale = now() - settings.menuFetchedAt() >= STALE_AFTER_MS
        return if (dao.count() == 0 || stale) refresh() else null
    }

    /**
     * 登録した掲示板の板一覧を取り直す。どれかの取得に失敗したら何も書き換えずに、その理由を返す
     * （一部だけ消えて見えるのを防ぐ）。板の URL で登録した掲示板は取得せず、その板を並べる。
     */
    suspend fun refresh(): SourceError? {
        val sites = dao.getSites()
        val existing = dao.getAll().associateBy { it.id }
        val fresh = mutableListOf<BoardEntity>()
        val entries = mutableListOf<MenuEntryEntity>()
        sites.forEachIndexed { si, site ->
            // 板の URL で足した掲示板と、全体の板一覧がないしたらばは、足した板だけを並べる
            val categories = if (site.menuUrl == null || ShitarabaUrls.isShitaraba(site.id)) {
                listOf(CategoryWithBoards(site.label, dao.getBySource(site.id).map { BoardInfo(it.name, it.host, it.boardKey) }))
                    .filter { it.boards.isNotEmpty() }
            } else {
                when (val result = sourceOf(site.id, site.menuUrl).fetchBoardMenu()) {
                    is FetchResult.Success -> result.value
                    FetchResult.NotModified -> return null
                    is FetchResult.Failed -> return result.error
                }
            }
            categories.forEachIndexed { ci, category ->
                val categoryOrder = si * SITE_STRIDE + ci
                category.boards.forEachIndexed { bi, info ->
                    val id = BoardRef.boardId(site.id, info.boardKey)
                    val old = existing[id]
                    fresh += BoardEntity(
                        id = id,
                        source = site.id,
                        boardKey = info.boardKey,
                        host = info.host,
                        name = info.name,
                        categoryName = category.name,
                        categoryOrder = categoryOrder,
                        boardOrder = bi,
                        isFavorite = old?.isFavorite ?: false,
                        favoriteOrder = old?.favoriteOrder,
                    )
                    entries += MenuEntryEntity(categoryOrder, bi, category.name, id)
                }
            }
        }
        val unique = fresh.distinctBy { it.id }
        dao.upsertAll(unique)
        dao.replaceMenu(entries)
        val freshIds = unique.mapTo(HashSet()) { it.id }
        dao.deleteUnfavorited(existing.keys.filter { it !in freshIds })
        settings.setMenuFetchedAt(now())
        return null
    }

    /**
     * 掲示板を登録する。スレや板の URL を入れても、まずそのドメインの bbsmenu を探して掲示板ごと登録する。
     * - スレの URL: 掲示板を登録して、そのスレを返す（呼び出し側で開く）。bbsmenu がなければ、その板だけを足す
     * - 板の URL: 掲示板を登録する。bbsmenu がなければ、その板だけを持つ掲示板にする
     * - それ以外（bbsmenu の URL、サイトの URL、ドメインだけ）: 入力そのものと、2ch 系でよくある bbsmenu の置き場所を順に試す
     * [label] が空なら、ドメイン（板だけなら板キー）を仮のラベルにする。
     */
    suspend fun addSite(label: String, url: String): SiteAdded {
        val input = url.trim().let { if ("://" in it) it else "https://$it" }
        val http = input.toHttpUrlOrNull() ?: return SiteAdded(SiteAddResult.INVALID_URL)
        // したらばは全体の板一覧がないので、板の URL・スレの URL で足した板を「したらば」という1つの掲示板にまとめる（spec 006）
        if (ShitarabaUrls.isShitaraba(http.host)) return addShitaraba(label, input)
        val thread = DatUrls.parseThreadUrl(input)
        val board = thread?.let { it.host to it.boardKey } ?: DatUrls.parseBoardUrl(input)
        if (board != null) {
            val (host, key) = board
            // すでに登録した掲示板の板なら、登録はせずにスレだけ開く
            val known = dao.findByHostKey(host, key)
            val siteId = known?.source ?: registerMenu(menuCandidates(null, host), label).siteId
            val ref = when {
                known != null -> BoardRef(known.source, known.host, known.boardKey)
                // URL のホストが板のサーバーと違う形（スマホ向けのページ等）でも、見つけた板一覧の中から板キーで探す
                siteId != null -> (dao.findByHostKey(host, key) ?: dao.findBySourceKey(siteId, key))?.let { BoardRef(it.source, it.host, it.boardKey) }
                    ?: BoardRef(siteId, host, key).also { ensure(it) }
                else -> {
                    // bbsmenu が見つからなければ、その板だけを持つ掲示板にする
                    val id = "$host/$key"
                    if (dao.getSite(id) != null && thread == null) return SiteAdded(SiteAddResult.DUPLICATE)
                    val name = label.trim().ifEmpty { key }
                    if (dao.getSite(id) == null) dao.upsertSite(SiteEntity(id, name, input, null, nextOrder()))
                    BoardRef(id, host, key).also { ensure(it, name); refresh() }
                }
            }
            return SiteAdded(SiteAddResult.ADDED, thread?.let { ThreadRef(ref, it.threadKey) })
        }
        val found = registerMenu(menuCandidates(input.takeIf { http.encodedPath.length > 1 }, http.host), label)
        return SiteAdded(found.result)
    }

    private suspend fun addShitaraba(label: String, input: String): SiteAdded {
        val thread = ShitarabaUrls.parseThreadUrl(input)
        val key = thread?.boardKey ?: ShitarabaUrls.parseBoardUrl(input)?.second ?: return SiteAdded(SiteAddResult.INVALID_URL)
        val siteId = ShitarabaUrls.HOST
        if (dao.getSite(siteId) == null) dao.upsertSite(SiteEntity(siteId, SHITARABA_LABEL, "https://$siteId/", null, nextOrder()))
        val ref = BoardRef(siteId, siteId, key)
        val known = dao.get(ref.id) != null
        if (known && thread == null) return SiteAdded(SiteAddResult.DUPLICATE)
        ensure(ref, label.trim().ifEmpty { key })
        refresh()
        return SiteAdded(SiteAddResult.ADDED, thread?.let { ThreadRef(ref, it.threadKey) })
    }

    private data class MenuFound(val result: SiteAddResult, val siteId: String?)

    /** 候補の URL を順に試し、最初に板が読めた bbsmenu で掲示板を登録する。すでに登録していればその掲示板を返す。 */
    private suspend fun registerMenu(candidates: List<String>, label: String): MenuFound {
        var sawEmpty = false
        for (candidate in candidates) {
            val host = candidate.toHttpUrlOrNull()?.host ?: continue
            if (dao.getSite(host) != null) return MenuFound(SiteAddResult.DUPLICATE, host)
            when (val result = sourceOf(host, candidate).fetchBoardMenu()) {
                is FetchResult.Success -> if (result.value.isEmpty()) sawEmpty = true else {
                    dao.upsertSite(SiteEntity(host, label.trim().ifEmpty { domainOf(host) }, candidate, candidate, nextOrder()))
                    refresh()
                    return MenuFound(SiteAddResult.ADDED, host)
                }
                else -> Unit
            }
        }
        return MenuFound(if (sawEmpty) SiteAddResult.EMPTY else SiteAddResult.FETCH_FAILED, null)
    }

    private suspend fun nextOrder() = (dao.getSites().maxOfOrNull { it.sortOrder } ?: -1) + 1

    /** bbsmenu を探す候補。入力にパスがあればそれを先に、続けて 2ch 系でよくある置き場所（menu.ドメイン・ホストとドメインの直下）を試す。 */
    private fun menuCandidates(input: String?, host: String): List<String> {
        val domain = domainOf(host)
        return buildList {
            if (input != null) add(input)
            for (base in listOf("https://menu.$domain", "https://$host", "https://$domain")) {
                add("$base/bbsmenu.json")
                add("$base/bbsmenu.html")
            }
        }.distinct()
    }

    private fun domainOf(host: String) = host.split('.').takeLast(2).joinToString(".")

    suspend fun renameSite(id: String, label: String) {
        if (label.isNotBlank()) dao.renameSite(id, label.trim())
    }

    /** 掲示板を消す。その掲示板の板と板一覧も消す（保存したスレのログは残す）。 */
    suspend fun deleteSite(id: String) {
        dao.deleteSite(id)
        dao.deleteBySource(id)
        refresh()
    }

    /**
     * 本文のリンクが 2ch 互換のスレ（前スレ・関連スレ等）なら、その板を登録して返す。スレでなければ null（ブラウザで開く）。
     * 登録した掲示板の板なら、その掲示板のものとして開く。知らない掲示板なら、ホストを掲示板の ID にして開く。
     */
    suspend fun resolveThreadLink(url: String): ParsedUrl.Thread? {
        val parsed = ShitarabaUrls.parseThreadUrl(url) ?: DatUrls.parseThreadUrl(url) ?: return null
        val known = dao.findByHostKey(parsed.host, parsed.boardKey)
        val siteId = known?.source ?: siteForHost(parsed.host) ?: parsed.host
        val ref = BoardRef(siteId, parsed.host, parsed.boardKey)
        ensure(ref)
        return ParsedUrl.Thread(ThreadRef(ref, parsed.threadKey), parsed.resNumber)
    }

    /** 同じドメイン（末尾の2つ）の板を持つ掲示板。板がサーバを移っても、同じ掲示板として扱う。 */
    private suspend fun siteForHost(host: String): String? {
        val domain = host.split('.').takeLast(2).joinToString(".")
        return dao.getAll().firstOrNull { it.host == host || it.host.endsWith(".$domain") || it.host == domain }?.source
    }

    /** 一覧にない板（URL から開いた場合など）を登録する。 */
    suspend fun ensure(ref: BoardRef, name: String = ref.boardKey) {
        val old = dao.get(ref.id)
        if (old == null) {
            dao.upsertAll(listOf(BoardEntity(ref.id, ref.source, ref.boardKey, ref.host, name, OTHER_CATEGORY, Int.MAX_VALUE, 0)))
        } else if (old.host != ref.host) {
            dao.upsertAll(listOf(old.copy(host = ref.host)))
        }
    }

    /** スターを付け外しする。favoriteOrder は「最後に開いた（スターを付けた）時刻」の秒で、大きいほど前に並ぶ。 */
    suspend fun setFavorite(id: String, favorite: Boolean) =
        dao.setFavorite(id, favorite, if (favorite) nowSeconds() else null)

    /** 板を開いたとき。スター付きなら先頭に来るよう並び順を更新する。 */
    suspend fun markOpened(id: String) = dao.touchFavorite(id, nowSeconds())

    private fun nowSeconds(): Int = (now() / 1000).toInt()

    companion object {
        /** 一覧にない板の保存用カテゴリ名。保存値なので訳さない（表示時に board_category_other へ置き換える）。 */
        const val OTHER_CATEGORY = "その他"

        const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

        /** したらばの掲示板の仮のラベル（掲示板の名前。訳さない）。 */
        private const val SHITARABA_LABEL = "したらば"

        /** 掲示板ごとにカテゴリの並び順をずらす幅（1つの掲示板のカテゴリはこれより少ない前提）。 */
        private const val SITE_STRIDE = 10_000
    }
}
