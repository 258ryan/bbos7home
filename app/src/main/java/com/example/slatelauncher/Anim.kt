package com.example.slatelauncher

import android.app.ActivityOptions
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * All launcher animation goes through here. Level 2 = full (overshoot pops, springy panels, staggered lists,
 * zoom-out launches), 1 = light (short fades and slides only), 0 = off. Android's own "animator duration scale"
 * (Developer options) is honoured: 0 turns everything off, other values stretch the durations.
 * Everything animates transforms and alpha only, which is cheap even on small GPUs.
 */
object Anim {
    fun level(ctx: Context): Int {
        if (Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return 0
        return Prefs.int(ctx, Prefs.ANIM, 2).coerceIn(0, 2)
    }

    private fun scale(ctx: Context) = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f).coerceIn(0.25f, 4f)
    private fun ms(ctx: Context, base: Long) = (base * scale(ctx)).toLong()

    /** Selection "pop": grows with a small overshoot when focused, settles back when not. */
    fun focusPop(v: View, focused: Boolean, amount: Float = 1.1f) {
        val lvl = level(v.context)
        val target = if (focused) amount else 1f
        if (lvl == 0) { v.scaleX = target; v.scaleY = target; return }
        val a = v.animate().scaleX(target).scaleY(target)
        if (lvl == 2 && focused) a.setInterpolator(OvershootInterpolator(2.2f)).setDuration(ms(v.context, 190))
        else a.setInterpolator(DecelerateInterpolator()).setDuration(ms(v.context, 120))
        a.start()
    }

    /** Button press: a quick squash and release. */
    fun press(v: View) {
        if (level(v.context) < 2) return
        v.animate().cancel()
        val keep = if (v.isFocused) 1.1f else 1f
        v.animate().scaleX(0.9f).scaleY(0.9f).translationY(v.resources.displayMetrics.density * 1.5f)
            .setDuration(ms(v.context, 70)).withEndAction {
                v.animate().scaleX(keep).scaleY(keep).translationY(0f).setInterpolator(OvershootInterpolator(3f))
                    .setDuration(ms(v.context, 180)).start()
            }.start()
    }

    /** Slide + fade a view in from an offset (dp). */
    fun slideIn(v: View, dxDp: Float, dyDp: Float, baseMs: Long = 220) {
        val lvl = level(v.context)
        if (lvl == 0) return
        val d = v.resources.displayMetrics.density
        v.animate().cancel()
        v.alpha = 0f
        v.translationX = dxDp * d * (if (lvl == 2) 1f else 0.4f)
        v.translationY = dyDp * d * (if (lvl == 2) 1f else 0.4f)
        v.animate().alpha(1f).translationX(0f).translationY(0f)
            .setInterpolator(if (lvl == 2) OvershootInterpolator(0.8f) else DecelerateInterpolator())
            .setDuration(ms(v.context, baseMs)).start()
    }

    /** Children rise in one after another (full level only). */
    fun stagger(parent: ViewGroup, max: Int = 14, horizontal: Boolean = false) {
        if (level(parent.context) < 2) return
        val d = parent.resources.displayMetrics.density
        val n = minOf(parent.childCount, max)
        for (i in 0 until n) {
            val c = parent.getChildAt(i)
            c.animate().cancel()
            c.alpha = 0f
            if (horizontal) c.translationX = 22f * d else c.translationY = 16f * d
            c.animate().alpha(1f).translationX(0f).translationY(0f)
                .setStartDelay(ms(parent.context, 28L * i)).setDuration(ms(parent.context, 240))
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** The "launch from the icon" zoom (full level only). */
    fun launchOptions(v: View?): Bundle? {
        if (v == null || level(v.context) < 2) return null
        return try { ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle() } catch (_: Exception) { null }
    }
}
