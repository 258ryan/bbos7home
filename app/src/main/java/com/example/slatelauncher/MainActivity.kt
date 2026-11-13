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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * BlackBerry-OS-7-style home screen for gamepads.
 *
 * Collapsed: the current folder is a single scrolling app bar (D-pad left/right).
 * Press Down on it to expand into the full grid; B (or Up from the top row) collapses again.
 *
 *   D-pad / left stick  move      A  open        B  collapse / back to "All"
 *   X  options / clear counter    Y or Start  search      Select  system settings
 *   L1 / R1  previous / next folder
 */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pm: PackageManager
    private lateinit var imm: InputMethodManager
    private val prefs by lazy { getSharedPreferences("slate", MODE_PRIVATE) }

    private lateinit var root: LinearLayout
    private lateinit var midSpacer: View
    private lateinit var barScroll: HorizontalScrollView
    private lateinit var barRow: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var gridHost: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var caption: TextView
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
    private var expanded = false
    private var cols = 6
    private var cellDp = 60

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
        computeColumns()
        updatePager()
        applyMode()
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
            if (searching) { searching = false; closeSearchQuiet() }
            category = Category.ALL
            expanded = false
            updatePager(); applyMode(); rebuildGrid(); focusFirst()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        computeColumns()
        rebuildGrid()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() = handleBack()

    // ───────────────────────── UI construction ─────────────────────────

    private fun hairline() = View(this).apply { setBackgroundColor(Color.parseColor("#44FFFFFF")) }
    private fun addHairline(parent: LinearLayout) = parent.addView(hairline(), LinearLayout.LayoutParams(-1, 1))

    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }

        // Header: date (left), big clock (centre), battery (right)
        val header = FrameLayout(this).apply { setPadding(dp(12), dp(2), dp(12), dp(2)) }
        header.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 44f; setTextColor(Theme.TEXT)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        header.addView(TextClock(this).apply {
            format12Hour = "EEE, MMM d"; format24Hour = "EEE, MMM d"
            textSize = 14f; setTextColor(Theme.TEXT); setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(4) })
        batteryText = TextView(this).apply { textSize = 14f; setTextColor(Theme.TEXT); setShadowLayer(4f, 0f, 1f, Color.BLACK) }
        header.addView(batteryText, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(4) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        // Counters: phone / messages / mail, search at the right
        addHairline(root)
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
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setBackgroundColor(Color.parseColor("#55000000"))
            addView(View(context), LinearLayout.LayoutParams(dp(48), 1))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(phoneChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(msgChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(mailChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(searchChip, LinearLayout.LayoutParams(-2, -2))
        }
        root.addView(counters, LinearLayout.LayoutParams(-1, -2))
        addHairline(root)

        // Search bar (hidden until used)
        searchBar = EditText(this).apply {
            hint = "Search"
            setHintTextColor(Theme.TEXT_DIM); setTextColor(Theme.TEXT)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC101114")); cornerRadius = dpf(4f)
                setStroke(dp(2), Theme.BLUE)
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { if (searching) rebuildGrid() }
            })
            setOnEditorActionListener { _, _, _ -> submitSearch(); true }
        }
        root.addView(searchBar, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10), dp(4), dp(10), dp(2)) })

        // Wallpaper window shown while collapsed
        midSpacer = View(this)
        root.addView(midSpacer, LinearLayout.LayoutParams(-1, 0, 1f))

        // Folder strip: previous (dim) | current | next (dim)
        addHairline(root)
        pagerPrev = pagerLabel(18f, Theme.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL) { shiftCategory(-1) }
        pagerCur = pagerLabel(22f, Theme.TEXT, Gravity.CENTER) { }
        pagerNext = pagerLabel(18f, Theme.TEXT_DIM, Gravity.END or Gravity.CENTER_VERTICAL) { shiftCategory(1) }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setBackgroundColor(Color.parseColor("#66000000"))
            addView(pagerPrev, LinearLayout.LayoutParams(0, -2, 1f))
            addView(pagerCur, LinearLayout.LayoutParams(0, -2, 1.3f))
            addView(pagerNext, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(strip, LinearLayout.LayoutParams(-1, -2))
        addHairline(root)

        // Name of the focused icon
        caption = TextView(this).apply {
            textSize = 13f; setTextColor(Theme.TEXT_DIM); gravity = Gravity.CENTER
            maxLines = 1; setShadowLayer(3f, 0f, 1f, Color.BLACK)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        root.addView(caption, LinearLayout.LayoutParams(-1, -2))

        emptyText = TextView(this).apply {
            setTextColor(Theme.TEXT_DIM); textSize = 15f; gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24)); visibility = View.GONE
        }
        root.addView(emptyText, LinearLayout.LayoutParams(-1, -2))

        // Collapsed: one horizontally scrolling app bar
        barRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        barScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.parseColor("#B0000000"))
            addView(barRow, ViewGroup.LayoutParams(-2, -2))
        }
        root.addView(barScroll, LinearLayout.LayoutParams(-1, -2))

        // Expanded: full grid with folder sections
        gridHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(0, dp(4), 0, dp(8))
        }
        homeScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false; clipToPadding = false
            addView(gridHost, ViewGroup.LayoutParams(-1, -2))
        }
        root.addView(homeScroll, LinearLayout.LayoutParams(-1, 0, 1f))

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

    private fun computeColumns() {
        val wDp = resources.configuration.screenWidthDp
        cols = (wDp / 70).coerceIn(5, 9)
        cellDp = wDp / cols
    }

    /** Show/hide pieces for collapsed (app bar over wallpaper) vs expanded (dimmed full grid). */
    private fun applyMode() {
        val full = expanded || searching
        midSpacer.visibility = if (full) View.GONE else View.VISIBLE
        barScroll.visibility = if (full) View.GONE else View.VISIBLE
        homeScroll.visibility = if (full) View.VISIBLE else View.GONE
        root.setBackgroundColor(if (full) Color.parseColor("#D8000000") else Color.TRANSPARENT)
        updateHints()
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
        val items = when {
            searching -> listOf("A" to "Go", "B" to "Cancel")
            expanded -> listOf("A" to "Open", "X" to "Options", "B" to "Collapse", "L1/R1" to "Folders")
            else -> listOf("A" to "Open", "X" to "Options", "↓" to "All apps", "L1/R1" to "Folders")
        }
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

    // Favourites are an ordered list; they form the first bar in "All", like OS 7's pinned row.
    private fun favorites(): MutableList<String> =
        (prefs.getString("favlist", "") ?: "").split("|").filter { it.isNotEmpty() }.toMutableList()

    private fun saveFavorites(l: List<String>) = prefs.edit().putString("favlist", l.joinToString("|")).apply()

    private fun seedFavorites() {
        if (prefs.contains("favlist")) return
        val seed = listOf("mail", "messages", "contacts", "browser", "video", "calendar", "phone", "camera", "music", "settings")
            .mapNotNull { n -> apps.firstOrNull { it.themed == n }?.key }
        saveFavorites(seed)
    }

    private fun launchCount(e: AppEntry) = prefs.getInt("cnt_" + e.key, 0)

    private fun isMedia(e: AppEntry) =
        e.category == ApplicationInfo.CATEGORY_AUDIO || e.category == ApplicationInfo.CATEGORY_VIDEO ||
            e.category == ApplicationInfo.CATEGORY_IMAGE ||
            e.themed == "music" || e.themed == "video" || e.themed == "gallery" || e.themed == "recorder"

    private fun isGame(e: AppEntry) = e.category == ApplicationInfo.CATEGORY_GAME

    private fun pinned(): List<AppEntry> {
        val by = apps.associateBy { it.key }
        return favorites().mapNotNull { by[it] }
    }

    /** Sections of the current folder. A null title means no folder header. */
    private fun sections(): List<Pair<String?, List<AppEntry>>> {
        val q = searchBar.text.toString().trim()
        if (searching) {
            val r = if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
            return listOf<Pair<String?, List<AppEntry>>>(null to r).filter { it.second.isNotEmpty() }
        }
        val out: List<Pair<String?, List<AppEntry>>> = when (category) {
            Category.ALL -> {
                val pin = pinned()
                val pinKeys = pin.map { it.key }.toSet()
                val rest = apps.filter { it.key !in pinKeys }
                val media = rest.filter { isMedia(it) }
                val r2 = rest.filter { !isMedia(it) }
                val games = r2.filter { isGame(it) }
                val r3 = r2.filter { !isGame(it) }
                val dl = r3.filter { it.isUser }
                val sys = r3.filter { !it.isUser }
                listOf(null to pin, "Multimedia" to media, "Games" to games, "Downloads" to dl, "Applications" to sys)
            }
            Category.FAVORITES -> listOf(null to pinned())
            Category.FREQUENT -> listOf(null to apps.map { it to launchCount(it) }
                .filter { it.second > 0 }.sortedByDescending { it.second }.take(24).map { it.first })
            Category.MEDIA -> listOf(null to apps.filter { isMedia(it) })
            Category.GAMES -> listOf(null to apps.filter { isGame(it) })
            Category.DOWNLOADS -> listOf(null to apps.filter { it.isUser })
        }
        return out.filter { it.second.isNotEmpty() }
    }

    private fun makeTile(e: AppEntry): IconTile {
        val t = IconTile(this, e, (cellDp * 0.8f).toInt())
        t.setOnClickListener { launch(e) }
        t.setOnLongClickListener { showOptions(e); true }
        t.setOnFocusChangeListener { _, has -> if (has) caption.text = e.label }
        return t
    }

    private fun sectionHeader(title: String): View {
        val icon = when (title) {
            "Multimedia" -> "folder_media"
            "Games" -> "games"
            "Downloads" -> "folder_dl"
            else -> "folder_apps"
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(6))
            addView(ImageView(context).apply { setImageDrawable(BbIcons.drawable(context, icon)) }, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(TextView(context).apply {
                text = title; textSize = 20f; setTextColor(Theme.TEXT); setPadding(dp(8), 0, 0, 0)
            })
        }
    }

    private fun allTiles(): List<IconTile> =
        if (expanded || searching) (0 until gridHost.childCount).map { gridHost.getChildAt(it) }
            .filterIsInstance<TileGrid>().flatMap { g -> (0 until g.childCount).map { g.getChildAt(it) as IconTile } }
        else (0 until barRow.childCount).map { barRow.getChildAt(it) as IconTile }

    private fun rebuildGrid() {
        if (!::root.isInitialized) return
        val focus = currentFocus
        val wasGrid = focus == null || focus is IconTile
        val focusedKey = (focus as? IconTile)?.entry?.key
        barRow.removeAllViews()
        gridHost.removeAllViews()
        val secs = sections()
        val flat = secs.flatMap { it.second }
        if (expanded || searching) {
            secs.forEach { (title, list) ->
                if (title != null) gridHost.addView(sectionHeader(title))
                val g = TileGrid(this).apply { columns = cols }
                list.forEach { g.addView(makeTile(it)) }
                gridHost.addView(g, LinearLayout.LayoutParams(-1, -2))
            }
        } else {
            val px = dp(cellDp)
            flat.forEach { barRow.addView(makeTile(it), LinearLayout.LayoutParams(px, px)) }
        }
        emptyText.visibility = if (flat.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = when {
            searching -> "No matches"
            category == Category.FAVORITES -> "No favourites yet — press X on an app to add one"
            category == Category.FREQUENT -> "Apps you use often will appear here"
            else -> "Nothing in this folder"
        }
        if (flat.isEmpty()) caption.text = ""
        if (wasGrid) {
            val tiles = allTiles()
            (tiles.firstOrNull { it.entry.key == focusedKey } ?: tiles.firstOrNull())?.requestFocus()
        }
    }

    private fun setCategory(c: Category) {
        category = c
        updatePager()
        homeScroll.scrollTo(0, 0)
        barScroll.scrollTo(0, 0)
        rebuildGrid()
        focusFirst()
    }

    private fun focusFirst() { allTiles().firstOrNull()?.requestFocus() }

    private fun setExpanded(v: Boolean) {
        if (expanded == v) return
        expanded = v
        applyMode()
        rebuildGrid()
        homeScroll.scrollTo(0, 0)
        barScroll.scrollTo(0, 0)
    }

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
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "Open" to { launch(e) }
        actions += (if (isFav) "Remove from Favourites" else "Add to Favourites") to {
            if (isFav) favs.remove(e.key) else favs.add(e.key)
            saveFavorites(favs)
            rebuildGrid()
        }
        if (isFav && favs.indexOf(e.key) > 0) actions += "Move left" to {
            val i = favs.indexOf(e.key); favs.removeAt(i); favs.add(i - 1, e.key); saveFavorites(favs); rebuildGrid()
        }
        if (isFav && favs.indexOf(e.key) < favs.size - 1) actions += "Move right" to {
            val i = favs.indexOf(e.key); favs.removeAt(i); favs.add(i + 1, e.key); saveFavorites(favs); rebuildGrid()
        }
        actions += "App info" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)))
        }
        actions += "Uninstall" to { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null))) }
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
                background = focusGlow(context, 6, 1)
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
        applyMode()
        rebuildGrid()
        searchBar.requestFocus()
        searchBar.post { imm.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeSearchQuiet() {
        searchBar.setText("")
        searchBar.visibility = View.GONE
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
    }

    private fun closeSearch() {
        searching = false
        expanded = false
        closeSearchQuiet()
        applyMode()
        rebuildGrid()
        focusFirst()
    }

    private fun submitSearch() {
        val list = sections().flatMap { it.second }
        if (list.size == 1) { launch(list[0]); closeSearch() } else focusFirst()
    }

    // ───────────────────────── gamepad input ─────────────────────────

    private fun handleBack() {
        when {
            searching -> closeSearch()
            expanded -> { setExpanded(false); focusFirst() }
            category != Category.ALL -> setCategory(Category.ALL)
            else -> { barScroll.smoothScrollTo(0, 0); focusFirst() }
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

    /** Is [f] a tile in the very first row of the expanded grid? */
    private fun inTopRow(f: View): Boolean {
        val p = f.parent
        return p is TileGrid && gridHost.getChildAt(0) === p && f.top < f.height
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val up = e.action == KeyEvent.ACTION_UP
        val down = e.action == KeyEvent.ACTION_DOWN
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
            // Down on the app bar expands to the grid; Up from the grid's top row collapses it.
            KeyEvent.KEYCODE_DPAD_DOWN -> if (!expanded && !searching && currentFocus is IconTile) {
                if (down && e.repeatCount == 0) setExpanded(true)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                val f = currentFocus
                if (expanded && !searching && f is IconTile && inTopRow(f)) {
                    if (down && e.repeatCount == 0) { setExpanded(false); focusFirst() }
                    return true
                }
            }
        }
        // Hardware keyboard: start typing anywhere to search.
        if (down && !searching && e.repeatCount == 0 && e.isPrintingKey &&
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
