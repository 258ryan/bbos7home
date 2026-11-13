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
import android.service.notification.StatusBarNotification
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * BlackBerry-OS-7-style home screen, driven by a gamepad:
 *   D-pad / left stick  move      A  open        B  back / "All Apps"
 *   X  options / clear counter    Y or Start  search      Select  system settings
 *   L1 / R1  previous / next folder
 * Typing on a hardware keyboard starts search, like OS 7's universal search.
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pm: PackageManager
    private lateinit var imm: InputMethodManager
    private val prefs by lazy { getSharedPreferences("slate", MODE_PRIVATE) }

    private lateinit var homeScroll: ScrollView
    private lateinit var grid: TileGrid
    private lateinit var dock: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var searchBar: EditText
    private lateinit var batteryText: TextView
    private lateinit var hintBar: LinearLayout
    private lateinit var phoneChip: CounterChip
    private lateinit var msgChip: CounterChip
    private lateinit var mailChip: CounterChip
    private lateinit var pagerPrev: TextView
    private lateinit var pagerCur: TextView
    private lateinit var pagerNext: TextView

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
        updatePager()
        updateHints()
    }

    override fun onStart() {
        super.onStart()
        updateBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        HubService.onChange = { handler.post { refreshCounters() } }
        refreshCounters()
    }

    override fun onResume() {
        super.onResume()
        refreshApps()
        refreshCounters()
        if (!listenerEnabled() && !prefs.getBoolean("hinted", false)) {
            prefs.edit().putBoolean("hinted", true).apply()
            Toast.makeText(this, "Press X on the counters to enable notification counts", Toast.LENGTH_LONG).show()
        }
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

        // Header: date (left), big clock (centre), battery (right)
        val header = FrameLayout(this).apply { setPadding(dp(12), dp(4), dp(12), 0) }
        header.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 64f; setTextColor(Theme.TEXT)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        header.addView(TextClock(this).apply {
            format12Hour = "EEE, MMM d"; format24Hour = "EEE, MMM d"
            textSize = 13f; setTextColor(Theme.TEXT)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(8) })
        batteryText = TextView(this).apply { textSize = 13f; setTextColor(Theme.TEXT) }
        header.addView(batteryText, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(8) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // Counters: phone / messages / mail, search at the right
        phoneChip = CounterChip(this, BbIcons.drawable(this, "phone"), "phone")
        msgChip = CounterChip(this, BbIcons.drawable(this, "messages"), "msg")
        mailChip = CounterChip(this, BbIcons.drawable(this, "mail"), "mail")
        phoneChip.setOnClickListener { startFirst(Intent(Intent.ACTION_DIAL)) }
        msgChip.setOnClickListener { startFirst(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)) }
        mailChip.setOnClickListener { startFirst(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)) }
        val searchChip = CounterChip(this, BbIcons.drawable(this, "search"), "search").apply {
            setOnClickListener { if (searching) closeSearch() else openSearch("") }
        }
        val counters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
            addView(View(context), LinearLayout.LayoutParams(dp(48), 1))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(phoneChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(msgChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(mailChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(searchChip, LinearLayout.LayoutParams(-2, -2))
        }
        root.addView(counters, LinearLayout.LayoutParams(-1, -2))

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
            setPadding(dp(4), dp(8), dp(4), dp(8))
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

        // Folder pager: previous (dim) | current | next (dim)
        pagerPrev = pagerLabel(18f, Theme.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL) { shiftCategory(-1) }
        pagerCur = pagerLabel(22f, Theme.TEXT, Gravity.CENTER) { }
        pagerNext = pagerLabel(18f, Theme.TEXT_DIM, Gravity.END or Gravity.CENTER_VERTICAL) { shiftCategory(1) }
        val pager = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(6), dp(12), dp(2))
            addView(pagerPrev, LinearLayout.LayoutParams(0, -2, 1f))
            addView(pagerCur, LinearLayout.LayoutParams(0, -2, 1.3f))
            addView(pagerNext, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(pager, LinearLayout.LayoutParams(-1, -2))

        // Dock (first favourites)
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Theme.BAR)
            setPadding(dp(4), dp(2), dp(4), dp(2))
            visibility = View.GONE
        }
        root.addView(dock, LinearLayout.LayoutParams(-1, -2))

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

    private fun pagerLabel(size: Float, color: Int, gravity: Int, onClick: () -> Unit) = TextView(this).apply {
        textSize = size; setTextColor(color); this.gravity = gravity
        maxLines = 1; setShadowLayer(3f, 0f, 1f, Color.BLACK)
        setPadding(0, dp(4), 0, dp(4))
        setOnClickListener { onClick() }
    }

    private fun applyColumns() {
        val wDp = resources.configuration.screenWidthDp
        grid.columns = (wDp / 60).coerceIn(4, 8)
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

    private fun updatePager() {
        val all = Category.values()
        pagerPrev.text = all[(category.ordinal + all.size - 1) % all.size].title
        pagerCur.text = category.title
        pagerNext.text = all[(category.ordinal + 1) % all.size].title
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
                    val label = it.loadLabel(pm).toString()
                    val themed = BbIcons.nameFor(it.activityInfo.packageName, label)
                    AppEntry(
                        label,
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                        (themed?.let { n -> BbIcons.drawable(this, n) }) ?: it.loadIcon(pm),
                        ai.category,
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0,
                        themed
                    )
                }
                .sortedBy { it.label.lowercase() }
            val key = list.joinToString("|") { it.key + it.label }
            handler.post {
                if (isDestroyed) return@post
                if (key != appsKey) {
                    appsKey = key; apps = list
                    seedFavorites()
                    rebuildGrid()
                } else if (category == Category.FREQUENT) rebuildGrid()
            }
        }.start()
    }

    /** First run: put phone, messages, browser, camera and settings in the dock, like a fresh BB home screen. */
    private fun seedFavorites() {
        if (prefs.contains("favs")) return
        val seed = listOf("phone", "messages", "browser", "camera", "settings")
            .mapNotNull { n -> apps.firstOrNull { it.themed == n }?.key }
        prefs.edit().putStringSet("favs", seed.toSet()).apply()
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
                    it.category == ApplicationInfo.CATEGORY_IMAGE ||
                    it.themed == "music" || it.themed == "video" || it.themed == "gallery"
            }
            Category.GAMES -> apps.filter { it.category == ApplicationInfo.CATEGORY_GAME }
            Category.DOWNLOADS -> apps.filter { it.isUser }
            Category.FREQUENT -> apps.map { it to launchCount(it) }
                .filter { it.second > 0 }.sortedByDescending { it.second }.take(12).map { it.first }
        }
    }

    private fun wireTile(t: IconTile) {
        val e = t.entry
        t.setOnClickListener { launch(e) }
        t.setOnLongClickListener { showOptions(e); true }
    }

    private fun rebuildGrid() {
        val focus = currentFocus
        val wasGrid = focus == null || focus is IconTile
        val focusedKey = (focus as? IconTile)?.entry?.key
        grid.removeAllViews()
        val list = filtered()
        list.forEach { e -> grid.addView(IconTile(this, e).also { wireTile(it) }) }
        emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = when {
            searching -> "No matches"
            category == Category.FAVORITES -> "No favourites yet — press X on an app to add one"
            category == Category.FREQUENT -> "Apps you use often will appear here"
            else -> "Nothing in this folder"
        }
        rebuildDock()
        if (wasGrid) {
            val target = (0 until grid.childCount).map { grid.getChildAt(it) as IconTile }
                .firstOrNull { it.entry.key == focusedKey } ?: grid.getChildAt(0)
            target?.requestFocus()
        }
    }

    private fun rebuildDock() {
        dock.removeAllViews()
        val favs = favorites()
        apps.filter { it.key in favs }.take(6).forEach { e ->
            val t = IconTile(this, e, showLabel = false, iconDp = 44).also { wireTile(it) }
            dock.addView(t, LinearLayout.LayoutParams(dp(64), dp(56)))
        }
        dock.visibility = if (dock.childCount > 0 && !searching) View.VISIBLE else View.GONE
    }

    private fun setCategory(c: Category) {
        category = c
        updatePager()
        homeScroll.scrollTo(0, 0)
        rebuildGrid()
        focusFirst()
    }

    private fun focusFirst() { grid.getChildAt(0)?.requestFocus() }

    // ───────────────────────── counters (phone / messages / mail) ─────────────────────────

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.split(":").any { ComponentName.unflattenFromString(it)?.packageName == packageName }
    }

    private fun activeNotifs(): List<StatusBarNotification> = HubService.current().filter {
        !it.isOngoing && it.packageName != packageName &&
            (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
    }

    private fun kindOf(n: StatusBarNotification): String? {
        val cat = n.notification.category
        val w = n.packageName.lowercase().split(Regex("[^a-z]+")).toSet()
        return when {
            cat == Notification.CATEGORY_CALL || cat == "missed_call" || "dialer" in w || "telecom" in w -> "phone"
            cat == Notification.CATEGORY_EMAIL || "gm" in w || "email" in w || "outlook" in w || "mail" in w -> "mail"
            cat == Notification.CATEGORY_MESSAGE || "messaging" in w || "mms" in w || "sms" in w ||
                "whatsapp" in w || "telegram" in w || "signal" in w || "messenger" in w -> "msg"
            else -> null
        }
    }

    private fun refreshCounters() {
        if (!::phoneChip.isInitialized) return
        if (!listenerEnabled()) {
            listOf(phoneChip, msgChip, mailChip).forEach { it.setCount("–") }
            return
        }
        val counts = activeNotifs().mapNotNull { kindOf(it) }.groupingBy { it }.eachCount()
        phoneChip.setCount((counts["phone"] ?: 0).toString())
        msgChip.setCount((counts["msg"] ?: 0).toString())
        mailChip.setCount((counts["mail"] ?: 0).toString())
    }

    private fun clearCounter(kind: String) {
        if (!listenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            return
        }
        activeNotifs().filter { kindOf(it) == kind }.forEach { HubService.instance?.cancelNotification(it.key) }
        refreshCounters()
    }

    private fun startFirst(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(this, "No app available", Toast.LENGTH_SHORT).show() }
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
            (if (isFav) "Remove from Favourites" else "Add to Favourites") to {
                if (isFav) favs.remove(e.key) else favs.add(e.key)
                prefs.edit().putStringSet("favs", favs).apply()
                rebuildGrid()
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
            is CounterChip -> if (f.kind != "search") clearCounter(f.kind)
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
