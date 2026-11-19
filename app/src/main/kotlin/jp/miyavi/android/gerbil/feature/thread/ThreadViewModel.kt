package jp.miyavi.android.gerbil.feature.thread

import jp.miyavi.android.gerbil.data.NgKey
import jp.miyavi.android.gerbil.core.network.ImgurResolver
import jp.miyavi.android.gerbil.core.network.ImgurLinks
import jp.miyavi.android.gerbil.core.settings.SettingsStore
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import jp.miyavi.android.gerbil.data.BoardRepository
import jp.miyavi.android.gerbil.data.ThreadRepository
import jp.miyavi.android.gerbil.source.PostFailure
import jp.miyavi.android.gerbil.ui.UiText
import jp.miyavi.android.gerbil.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import jp.miyavi.android.gerbil.core.database.ThreadLogEntity
import jp.miyavi.android.gerbil.core.database.ThreadState
import jp.miyavi.android.gerbil.data.LoadedThread
import jp.miyavi.android.gerbil.data.RefreshOutcome
import jp.miyavi.android.gerbil.source.Post
import jp.miyavi.android.gerbil.source.PostReplyResult
import jp.miyavi.android.gerbil.source.SourceError
import jp.miyavi.android.gerbil.ui.UserMessage
import jp.miyavi.android.gerbil.ui.messageFor
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.UUID

data class ReplyConfirmationUi(
    val message: String,
    val hiddenFields: Map<String, String>,
    val submitLabel: String,
)

data class ReplyUiState(
    val open: Boolean = false,
    val name: String = "",
    val mail: String = "",
    val body: String = "",
    val sending: Boolean = false,
    val error: UiText? = null,
    val confirmation: ReplyConfirmationUi? = null,
)

enum class ModelStatus { UNKNOWN, CHECKING, DOWNLOADABLE, DOWNLOADING, READY, UNAVAILABLE }

enum class SummaryStage { CLOSED, IDLE, CHECKING, DOWNLOADABLE, DOWNLOADING, RUNNING, COMPLETE, FAILED, UNSUPPORTED }

data class SummaryUiState(
    val stage: SummaryStage = SummaryStage.CLOSED,
    /** モデルの取得の進み具合。合計が分かるのは、このアプリが最初に取得を頼んだときだけ。 */
    val downloadedBytes: Long = 0,
    val downloadTotalBytes: Long? = null,
    val progress: SummaryProgress? = null,
    val result: String = "",
    val first: Int = 0,
    val last: Int = 0,
    val count: Int = 0,
    val partial: Boolean = false,
    val stale: Boolean = false,
    val error: SummaryError = SummaryError.GENERIC,
    val errorPost: Int = 0,
    val canResume: Boolean = false,
    /** 要約を作った後に増えたレスの数。1件以上なら、作り直すかを尋ねる。 */
    val newPostCount: Int = 0,
    /** 要約中にできた部分要約（元のレスの範囲と内容）。完成したら全体の要約に置き換える。 */
    val partials: List<SummaryPartial> = emptyList(),
)

data class SummaryPartial(val first: Int, val last: Int, val text: String)
enum class SummaryError { GENERIC, EMPTY, TOO_LARGE, BUSY, QUOTA, CANCELLED, BACKGROUND, DOWNLOAD_TIMEOUT, OUTPUT_UNAVAILABLE }

/** スレへの質問。 */
enum class QuestionStage { IDLE, RUNNING, COMPLETE, FAILED }
enum class QuestionError { GENERIC, MODEL_NOT_READY, TOO_LARGE, BUSY, QUOTA }

data class QuestionUiState(
    val stage: QuestionStage = QuestionStage.IDLE,
    val question: String = "",
    val answer: String = "",
    val error: QuestionError = QuestionError.GENERIC,
)

/** AI フィルター（本文をぼかす）。[junk] はゴミレスと判定したレス番号（決まり＋AI）。 */
data class AiFilterUiState(
    val kinds: Set<AiFilterKind> = emptySet(),
    val junk: Set<Int> = emptySet(),
    val judging: Boolean = false,
    val applied: Boolean = false,
    /** モデルが使えず、AI の判定ができない（決まりで分かる分だけミュートしている）。 */
    val unavailable: Boolean = false,
    val judged: Int = 0,
    val total: Int = 0,
)

data class ThreadUiState(
    val title: String = "",
    /** この板の NG（ワッチョイか ID）。一致したレスの本文は隠し、押すと見せる。 */
    val ng: Set<NgKey> = emptySet(),
    /** 自分が書き込んだレスの番号（ヘッダに「自分」のチップを出す）。 */
    val myPosts: Set<Int> = emptySet(),
    val posts: List<Post> = emptyList(),
    val index: PostIndex = PostIndex(emptyList()),
    val newFrom: Int? = null,
    val lastRead: Int = 0,
    val archived: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val automaticRefresh: Boolean = false,
    /** 最初に表示する位置（0 始まりの index）。一度だけ使う。 */
    val initialIndex: Int? = null,
    val initialOffset: Int = 0,
    /** 更新して新着レスへ飛ぶときの一時スクロール先（0 始まり。posts.size は末尾の「新着なし」）。 */
    val scrollToIndex: Int? = null,
    val reply: ReplyUiState = ReplyUiState(),
    val summary: SummaryUiState = SummaryUiState(),
    val modelStatus: ModelStatus = ModelStatus.UNKNOWN,
    val modelName: String? = null,
    val question: QuestionUiState = QuestionUiState(),
    /** 要約・質問のシートを出しているか。閉じても、同じスレにいるあいだは要約を続ける。 */
    val summarySheetOpen: Boolean = false,
    val aiFilter: AiFilterUiState = AiFilterUiState(),
)

