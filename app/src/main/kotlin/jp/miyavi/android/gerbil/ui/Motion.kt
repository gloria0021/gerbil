package jp.miyavi.android.gerbil.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * アプリ全体の動き。Material 3 Expressive のばね（位置・大きさは少し弾み、色・不透明度は弾まない）。
 * 所要時間は決めず、すべて [MaterialTheme.motionScheme] のトークンから取る。
 */
val AppMotionScheme: MotionScheme = MotionScheme.expressive()

/**
 * 画面全体が動く遷移用。弾むと端に下の画面がのぞくので、行き過ぎのほとんどない標準のばねを使う。
 */
val ScreenMotionScheme: MotionScheme = MotionScheme.standard()

/**
 * リストの中身が変わったとき（追加・削除・並べ替え）だけ項目を動かす。
 * スクロールで画面に入ってくる項目には効かない（出現の演出はしない）。
 */
@Composable
fun LazyItemScope.motionItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

@Composable
fun LazyGridItemScope.motionItem(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motion.defaultEffectsSpec(),
        placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec(),
    )
}

/** その場で縦に開く部分（レスの詳細・名前欄）の出方。 */
@Composable
fun expandEnter(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return expandVertically(animationSpec = motion.defaultSpatialSpec()) + fadeIn(animationSpec = motion.defaultEffectsSpec())
}

@Composable
fun shrinkExit(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return shrinkVertically(animationSpec = motion.fastSpatialSpec()) + fadeOut(animationSpec = motion.fastEffectsSpec())
}
