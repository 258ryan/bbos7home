package com.example.slatelauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

enum class Category(val title: String) {
    ALL("All"), FAVORITES("Favorites"), MEDIA("Media"),
    GAMES("Games"), DOWNLOADS("Downloads"), FREQUENT("Frequent")
}

class AppEntry(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
    val category: Int,
    val isUser: Boolean
) {
    val pkg: String get() = component.packageName
    val key: String get() = component.flattenToString()
}

/** Fixed-column grid of equal square cells. Skips GONE children, so focus search works on what you see. */
class TileGrid(ctx: Context) : ViewGroup(ctx) {
    var columns = 4
        set(v) { field = v.coerceAtLeast(1); requestLayout() }

    init { clipChildren = false; clipToPadding = false }

    private fun visibleKids(): List<View> =
        (0 until childCount).map { getChildAt(it) }.filter { it.visibility != GONE }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        val cell = width / columns
        val kids = visibleKids()
        val spec = MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY)
        kids.forEach { it.measure(spec, spec) }
        val rows = (kids.size + columns - 1) / columns
        setMeasuredDimension(width, rows * cell)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cell = (r - l) / columns
        visibleKids().forEachIndexed { i, v ->
            val x = (i % columns) * cell
            val y = (i / columns) * cell
            v.layout(x, y, x + cell, y + cell)
        }
    }
}

/** One app: glossy plate + icon, blue glow when focused. Name is shown in the caption bar. */
class IconTile(ctx: Context, val entry: AppEntry) : FrameLayout(ctx) {
    init {
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx)
        val plate = ImageView(ctx).apply {
            background = iconPlate(ctx)
            setImageDrawable(entry.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            val p = ctx.dp(9)
            setPadding(p, p, p, p)
        }
        addView(plate, LayoutParams(ctx.dp(60), ctx.dp(60), Gravity.CENTER))
        contentDescription = entry.label
        val prevFocus = onFocusChangeListener
        setOnFocusChangeListener { v, has ->
            prevFocus?.onFocusChange(v, has)
            v.animate().scaleX(if (has) 1.1f else 1f).scaleY(if (has) 1.1f else 1f).setDuration(110).start()
        }
    }
}

/** Notification indicator in the top row (like OS 7's message icons with counts). */
class NotifChip(ctx: Context, val pkg: String, icon: Drawable?, count: Int) : FrameLayout(ctx) {
    init {
        isFocusable = true
        isClickable = true
        tag = pkg
        background = focusGlow(ctx, 10, 1)
        val img = ImageView(ctx).apply {
            if (icon != null) setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        addView(img, LayoutParams(ctx.dp(32), ctx.dp(32), Gravity.CENTER))
        if (count > 1) {
            val badge = TextView(ctx).apply {
                text = if (count > 99) "99+" else count.toString()
                setTextColor(Color.WHITE)
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(ctx.dp(4), 0, ctx.dp(4), 0)
                minWidth = ctx.dp(16)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E0262B")); cornerRadius = ctx.dpf(8f)
                }
            }
            addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, ctx.dp(16), Gravity.TOP or Gravity.END).apply {
                topMargin = ctx.dp(4); marginEnd = ctx.dp(2)
            })
        }
    }
}
