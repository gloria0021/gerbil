package jp.miyavi.android.gerbil.navigation

import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * 画面が入ってくるアニメーションの途中に頼まれた画面遷移（戻る・開く）を、入り終わるまで待たせる。
 *
 * Navigation 3 は遷移の途中で逆向きの遷移が割り込むと、画面の重なり順の記録が狂い、
 * 以後の遷移で下の画面が上に重なったまま動くことがある。割り込みを起こさないことで防ぐ。
 * 待たせるのは入ってくるあいだ（数百ミリ秒）だけで、頼まれた操作は捨てずに必ず実行する。
 */
class NavGate internal constructor(private val entering: () -> Boolean, private val defer: (() -> Unit) -> Unit) {
    /** 入ってくる途中なら入り終わってから、そうでなければすぐに [action] を実行する。 */
    fun run(action: () -> Unit) = if (entering()) defer(action) else action()
}

/** この画面用の [NavGate]。[onBack] を渡すと、入ってくる途中の「戻る」（ジェスチャーを含む）も待たせる。 */
@Composable
fun rememberNavGate(onBack: (() -> Unit)? = null): NavGate {
    val transition = LocalNavAnimatedContentScope.current.transition
    // 実際に動いているときだけ「入り途中」と見なす（遷移の記録が途中のまま止まっても、操作を待たせ続けないため）
    val entering = transition.isRunning &&
        transition.targetState == EnterExitState.Visible && transition.currentState != EnterExitState.Visible
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 入ってくる途中だけ「戻る」を受け取り、NavDisplay の予測型「戻る」が遷移を巻き戻さないようにする
    if (onBack != null) BackHandler(enabled = entering) { pending = onBack }
    LaunchedEffect(entering, pending) {
        val action = pending ?: return@LaunchedEffect
        // 入り終わりを待つ。万一戻ってこなくても、上限を過ぎたら実行する（設定が開かない・戻れないを防ぐ）
        if (entering) delay(ENTER_WAIT_LIMIT_MS)
        pending = null
        action()
    }
    val isEntering by androidx.compose.runtime.rememberUpdatedState(entering)
    return remember { NavGate({ isEntering }, { pending = it }) }
}

/**
 * 画面が入ってくる動きが終わったか（一度 true になったら戻らない）。AI の準備のような重い処理を、
 * 遷移の動きと重ねないために使う。予測型「戻る」で指を止めている間も待つ。
 */
@Composable
fun rememberEnterSettled(): Boolean {
    val transition = LocalNavAnimatedContentScope.current.transition
    // 予測型「戻る」で指を止めている間は targetState が Visible でなくなるので、ここでは入り終わりとみなさない
    // （Transition.isSeeking はライブラリ内部向けの API で使えない）
    val done = transition.currentState == EnterExitState.Visible &&
        transition.targetState == EnterExitState.Visible && !transition.isRunning
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(done) { if (done) settled = true }
    return settled
}

/** 入り終わりを待つ上限（遷移のばねは通常これより早く収まる）。 */
private const val ENTER_WAIT_LIMIT_MS = 1_000L
