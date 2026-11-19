package jp.miyavi.android.gerbil

import jp.miyavi.android.gerbil.feature.thread.LocalPostStyle
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import jp.miyavi.android.gerbil.core.designsystem.BoardsTheme
import jp.miyavi.android.gerbil.core.designsystem.isDark
import jp.miyavi.android.gerbil.navigation.AppNavigation
import jp.miyavi.android.gerbil.ui.GlassEffectConfig
import jp.miyavi.android.gerbil.ui.LocalGlassEffect
import jp.miyavi.android.gerbil.ui.LocalImageDownloads
import jp.miyavi.android.gerbil.ui.playSplashReveal

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as BoardsApp).container }
    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 設定を読み終えるまでスプラッシュ相当の待ちにして、テーマのちらつきを防ぐ
        splashScreenKeep { appViewModel.settings.value == null }
        splashScreen.setOnExitAnimationListener { playSplashReveal(this, it) }
        setContent {
            val settings by appViewModel.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            // システムバーのアイコンの明暗を、端末ではなくアプリのテーマに合わせる
            val dark = s.themeMode.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            BoardsTheme(themeMode = s.themeMode, colorTheme = s.colorTheme, customColorArgb = s.customColorArgb, font = s.font) {
                CompositionLocalProvider(
                    LocalImageDownloads provides container.imageDownloads.progress,
                    LocalPostStyle provides s.postStyle,
                    LocalGlassEffect provides GlassEffectConfig(
                        opacityLevel = s.glassOpacityLevel,
                        refractionLevel = s.glassRefractionLevel,
                        mode = s.hazeMode,
                        blurStrengthLevel = s.blurStrengthLevel,
                        blurOpacityLevel = s.blurOpacityLevel,
                    ),
                ) {
                    AppNavigation(container = container, appViewModel = appViewModel, settings = s)
                }
            }
        }
    }

    private fun splashScreenKeep(condition: () -> Boolean) {
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (condition()) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }
}
