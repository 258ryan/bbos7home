package com.example.slatelauncher

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Pick a new icon for one app: from the active icon pack, the full BB OS 7 set, the Tango / elementary set,
 * an image from storage, or go back to the automatic icon.
 */
object IconPicker {
    private const val PAGE = 120

    fun show(act: Activity, key: String, label: String, onPickImage: () -> Unit, onChanged: () -> Unit) {
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Dialogs.title(act, "Icon for $label"))
        val tabs = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val tabScroll = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        root.addView(tabScroll, LinearLayout.LayoutParams(-1, -2))
        val grid = TileGrid(act)
        val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(grid) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val d: Dialog = Dialogs.skin(act, root, Gravity.CENTER, tall = true)

        val dm = act.resources.displayMetrics
        val widthDp = minOf(dm.widthPixels / dm.density - 48f, 520f)
        grid.columns = (widthDp / 68f).toInt().coerceIn(3, 8)
        val iconDp = ((widthDp / grid.columns) * 0.78f).toInt()

        fun pick(src: String) { IconStore.setOverride(act, key, src); d.dismiss(); onChanged() }

        fun tile(dr: Drawable, onClick: () -> Unit): View = FrameLayout(act).apply {
            isFocusable = true; isClickable = true
            background = focusGlow(act, 8, 2)
            addView(ImageView(act).apply { setImageDrawable(dr); scaleType = ImageView.ScaleType.FIT_CENTER },
                FrameLayout.LayoutParams(act.dp(iconDp), act.dp(iconDp), Gravity.CENTER))
            setOnClickListener { onClick() }
        }

        fun fill(items: List<Pair<Drawable, String>>, more: (() -> Unit)? = null) {
            grid.removeAllViews()
            items.forEach { (dr, src) -> grid.addView(tile(dr) { pick(src) }) }
            if (more != null) grid.addView(TextView(act).apply {
                text = "More…"; textSize = 14f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER
                isFocusable = true; isClickable = true; background = focusGlow(act, 8, 2)
                setOnClickListener { more() }
            })
            scroll.scrollTo(0, 0)
            grid.getChildAt(0)?.requestFocus()
        }

        fun message(text: String) {
            grid.removeAllViews()
            grid.addView(TextView(act).apply { this.text = text; setTextColor(Theme.TEXT_DIM); textSize = 14f; setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(12)) })
        }

        var shown = PAGE
        fun showPack() {
            val pack = IconStore.activePack(act)
            if (pack == null) { message("No icon pack selected.\nChoose one in Launcher settings → Icons."); return }
            val names = pack.drawableNames
            fun render() {
                val items = names.take(shown).mapNotNull { n -> pack.drawable(n)?.let { it to "pack:${pack.pkg}/$n" } }
                fill(items, if (names.size > shown) ({ shown += PAGE; render() }) else null)
            }
            shown = PAGE; render()
        }
        fun showBb() {
            val files = (act.assets.list("bbfull") ?: emptyArray()).sorted()
            fill(files.mapNotNull { f -> BbIcons.assetDrawable(act, "bbfull/$f")?.let { it to "bball:${f.removeSuffix(".png")}" } })
        }
        fun showUi() {
            val files = (act.assets.list("ui") ?: emptyArray()).sorted()
            fill(files.mapNotNull { f -> BbIcons.ui(act, f.removeSuffix(".png"))?.let { it to "ui:${f.removeSuffix(".png")}" } })
        }

        val tabViews = ArrayList<TextView>()
        fun addTab(name: String, run: () -> Unit) {
            val tv = TextView(act).apply {
                text = name; textSize = 13f; setTextColor(Theme.TEXT); setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(act.dp(12), act.dp(7), act.dp(12), act.dp(9))
                isFocusable = true; isClickable = true
                background = focusGlow(act, 8, 1)
                setOnClickListener { tabViews.forEach { it.alpha = 0.6f }; alpha = 1f; run() }
                alpha = 0.6f
            }
            tabViews.add(tv)
            tabs.addView(tv)
        }
        addTab("Icon pack") { showPack() }
        addTab("BB OS 7") { showBb() }
        addTab("Tango / elementary") { showUi() }
        addTab("Image…") { d.dismiss(); onPickImage() }
        addTab("Reset") { IconStore.setOverride(act, key, null); d.dismiss(); onChanged() }

        d.setOnShowListener { tabViews.first().performClick(); tabViews.first().requestFocus() }
        d.show()
    }
}
