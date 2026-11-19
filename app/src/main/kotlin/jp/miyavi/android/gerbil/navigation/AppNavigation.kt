package jp.miyavi.android.gerbil.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.miyavi.android.gerbil.ui.ScreenMotionScheme
import jp.miyavi.android.gerbil.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import jp.miyavi.android.gerbil.AppContainer
import jp.miyavi.android.gerbil.AppViewModel
import jp.miyavi.android.gerbil.core.settings.Settings
import jp.miyavi.android.gerbil.feature.aichat.AiChatViewModel
import jp.miyavi.android.gerbil.feature.settings.SettingsScreen
import jp.miyavi.android.gerbil.feature.settings.SitesScreen
import jp.miyavi.android.gerbil.feature.thread.DEFAULT_MUTE_CONDITIONS
import jp.miyavi.android.gerbil.feature.thread.ThreadScreen
import jp.miyavi.android.gerbil.feature.thread.ThreadViewModel
import jp.miyavi.android.gerbil.feature.threadlist.ThreadListScreen
import jp.miyavi.android.gerbil.feature.threadlist.ThreadListViewModel
import kotlinx.serialization.Serializable

// 画面は3つ（FR-028）。例外として「掲示板管理」だけを置く（スレ選択の ⋮ メニューから開く。めったに触らないため設定には置かない）。
@Serializable data object ThreadListRoute : NavKey

@Serializable data class ThreadRoute(val boardId: String, val threadKey: Long, val resNumber: Int? = null) : NavKey

/** [focusFeedback] が true なら、設定を開いて「フィードバックを送信」の位置まで送る（ジャービルの案内から）。 */
@Serializable data class SettingsRoute(val focusFeedback: Boolean = false) : NavKey

@Serializable data object SitesRoute : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppNavigation(container: AppContainer, appViewModel: AppViewModel, settings: Settings) {
    val aiSupported by appViewModel.aiSupported.collectAsStateWithLifecycle()
    val aiChecked by appViewModel.aiChecked.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(ThreadListRoute)
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surface),
        onBack = { backStack.pop() },
        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
        transitionSpec = { pushTransition() },
        popTransitionSpec = { popTransition() },
        // 予測型「戻る」も同じ形にする。指を離したあとは、ばねがその速度を引き継いで収まる
        predictivePopTransitionSpec = { _ -> popTransition() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<ThreadListRoute>(
                metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPlaceholder() }),
            ) {
                val vm = viewModel { ThreadListViewModel(container.boards, container.threads, container.settings) }
                // 戻ってくるアニメーションの途中にスレを開いても、戻り終わってから開く
                val gate = rememberNavGate()
                ThreadListScreen(
                    viewModel = vm,
                    chatViewModel = if (aiSupported && aiChecked) viewModel {
                        AiChatViewModel(container.appContext.assets, container.modelDownloader, container.boards, container.threads)
                    } else null,
                    fontSize = settings.boardFontSizeSp,
                    chatFontSize = settings.threadFontSizeSp,
                    onFontSize = appViewModel::setBoardFontSize,
                    onOpenThread = { boardId, key -> gate.run { backStack.openThread(ThreadRoute(boardId, key)) } },
                    onOpenSettings = { gate.run { backStack.openSettings() } },
                    onOpenFeedback = { gate.run { backStack.openSettings(focusFeedback = true) } },
                    onOpenSites = { gate.run { backStack.openSites() } },
                    // 板が1つもないときの入力欄から登録する。スレの URL なら、そのスレを開く
                    onAddFirstBoard = { url, onResult ->
                        appViewModel.addSite("", url, onResult) { thread ->
                            gate.run { backStack.openThread(ThreadRoute(thread.board.id, thread.threadKey)) }
                        }
                    },
                )
            }
            entry<ThreadRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                val vm = viewModel {
                    ThreadViewModel(
                        container.threads, container.boards, route.boardId, route.threadKey, route.resNumber,
                        summaryCache = container.summaries,
                        imgur = container.imgur,
                        modelDownloader = container.modelDownloader,
                        muteCache = container.muteVerdicts,
                        settingsStore = container.settings,
                    )
                }
                // 開くアニメーションの途中で閉じても、開き終わってから閉じる
                val gate = rememberNavGate(onBack = { backStack.pop() })
                ThreadScreen(
                    viewModel = vm,
                    linkOpenMode = settings.linkOpenMode,
                    // 非対応の端末では、AI の入口を出さず、自動の要約・AI ミュートも動かさない
                    aiAvailable = aiSupported,
                    autoSummary = settings.autoSummary && aiSupported,
                    aiMute = settings.aiMute && aiSupported,
                    autoMute = settings.aiMute && settings.autoMute && aiSupported,
                    muteConditions = settings.muteConditions ?: DEFAULT_MUTE_CONDITIONS,
                    fontSize = settings.threadFontSizeSp,
                    onFontSize = appViewModel::setThreadFontSize,
                    onBack = { gate.run { backStack.pop() } },
                    onOpenSettings = { gate.run { backStack.openSettings() } },
                    onOpenThread = { boardId, key, res -> gate.run { backStack.openThread(ThreadRoute(boardId, key, res)) } },
                )
            }
            entry<SettingsRoute> { route ->
                val gate = rememberNavGate(onBack = { backStack.pop() })
                SettingsScreen(
                    scrollToFeedback = route.focusFeedback,
                    settings = settings,
                    aiSupported = aiSupported,
                    cacheBytes = appViewModel.cacheBytes.collectAsStateWithLifecycle().value,
                    onMeasureCache = appViewModel::measureCache,
                    onClearCache = appViewModel::clearCache,
                    onBack = { gate.run { backStack.pop() } },
                    onThemeMode = appViewModel::setThemeMode,
                    onColorTheme = appViewModel::setColorTheme,
                    onCustomColor = appViewModel::setCustomColor,
                    onFont = appViewModel::setFont,
                    onPostStyle = appViewModel::setPostStyle,
                    onLinkOpenMode = appViewModel::setLinkOpenMode,
                    onAutoSummary = appViewModel::setAutoSummary,
                    onAiMute = appViewModel::setAiMute,
                    onAutoMute = appViewModel::setAutoMute,
                    onMuteConditions = appViewModel::setMuteConditions,
                    onGlassOpacity = appViewModel::setGlassOpacityLevel,
                    onGlassRefraction = appViewModel::setGlassRefractionLevel,
                    onHazeMode = appViewModel::setHazeMode,
                    onBlurStrength = appViewModel::setBlurStrengthLevel,
                    onBlurOpacity = appViewModel::setBlurOpacityLevel,
                )
            }
            entry<SitesRoute> {
                val gate = rememberNavGate(onBack = { backStack.pop() })
                val sites by appViewModel.sites.collectAsStateWithLifecycle()
                SitesScreen(
                    sites = sites,
                    onBack = { gate.run { backStack.pop() } },
                    // スレの URL で登録したら、設定を閉じてそのスレを開く
                    onAddSite = { label, url, onResult ->
                        appViewModel.addSite(label, url, onResult) { thread ->
                            gate.run { backStack.openThread(ThreadRoute(thread.board.id, thread.threadKey)) }
                        }
                    },
                    onRenameSite = appViewModel::renameSite,
                    onDeleteSite = appViewModel::deleteSite,
                )
            }
        },
    )
}

