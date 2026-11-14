package com.example.slatelauncher

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private sealed class Item {
    class Header(val text: String) : Item()
    class Info(val text: String) : Item()
    class Toggle(val title: String, val sub: String?, val get: () -> Boolean, val set: (Boolean) -> Unit) : Item()
    class Choice(val title: String, val sub: String?, val current: () -> String, val options: () -> List<Pair<String, String>>, val set: (String) -> Unit) : Item()
    class Seek(val title: String, val min: Int, val max: Int, val step: Int, val unit: String, val get: () -> Int, val set: (Int) -> Unit) : Item()
    class Action(val title: String, val sub: String?, val run: () -> Unit) : Item()
}

private class Cat(val id: String, val title: String, val icon: String, val items: () -> List<Item>)

/**
 * Launcher settings, master-detail: categories on the left, options on the right
 * (on narrow screens the categories fill the screen and an option page slides in).
 * D-pad moves, A selects, B goes back, L1 / R1 switch category, left / right adjust sliders.
 */
class SettingsActivity : Activity() {

    private companion object { const val REQ_EXPORT = 201; const val REQ_IMPORT = 202 }

    private lateinit var cats: List<Cat>
    private val catRows = ArrayList<TextView>()
    private lateinit var catList: LinearLayout
    private lateinit var catScroll: ScrollView
    private lateinit var detail: LinearLayout
    private lateinit var detailScroll: ScrollView
    private lateinit var subtitle: TextView
    private var current = 0
    private var wide = true
    private var showingDetail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Materials.enabled = Prefs.bool(this, Prefs.TEXTURES, true)
        wide = resources.configuration.screenWidthDp >= 520
        cats = buildCats()
        setContentView(buildUi())
        selectCategory(0, focus = true)
    }

    // ───────────────────────── categories ─────────────────────────

    private fun toggle(title: String, sub: String?, key: String, def: Boolean, after: () -> Unit = {}) =
        Item.Toggle(title, sub, { Prefs.bool(this, key, def) }, { Prefs.set(this, key, it); after() })

    private fun buildCats(): List<Cat> = listOf(
        Cat("appearance", "Appearance", "ui:elem_preferences-desktop-wallpaper") { listOf(
            Item.Action("Wallpaper", "Built-in wallpapers or your own picture") { wallpaperDialog() },
            Item.Seek("Dim behind panels", 40, 100, 5, "%", { Prefs.int(this, Prefs.DIM, 92) }, { Prefs.set(this, Prefs.DIM, it) }),
            Item.Choice("Animations", "Full: pops, springs, zoom launches. Light: short fades. Off: none", {
                Prefs.int(this, Prefs.ANIM, 2).toString()
            }, { listOf("2" to "Full", "1" to "Light", "0" to "Off") }, { Prefs.set(this, Prefs.ANIM, it.toInt()) }),
            toggle("Leather and metal textures", "Turn off for a flatter, faster look", Prefs.TEXTURES, true),
            Item.Choice("Grid columns", "How many icons across", {
                Prefs.int(this, Prefs.COLS, 0).toString()
            }, { listOf("0" to "Automatic") + (4..9).map { it.toString() to "$it" } }, { Prefs.set(this, Prefs.COLS, it.toInt()) }),
            toggle("Names under bar icons", null, Prefs.BAR_LABELS, true),
            toggle("Show button hints", "The bar along the bottom", Prefs.HINTS, true)
        ) },
        Cat("icons", "Icons", "ui:elem_applications-graphics") { listOf(
            Item.Choice("Icon pack", "Nova / ADW style packs from installed apps", {
                Prefs.str(this, Prefs.ICON_PACK, "")
            }, {
                listOf("" to "None (BB OS 7 look)") + IconStore.packs(this).map { it.pkg to it.label }
            }, { Prefs.set(this, Prefs.ICON_PACK, it); IconStore.dropCache(); Prefs.bumpIcons(this) }),
            toggle("Use BB OS 7 icons", "Phone, mail, browser, camera and other well-known apps", Prefs.USE_BB, true) { Prefs.bumpIcons(this) },
            Item.Choice("Icons without a match", "What to do with every other app", {
                Prefs.str(this, Prefs.UNMATCHED, "plate")
            }, { listOf("plate" to "On a glossy plate", "original" to "Original icon", "packback" to "On the icon pack's background") },
                { Prefs.set(this, Prefs.UNMATCHED, it); Prefs.bumpIcons(this) }),
            Item.Action("Reset custom icons", "${IconStore.overrides(this).length()} icons changed by hand") {
                Prefs.setJson(this, Prefs.ICON_OVERRIDES, org.json.JSONObject()); Prefs.bumpIcons(this); toast("Custom icons cleared"); refreshDetail()
            },
            Item.Info("Press X on any app and choose Change icon to pick one for that app: from the pack, the BB OS 7 set, Tango / elementary, or an image.")
        ) },
        Cat("apps", "Apps", "ui:elem_system-software-install") { listOf(
            Item.Action("Hidden apps", "${IconStore.hidden(this).size} hidden") { hiddenDialog() },
            Item.Action("Reset renamed apps", "${IconStore.labelOverrides(this).length()} renamed") {
                Prefs.setJson(this, Prefs.LABEL_OVERRIDES, org.json.JSONObject()); Prefs.bumpIcons(this); toast("Names restored"); refreshDetail()
            },
            Item.Action("Reset favourites", "Back to the default dock") {
                Prefs.sp(this).edit().remove("favlist").apply(); toast("Favourites reset")
            },
            Item.Action("Reset launch counts", "Clears the Frequent folder") {
                val e = Prefs.sp(this).edit(); Prefs.sp(this).all.keys.filter { it.startsWith("cnt_") }.forEach { e.remove(it) }; e.apply(); toast("Cleared")
            }
        ) },
        Cat("folders", "Folders", "ui:elem_folder") {
            listOf<Item>(Item.Info("Choose which folders appear in the strip. \"All\" is always there.")) +
                Category.values().filter { it != Category.ALL }.map { c ->
                    Item.Toggle(c.title, null, { c.name !in Prefs.strSet(this, Prefs.FOLDERS_HIDDEN) }, { on ->
                        val s = Prefs.strSet(this, Prefs.FOLDERS_HIDDEN); if (on) s.remove(c.name) else s.add(c.name)
                        Prefs.setStrSet(this, Prefs.FOLDERS_HIDDEN, s)
                    })
                }
        },
        Cat("controls", "Controls", "ui:elem_input-gaming") { listOf(
            toggle("Swap A and B", "Use B to select and A to go back", Prefs.SWAP_AB, false),
            Item.Header("Buttons"),
            Item.Info("D-pad  move\nA  open     B  back / collapse     X  options     Y  search\nL1 / R1  previous / next folder     Select  these settings\nDown on the app bar  open the full grid")
        ) },
        Cat("games", "Games", "ui:elem_applications-games") { listOf(
            Item.Action("File access for ROMs",
                if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) "Granted" else "Tap to grant (needed to scan ROM folders)") { grantFiles() },
            Item.Action("ROM folders", Prefs.str(this, Prefs.ROM_ROOTS, "").lines().filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Automatic: ROMs / roms on internal storage and SD cards" }) { romFoldersDialog() },
            Item.Action("Emulators", "Which app starts each system") { emulatorsDialog() },
            Item.Action("Forget game favourites and counts", null) {
                val e = Prefs.sp(this).edit(); Prefs.sp(this).all.keys.filter { it.startsWith("gn_") || it.startsWith("gt_") || it == "game_favs" }.forEach { e.remove(it) }; e.apply(); toast("Cleared")
            },
            Item.Info("ROMs go in <ROM folder>/<system>/ (gba, snes, psx, psp, ...). Cover art: put <game>.png next to the ROM, or in an images/ or art/<system>/ folder. Emulator launch settings are starting points and may need adjusting for your build.")
        ) },
        Cat("backup", "Backup", "tango_media-floppy") { listOf(
            Item.Action("Export settings", "Save everything to a file") {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "slate-launcher-settings.json"), REQ_EXPORT)
            },
            Item.Action("Import settings", "Replaces the current settings") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT)
            },
            Item.Info("Images chosen for custom icons are not included in the export.")
        ) },
        Cat("about", "About", "tango_dialog-information") { listOf(
            Item.Info("Slate Launcher ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }}"),
            Item.Info("Icons: BB OS 7 theme icons (supplied by you), Tango Icon Library (public domain), elementary icons (GPL-3.0-or-later).")
        ) }
    ).map { if (it.icon.startsWith("ui:")) it else Cat(it.id, it.title, "ui:" + it.icon, it.items) }

    // ───────────────────────── UI ─────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Materials.leatherTiled(this@SettingsActivity)
        }
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bannerBackground(this@SettingsActivity)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        banner.addView(TextView(this).apply { text = "Launcher settings"; textSize = 24f; setTextColor(Theme.TEXT); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) })
        subtitle = TextView(this).apply { textSize = 12f; setTextColor(Theme.TEXT_DIM) }
        banner.addView(subtitle)
        root.addView(banner, LinearLayout.LayoutParams(-1, -2))

        catList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(8), dp(6), dp(8)) }
        catScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(catList) }
        detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(16)) }
        detailScroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(detail) }

        cats.forEachIndexed { i, c ->
            val row = TextView(this).apply {
                text = c.title; textSize = 16f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                isFocusable = true; isClickable = true
                background = catBackground()
                BbIcons.any(this@SettingsActivity, c.icon)?.let { ic ->
                    ic.setBounds(0, 0, dp(32), dp(32)); setCompoundDrawables(ic, null, null, null); compoundDrawablePadding = dp(12)
                }
                setOnFocusChangeListener { _, has -> if (has && wide) selectCategory(i, focus = false) }
                setOnClickListener { selectCategory(i, focus = false); showDetail(true) }
            }
            catRows.add(row)
            catList.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }

        val body = if (wide) LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(catScroll, LinearLayout.LayoutParams(0, -1, 0.34f))
            addView(detailScroll, LinearLayout.LayoutParams(0, -1, 0.66f))
        } else FrameLayout(this).apply {
            addView(catScroll, FrameLayout.LayoutParams(-1, -1))
            addView(detailScroll, FrameLayout.LayoutParams(-1, -1))
            detailScroll.visibility = View.GONE
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))

        val hints = LinearLayout(this)
        styleHintBar(this, hints)
        fillHints(this, hints, listOf("A" to "Select", "B" to "Back", "L1/R1" to "Category", "◀ ▶" to "Adjust"))
        root.addView(hints, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun catBackground(): Drawable {
        val gel = GelDrawable(this, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 8f, glow = true)
        val dim = GelDrawable(this, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 8f).apply { alpha = 90 }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), gel)
            addState(intArrayOf(android.R.attr.state_selected), dim)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun selectCategory(i: Int, focus: Boolean) {
        if (i == current && detail.childCount > 0 && !focus) return
        current = i
        catRows.forEachIndexed { idx, r -> r.isSelected = idx == i }
        subtitle.text = cats[i].title
        refreshDetail()
        if (focus) catRows[i].requestFocus()
    }

    private fun showDetail(show: Boolean) {
        showingDetail = show
        if (!wide) {
            detailScroll.visibility = if (show) View.VISIBLE else View.GONE
            catScroll.visibility = if (show) View.GONE else View.VISIBLE
        }
        if (show) firstFocusable(detail)?.requestFocus() else catRows[current].requestFocus()
    }

    private fun firstFocusable(v: View): View? {
        if (v.isFocusable) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstFocusable(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun refreshDetail() {
        val keep = detail.indexOfChild(currentFocus)
        detail.removeAllViews()
        cats[current].items().forEach { detail.addView(itemView(it), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(2), 0, dp(2)) }) }
        detailScroll.scrollTo(0, 0)
        if (keep >= 0) detail.getChildAt(keep.coerceAtMost(detail.childCount - 1))?.takeIf { it.isFocusable }?.requestFocus()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ───────────────────────── rows ─────────────────────────

    private fun texts(title: String, sub: String?, subColor: Int = Theme.TEXT_DIM): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = title; textSize = 16f; setTextColor(Theme.TEXT); setShadowLayer(2f, 0f, 1f, Color.BLACK) })
        if (!sub.isNullOrEmpty()) col.addView(TextView(this).apply { text = sub; textSize = 12f; setTextColor(subColor) })
        return col
    }

    private fun rowShell(content: View, accessory: View?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        isFocusable = true; isClickable = true
        background = focusGlow(this@SettingsActivity, 8, 1)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        addView(content, LinearLayout.LayoutParams(0, -2, 1f))
        if (accessory != null) addView(accessory, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
    }

    private fun chip(text: String, on: Boolean) = TextView(this).apply {
        this.text = text; textSize = 12f; setTextColor(Theme.TEXT); setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER; minWidth = dp(46); setPadding(dp(12), dp(4), dp(12), dp(7))
        background = if (on) GelDrawable(this@SettingsActivity, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 12f) else ChromeDrawable(this@SettingsActivity, 12f, dark = true)
    }

    private fun itemView(it: Item): View = when (it) {
        is Item.Header -> TextView(this).apply {
            text = it.text.uppercase(); textSize = 11f; setTextColor(Theme.BLUE_LIGHT); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(12), dp(12), dp(2))
        }
        is Item.Info -> TextView(this).apply {
            text = it.text; textSize = 13f; setTextColor(Theme.TEXT_DIM); setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        is Item.Toggle -> {
            var on = it.get()
            val c = chip(if (on) "ON" else "OFF", on)
            val row = rowShell(texts(it.title, it.sub), c)
            row.setOnClickListener {_ ->
                on = !on; it.set(on)
                c.text = if (on) "ON" else "OFF"
                c.background = if (on) GelDrawable(this, 0xFF4DA3F5.toInt(), 0xFF1668C6.toInt(), 12f) else ChromeDrawable(this, 12f, dark = true)
            }
            row
        }
        is Item.Choice -> {
            fun label(): String = it.options().firstOrNull { o -> o.first == it.current() }?.second ?: it.current()
            val t = texts(it.title, label(), Theme.BLUE_LIGHT)
            val row = rowShell(t, TextView(this).apply { text = "›"; textSize = 22f; setTextColor(Theme.TEXT_DIM) })
            row.setOnClickListener { _ ->
                Dialogs.list(this, it.title, it.options().map { (v, l) ->
                    Triple(l, null as String?, { it.set(v); refreshDetail() })
                })
            }
            row
        }
        is Item.Seek -> {
            var v = it.get()
            val value = TextView(this).apply { text = "$v${it.unit}"; textSize = 14f; setTextColor(Theme.BLUE_LIGHT); setTypeface(typeface, Typeface.BOLD) }
            val row = rowShell(texts(it.title, "◀ ▶ to adjust"), value)
            row.setOnKeyListener { _, code, ev ->
                if (ev.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                    v = (v + (if (code == KeyEvent.KEYCODE_DPAD_LEFT) -it.step else it.step)).coerceIn(it.min, it.max)
                    it.set(v); value.text = "$v${it.unit}"; true
                } else false
            }
            row
        }
        is Item.Action -> rowShell(texts(it.title, it.sub), TextView(this).apply { text = "›"; textSize = 22f; setTextColor(Theme.TEXT_DIM) })
            .also { row -> row.setOnClickListener { _ -> it.run() } }
    }

    // ───────────────────────── dialogs ─────────────────────────

    private fun launchables(): List<Pair<String, String>> {
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return packageManager.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { ri ->
                val cn = android.content.ComponentName(ri.activityInfo.packageName, ri.activityInfo.name).flattenToString()
                cn to ri.loadLabel(packageManager).toString()
            }.sortedBy { it.second.lowercase() }
    }

    private fun wallpaperDialog() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Dialogs.title(this, "Wallpaper"))
        val grid = TileGrid(this).apply { columns = 3; aspect = 0.95f }
        lateinit var d: Dialog
        Wallpapers.items.forEach { w ->
            val cell = FrameLayout(this).apply {
                isFocusable = true; isClickable = true; background = focusGlow(this@SettingsActivity, 8, 2)
                val img = ImageView(this@SettingsActivity).apply {
                    setImageBitmap(Wallpapers.render(w.id, 192, 144)); scaleType = ImageView.ScaleType.CENTER_CROP
                }
                addView(img, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(8), dp(8), dp(26)) })
                addView(TextView(this@SettingsActivity).apply { text = w.title; textSize = 11f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(6) })
                setOnClickListener {
                    d.dismiss()
                    Thread {
                        val ok = Wallpapers.apply(this@SettingsActivity, w.id)
                        runOnUiThread { toast(if (ok) "Wallpaper set" else "Couldn't set wallpaper") }
                    }.start()
                }
            }
            grid.addView(cell)
        }
        col.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val own = Dialogs.row(this, "Choose your own picture…") {
            d.dismiss(); startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
        }
        col.addView(own, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(col) }
        d = Dialogs.skin(this, sv, Gravity.CENTER, tall = true)
        d.setOnShowListener { grid.getChildAt(0)?.requestFocus() }
        d.show()
    }

    private fun hiddenDialog() {
        val hidden = IconStore.hidden(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Dialogs.title(this, "Hidden apps — press A to hide or show"))
        lateinit var d: Dialog
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        launchables().forEach { (key, label) ->
            val row = Dialogs.row(this, (if (key in hidden) "☒  " else "☐  ") + label) { }
            row.setOnClickListener {
                val h = IconStore.hidden(this)
                if (!h.add(key)) h.remove(key)
                Prefs.setStrSet(this, Prefs.HIDDEN, h); Prefs.bumpIcons(this)
                row.text = (if (key in h) "☒  " else "☐  ") + label
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        col.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(Dialogs.row(this, "Done") { d.dismiss() })
        d = Dialogs.skin(this, col, Gravity.CENTER, tall = true)
        d.setOnDismissListener { refreshDetail() }
        d.setOnShowListener { firstFocusable(list)?.requestFocus() }
        d.show()
    }

    private fun grantFiles() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            catch (_: Exception) { toast("Open Android settings → Apps → Slate Launcher → Permissions") }
        }
    }

    private fun romFoldersDialog() {
        val cur = Prefs.str(this, Prefs.ROM_ROOTS, "").lines().filter { it.isNotBlank() }.joinToString(", ")
        Dialogs.prompt(this, "ROM folders (comma separated, empty = automatic)", cur, "/storage/emulated/0/ROMs") { text ->
            Prefs.set(this, Prefs.ROM_ROOTS, text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
            refreshDetail()
        }
    }

    private fun emulatorsDialog() {
        Dialogs.list(this, "Emulators", GameSystems.all.map { def ->
            val r = GameSystems.recipe(this, def)
            Triple("${def.label}\n${r.pkg}  ·  ${r.style}${if (r.arg.isNotEmpty()) "  ·  ${r.arg}" else ""}", null as String?, { editEmulator(def) })
        })
    }

    private fun editEmulator(def: SystemDef) {
        val r = GameSystems.recipe(this, def)
        Dialogs.list(this, def.label, listOf(
            Triple("Emulator app…", null as String?, {
                val apps = launchables()
                Dialogs.list(this, "Emulator for ${def.label}", apps.map { (key, label) ->
                    Triple(label, null as String?, { GameSystems.setRecipe(this, def.key, r.copy(pkg = key.substringBefore("/"))); editEmulator(def) })
                })
            }),
            Triple("Launch style: ${r.style}", null as String?, {
                Dialogs.list(this, "Launch style", listOf(
                    "view" to "Open the file with the emulator (PPSSPP and similar)",
                    "retroarch" to "RetroArch with a core",
                    "extra" to "Start the emulator with the path as an extra",
                    "action" to "Custom intent action"
                ).map { (v, l) -> Triple(l, null as String?, { GameSystems.setRecipe(this, def.key, r.copy(style = v)); editEmulator(def) }) })
            }),
            Triple("Core / extra name / action: ${r.arg.ifEmpty { "(none)" }}", null as String?, {
                Dialogs.prompt(this, "RetroArch core (e.g. mgba), extra key or action", r.arg, "mgba") { t ->
                    GameSystems.setRecipe(this, def.key, r.copy(arg = t)); editEmulator(def)
                }
            }),
            Triple("Reset to default", null as String?, { GameSystems.setRecipe(this, def.key, null); toast("Reset") })
        ))
    }

    // ───────────────────────── input & results ─────────────────────────

    private fun back() {
        when {
            !wide && showingDetail -> showDetail(false)
            wide && currentFocus != null && detail.indexOfChild(currentFocus) >= 0 -> catRows[current].requestFocus()
            else -> finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = back()

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val up = e.action == KeyEvent.ACTION_UP
        when (Prefs.mapKey(this, e.keyCode)) {
            KeyEvent.KEYCODE_BUTTON_A -> { if (up) currentFocus?.performClick(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { if (up) back(); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (up) selectCategory((current + cats.size - 1) % cats.size, focus = true).also { if (!wide) showDetail(false) }; return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (up) selectCategory((current + 1) % cats.size, focus = true).also { if (!wide) showDetail(false) }; return true }
        }
        return super.dispatchKeyEvent(e)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                REQ_EXPORT -> contentResolver.openOutputStream(uri)?.use { it.write(Prefs.export(this).toByteArray()) }.also { toast("Settings saved") }
                REQ_IMPORT -> {
                    val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    if (Prefs.import(this, text)) { IconStore.dropCache(); toast("Settings imported") } else toast("That file isn't a settings export")
                    refreshDetail()
                }
            }
        } catch (_: Exception) { toast("Couldn't read or write the file") }
    }
}
