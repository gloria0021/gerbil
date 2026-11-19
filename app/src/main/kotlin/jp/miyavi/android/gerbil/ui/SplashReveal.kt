package jp.miyavi.android.gerbil.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.window.SplashScreenView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.drawToBitmap
import jp.miyavi.android.gerbil.R

/**
 * 起動画面の消え方。アイコンの形にくり抜いた穴が手前に迫り、その穴から最初の画面が現れる。
 * 起動画面は Compose の外なので motionScheme は使えず、M3 の emphasized accelerate（500ms）に合わせる。
 * 上に重ねる面はタップを受けないので、動きの途中でも操作できる。
 */
fun playSplashReveal(activity: Activity, splash: SplashScreenView) {
    val icon = splash.iconView
    val background = (splash.background as? ColorDrawable)?.color
    if (icon == null || background == null || icon.width == 0) {
        splash.remove()
        return
    }
    val location = IntArray(2).also { icon.getLocationInWindow(it) }
    val size = icon.width
    val mask = ContextCompat.getDrawable(activity, R.drawable.ic_launcher_monochrome)?.toBitmap(size, size)
    if (mask == null) {
        splash.remove()
        return
    }
    val overlay = RevealView(
        activity, background, mask, icon.drawToBitmap(),
        cx = location[0] + size / 2f, cy = location[1] + size / 2f,
    )
    val root = activity.window.decorView as ViewGroup
    root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    splash.remove()
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = REVEAL_DURATION_MS
        interpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        addUpdateListener { overlay.progress = it.animatedValue as Float }
        doOnEnd { root.removeView(overlay) }
        start()
    }
}

private class RevealView(
    context: Context,
    private val background: Int,
    private val mask: Bitmap,
    private val icon: Bitmap,
    private val cx: Float,
    private val cy: Float,
) : View(context) {
    var progress = 0f
        set(value) {
            field = value
            // 後半は面ごと消して、穴の形によらず最後は画面がすべて見えるようにする
            alpha = 1f - ((value - FADE_START) / (1f - FADE_START)).coerceIn(0f, 1f)
            invalidate()
        }

    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        val scale = 1f + (MAX_SCALE - 1f) * progress
        val left = cx - mask.width / 2f
        val top = cy - mask.height / 2f
        val layer = canvas.saveLayer(null, null)
        canvas.drawColor(background)
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawBitmap(mask, left, top, holePaint)
        canvas.restore()
        canvas.restoreToCount(layer)
        // 色の付いたアイコンは、穴が開き始めるのに合わせてすぐ消す
        val iconAlpha = (1f - progress / ICON_FADE_END).coerceIn(0f, 1f)
        if (iconAlpha > 0f) {
            iconPaint.alpha = (iconAlpha * 255).toInt()
            canvas.save()
            canvas.scale(scale, scale, cx, cy)
            canvas.drawBitmap(icon, left, top, iconPaint)
            canvas.restore()
        }
    }
}

private const val REVEAL_DURATION_MS = 500L
/** 穴の最終的な大きさ（アイコンに対する倍率）。 */
private const val MAX_SCALE = 14f
private const val ICON_FADE_END = 0.2f
private const val FADE_START = 0.6f