@Composable
private fun DetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.thread_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier,
        )
    }
}

/** スレは常に1つだけ積む（一覧→スレの2段）。 */
private fun NavBackStack<NavKey>.openThread(route: ThreadRoute) {
    removeAll { it is ThreadRoute || it is SettingsRoute || it is SitesRoute }
    add(route)
}

/** 掲示板管理は、スレ選択の ⋮ メニューから開く（設定の下には積まない）。 */
private fun NavBackStack<NavKey>.openSites() {
    removeAll { it is ThreadRoute || it is SettingsRoute || it is SitesRoute }
    add(SitesRoute)
}

private fun NavBackStack<NavKey>.openSettings(focusFeedback: Boolean = false) {
    if (lastOrNull() !is SettingsRoute) add(SettingsRoute(focusFeedback))
}

// 画面の遷移は M3 のモーショントークン（ばね）で動かす。途中で戻っても速度を保ったまま向きを変えられる
private fun slideSpec(): FiniteAnimationSpec<IntOffset> = ScreenMotionScheme.defaultSpatialSpec()

private fun fadeSpec(): FiniteAnimationSpec<Float> = ScreenMotionScheme.defaultEffectsSpec()

/** 上の画面が右から重なるあいだ、下の画面はその場で暗くなる。 */
internal fun pushTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(animationSpec = slideSpec()) { it },
    initialContentExit = fadeOut(animationSpec = fadeSpec(), targetAlpha = PARALLAX_ALPHA),
    targetContentZIndex = 1f,
    sizeTransform = null,
)

/** 上のレイヤーを右へめくって、下のレイヤーを戻す遷移。 */
internal fun popTransition(): ContentTransform = ContentTransform(
    targetContentEnter = slideInHorizontally(animationSpec = slideSpec()) { -it / PARALLAX_DIVISOR } +
        fadeIn(animationSpec = fadeSpec(), initialAlpha = PARALLAX_ALPHA),
    initialContentExit = slideOutHorizontally(animationSpec = slideSpec()) { it },
    targetContentZIndex = -1f,
    sizeTransform = null,
)

private const val PARALLAX_DIVISOR = 4
private const val PARALLAX_ALPHA = 0.9f

/** 1つ前の画面へ戻る。最初の画面（スレ選択）は消さない（戻るが重なって空になると落ちるため）。 */
private fun NavBackStack<NavKey>.pop() {
    if (size > 1) removeAt(lastIndex)
}
