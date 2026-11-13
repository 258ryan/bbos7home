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
    MEDIA("Media"), GAMES("Games"), DOWNLOADS("Downloads"), WIDGETS("Widgets"), HUB("Hub"), QUICK("Quick Settings")
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

/** One app icon, optionally with its name underneath. Rounded blue rectangle when focused. */
class IconTile(ctx: Context, val entry: AppEntry, iconDp: Int, showLabel: Boolean = false) : LinearLayout(ctx) {
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx, 8, 2)
        contentDescription = entry.label
        addView(ImageView(ctx).apply {
            setImageDrawable(entry.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp)))
        if (showLabel) {
            addView(TextView(ctx).apply {
                text = entry.label
                textSize = 12f
                setTextColor(Theme.TEXT)
                setShadowLayer(3f, 0f, 1f, 0xFF000000.toInt())
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(ctx.dp(2), ctx.dp(2), ctx.dp(2), 0)
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
    }
}

/** Banner indicator: number first, then the icon (like OS 7). Empty count = icon only. */
class CounterChip(ctx: Context, icon: Drawable?, val kind: String) : LinearLayout(ctx) {
    private val count = TextView(ctx)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx, 8, 1)
        setPadding(ctx.dp(8), ctx.dp(3), ctx.dp(8), ctx.dp(3))
        count.apply {
            textSize = 20f
            setTextColor(Theme.TEXT)
            setPadding(0, 0, ctx.dp(4), 0)
            visibility = GONE
        }
        addView(count)
        addView(ImageView(ctx).apply {
            if (icon != null) setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LayoutParams(ctx.dp(28), ctx.dp(28)))
    }

    fun setCount(text: String) {
        count.text = text
        count.visibility = if (text.isEmpty()) GONE else VISIBLE
    }
}

/** Focusable frame around a hosted widget so the D-pad can select it (X = remove). */
class WidgetFrame(ctx: Context, val widgetId: Int) : FrameLayout(ctx) {
    init {
        isFocusable = true
        isClickable = true
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        background = focusGlow(ctx, 8, 0)
        setPadding(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
    }
}

/** One notification in the Hub panel. [key] is the notification key (null for info rows). */
class HubRow(
    ctx: Context, icon: Drawable?, title: String, body: String, meta: String, val key: String?
) : LinearLayout(ctx) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = cardBackground(ctx)
        setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        addView(ImageView(ctx).apply {
            if (icon != null) setImageDrawable(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LayoutParams(ctx.dp(40), ctx.dp(40)))
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding(ctx.dp(12), 0, 0, 0) }
        col.addView(TextView(ctx).apply {
            text = title; textSize = 16f; setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        if (body.isNotEmpty()) col.addView(TextView(ctx).apply {
            text = body; textSize = 14f; setTextColor(Theme.TEXT_DIM)
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        })
        if (meta.isNotEmpty()) col.addView(TextView(ctx).apply {
            text = meta; textSize = 12f; setTextColor(Theme.BLUE_LIGHT); maxLines = 1
        })
        addView(col, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }
}

/** A quick-settings tile: glossy plate with icon (or glyph), title and state. */
class QuickTile(ctx: Context, val id: String, title: String, iconName: String?, glyph: String?) : LinearLayout(ctx) {
    private val iconView = ImageView(ctx)
    private val stateView = TextView(ctx)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = focusGlow(ctx, 8, 3)
        val plate = FrameLayout(ctx).apply { background = iconPlate(ctx) }
        if (iconName != null) {
            iconView.setImageDrawable(BbIcons.drawable(ctx, iconName))
            iconView.scaleType = ImageView.ScaleType.FIT_CENTER
            val p = ctx.dp(6)
            iconView.setPadding(p, p, p, p)
            plate.addView(iconView, FrameLayout.LayoutParams(-1, -1))
        } else {
            plate.addView(TextView(ctx).apply {
                text = glyph; textSize = 26f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(-1, -1))
        }
        addView(plate, LayoutParams(ctx.dp(54), ctx.dp(54)))
        addView(TextView(ctx).apply {
            text = title; textSize = 12f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(ctx.dp(2), ctx.dp(4), ctx.dp(2), 0)
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        stateView.apply { textSize = 11f; setTextColor(Theme.BLUE_LIGHT); gravity = Gravity.CENTER; maxLines = 1 }
        addView(stateView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setState(text: String) { stateView.text = text }

    fun setIcon(name: String) {
        BbIcons.drawable(context, name)?.let { iconView.setImageDrawable(it) }
    }
}
