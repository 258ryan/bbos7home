package com.example.slatelauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

enum class Category(val title: String) {
    ALL("All"), FAVORITES("Favourites"), FREQUENT("Frequent"),
    MEDIA("Media"), GAMES("Games"), DOWNLOADS("Downloads")
}

class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
    val category: Int,
    val isUser: Boolean,
    /** Name of the bundled BB OS 7 icon used for this app, if any. */
    val themed: String?
) {
    val pkg: String get() = component.packageName
    val key: String get() = component.flattenToString()
}

/** Fixed-column grid. Skips GONE children, so focus search works on what you see. */
class TileGrid(ctx: Context) : ViewGroup(ctx) {
    var columns = 4
        set(v) { field = v.coerceAtLeast(1); requestLayout() }
    var aspect = 1f

    init { clipChildren = false; clipToPadding = false }

    private fun visibleKids(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val cw = width / columns
        val ch = (cw * aspect).toInt()
        val kids = visibleKids()
        kids.forEach {
            it.measure(
                MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY)
            )
        }
        setMeasuredDimension(width, ((kids.size + columns - 1) / columns) * ch)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / columns
        val ch = (cw * aspect).toInt()
        visibleKids().forEachIndexed { i, v ->
            val x = (i % columns) * cw
            val y = (i / columns) * ch
            v.layout(x, y, x + cw, y + ch)
        }
    }
}

/** One app icon (no label, like OS 7). Solid blue square when focused. */
class IconTile(ctx: Context, val entry: AppEntry, iconDp: Int) : FrameLayout(ctx) {
    init {
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx, 6, 2)
        contentDescription = entry.label
        addView(ImageView(ctx).apply {
            setImageDrawable(entry.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp), Gravity.CENTER))
    }
}

/** Icon + big number in the header row (phone / messages / mail counters). */
class CounterChip(ctx: Context, icon: Drawable?, val kind: String) : LinearLayout(ctx) {
    private val count = TextView(ctx)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx, 10, 1)
        setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
        addView(ImageView(ctx).apply {
            if (icon != null) setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LayoutParams(ctx.dp(30), ctx.dp(30)))
        count.apply {
            textSize = 24f
            setTextColor(Theme.TEXT)
            setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
            setPadding(ctx.dp(6), 0, 0, 0)
        }
        addView(count)
    }

    fun setCount(text: String) { count.text = text }
}
