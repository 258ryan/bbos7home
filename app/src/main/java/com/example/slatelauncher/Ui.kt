package com.example.slatelauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable

/** Colours loosely modelled on the BlackBerry OS 7 "Bold" theme: charcoal chrome + light-blue highlight. */
object Theme {
    val TEXT = Color.WHITE
    val TEXT_DIM = Color.parseColor("#9AA0A8")
    val BLUE_LIGHT = Color.parseColor("#8FCBFF")
    val BLUE = Color.parseColor("#2F86DB")
    val BLUE_DARK = Color.parseColor("#1B5FA8")
    val CHROME_HI = Color.parseColor("#5B5F66")
    val CHROME_MID = Color.parseColor("#2A2C30")
    val CHROME_LO = Color.parseColor("#121315")
    val BAR = Color.parseColor("#B0000000")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun wallpaper(): Drawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.parseColor("#3A3E45"), Color.parseColor("#17191D"), Color.parseColor("#050506"))
)

/** Glossy dark rounded plate that sits behind every icon. */
fun iconPlate(ctx: Context): Drawable {
    val r = ctx.dpf(12f)
    val base = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Theme.CHROME_HI, Theme.CHROME_MID, Theme.CHROME_LO)
    ).apply { cornerRadius = r; setStroke(ctx.dp(1), Color.parseColor("#80868E")) }
    val gloss = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#55FFFFFF"), Color.parseColor("#05FFFFFF"))
    ).apply { cornerRadius = r }
    return LayerDrawable(arrayOf(base, gloss))
}

/** Solid blue rounded "selected item" square, like OS 7. */
fun focusGlow(ctx: Context, radiusDp: Int = 6, insetDp: Int = 2): Drawable {
    val glow = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.parseColor("#3C9AF0"), Color.parseColor("#1668C6"))
    ).apply { cornerRadius = ctx.dpf(radiusDp.toFloat()) }
    val none = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
    val sl = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), glow)
        addState(intArrayOf(android.R.attr.state_pressed), glow)
        addState(intArrayOf(), none)
    }
    return InsetDrawable(sl, ctx.dp(insetDp))
}

fun selectedPill(ctx: Context): Drawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Theme.BLUE_LIGHT, Theme.BLUE, Theme.BLUE_DARK)
).apply { cornerRadius = ctx.dpf(14f) }

/** Solid navy banner block (date, clock, counters), as on OS 7. */
fun bannerBackground(): Drawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Color.parseColor("#F0183663"), Color.parseColor("#F00B1A38"))
)
