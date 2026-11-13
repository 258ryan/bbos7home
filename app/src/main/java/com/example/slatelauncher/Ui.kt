package com.example.slatelauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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

/** Glossy bevelled chrome plate that sits behind every icon. */
fun iconPlate(ctx: Context): Drawable = ChromeDrawable(ctx, 12f)

/** Blue gel "selected item" highlight (gloss + glow), shown while a tile / chip / row has focus. */
fun focusGlow(ctx: Context, radiusDp: Int = 6, insetDp: Int = 2): Drawable {
    val gel = GelDrawable(ctx, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), radiusDp.toFloat(), glow = true)
    val sl = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), gel)
        addState(intArrayOf(android.R.attr.state_pressed), gel)
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }
    return InsetDrawable(sl, ctx.dp(insetDp))
}

/** Card for list rows: stitched leather at rest, blue gel when focused. */
fun cardBackground(ctx: Context): Drawable {
    val gel = GelDrawable(ctx, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 8f, glow = true)
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), gel)
        addState(intArrayOf(android.R.attr.state_pressed), gel)
        addState(intArrayOf(), Materials.stitchedLeather(ctx, 3))
    }
}

fun selectedPill(ctx: Context): Drawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Theme.BLUE_LIGHT, Theme.BLUE, Theme.BLUE_DARK)
).apply { cornerRadius = ctx.dpf(14f) }

/** Navy glass banner block (date, clock, counters), as on OS 7. */
fun bannerBackground(ctx: Context): Drawable = GlassBarDrawable(ctx)

/** Row in a navigation list (left pane): gel when focused, dim gel while it is the current page. */
fun navRowBackground(ctx: Context): Drawable {
    val gel = GelDrawable(ctx, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 8f, glow = true)
    val dim = GelDrawable(ctx, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 8f).apply { alpha = 90 }
    return StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), gel)
        addState(intArrayOf(android.R.attr.state_selected), dim)
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }
}
