package com.example.slatelauncher

import android.app.Activity
import android.app.Dialog
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * BlackBerry-OS-7-style home screen, driven by a gamepad:
 *   D-pad / left stick  move      A  open        B  back / "All" folder
 *   X  options / clear            Y or Start  search      Select  system settings
 *   L1 / R1  previous / next folder
 * Typing on a hardware keyboard starts search, like OS 7's universal search.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pm: PackageManager
    private lateinit var imm: InputMethodManager
    private val prefs by lazy { getSharedPreferences("slate", MODE_PRIVATE) }

    private lateinit var notifRow: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var grid: TileGrid
    private lateinit var emptyText: TextView
    private lateinit var searchBar: EditText
    private lateinit var caption: TextView
    private lateinit var batteryText: TextView
    private lateinit var hintBar: LinearLayout
    private val catViews = mutableListOf<TextView>()

    private var apps: List<AppEntry> = emptyList()
    private var appsKey = ""
    private var category = Category.ALL
    private var searching = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateBattery(i)
    }

    // ───────────────────────── lifecycle ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pm = packageManager
        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        setContentView(buildUi())
        applyColumns()
        updateStrip()
        updateHints()
    }

    override fun onStart() {
        super.onStart()
        updateBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        HubService.onChange = { handler.post { refreshNotifs() } }
        refreshNotifs()
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        refreshNotifs()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        HubService.onChange = null
    }

    /** Home button pressed while we are already the foreground launcher. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (searching) closeSearch()
            if (category != Category.ALL) setCategory(Category.ALL)
            homeScroll.scrollTo(0, 0)
            focusFirst()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyColumns()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = handleBack()

    // ───────────────────────── UI construction ─────────────────────────

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = wallpaper()
            fitsSystemWindows = true
        }

        // Status bar: time, date, battery
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.BAR)
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        status.addView(TextClock(this).apply {
            format12Hour = "h:mm a"; format24Hour = "HH:mm"
            textSize = 16f; setTextColor(Theme.TEXT); setTypeface(typeface, Typeface.BOLD)
        })
        status.addView(TextClock(this).apply {
            format12Hour = "EEE, MMM d"; format24Hour = "EEE, MMM d"
            textSize = 12f; setTextColor(Theme.TEXT_DIM); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        batteryText = TextView(this).apply { textSize = 13f; setTextColor(Theme.TEXT) }
        status.addView(batteryText)
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        // Notification indicator row
        notifRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val notifScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.parseColor("#55000000"))
            addView(notifRow, ViewGroup.LayoutParams(-2, -1))
        }
        root.addView(notifScroll, LinearLayout.LayoutParams(-1, dp(56)))

        // Search bar (hidden until used)
        searchBar = EditText(this).apply {
            hint = "Search"
            setHintTextColor(Theme.TEXT_DIM); setTextColor(Theme.TEXT)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC101114")); cornerRadius = dpf(6f)
                setStroke(dp(2), Theme.BLUE)
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (searching) rebuildGrid() }
            })
            setOnEditorActionListener { _, _, _ -> submitSearch(); true }
        }
        root.addView(searchBar, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(10), dp(6), dp(10), dp(2))
        })

        // App grid
        grid = TileGrid(this)
        emptyText = TextView(this).apply {
            setTextColor(Theme.TEXT_DIM); textSize = 15f; gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, 0); visibility = View.GONE
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(dp(6), dp(8), dp(6), dp(8))
            addView(grid, LinearLayout.LayoutParams(-1, -2))
            addView(emptyText, LinearLayout.LayoutParams(-1, -2))
        }
        homeScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            addView(inner, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(homeScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Caption (name of the focused item)
        caption = TextView(this).apply {
            textSize = 15f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD); maxLines = 1
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        root.addView(caption, LinearLayout.LayoutParams(-1, dp(30)))

        // Folder strip
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.BAR)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        Category.values().forEach { c ->
            val tv = TextView(this).apply {
                text = c.title; textSize = 11f; gravity = Gravity.CENTER
                maxLines = 1; setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { setCategory(c) }
            }
            catViews.add(tv)
            strip.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(strip, LinearLayout.LayoutParams(-1, -2))

        // Button hints
        hintBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F0050506"))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        root.addView(hintBar, LinearLayout.LayoutParams(-1, -2))
        return root
    }

    private fun applyColumns() {
        val wDp = resources.configuration.screenWidthDp
        grid.columns = maxOf(4, wDp / 92)
    }

    private fun hint(btn: String, label: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = btn; textSize = 10f; setTextColor(Theme.BLUE_LIGHT)
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            minWidth = dp(20); setPadding(dp(5), dp(1), dp(5), dp(1))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); setStroke(dp(1), Theme.BLUE)
                cornerRadius = dpf(if (btn.length == 1) 10f else 5f)
            }
        })
        row.addView(TextView(this).apply {
            text = label; textSize = 11f; setTextColor(Theme.TEXT_DIM); setPadding(dp(5), 0, 0, 0)
        })
        return row
    }

    private fun updateHints() {
        hintBar.removeAllViews()
        val items = if (searching) listOf("A" to "Go", "B" to "Cancel")
        else listOf("A" to "Open", "X" to "Options", "Y" to "Search", "L1/R1" to "Folders")
        items.forEach { (b, l) ->
            hintBar.addView(hint(b, l), LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(8), 0, dp(8), 0) })
        }
    }

    private fun updateStrip() {
        catViews.forEachIndexed { i, tv ->
            val sel = Category.values()[i] == category
            tv.background = if (sel) selectedPill(this) else null
            tv.setTextColor(if (sel) Color.WHITE else Theme.TEXT_DIM)
            tv.setTypeface(null, if (sel) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun updateBattery(i: Intent?) {
        if (i == null) return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lvl >= 0 && scale > 0) batteryText.text = "${lvl * 100 / scale}%"
    }

    // ───────────────────────── apps, folders, grid ─────────────────────────

    @Suppress("DEPRECATION")
    private fun refreshApps() {
        Thread {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(q, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    val ai = it.activityInfo.applicationInfo
                    AppEntry(
                        it.loadLabel(pm).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        it.loadIcon(pm),
                        ai.category,
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    )
                }
                .sortedBy { it.label.lowercase() }
            val key = list.joinToString("|") { it.key + it.label }
            handler.post {
                if (isDestroyed) return@post
                if (key != appsKey) { appsKey = key; apps = list; rebuildGrid() }
                else if (category == Category.FREQUENT) rebuildGrid()
            }
        }.start()
    }

    private fun favorites(): MutableSet<String> =
        HashSet(prefs.getStringSet("favs", emptySet()) ?: emptySet())

    private fun launchCount(e: AppEntry) = prefs.getInt("cnt_" + e.key, 0)

    private fun filtered(): List<AppEntry> {
        val q = searchBar.text.toString().trim()
        if (searching && q.isNotEmpty()) return apps.filter { it.label.contains(q, ignoreCase = true) }
        return when (category) {
            Category.ALL -> apps
            Category.FAVORITES -> { val f = favorites(); apps.filter { it.key in f } }
            Category.MEDIA -> apps.filter {
                it.category == ApplicationInfo.CATEGORY_AUDIO || it.category == ApplicationInfo.CATEGORY_VIDEO ||
                    it.category == ApplicationInfo.CATEGORY_IMAGE
            }
            Category.GAMES -> apps.filter { it.category == ApplicationInfo.CATEGORY_GAME }
            Category.DOWNLOADS -> apps.filter { it.isUser }
            Category.FREQUENT -> apps.map { it to launchCount(it) }
                .filter { it.second > 0 }.sortedByDescending { it.second }.take(12).map { it.first }
        }
    }

    private fun rebuildGrid() {
        val focus = currentFocus
        val wasGrid = focus == null || focus is IconTile
        val focusedKey = (focus as? IconTile)?.entry?.key
        grid.removeAllViews()
        val list = filtered()
        list.forEach { e ->
            val t = IconTile(this, e)
            t.setOnClickListener { launch(e) }
            t.setOnLongClickListener { showOptions(e); true }
            val anim = t.onFocusChangeListener
            t.setOnFocusChangeListener { v, has -> anim.onFocusChange(v, has); if (has) caption.text = e.label }
            grid.addView(t)
        }
        emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = when {
            searching -> "No matches"
            category == Category.FAVORITES -> "No favorites yet — press X on an app to add one"
            category == Category.FREQUENT -> "Apps you use often will appear here"
            else -> "Nothing in this folder"
        }
        if (list.isEmpty()) caption.text = ""
        if (wasGrid) {
            val target = (0 until grid.childCount).map { grid.getChildAt(it) as IconTile }
                .firstOrNull { it.entry.key == focusedKey } ?: grid.getChildAt(0)
            target?.requestFocus()
        }
    }

    private fun setCategory(c: Category) {
        category = c
        updateStrip()
        homeScroll.scrollTo(0, 0)
        rebuildGrid()
        focusFirst()
    }

    private fun focusFirst() { grid.getChildAt(0)?.requestFocus() }

    // ───────────────────────── notifications row ─────────────────────────

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun refreshNotifs() {
        if (!::notifRow.isInitialized) return
        val hadFocus = notifRow.hasFocus()
        notifRow.removeAllViews()
        if (!listenerEnabled()) {
            val tv = TextView(this).apply {
                text = "Press A to turn on notification indicators"
                setTextColor(Theme.TEXT); textSize = 13f; gravity = Gravity.CENTER_VERTICAL
                isFocusable = true; isClickable = true; tag = "enable"
                background = focusGlow(this@MainActivity, 10, 1)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            notifRow.addView(tv, LinearLayout.LayoutParams(-2, dp(44)))
            return
        }
        val groups = activeNotifs().groupBy { it.packageName }
        if (groups.isEmpty()) {
            notifRow.addView(TextView(this).apply {
                text = "No new notifications"; setTextColor(Theme.TEXT_DIM); textSize = 13f
                setPadding(dp(8), 0, 0, 0)
            })
            return
        }
        groups.entries.sortedByDescending { e -> e.value.maxOf { it.postTime } }.take(10).forEach { (pkg, list) ->
            val icon = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
            val chip = NotifChip(this, pkg, icon, list.size)
            chip.setOnClickListener { openNotifs(pkg) }
            chip.setOnLongClickListener { clearNotifs(pkg); true }
            val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            chip.setOnFocusChangeListener { _, has -> if (has) caption.text = "$label  (${list.size})" }
            notifRow.addView(chip, LinearLayout.LayoutParams(dp(52), dp(48)).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        if (hadFocus) notifRow.getChildAt(0)?.requestFocus()
    }

    private fun activeNotifs() = HubService.current().filter {
        !it.isOngoing && it.packageName != packageName &&
            (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
    }

    private fun openNotifs(pkg: String) {
        val latest = activeNotifs().filter { it.packageName == pkg }.maxByOrNull { it.postTime }
        try {
            val pi = latest?.notification?.contentIntent
            if (pi != null) {
                pi.send()
                if ((latest.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) {
                    HubService.instance?.cancelNotification(latest.key)
                }
            } else {
                pm.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearNotifs(pkg: String) {
        activeNotifs().filter { it.packageName == pkg }.forEach { HubService.instance?.cancelNotification(it.key) }
        refreshNotifs()
    }

    // ───────────────────────── launching & options ─────────────────────────

    private fun launch(e: AppEntry) {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(i)
            prefs.edit().putInt("cnt_" + e.key, launchCount(e) + 1).apply()
        } catch (_: Exception) {
            Toast.makeText(this, "Can't open ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOptions(e: AppEntry) {
        val favs = favorites()
        val isFav = e.key in favs
        val actions = listOf<Pair<String, () -> Unit>>(
            "Open" to { launch(e) },
            (if (isFav) "Remove from Favorites" else "Add to Favorites") to {
                if (isFav) favs.remove(e.key) else favs.add(e.key)
                prefs.edit().putStringSet("favs", favs).apply()
                if (category == Category.FAVORITES) rebuildGrid()
            },
            "App info" to {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)))
            },
            "Uninstall" to { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null))) }
        )
        showMenu(e.label, actions)
    }

    private fun showMenu(title: String, actions: List<Pair<String, () -> Unit>>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Theme.CHROME_HI, Theme.CHROME_MID, Theme.CHROME_LO)
            )
            setPadding(dp(8), dp(8), dp(8), dp(12))
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(Theme.BLUE_LIGHT)
            setTypeface(typeface, Typeface.BOLD); setPadding(dp(12), dp(4), dp(12), dp(8))
        })
        val rows = actions.map { (label, act) ->
            TextView(this).apply {
                text = label; textSize = 18f; setTextColor(Theme.TEXT)
                setPadding(dp(16), dp(13), dp(16), dp(13))
                isFocusable = true; isClickable = true
                background = focusGlow(context, 8, 1)
                setOnClickListener { d.dismiss(); act() }
            }
        }
        rows.forEach { box.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }) }
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        d.setOnKeyListener { _, code, ev ->
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> { if (ev.action == KeyEvent.ACTION_UP) d.currentFocus?.performClick(); true }
                KeyEvent.KEYCODE_BUTTON_B -> { if (ev.action == KeyEvent.ACTION_UP) d.dismiss(); true }
                else -> false
            }
        }
        d.setOnShowListener { rows.firstOrNull()?.requestFocus() }
        d.show()
    }

    // ───────────────────────── search ─────────────────────────

    private fun openSearch(initial: String) {
        searching = true
        searchBar.visibility = View.VISIBLE
        searchBar.setText(initial)
        searchBar.setSelection(searchBar.text.length)
        updateHints()
        rebuildGrid()
        searchBar.requestFocus()
        searchBar.post { imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeSearch() {
        searching = false
        searchBar.setText("")
        searchBar.visibility = View.GONE
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
        updateHints()
        rebuildGrid()
        focusFirst()
    }

    private fun submitSearch() {
        val list = filtered()
        if (list.size == 1) { launch(list[0]); closeSearch() } else focusFirst()
    }

    // ───────────────────────── gamepad input ─────────────────────────

    private fun handleBack() {
        when {
            searching -> closeSearch()
            category != Category.ALL -> setCategory(Category.ALL)
            else -> { homeScroll.smoothScrollTo(0, 0); focusFirst() }
        }
    }

    private fun shiftCategory(delta: Int) {
        val all = Category.values()
        setCategory(all[(category.ordinal + delta + all.size) % all.size])
    }

    private fun onConfirm() {
        val f = currentFocus
        if (f === searchBar) { submitSearch(); return }
        if (f != null && f.isClickable) f.performClick()
    }

    private fun onOptions() {
        when (val f = currentFocus) {
            is IconTile -> showOptions(f.entry)
            is NotifChip -> clearNotifs(f.pkg)
        }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val up = e.action == KeyEvent.ACTION_UP
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> { if (up) onConfirm(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { if (up) handleBack(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (up) onOptions(); return true }
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START -> {
                if (up) { if (searching) closeSearch() else openSearch("") }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (up) startActivity(Intent(Settings.ACTION_SETTINGS))
                return true
            }
            KeyEvent.KEYCODE_BUTTON_L1 -> { if (up) shiftCategory(-1); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { if (up) shiftCategory(1); return true }
        }
        // Hardware keyboard: start typing anywhere to search.
        if (e.action == KeyEvent.ACTION_DOWN && !searching && e.repeatCount == 0 && e.isPrintingKey &&
            !e.isCtrlPressed && !e.isAltPressed && e.keyCode != KeyEvent.KEYCODE_SPACE
        ) {
            val ch = e.unicodeChar
            if (ch != 0 && Character.isLetterOrDigit(ch)) {
                openSearch(String(Character.toChars(ch)))
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }
}