class ThreadViewModel(
    private val threads: ThreadRepository,
    private val boards: BoardRepository,
    private val boardId: String,
    private val threadKey: Long,
    private val resNumber: Int?,
    private val summaryCache: SummaryCache? = null,
    private val imgur: ImgurResolver? = null,
    private val modelDownloader: ModelDownloader? = null,
    private val muteCache: SummaryCache? = null,
    private val settingsStore: SettingsStore? = null,
    private val summaryClientFactory: () -> SummaryClient = { NanoSummaryClient() },
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** 画面先頭の位置（スクロール中は最新だけを持ち、止まってから・画面を離れるときに保存する）。 */
    private val scrollPosition = MutableStateFlow<Pair<Int, Int>?>(null)
    private var savedPosition: Pair<Int, Int>? = null
    private val saveLock = Mutex()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            scrollPosition.filterNotNull().debounce(SAVE_DELAY_MS).collect { savePosition(it) }
        }
    }
    private val nanoInstance = lazy { summaryClientFactory() }
    private val nano by nanoInstance
    private var summaryJob: Job? = null
    private var summarySnapshot: List<SummaryPost> = emptyList()
    private var summaryPartial = false
    private var summarySourcePosts: List<Post> = emptyList()
    private var freshSummary = false
    private val unknownModel = UUID.randomUUID().toString()

    /** 最後に完成した要約と、その元になったレス。同じ対象で開き直したときは、準備中を挟まずにそのまま出す。 */
    private var lastCompleted: Pair<List<SummaryPost>, SummaryUiState>? = null

    /**
     * 要約のシートを開く（まだ要約は始めない）。同じ対象の完成済みの要約があればそれを出し、
     * なければ説明と「要約」ボタンの画面にする。要約は利用者がボタンを押してから始める。
     */
    fun openSummary(posts: List<Post>, partial: Boolean) {
        _state.update { it.copy(summarySheetOpen = true) }
        // 要約中・完成済みの要約があればそれを出す（閉じても裏で続けているので、開き直すと続きが見える）
        if (summaryJob?.isActive == true || _state.value.summary.stage !in listOf(SummaryStage.CLOSED, SummaryStage.IDLE)) {
            if (_state.value.summary.stage != SummaryStage.COMPLETE) return
        }
        freshSummary = false
        if (!prepareSummary(posts, partial)) return
        val done = lastCompleted
        // 前の要約の後にレスが増えただけなら、前の要約を出したうえで作り直すかを尋ねる
        val newCount = done?.let { (old, _) -> summarySnapshot.count { it.number > (old.lastOrNull()?.number ?: 0) } } ?: 0
        _state.update {
            it.copy(summary = when {
                done != null && done.first == summarySnapshot -> done.second
                done != null && newCount > 0 -> done.second.copy(newPostCount = newCount)
                else -> SummaryUiState(stage = SummaryStage.IDLE)
            })
        }
    }

    /** 増えたレスを含めて要約を作り直す。保存済みの部分要約は使い回すので、増えた分だけ処理する。 */
    fun updateSummary(posts: List<Post>, partial: Boolean) {
        if (summaryJob?.isActive == true) return
        freshSummary = false
        if (!prepareSummary(posts, partial)) return
        launchSummary(false)
    }

    private var autoSummaryStarted = false

    /** 自動要約（設定）。このスレで1回だけ、要約がまだないときに裏で始める。 */
    fun autoSummary(posts: List<Post>) {
        if (autoSummaryStarted || summaryJob?.isActive == true) return
        autoSummaryStarted = true
        if (_state.value.summary.stage != SummaryStage.CLOSED) return
        freshSummary = false
        if (!prepareSummary(posts, false)) return
        launchSummary(false)
    }

    /** 開いたシートの「要約」ボタン。 */
    fun beginSummary() = launchSummary(false)

    /** 再要約（シートの右上）。完成した要約を表示中なら作り直す。 */
    fun startSummary(posts: List<Post>, partial: Boolean) {
        if (summaryJob?.isActive == true) return
        freshSummary = _state.value.summary.stage == SummaryStage.COMPLETE
        if (!prepareSummary(posts, partial)) return
        // 再要約（完成した要約を表示中に押した）でなければ、同じ対象の完成済みの要約をすぐに出す
        val done = lastCompleted
        if (!freshSummary && done != null && done.first == summarySnapshot) {
            _state.update { it.copy(summary = done.second) }
            return
        }
        launchSummary(false)
    }

    /** 要約の対象（レス番号順のヘッダーと本文）を固定する。対象がなければ失敗を出して false。 */
    /** AI に渡すレス（レス番号順。ヘッダーと本文）。あぼーん・本文のないレスは除く。 */
    private fun toSummaryPosts(posts: List<Post>): List<SummaryPost> = posts.filterNot { it.isAbone }.mapNotNull { post ->
        val text = post.body.joinToString("") { it.text }.trim()
        text.takeIf { it.isNotEmpty() }?.let {
            val header = listOfNotNull(
                post.name.takeIf(String::isNotBlank),
                post.mail.takeIf(String::isNotBlank),
                post.dateText.takeIf(String::isNotBlank),
                post.id?.let { id -> "ID:$id" },
            ).joinToString(" ")
            SummaryPost(post.number, if (header.isEmpty()) it else "$header\n$it")
        }
    }.sortedBy { it.number }

    // --- スレへの質問 ---

    private var questionJob: Job? = null

    fun updateQuestion(text: String) = _state.update { it.copy(question = it.question.copy(question = text)) }

    /**
     * 質問に答える。質問に関係のありそうなレスを選び（完成した要約があれば添える）、入力上限に収まる分だけ入れて
     * 1回だけ尋ねる。答えの中のスレに無いレス番号は取り除く。
     */
    fun askQuestion(posts: List<Post>) {
        val question = _state.value.question.question.trim()
        if (question.isEmpty() || questionJob?.isActive == true) return
        val snapshot = toSummaryPosts(posts)
        val known = _state.value.posts.map { it.number }.toSet()
        questionJob = viewModelScope.launch {
            _state.update { it.copy(question = it.question.copy(stage = QuestionStage.RUNNING, answer = "")) }
            try {
                if (nano.status() != FeatureStatus.AVAILABLE) {
                    _state.update { it.copy(question = it.question.copy(stage = QuestionStage.FAILED, error = QuestionError.MODEL_NOT_READY)) }
                    return@launch
                }
                val summary = lastCompleted?.second?.result
                val ranked = rankForQuestion(question, snapshot)
                // 入る件数を二分探索で決める（計測は軽いので、生成は1回だけ）
                var low = 0
                var high = ranked.size
                while (low < high) {
                    val mid = (low + high + 1) / 2
                    if (retryWhenBusy { nano.fitsPrompt(questionPrompt(question, summary, ranked.take(mid))) }) low = mid else high = mid - 1
                }
                if (low == 0 && !retryWhenBusy { nano.fitsPrompt(questionPrompt(question, summary, emptyList())) }) {
                    _state.update { it.copy(question = it.question.copy(stage = QuestionStage.FAILED, error = QuestionError.TOO_LARGE)) }
                    return@launch
                }
                val answer = retryWhenBusy { nano.generatePrompt(questionPrompt(question, summary, ranked.take(low))) }
                coroutineContext.ensureActive()
                _state.update { it.copy(question = it.question.copy(stage = QuestionStage.COMPLETE, answer = dropUnknownAnchors(answer, known))) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = when ((e as? GenAiException)?.errorCode) {
                    GenAiException.ErrorCode.BUSY -> QuestionError.BUSY
                    GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> QuestionError.QUOTA
                    else -> if (e is SummaryTooLarge) QuestionError.TOO_LARGE else QuestionError.GENERIC
                }
                _state.update { it.copy(question = it.question.copy(stage = QuestionStage.FAILED, error = reason)) }
            }
        }
    }

    /** 質問と回答を消して、最初の状態に戻す。 */
    fun resetQuestion() {
        questionJob?.cancel()
        _state.update { it.copy(question = QuestionUiState()) }
    }

    fun stopQuestion() {
        questionJob?.cancel()
        _state.update { it.copy(question = it.question.copy(stage = QuestionStage.IDLE)) }
    }

    // --- AI フィルター ---

    private var junkJob: Job? = null
    private val junkMutex = Mutex()
    private val junkResults = mutableMapOf<String, String>()
    private var freshJunk = false
    /** 最後に画面の先頭にあったレスの位置（判定をそこから近い順に進める）。 */
    private var focusIndex = 0
    private var threadMuteChoice: Boolean? = null

    /** 画面が裏に回ったら AI の判定を止める（判定済みは覚えているので、戻ったら残りから続ける）。 */
    fun pauseJunk() {
        junkPaused = true
        junkJob?.cancel()
    }

    fun resumeJunk() {
        junkPaused = false
        if (AiFilterKind.JUNK in _state.value.aiFilter.kinds) judgeJunk()
    }

    private var muteConditions = DEFAULT_MUTE_CONDITIONS

    /** ミュートの条件（設定）。変わったら、AI の判定をやり直す（決まりでの判定はそのまま）。 */
    private fun setMuteConditions(conditions: String) {
        if (conditions == muteConditions) return
        muteConditions = conditions
        restartJunk()
    }

    fun reloadJunk() {
        if (AiFilterKind.JUNK !in _state.value.aiFilter.kinds) return
        freshJunk = true
        restartJunk()
    }

    private fun restartJunk() {
        junkJob?.cancel()
        _state.update { it.copy(aiFilter = it.aiFilter.copy(junk = emptySet(), applied = false, unavailable = false)) }
        if (AiFilterKind.JUNK in _state.value.aiFilter.kinds) judgeJunk()
    }

    /** 裏に回っているあいだは、更新やモデルの取得が終わっても判定を始めない。 */
    private var junkPaused = false

    suspend fun configureJunk(allowed: Boolean, autoMute: Boolean, conditions: String) {
        if (!allowed) applyJunkSelection(false)
        setMuteConditions(conditions)
        if (!allowed) return

        if (threadMuteChoice == null) {
            // 以前の版ではオン・オフを保存していなかったので、判定が残っているスレはオンにする。
            val saved = settingsStore?.threadMute(boardId, threadKey)
            val enabled = saved ?: (autoMute || muteCache?.hasVerdicts(boardId, threadKey) == true)
            if (threadMuteChoice == null) {
                if (saved == null && enabled) rememberJunkSelection(true)
                else threadMuteChoice = saved
            }
        }
        applyJunkSelection(threadMuteChoice == true)
    }

    fun setAiFilter(kind: AiFilterKind, enabled: Boolean) {
        if (kind != AiFilterKind.JUNK) return
        rememberJunkSelection(enabled)
        applyJunkSelection(enabled)
    }

    private fun rememberJunkSelection(enabled: Boolean) {
        threadMuteChoice = enabled
        val store = settingsStore ?: return
        viewModelScope.launch {
            // メニューを押してすぐスレを離れても、最後の操作を書き込む。
            withContext(NonCancellable) {
                store.setThreadMute(boardId, threadKey, enabled)
            }
        }
    }

    private fun applyJunkSelection(enabled: Boolean) {
        val kind = AiFilterKind.JUNK
        _state.update {
            it.copy(aiFilter = it.aiFilter.copy(
                kinds = if (enabled) it.aiFilter.kinds + kind else it.aiFilter.kinds - kind,
                judging = enabled && it.aiFilter.judging,
            ))
        }
        if (enabled) judgeJunk() else junkJob?.cancel()
    }

    private fun judgeJunk() {
        val state = _state.value
        if (AiFilterKind.JUNK !in state.aiFilter.kinds || state.aiFilter.applied || state.posts.isEmpty()) return
        if (junkPaused || junkJob?.isActive == true) return
        junkJob = viewModelScope.launch { runJunk() }
    }

    private suspend fun runJunk() = junkMutex.withLock {
        val session = muteCache?.start(boardId, threadKey, freshJunk)
        if (freshJunk || session != null) junkResults.clear()
        if (session != null) junkResults.putAll(session.results)
        freshJunk = false

        val pending = mutableMapOf<String, String>()
        suspend fun saveVerdicts() {
            if (muteCache != null && session != null && pending.isNotEmpty()) {
                withContext(NonCancellable) {
                    muteCache.saveVerdicts(boardId, threadKey, session.generation, pending)
                    pending.clear()
                }
            }
        }

        try {
            do {
                val posts = _state.value.posts
                val conditions = muteConditions
                val (ruled, candidates) = withContext(Dispatchers.Default) {
                    val ruled = ruleJunk(posts)
                    ruled to junkCandidates(posts, ruled, emptySet()).map { post ->
                        val prompt = junkJudgePrompt(post, conditions)
                        Triple(post.number, SummaryCache.verdictKey(post.number, prompt), prompt)
                    }
                }
                val muted = (ruled + candidates.filter { (_, key) -> junkResults[key] == "R" }.map { it.first }).toMutableSet()
                val remaining = candidates.filter { (_, key) -> key !in junkResults }
                    .sortedBy { kotlin.math.abs(it.first - 1 - focusIndex) }
                _state.update { it.copy(aiFilter = it.aiFilter.copy(
                    junk = muted.toSet(), applied = remaining.isEmpty(), unavailable = false,
                )) }
                if (remaining.isNotEmpty()) {
                    val status = try {
                        withTimeoutOrNull(MODEL_STATUS_TIMEOUT_MS) { nano.status() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (status != FeatureStatus.AVAILABLE) {
                        if (status == FeatureStatus.DOWNLOADING) modelDownloader?.start()
                        _state.update { it.copy(aiFilter = it.aiFilter.copy(unavailable = true)) }
                        if (posts === _state.value.posts) return@withLock
                        continue
                    }
                    _state.update { it.copy(aiFilter = it.aiFilter.copy(judging = true, judged = 0, total = remaining.size)) }
                    for ((number, key, prompt) in remaining) {
                        val reply = retryWhenBusy { nano.generatePrompt(prompt) }
                        coroutineContext.ensureActive()
                        val verdict = if (junkVerdictOf(reply) == true) "R" else "G"
                        junkResults[key] = verdict
                        if (session != null) pending[key] = verdict
                        if (verdict == "R") muted += number
                        _state.update { it.copy(aiFilter = it.aiFilter.copy(junk = muted.toSet(), judged = it.aiFilter.judged + 1)) }
                        if (pending.size >= 32) saveVerdicts()
                        if (posts !== _state.value.posts) break
                    }
                    saveVerdicts()
                }
                _state.update { it.copy(aiFilter = it.aiFilter.copy(judging = false, applied = posts === it.posts)) }
            } while (posts !== _state.value.posts)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _messages.send(UserMessage(R.string.ai_filter_failed, retry = { judgeJunk() }))
        } finally {
            saveVerdicts()
            _state.update { it.copy(aiFilter = it.aiFilter.copy(judging = false)) }
        }
    }

    private fun prepareSummary(posts: List<Post>, partial: Boolean): Boolean {
        summarySourcePosts = _state.value.posts.toList()
        summarySnapshot = toSummaryPosts(posts)
        summaryPartial = partial || summarySnapshot.size != _state.value.posts.count { !it.isAbone }
        if (summarySnapshot.isEmpty()) {
            _state.update { it.copy(summary = SummaryUiState(stage = SummaryStage.FAILED, error = SummaryError.EMPTY)) }
            return false
        }
        return true
    }

    override fun onCleared() {
        // 戻ってすぐ破棄されても最後の位置を失わない（viewModelScope はもう使えない）
        // 書きかけの保存が終わるのを待ってから、最後の位置を書く（古い位置で上書きしない）
        scrollPosition.value?.let { position ->
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { savePosition(position) }
        }
        summaryJob?.cancel()
        if (nanoInstance.isInitialized()) nano.close()
        super.onCleared()
    }

    /** モデルを取得する。アプリ全体の取得に任せ（閉じても続く）、なければこの画面で取得する。 */
    fun downloadSummaryModel() {
        val downloader = modelDownloader ?: return launchSummary(true)
        downloader.start()
    }


    /** アプリ全体の取得の進み具合を、この画面の表示に映す。終わったら、要約の流れに戻る。 */
    private fun observeModelDownload() {
        val downloader = modelDownloader ?: return
        viewModelScope.launch {
            downloader.state.collect { d ->
                when (d) {
                    is ModelDownloadState.Downloading -> _state.update {
                        val summary = if (it.summary.stage == SummaryStage.DOWNLOADABLE || it.summary.stage == SummaryStage.DOWNLOADING) {
                            it.summary.copy(stage = SummaryStage.DOWNLOADING, downloadedBytes = d.bytes, downloadTotalBytes = d.total)
                        } else it.summary
                        it.copy(summary = summary, modelStatus = ModelStatus.DOWNLOADING)
                    }
                    ModelDownloadState.Done -> {
                        _state.update { it.copy(modelStatus = ModelStatus.UNKNOWN) }
                        // モデルが入ったら、止まっていたゴミレスの判定を始める
                        if (AiFilterKind.JUNK in _state.value.aiFilter.kinds) judgeJunk()
                        if (_state.value.summary.stage == SummaryStage.DOWNLOADING) launchSummary(false)
                    }
                    is ModelDownloadState.Failed -> _state.update {
                        val summary = if (it.summary.stage == SummaryStage.DOWNLOADING) {
                            it.summary.copy(stage = SummaryStage.FAILED, error = if (d.timedOut) SummaryError.DOWNLOAD_TIMEOUT else SummaryError.GENERIC)
                        } else it.summary
                        it.copy(summary = summary, modelStatus = ModelStatus.DOWNLOADABLE)
                    }
                    ModelDownloadState.Idle -> Unit
                }
            }
        }
    }
    fun interruptSummary() {
        if (summaryJob?.isActive == true) {
            summaryJob?.cancel()
            _state.update { it.copy(summary = it.summary.copy(stage = SummaryStage.FAILED, error = SummaryError.CANCELLED), modelStatus = if (it.modelStatus == ModelStatus.READY) ModelStatus.READY else ModelStatus.UNKNOWN) }
        }
    }
    fun resumeSummary() {
        val summary = _state.value.summary
        if (summary.stage == SummaryStage.FAILED && summary.error in listOf(SummaryError.CANCELLED, SummaryError.BACKGROUND, SummaryError.BUSY)) {
            launchSummary(false)
        }
    }
    /** シートを閉じる。要約・質問は同じスレにいるあいだ裏で続ける（止めるのは停止ボタンか、スレを離れたとき）。 */
    fun hideSummary() = _state.update { it.copy(summarySheetOpen = false) }

    /** スレを離れるとき。要約と質問を止めて片付ける。 */
    fun closeSummary() {
        questionJob?.cancel()
        summaryJob?.cancel()
        _state.update { it.copy(summary = SummaryUiState(), summarySheetOpen = false, modelStatus = if (it.modelStatus == ModelStatus.CHECKING || it.modelStatus == ModelStatus.DOWNLOADING) ModelStatus.UNKNOWN else it.modelStatus) }
    }
    private fun launchSummary(download: Boolean) {
        if (summaryJob?.isActive == true || summarySnapshot.isEmpty()) return
        summaryJob = viewModelScope.launch {
            // 使えると分かっているモデルは、確認し直すあいだも表示を「確認中」に戻さない（ちらつかないように）
            _state.update {
                if (it.modelStatus == ModelStatus.READY) it.copy(summary = SummaryUiState(stage = SummaryStage.CHECKING))
                else it.copy(summary = SummaryUiState(stage = SummaryStage.CHECKING), modelStatus = ModelStatus.CHECKING, modelName = null)
            }
            try {
                var status = nano.status()
                if (download && (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.DOWNLOADING)) {
                    _state.update { it.copy(summary = it.summary.copy(stage = SummaryStage.DOWNLOADING), modelStatus = ModelStatus.DOWNLOADING) }
                    nano.download { _, _ -> }
                    status = nano.status()
                }
                when (status) {
                    FeatureStatus.UNAVAILABLE -> { _state.update { it.copy(summary = it.summary.copy(stage = SummaryStage.UNSUPPORTED), modelStatus = ModelStatus.UNAVAILABLE) }; return@launch }
                    FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                        // AICore が取得中なら、こちらも見守りを始めて進み具合を見せる（終わったら要約に戻る）
                        if (status == FeatureStatus.DOWNLOADING) modelDownloader?.start()
                        val downloading = modelDownloader?.isDownloading == true
                        _state.update {
                            it.copy(
                                summary = it.summary.copy(stage = if (downloading) SummaryStage.DOWNLOADING else SummaryStage.DOWNLOADABLE),
                                modelStatus = if (downloading) ModelStatus.DOWNLOADING else ModelStatus.DOWNLOADABLE,
                            )
                        }
                        return@launch
                    }
                    FeatureStatus.AVAILABLE -> Unit
                    else -> error("Unknown model status")
                }
                val modelName = try { nano.modelName().takeIf { it.isNotBlank() } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                coroutineContext.ensureActive()
                _state.update { it.copy(summary = it.summary.copy(stage = SummaryStage.RUNNING), modelStatus = ModelStatus.READY, modelName = modelName) }
                val cached = summaryCache?.start(boardId, threadKey, freshSummary)
                freshSummary = false
                val results = cached?.results?.toMutableMap() ?: mutableMapOf()
                // 保存済みの部分要約などですぐ終わる範囲は表示せず、しばらく続いている今の範囲だけを進捗に出す
                val latestProgress = MutableStateFlow<SummaryProgress?>(null)
                val progressShown = launch {
                    latestProgress.filterNotNull().debounce(PROGRESS_SETTLE_MS).collect { progress ->
                        _state.update { it.copy(summary = it.summary.copy(progress = progress)) }
                    }
                }
                val result = try { ThreadSummarizer(
                    fits = { texts, merging -> retryWhenBusy { nano.fits(texts, merging) } },
                    generate = { texts, merging ->
                        val key = SummaryCache.key(modelName ?: unknownModel, merging, texts)
                        val value = results[key] ?: retryWhenBusy { nano.generate(texts, merging) }.also { generated ->
                            coroutineContext.ensureActive()
                            if (generated.isNotBlank()) {
                                results[key] = generated
                                if (cached != null) withContext(NonCancellable) {
                                    summaryCache.save(boardId, threadKey, cached.generation, key, generated)
                                }
                            }
                        }
                        if (value.isNotBlank()) _state.update { it.copy(summary = it.summary.copy(canResume = true)) }
                        value
                    },
                ).summarize(
                    summarySnapshot,
                    // 部分要約（範囲ごとの一文）をそのまま要約として見せる。まとめ直しはしない
                    merge = false,
                    onPartial = { first, last, text ->
                        _state.update { it.copy(summary = it.summary.copy(partials = it.summary.partials + SummaryPartial(first, last, text))) }
                    },
                ) { progress -> latestProgress.value = progress }
                } finally {
                    progressShown.cancel()
                }
                coroutineContext.ensureActive()
                _state.update { state -> state.copy(summary = state.summary.copy(
                    stage = SummaryStage.COMPLETE, result = result, first = summarySnapshot.first().number,
                    last = summarySnapshot.last().number, count = summarySnapshot.size, partial = summaryPartial,
                    stale = summaryChanged(state.posts),
                )) }
                lastCompleted = summarySnapshot to _state.value.summary
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val reason = when (e) {
                    is SummaryPostTooLarge -> SummaryError.TOO_LARGE
                    is SummaryDownloadTimeout -> SummaryError.DOWNLOAD_TIMEOUT
                    is SummaryOutputUnavailable -> SummaryError.OUTPUT_UNAVAILABLE
                    is GenAiException -> when (e.errorCode) {
                        GenAiException.ErrorCode.BUSY -> SummaryError.BUSY
                        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> SummaryError.QUOTA
                        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> SummaryError.BACKGROUND
                        else -> SummaryError.GENERIC
                    }
                    else -> SummaryError.GENERIC
                }
                _state.update { it.copy(summary = it.summary.copy(stage = SummaryStage.FAILED, error = reason, errorPost = (e as? SummaryPostTooLarge)?.number ?: 0), modelStatus = if (it.modelStatus == ModelStatus.READY) ModelStatus.READY else ModelStatus.UNKNOWN) }
            }
        }
    }
    private fun summaryChanged(posts: List<Post>): Boolean = posts != summarySourcePosts


    init {
        // NG は画面の流れと関係なく読むので、Main を使わずに集める（状態の更新はスレッドをまたいでも安全）
        viewModelScope.launch(Dispatchers.Default) {
            threads.purgeExpiredNg()
            threads.observeNg(boardId).collect { list -> _state.update { it.copy(ng = list.mapTo(HashSet()) { e -> NgKey(e.kind, e.value) }) } }
        }
        observeModelDownload()
        viewModelScope.launch {
            val cached = try {
                threads.loadCached(boardId, threadKey)
            } catch (_: java.io.IOException) {
                null
            }
            cached?.let {
                apply(it, first = true)
                threads.markViewed(boardId, threadKey)
            }
            refresh(automatic = true)
        }
    }

    /** 画面が入り終わってから、端末の AI の状態を確かめる（AI の準備を遷移の動きと重ねない）。 */
    fun refreshModelStatus() {
        viewModelScope.launch {
            try {
                val status = nano.status()
                val name = if (status == FeatureStatus.AVAILABLE) {
                    try { nano.modelName().takeIf { it.isNotBlank() } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                } else null
                coroutineContext.ensureActive()
                _state.update { cur ->
                    if (cur.summary.stage != SummaryStage.CLOSED) cur
                    else cur.copy(
                        modelStatus = when (status) {
                            FeatureStatus.AVAILABLE -> ModelStatus.READY
                            FeatureStatus.DOWNLOADABLE -> ModelStatus.DOWNLOADABLE
                            FeatureStatus.DOWNLOADING -> ModelStatus.DOWNLOADING
                            FeatureStatus.UNAVAILABLE -> ModelStatus.UNAVAILABLE
                            else -> ModelStatus.UNKNOWN
                        },
                        modelName = name,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // SDK 未対応のテスト環境などでは UNKNOWN のままにする
            }
        }
    }

    fun refresh(jumpToNew: Boolean = false, automatic: Boolean = false) {
        if (_state.value.refreshing || (!automatic && _state.value.loading)) return
        _state.update { it.copy(refreshing = true, automaticRefresh = automatic) }
        viewModelScope.launch {
            val shown = _state.value.posts.size
            val firstLoad = shown == 0
            try {
                when (val r = threads.refreshThread(boardId, threadKey, shown.takeIf { it > 0 })) {
                    is RefreshOutcome.Updated -> onRefreshed(r.thread.log, r.thread.posts, firstLoad, jumpToNew)
                    is RefreshOutcome.Unchanged -> {
                        // 表示中の本文がなければ（保存の読み込みに失敗していた等）保存から読む
                        val cached = if (firstLoad) threads.loadCached(boardId, threadKey) else null
                        onRefreshed(r.log, cached?.posts, firstLoad, jumpToNew)
                    }
                    is RefreshOutcome.Failed -> {
                        if (firstLoad) r.cached?.let { apply(it, first = true) }
                        _messages.send(UserMessage(messageFor(r.error, R.string.error_target_thread), retry = { refresh(jumpToNew) }))
                    }
                    RefreshOutcome.NotFound -> _messages.send(UserMessage(R.string.thread_not_found))
                }
            } catch (_: java.io.IOException) {
                _messages.send(UserMessage(messageFor(SourceError.PARSE, R.string.error_target_thread), retry = { refresh(jumpToNew) }))
            } finally {
                _state.update { it.copy(refreshing = false, automaticRefresh = false, loading = false) }
            }
        }
    }

    private suspend fun onRefreshed(log: ThreadLogEntity, posts: List<Post>?, firstLoad: Boolean, jumpToNew: Boolean) {
        apply(log, posts, first = firstLoad)
        threads.markViewed(boardId, threadKey)
        val count = _state.value.posts.size
        if (jumpToNew && count > 0) {
            // 増えていれば新着の先頭へ、増えていなければ末尾の「新着なし」へ
            val target = log.newFrom?.let { it - 1 } ?: (count - 1)
            _state.update { it.copy(scrollToIndex = target.coerceIn(0, count)) }
        }
        if (log.state == ThreadState.ARCHIVED && firstLoad) {
            _messages.send(UserMessage(R.string.thread_archived_notice))
        }
    }

    private suspend fun apply(thread: LoadedThread, first: Boolean) = apply(thread.log, thread.posts, first)

    /** [posts] が null なら本文と索引は表示中のものを使い、ログ由来の表示状態だけを更新する。 */
    private suspend fun apply(log: ThreadLogEntity, posts: List<Post>?, first: Boolean) {
        val initial = if (first) initialPosition(log, posts?.size ?: _state.value.posts.size) else null
        val postIndex = posts?.let { withContext(Dispatchers.Default) { PostIndex(it) } }
        _state.update {
            it.copy(
                title = log.title,
                // 読み直したログが記録より古いことがあるので、画面に出している分は消さずに足し合わせる
                myPosts = withSameId(log.myPostNumbers() + it.myPosts, posts ?: it.posts),
                posts = posts ?: it.posts,
                summary = if (posts != null && it.summary.stage == SummaryStage.COMPLETE) it.summary.copy(
                    stale = summaryChanged(posts),
                    newPostCount = posts.count { p -> !p.isAbone && p.number > it.summary.last },
                ) else it.summary,
                index = postIndex ?: it.index,
                newFrom = log.newFrom,
                lastRead = log.readPosition,
                archived = log.state == ThreadState.ARCHIVED,
                loading = false,
                aiFilter = if (posts != null) it.aiFilter.copy(applied = false) else it.aiFilter,
                initialIndex = initial?.first ?: it.initialIndex,
                initialOffset = initial?.second ?: it.initialOffset,
            )
        }
        // 書き込んだあとの更新なら、送った本文と一致するレスを自分の書き込みとして覚える
        val pending = pendingMyPost
        if (posts != null && pending != null) {
            findMyPost(posts, pending.first, pending.second)?.let { number ->
                pendingMyPost = null
                threads.addMyPost(boardId, threadKey, number)
                // 記録したらすぐ画面にも出す（スレを開き直すまで待たない）
                _state.update { it.copy(myPosts = withSameId(it.myPosts + number, it.posts)) }
            }
        }
        // 新着が来たら、ゴミレスの判定を新しいレスにも広げる
        if (posts != null && AiFilterKind.JUNK in _state.value.aiFilter.kinds) judgeJunk()
    }

    private suspend fun initialPosition(log: ThreadLogEntity, count: Int): Pair<Int, Int> {
        if (count == 0) return 0 to 0
        resNumber?.let { return (it - 1).coerceIn(0, count - 1) to 0 }
        // 前回読んだ位置から再開する（初めて開くスレは先頭）
        val lastRead = log.readPosition
        return if (lastRead > 0) (lastRead - 1).coerceIn(0, count - 1) to log.readOffsetPx else 0 to 0
    }

    /** 画面先頭のレスを保存する（スクロールが落ち着いてから）。 */
    fun onScrolled(index: Int, offsetPx: Int) {
        focusIndex = index
        scrollPosition.value = index to offsetPx
    }

    /** 画面が裏に回るときに、まだ保存していない位置をすぐ保存する。 */
    fun flushPosition() {
        val position = scrollPosition.value ?: return
        viewModelScope.launch { savePosition(position) }
    }

    private suspend fun savePosition(position: Pair<Int, Int>) = saveLock.withLock {
        if (position == savedPosition) return@withLock
        threads.saveReadPosition(boardId, threadKey, position.first + 1, position.second)
        // 書き込みが終わってから印を付ける（途中で取り消されたら、破棄のときにもう一度書く）
        savedPosition = position
    }

    fun consumeInitialPosition() = _state.update { it.copy(initialIndex = null) }

    fun consumeScrollToIndex() = _state.update { it.copy(scrollToIndex = null) }

    suspend fun currentThreadUrl(): String? = threads.threadPageUrl(boardId, threadKey)

    /**
     * 本文のリンクを開く。この掲示板のスレならアプリ内で開き（同じスレならそのレスへ移動）、
     * それ以外は [openExternally] に渡す。
     */
    fun openLink(url: String, onOpenThread: (boardId: String, threadKey: Long, resNumber: Int?) -> Unit, openExternally: (String) -> Unit) {
        viewModelScope.launch {
            val link = boards.resolveThreadLink(url)
            when {
                link == null -> openExternally(url)
                link.thread.board.id == boardId && link.thread.threadKey == threadKey -> link.resNumber?.let { n ->
                    _state.update { it.copy(scrollToIndex = (n - 1).coerceIn(0, (it.posts.size - 1).coerceAtLeast(0))) }
                }
                else -> onOpenThread(link.thread.board.id, link.thread.threadKey, link.resNumber)
            }
        }
    }

    fun openReply(quoteNumber: Int? = null) {
        _state.update { cur ->
            val nextBody = if (quoteNumber == null) {
                cur.reply.body
            } else {
                val anchor = ">>$quoteNumber\n"
                if (cur.reply.body.isBlank()) anchor else "${cur.reply.body.trimEnd()}\n$anchor"
            }
            cur.copy(reply = cur.reply.copy(open = true, body = nextBody, error = null, confirmation = null))
        }
    }

    fun dismissReply() {
        _state.update { it.copy(reply = it.reply.copy(open = false, error = null, confirmation = null)) }
    }

    fun updateReplyName(name: String) {
        _state.update { it.copy(reply = it.reply.copy(name = name)) }
    }

    fun updateReplyMail(mail: String) {
        _state.update { it.copy(reply = it.reply.copy(mail = mail)) }
    }

    fun toggleSage() {
        _state.update {
            val next = if (it.reply.mail.equals("sage", ignoreCase = true)) "" else "sage"
            it.copy(reply = it.reply.copy(mail = next))
        }
    }

    /** レスの書き手を NG にする・外す（ワッチョイがあればワッチョイ、なければ ID）。 */
    fun toggleNg(post: Post) {
        val key = NgKey.of(post) ?: return
        viewModelScope.launch { threads.setNg(boardId, key, key !in _state.value.ng) }
    }

    /** 書き込みに成功したときの（その時点のレス数, 送った本文）。次の更新で自分のレスを探す。 */
    private var pendingMyPost: Pair<Int, String>? = null

    /** 直リンクへの変換を試した Imgur のページのリンク（同じリンクで何度も読みに行かない）。 */
    private val imgurTried = mutableSetOf<String>()

    fun updateReplyBody(body: String) {
        _state.update { it.copy(reply = it.reply.copy(body = body, error = null)) }
        // Imgur のページのリンクが貼られたら、直リンク（i.imgur.com/….png）に置き換える（ほかの専ブラでもプレビューできるように）
        val resolver = imgur ?: return
        ImgurLinks.findPageLinks(body).filter { imgurTried.add(it) }.forEach { page ->
            viewModelScope.launch {
                val direct = resolver.resolve(page) ?: return@launch
                _state.update { it.copy(reply = it.reply.copy(body = it.reply.body.replace(page, direct))) }
            }
        }
    }

    fun cancelConfirmation() {
        _state.update { it.copy(reply = it.reply.copy(confirmation = null)) }
    }

    fun submitReply() {
        val current = _state.value.reply
        if (current.sending || current.body.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(reply = it.reply.copy(sending = true, error = null)) }
            val confirmation = current.confirmation
            val result = threads.postReply(
                boardId = boardId,
                threadKey = threadKey,
                name = current.name,
                mail = current.mail,
                body = current.body,
                confirmationFields = confirmation?.hiddenFields.orEmpty(),
                submitLabel = confirmation?.submitLabel,
            )
            when (result) {
                PostReplyResult.Success -> {
                    pendingMyPost = _state.value.posts.size to current.body
                    _state.update {
                        it.copy(reply = ReplyUiState(open = false, name = current.name, mail = current.mail))
                    }
                    _messages.send(UserMessage(R.string.reply_posted))
                    refresh(jumpToNew = true)
                }
                is PostReplyResult.ConfirmationRequired -> {
                    _state.update {
                        it.copy(
                            reply = it.reply.copy(
                                sending = false,
                                confirmation = ReplyConfirmationUi(
                                    message = result.message,
                                    hiddenFields = result.hiddenFields,
                                    submitLabel = result.submitLabel,
                                ),
                            ),
                        )
                    }
                }
                is PostReplyResult.Failed -> {
                    val reason = result.message?.let(UiText::Raw) ?: when (result.reason) {
                        PostFailure.REJECTED -> UiText.Res(R.string.reply_failed)
                        PostFailure.BOARD_UNKNOWN -> UiText.Res(R.string.reply_board_not_found)
                        null -> messageFor(result.error, R.string.error_target_post)
                    }
                    _state.update {
                        it.copy(reply = it.reply.copy(sending = false, error = reason))
                    }
                }
            }
        }
    }

    companion object {
        private const val SAVE_DELAY_MS = 500L
    }
}

/** 進捗の表示を切り替える前に待つ時間。これより早く終わる範囲は表示しない。 */
private const val PROGRESS_SETTLE_MS = 400L

/** ゴミレス判定で1回に AI へ渡すレスの最大数（入らなければ半分ずつ減らす）。 */
/** モデルの状態を確かめるのを待つ上限。返ってこなければ使えないとみなす。 */
private const val MODEL_STATUS_TIMEOUT_MS = 10_000L
