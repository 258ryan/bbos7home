package com.example.slatelauncher

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.text.format.DateUtils
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.provider.AlarmClock
import java.util.Date
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
import android.os.Build
import android.text.format.DateFormat
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

    private companion object {
        const val REQ_BIND = 101
        const val REQ_CONFIG = 102
        const val REQ_IMAGE = 103
    }

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
    private lateinit var captionFloat: TextView
    private lateinit var searchBar: EditText
    private lateinit var batteryText: TextView
    private lateinit var hintBar: LinearLayout
    private lateinit var phoneChip: CounterChip
    private lateinit var alarmChip: CounterChip
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private var pendingWidgetId = -1
    private lateinit var msgChip: CounterChip
    private lateinit var mailChip: CounterChip
    private lateinit var pagerPrev: TextView
    private lateinit var pagerCur: TextView
    private lateinit var pagerNext: TextView

    private var apps: List<AppEntry> = emptyList()
    private var appsKey = ""
    private var texturesBuilt = true
    private var pendingIconKey = ""
    private var category = Category.ALL
    private var searching = false
    private var expanded = false
    private var cols = 6
    private var cellDp = 60
    private var barCellDp = 90

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refreshAlarm()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updateBattery(i)
    }

    // ───────────────────────── lifecycle ─────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pm = packageManager
        imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        appWidgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(this, 1024)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        Materials.enabled = Prefs.bool(this, Prefs.TEXTURES, true)
        texturesBuilt = Materials.enabled
        setContentView(buildUi())
        computeColumns()
        updatePager()
        applyMode()
    }

    override fun onStart() {
        super.onStart()
        updateBattery(registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        HubService.onChange = { handler.post { refreshCounters(); if (category == Category.HUB) rebuildGrid() } }
        refreshCounters()
        registerReceiver(alarmReceiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        refreshAlarm()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.bool(this, Prefs.TEXTURES, true) != texturesBuilt) { recreate(); return }
        if (category != Category.ALL && category !in cats()) category = Category.ALL
        computeColumns()
        updatePager()
        applyMode()
        refreshApps()
        refreshCounters()
        if (category == Category.QUICK) refreshQuick()
        if (!listenerEnabled() && !prefs.getBoolean("hinted", false)) {
            prefs.edit().putBoolean("hinted", true).apply()
            Toast.makeText(this, "Press X on the counters to enable notification counts", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(alarmReceiver) } catch (_: Exception) {}
        try { widgetHost.stopListening() } catch (_: Exception) {}
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

    @Suppress("DEPRECATION")
    private fun buildUi(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bannerBackground(this@MainActivity)
        }

        // Header: date (left), big clock (centre), battery (right)
        val header = FrameLayout(this).apply { setPadding(dp(12), dp(2), dp(12), dp(2)) }
        val clockBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        clockBox.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 44f; setTextColor(Theme.TEXT)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            includeFontPadding = false
        })
        if (!DateFormat.is24HourFormat(this)) clockBox.addView(TextClock(this).apply {
            format12Hour = "a"; format24Hour = "a"
            textSize = 14f; setTextColor(Theme.TEXT); setPadding(dp(3), 0, 0, dp(6))
        })
        header.addView(clockBox, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        header.addView(TextClock(this).apply {
            format12Hour = "EEE, MMM d"; format24Hour = "EEE, MMM d"
            textSize = 14f; setTextColor(Theme.TEXT); setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(4) })
        batteryText = TextView(this).apply { textSize = 14f; setTextColor(Theme.TEXT); setShadowLayer(4f, 0f, 1f, Color.BLACK) }
        header.addView(batteryText, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(4) })
        banner.addView(header, LinearLayout.LayoutParams(-1, -2))

        // Counters: sound profile (left), phone / messages / mail (centre), search (right)
        addHairline(banner)
        phoneChip = CounterChip(this, BbIcons.drawable(this, "phone"), "phone")
        msgChip = CounterChip(this, BbIcons.drawable(this, "messages"), "msg")
        mailChip = CounterChip(this, BbIcons.drawable(this, "mail"), "mail")
        phoneChip.setOnClickListener { startFirst(Intent(Intent.ACTION_DIAL)) }
        msgChip.setOnClickListener { startFirst(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)) }
        mailChip.setOnClickListener { startFirst(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)) }
        val searchChip = CounterChip(this, BbIcons.drawable(this, "search"), "search").apply {
            setOnClickListener { if (searching) closeSearch() else openSearch("") }
        }
        alarmChip = CounterChip(this, BbIcons.drawable(this, "clock"), "alarm").apply {
            visibility = View.GONE
            setOnClickListener { startFirst(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        }
        val soundChip = CounterChip(this, BbIcons.drawable(this, "sound"), "sound").apply {
            setOnClickListener { setCategory(Category.QUICK) }
        }
        val counters = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(2))
            addView(soundChip, LinearLayout.LayoutParams(-2, -2))
            addView(alarmChip, LinearLayout.LayoutParams(-2, -2))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(phoneChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(msgChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(mailChip, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), 0, dp(4), 0) })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(searchChip, LinearLayout.LayoutParams(-2, -2))
        }
        banner.addView(counters, LinearLayout.LayoutParams(-1, -2))
        root.addView(banner, LinearLayout.LayoutParams(-1, -2))
        addHairline(root)

        // Search bar (hidden until used)
        searchBar = EditText(this).apply {
            hint = "Search"
            setHintTextColor(Theme.TEXT_DIM); setTextColor(Theme.TEXT)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = Materials.recessed(context, 5f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
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
        captionFloat = TextView(this).apply {
            textSize = 14f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER
            maxLines = 1; visibility = View.GONE
        }
        midSpacer = FrameLayout(this).apply {
            addView(captionFloat, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
        root.addView(midSpacer, LinearLayout.LayoutParams(-1, 0, 1f))

        // Folder strip: previous (dim) | current | next (dim)
        addHairline(root)
        pagerPrev = pagerLabel(18f, Theme.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL) { shiftCategory(-1) }
        pagerCur = pagerLabel(22f, Theme.TEXT, Gravity.CENTER) { }
        pagerNext = pagerLabel(18f, Theme.TEXT_DIM, Gravity.END or Gravity.CENTER_VERTICAL) { shiftCategory(1) }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = Materials.metalBand(context)
            addView(pagerPrev, LinearLayout.LayoutParams(0, -2, 1f))
            addView(pagerCur, LinearLayout.LayoutParams(0, -2, 1.3f))
            addView(pagerNext, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(strip, LinearLayout.LayoutParams(-1, -2))
        addHairline(root)

        // Name of the focused icon (expanded view only; collapsed uses captionFloat above the strip)
        caption = TextView(this).apply {
            visibility = View.GONE
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
            background = Materials.stitchedLeather(context, 4)
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
            background = Materials.dimLeather(context, 250)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        root.addView(hintBar, LinearLayout.LayoutParams(-1, -2))
        root.setOnApplyWindowInsetsListener { _, ins ->
            banner.setPadding(0, ins.systemWindowInsetTop, 0, 0)
            hintBar.setPadding(dp(6), dp(6), dp(6), dp(6) + ins.systemWindowInsetBottom)
            ins
        }
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
        val forced = Prefs.int(this, Prefs.COLS, 0)
        cols = if (forced > 0) forced.coerceIn(3, 10) else (wDp / 70).coerceIn(5, 9)
        cellDp = wDp / cols
        val barCols = if (forced > 0) forced.coerceIn(3, 8) else (wDp / 90).coerceIn(4, 8)
        barCellDp = wDp / barCols
    }

    /** Show/hide pieces for collapsed (app bar over wallpaper) vs expanded (dimmed full grid). */
    private fun applyMode() {
        val full = isFull()
        midSpacer.visibility = if (full) View.GONE else View.VISIBLE
        barScroll.visibility = if (full) View.GONE else View.VISIBLE
        caption.visibility = if (full) View.VISIBLE else View.GONE
        homeScroll.visibility = if (full) View.VISIBLE else View.GONE
        if (full) root.background = Materials.dimLeather(this, Prefs.int(this, Prefs.DIM, 92) * 255 / 100)
        else root.setBackgroundColor(Color.TRANSPARENT)
        hintBar.visibility = if (Prefs.bool(this, Prefs.HINTS, true)) View.VISIBLE else View.GONE
        updateHints()
    }

    private fun hint(btn: String, label: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = btn; textSize = 10f; setTextColor(Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 1f, Color.BLACK)
            minWidth = dp(24); setPadding(dp(7), dp(2), dp(7), dp(4))
            background = ChromeDrawable(context, if (btn.length == 1) 11f else 6f, dark = true)
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
            category == Category.GAMES -> listOf("A" to "Play", "X" to "Options", "B" to "Back", "L1/R1" to "Folders")
            category == Category.HUB -> listOf("A" to "Open", "X" to "Options", "B" to "Back", "L1/R1" to "Folders")
            category == Category.QUICK -> listOf("A" to "Change", "X" to "Lower", "B" to "Back", "L1/R1" to "Folders")
            category == Category.WIDGETS -> listOf("A" to "Add", "X" to "Options", "B" to "Back", "L1/R1" to "Folders")
            expanded -> listOf("A" to "Open", "X" to "Options", "B" to "Collapse", "L1/R1" to "Folders")
            else -> listOf("A" to "Open", "X" to "Options", "↓" to "All apps", "L1/R1" to "Folders")
        }
        items.forEach { (b, l) ->
            hintBar.addView(hint(b, l), LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(8), 0, dp(8), 0) })
        }
    }

    /** Folders shown in the strip (the user can hide any except "All"). */
    private fun cats(): List<Category> {
        val hidden = Prefs.strSet(this, Prefs.FOLDERS_HIDDEN)
        return Category.values().filter { it == Category.ALL || it.name !in hidden }
    }

    private fun updatePager() {
        val all = cats()
        val cur = all.indexOf(category).let { if (it < 0) 0 else it }
        pagerPrev.text = all[(cur + all.size - 1) % all.size].title
        pagerCur.text = category.title
        pagerNext.text = all[(cur + 1) % all.size].title
    }


    private fun updateBattery(i: Intent?) {
        if (i == null) return
        val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (lvl < 0 || scale <= 0) return
        val pct = lvl * 100 / scale
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        // OS 7 shows the battery indicator only when it is low or charging.
        batteryText.text = if (charging) "⚡ $pct%" else "$pct%"
        batteryText.visibility = if (charging || pct <= 20) View.VISIBLE else View.INVISIBLE
    }

    // ───────────────────────── apps, folders, grid ─────────────────────────

    @Suppress("DEPRECATION")
    private fun refreshApps() {
        Thread {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val raw = pm.queryIntentActivities(q, 0).filter { it.activityInfo.name != MainActivity::class.java.name }
            val rev = Prefs.int(this, Prefs.ICON_REV, 0)
            val probe = raw.joinToString("|") { it.activityInfo.packageName + "/" + it.activityInfo.name } + "#" + rev
            if (probe == appsKey) {
                handler.post { if (!isDestroyed && category == Category.FREQUENT) rebuildGrid() }
                return@Thread
            }
            val hidden = IconStore.hidden(this)
            val labels = IconStore.labelOverrides(this)
            val list = raw.mapNotNull {
                val cn = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                if (cn.flattenToString() in hidden) return@mapNotNull null
                val ai = it.activityInfo.applicationInfo
                val label = labels.optString(cn.flattenToString(), "").ifEmpty { it.loadLabel(pm).toString() }
                val themed = BbIcons.nameFor(cn.packageName, label)
                AppEntry(
                    label, cn, IconStore.resolve(this, cn, it.loadIcon(pm), themed),
                    ai.category, (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0, themed
                )
            }.sortedBy { it.label.lowercase() }
            handler.post {
                if (isDestroyed) return@post
                appsKey = probe; apps = list
                seedFavorites()
                if (romCache != null) refreshGameList()
                rebuildGrid()
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

    private fun isGame(e: AppEntry) = Games.isAndroidGame(this, e)

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
            Category.DOWNLOADS -> listOf(null to apps.filter { it.isUser })
            Category.WIDGETS, Category.HUB, Category.QUICK, Category.GAMES -> emptyList()
        }
        return out.filter { it.second.isNotEmpty() }
    }

    private fun makeTile(e: AppEntry, bar: Boolean = false): IconTile {
        val t = if (bar) IconTile(this, e, (barCellDp * 0.62f).toInt(), showLabel = Prefs.bool(this, Prefs.BAR_LABELS, true))
        else IconTile(this, e, (cellDp * 0.8f).toInt())
        t.setOnClickListener { launch(e) }
        t.setOnLongClickListener { showOptions(e); true }
        t.setOnFocusChangeListener { _, has -> if (has) { caption.text = e.label; captionFloat.text = e.label } }
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

    private fun isPanel() = category == Category.WIDGETS || category == Category.HUB || category == Category.QUICK || category == Category.GAMES

    private fun isFull() = expanded || searching || isPanel()

    private fun allTiles(): List<IconTile> =
        if (isFull()) (0 until gridHost.childCount).map { gridHost.getChildAt(it) }
            .filterIsInstance<TileGrid>().flatMap { g -> (0 until g.childCount).map { g.getChildAt(it) as IconTile } }
        else (0 until barRow.childCount).map { barRow.getChildAt(it) as IconTile }

    private fun rebuildGrid() {
        if (!::root.isInitialized) return
        val focus = currentFocus
        val wasGrid = focus == null || focus is IconTile || focus is WidgetFrame || focus is HubRow ||
            focus is QuickTile || focus is CoverTile || focus?.tag == "addwidget" || focus?.tag == "gamecat"
        val panelIdx = if (focus?.parent === gridHost) gridHost.indexOfChild(focus) else -1
        val focusedKey = (focus as? IconTile)?.entry?.key
        barRow.removeAllViews()
        gridHost.removeAllViews()
        val secs = sections()
        val flat = secs.flatMap { it.second }
        if (isPanel() && !searching) {
            buildPanel()
        } else if (expanded || searching) {
            secs.forEach { (title, list) ->
                if (title != null) gridHost.addView(sectionHeader(title))
                val g = TileGrid(this).apply { columns = cols }
                list.forEach { g.addView(makeTile(it)) }
                gridHost.addView(g, LinearLayout.LayoutParams(-1, -2))
            }
        } else {
            val w = dp(barCellDp)
            flat.forEach { barRow.addView(makeTile(it, bar = true), LinearLayout.LayoutParams(w, (w * 1.2f).toInt())) }
        }
        val panel = isPanel() && !searching
        emptyText.visibility = if (flat.isEmpty() && !panel) View.VISIBLE else View.GONE
        emptyText.text = when {
            searching -> "No matches"
            category == Category.FAVORITES -> "No favourites yet — press X on an app to add one"
            category == Category.FREQUENT -> "Apps you use often will appear here"
            else -> "Nothing in this folder"
        }
        if (flat.isEmpty()) { caption.text = ""; captionFloat.text = "" }
        if (wasGrid && panel) {
            val keep = if (panelIdx >= 0) gridHost.getChildAt(panelIdx.coerceAtMost(gridHost.childCount - 1)) else null
            (if (category == Category.GAMES) gameFocusRow() else (keep?.takeIf { it.isFocusable } ?: firstFocusableIn(gridHost)))?.requestFocus()
        } else if (wasGrid) {
            val tiles = allTiles()
            (tiles.firstOrNull { it.entry.key == focusedKey } ?: tiles.firstOrNull())?.requestFocus()
        }
    }

    private fun setCategory(c: Category) {
        category = c
        updatePager()
        applyMode()
        homeScroll.scrollTo(0, 0)
        barScroll.scrollTo(0, 0)
        rebuildGrid()
        focusFirst()
    }

    private fun focusFirst() {
        if (isPanel() && !searching) (if (category == Category.GAMES) gameFocusRow() else firstFocusableIn(gridHost))?.requestFocus()
        else allTiles().firstOrNull()?.requestFocus()
    }

    private fun setExpanded(v: Boolean) {
        if (expanded == v) return
        expanded = v
        applyMode()
        rebuildGrid()
        homeScroll.scrollTo(0, 0)
        barScroll.scrollTo(0, 0)
    }

    // ───────────────────────── Games page ─────────────────────────

    private var romCache: List<Game>? = null
    private var gameList: List<Game> = emptyList()
    private var gameCat = "recent"
    private val gameCatRows = ArrayList<TextView>()
    private lateinit var gameGrid: TileGrid
    private lateinit var gameInfo: TextView

    private fun refreshGameList() {
        gameList = Games.androidGames(this, apps) + (romCache ?: emptyList())
    }

    private fun scanGames() {
        Thread {
            val roms = try { Games.scanRoms(this) } catch (_: Exception) { emptyList() }
            handler.post {
                if (isDestroyed) return@post
                romCache = roms
                refreshGameList()
                if (category == Category.GAMES) rebuildGrid()
            }
        }.start()
    }

    private fun gamesIn(id: String): List<Game> = when {
        id == "recent" -> gameList.filter { Games.lastPlayed(this, it) > 0 }.sortedByDescending { Games.lastPlayed(this, it) }.take(30)
        id == "favs" -> { val f = Games.favs(this); gameList.filter { it.id in f } }
        id == "android" -> gameList.filter { it.app != null }
        id.startsWith("sys:") -> gameList.filter { it.systemKey == id.removePrefix("sys:") }
        else -> gameList
    }

    private fun gameCats(): List<Triple<String, String, Int>> {
        val out = ArrayList<Triple<String, String, Int>>()
        gamesIn("recent").size.let { if (it > 0) out.add(Triple("recent", "Recent", it)) }
        out.add(Triple("favs", "Favourites", gamesIn("favs").size))
        out.add(Triple("all", "All games", gameList.size))
        gamesIn("android").size.let { if (it > 0) out.add(Triple("android", "Android", it)) }
        GameSystems.all.forEach { d -> gamesIn("sys:" + d.key).size.let { if (it > 0) out.add(Triple("sys:" + d.key, d.label, it)) } }
        return out
    }

    private fun gameFocusRow(): View? = gameCatRows.firstOrNull { it.isSelected } ?: gameCatRows.firstOrNull()

    private fun buildGamesPanel() {
        gameCatRows.clear()
        if (romCache == null) {
            gridHost.addView(TextView(this).apply {
                text = "Looking for games…"; textSize = 15f; setTextColor(Theme.TEXT_DIM); gravity = Gravity.CENTER
                setPadding(dp(16), dp(32), dp(16), dp(32))
            }, LinearLayout.LayoutParams(-1, -2))
            refreshGameList()
            scanGames()
            return
        }
        val cats = gameCats()
        if (cats.none { it.first == gameCat }) gameCat = cats.first().first
        val wDp = resources.configuration.screenWidthDp
        val leftDp = (wDp * 0.27f).toInt().coerceIn(104, 200)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        cats.forEach { (id, title, n) ->
            val r = TextView(this).apply {
                text = "$title  ($n)"; textSize = 14f; setTextColor(Theme.TEXT); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(8), dp(10)); maxLines = 2
                isFocusable = true; isClickable = true; tag = "gamecat"; isSelected = id == gameCat
                background = navRowBackground(this@MainActivity)
                setOnFocusChangeListener { _, has ->
                    if (has && gameCat != id) { gameCat = id; gameCatRows.forEach { it.isSelected = it === this }; fillGameGrid() }
                }
                setOnClickListener { gameGrid.getChildAt(0)?.requestFocus() }
            }
            gameCatRows.add(r)
            left.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        gameInfo = TextView(this).apply {
            textSize = 13f; setTextColor(Theme.TEXT_DIM); setPadding(dp(10), dp(6), dp(10), dp(4)); minLines = 2
        }
        right.addView(gameInfo, LinearLayout.LayoutParams(-1, -2))
        gameGrid = TileGrid(this).apply { columns = ((wDp - leftDp - 16) / 92).coerceIn(2, 6); aspect = 1.3f }
        right.addView(gameGrid, LinearLayout.LayoutParams(-1, -2))
        row.addView(left, LinearLayout.LayoutParams(dp(leftDp), -2))
        row.addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        gridHost.addView(row, LinearLayout.LayoutParams(-1, -2))
        fillGameGrid()
    }

    private fun gameDescription(g: Game): String {
        val n = Games.played(this, g)
        val last = Games.lastPlayed(this, g)
        val played = if (n == 0) "not played yet" else "played $n× · " +
            DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        return "${g.title}\n${g.systemLabel} · $played"
    }

    private fun fillGameGrid() {
        gameGrid.removeAllViews()
        val favs = Games.favs(this)
        val list = gamesIn(gameCat)
        gameInfo.text = (gameCatRows.firstOrNull { it.isSelected }?.text?.toString() ?: "") +
            if (list.isEmpty()) "\nNo games here yet. Put ROMs in a system folder (see Launcher settings → Games)." else "\nA to play, X for options"
        list.forEach { g ->
            val t = CoverTile(this, g, g.id in favs)
            t.setOnClickListener { playGame(g) }
            t.setOnLongClickListener { gameMenu(g); true }
            t.setOnFocusChangeListener { _, has -> if (has) gameInfo.text = gameDescription(g) }
            gameGrid.addView(t)
        }
    }

    private fun playGame(g: Game) {
        val err = Games.launch(this, g)
        if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show()
        else if (g.app != null) prefs.edit().putInt("cnt_" + g.app.key, launchCount(g.app) + 1).apply()
    }

    private fun gameMenu(g: Game) {
        val fav = g.id in Games.favs(this)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += "Play" to { playGame(g) }
        actions += (if (fav) "Remove from Favourites" else "Add to Favourites") to { Games.toggleFav(this, g); rebuildGrid() }
        g.app?.let { a -> actions += "Remove from Games" to { Games.setTag(this, a, false); refreshGameList(); rebuildGrid() } }
        actions += "Rescan games" to { romCache = null; rebuildGrid() }
        showMenu(g.title, actions)
    }

    // ───────────────────────── panels: Hub & Quick Settings ─────────────────────────

    private fun firstFocusableIn(v: View): View? {
        if (v.isFocusable) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) firstFocusableIn(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun buildPanel() {
        when (category) {
            Category.WIDGETS -> buildWidgetPanel()
            Category.HUB -> buildHubPanel()
            Category.QUICK -> buildQuickPanel()
            Category.GAMES -> buildGamesPanel()
            else -> {}
        }
    }

    private fun rowLp() = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(3), dp(8), dp(3)) }

    // ── Hub: every notification in one list ──

    private fun buildHubPanel() {
        if (!listenerEnabled()) {
            val row = HubRow(this, BbIcons.drawable(this, "settings"), "Notification access is off",
                "Press A to turn it on so notifications appear here.", "", null)
            row.setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            gridHost.addView(row, rowLp())
            return
        }
        val list = activeNotifs().sortedByDescending { it.postTime }
        if (list.isEmpty()) {
            gridHost.addView(TextView(this).apply {
                text = "No new notifications"; textSize = 15f; setTextColor(Theme.TEXT_DIM)
                gravity = Gravity.CENTER; setPadding(dp(16), dp(32), dp(16), dp(32))
            }, LinearLayout.LayoutParams(-1, -2))
            return
        }
        val now = System.currentTimeMillis()
        list.forEach { n ->
            val ex = n.notification.extras
            val title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: appLabel(n.packageName)
            val body = (ex?.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex?.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            val icon = try { pm.getApplicationIcon(n.packageName) } catch (_: Exception) { null }
            val meta = appLabel(n.packageName) + "  ·  " +
                DateUtils.getRelativeTimeSpanString(n.postTime, now, DateUtils.MINUTE_IN_MILLIS)
            val row = HubRow(this, icon, title, body, meta, n.key)
            row.setOnClickListener { openNotification(n.key) }
            row.setOnLongClickListener { showHubMenu(n.key); true }
            gridHost.addView(row, rowLp())
        }
    }

    private fun openNotification(key: String) {
        val n = activeNotifs().firstOrNull { it.key == key } ?: return
        try {
            val pi = n.notification.contentIntent
            if (pi != null) pi.send()
            else pm.getLaunchIntentForPackage(n.packageName)?.let { startActivity(it) }
            if ((n.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) HubService.instance?.cancelNotification(key)
        } catch (_: Exception) {
            Toast.makeText(this, "Can't open", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHubMenu(key: String) {
        val n = activeNotifs().firstOrNull { it.key == key } ?: return
        val pkg = n.packageName
        showMenu(appLabel(pkg), listOf<Pair<String, () -> Unit>>(
            "Open" to { openNotification(key) },
            "Dismiss" to { HubService.instance?.cancelNotification(key) },
            "Dismiss all from ${appLabel(pkg)}" to {
                activeNotifs().filter { it.packageName == pkg }.forEach { HubService.instance?.cancelNotification(it.key) }
            },
            "Clear all notifications" to { HubService.instance?.cancelAllNotifications() }
        ))
    }

    // ── Quick Settings: toggles that Android lets a launcher change ──

    private val quickIds = listOf("sound", "volume", "brightness", "wifi", "bluetooth", "airplane", "dnd",
        "rotation", "torch", "battery", "slate", "settings")
    private val quickTiles = HashMap<String, QuickTile>()
    private var torchOn = false

    private fun audio() = getSystemService(AUDIO_SERVICE) as AudioManager

    private fun quickMeta(id: String): Triple<String, String?, String?> = when (id) {
        "sound" -> Triple("Sound profile", "ui:tango_audio-volume-high", null)
        "volume" -> Triple("Media volume", "ui:tango_audio-volume-medium", null)
        "brightness" -> Triple("Brightness", "ui:tango_weather-clear", null)
        "wifi" -> Triple("Wi-Fi", "ui:tango_network-wireless", null)
        "bluetooth" -> Triple("Bluetooth", "ui:elem_bluetooth", null)
        "airplane" -> Triple("Airplane mode", "ui:sym_airplane-mode", null)
        "dnd" -> Triple("Do not disturb", "dnd", null)
        "rotation" -> Triple("Rotation", "ui:elem_object-rotate-right", null)
        "torch" -> Triple("Flashlight", "ui:sym_keyboard-brightness", null)
        "battery" -> Triple("Battery saver", "ui:tango_battery", null)
        "slate" -> Triple("Launcher settings", "ui:elem_preferences-desktop-theme", null)
        else -> Triple("All settings", "ui:tango_applications-system", null)
    }

    private fun buildQuickPanel() {
        quickTiles.clear()
        val g = TileGrid(this).apply {
            columns = (resources.configuration.screenWidthDp / 100).coerceIn(3, 6)
            aspect = 1.15f
        }
        quickIds.forEach { id ->
            val (title, icon, glyph) = quickMeta(id)
            val t = QuickTile(this, id, title, icon, glyph)
            t.setOnClickListener { quickPrimary(id); refreshQuick() }
            t.setOnLongClickListener { quickSecondary(id); refreshQuick(); true }
            quickTiles[id] = t
            g.addView(t)
        }
        gridHost.addView(g, LinearLayout.LayoutParams(-1, -2))
        refreshQuick()
    }

    private fun refreshQuick() {
        quickTiles.forEach { (id, t) ->
            t.setState(quickState(id))
            if (id == "sound") t.setIcon(when (audio().ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> "ui:tango_audio-volume-high"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "ui:sym_notification-disabled"
            })
        }
    }

    private fun dndOff(nm: NotificationManager) =
        nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    private fun quickState(id: String): String = when (id) {
        "sound" -> when (audio().ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Silent"
        }
        "volume" -> {
            val a = audio()
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            "${a.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%"
        }
        "brightness" -> "${Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255}%"
        "wifi" -> if ((applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled) "On" else "Off"
        "airplane" -> if (Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1) "On" else "Off"
        "dnd" -> if (dndOff(getSystemService(NOTIFICATION_SERVICE) as NotificationManager)) "Off" else "On"
        "rotation" -> if (Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1) "Auto" else "Locked"
        "torch" -> if (torchOn) "On" else "Off"
        "battery" -> if ((getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode) "On" else "Off"
        else -> ""
    }

    private fun askWriteSettings() {
        Toast.makeText(this, "Allow \"Modify system settings\" for Slate Launcher", Toast.LENGTH_LONG).show()
        startFirst(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun adjustBrightness(delta: Int) {
        if (!Settings.System.canWrite(this)) { askWriteSettings(); return }
        val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = if (delta > 0 && cur >= 255) 8 else (cur + delta).coerceIn(8, 255)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
    }

    private fun stepVolume(up: Boolean) {
        val a = audio()
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = maxOf(1, max / 10)
        val next = if (up) (if (cur >= max) 0 else minOf(max, cur + step)) else maxOf(0, cur - step)
        a.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
    }

    private fun toggleTorch() {
        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        val id = try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (_: Exception) { null }
        if (id == null) { Toast.makeText(this, "No flashlight", Toast.LENGTH_SHORT).show(); return }
        val want = !torchOn
        try { cm.setTorchMode(id, want); torchOn = want } catch (_: Exception) {}
    }

    private fun quickPrimary(id: String) {
        when (id) {
            "sound" -> {
                val a = audio()
                val next = when (a.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                try { a.ringerMode = next } catch (_: SecurityException) {
                    Toast.makeText(this, "Allow Do Not Disturb access to use Silent", Toast.LENGTH_LONG).show()
                    startFirst(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            "volume" -> stepVolume(true)
            "brightness" -> adjustBrightness(32)
            "wifi" -> startFirst(if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> startFirst(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "airplane" -> startFirst(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            "dnd" -> {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(
                        if (dndOff(nm)) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                } else startFirst(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
            "rotation" -> if (!Settings.System.canWrite(this)) askWriteSettings() else {
                val cur = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
                Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1 - cur)
            }
            "torch" -> toggleTorch()
            "battery" -> startFirst(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            "slate" -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> startFirst(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun quickSecondary(id: String) {
        when (id) {
            "volume" -> stepVolume(false)
            "brightness" -> adjustBrightness(-32)
        }
    }

    // ───────────────────────── alarm indicator ─────────────────────────

    private fun refreshAlarm() {
        if (!::alarmChip.isInitialized) return
        val next = (getSystemService(ALARM_SERVICE) as AlarmManager).nextAlarmClock
        if (next == null) { alarmChip.visibility = View.GONE; return }
        val t = next.triggerTime
        val soon = t - System.currentTimeMillis() < 24L * 60 * 60 * 1000
        val fmt = DateFormat.getTimeFormat(this).format(Date(t))
        alarmChip.setCount(if (soon) fmt else DateFormat.format("EEE", t).toString() + " " + fmt)
        alarmChip.visibility = View.VISIBLE
    }

    // ───────────────────────── widgets ─────────────────────────

    private fun widgetIds(): MutableList<Int> =
        (prefs.getString("widgets", "") ?: "").split("|").mapNotNull { it.toIntOrNull() }.toMutableList()

    private fun saveWidgetIds(l: List<Int>) = prefs.edit().putString("widgets", l.joinToString("|")).apply()

    private fun appLabel(pkg: String): String =
        try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }

    private fun showWidgetPicker() {
        val providers = appWidgetManager.installedProviders
            .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .sortedBy { it.loadLabel(pm).lowercase() }
        if (providers.isEmpty()) {
            Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show()
            return
        }
        showMenu("Add widget", providers.map { p ->
            val action: () -> Unit = { beginAddWidget(p) }
            "${p.loadLabel(pm)}  ·  ${appLabel(p.provider.packageName)}" to action
        })
    }

    private fun beginAddWidget(p: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        if (appWidgetManager.bindAppWidgetIdIfAllowed(id, p.provider)) afterBind(id)
        else startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider),
            REQ_BIND
        )
    }

    private fun afterBind(id: Int) {
        val info = appWidgetManager.getAppWidgetInfo(id)
        if (info?.configure != null) widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
        else addWidgetId(id)
    }

    private fun addWidgetId(id: Int) {
        val l = widgetIds(); l.add(id); saveWidgetIds(l)
        pendingWidgetId = -1
        setCategory(Category.WIDGETS)
    }

    private fun removeWidget(id: Int) {
        widgetHost.deleteAppWidgetId(id)
        saveWidgetIds(widgetIds().filter { it != id })
        rebuildGrid()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMAGE) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null && pendingIconKey.isNotEmpty()) {
                val src = IconStore.importImage(this, uri, pendingIconKey)
                if (src != null) { IconStore.setOverride(this, pendingIconKey, src); refreshApps() }
                else Toast.makeText(this, "Couldn't read that image", Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIG) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it >= 0 } ?: pendingWidgetId
        if (id < 0) return
        if (resultCode != RESULT_OK) { widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1; return }
        if (requestCode == REQ_BIND) afterBind(id) else addWidgetId(id)
    }

    private fun buildWidgetPanel() {
        val wDp = resources.configuration.screenWidthDp - 16
        val maxH = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val stale = ArrayList<Int>()
        widgetIds().forEach { id ->
            val info = appWidgetManager.getAppWidgetInfo(id)
            if (info == null) { stale.add(id); return@forEach }
            val hv = widgetHost.createView(this, id, info)
            val hPx = info.minHeight.coerceIn(dp(96), maxH)
            val hDp = (hPx / resources.displayMetrics.density).toInt()
            hv.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
            val frame = WidgetFrame(this, id)
            frame.addView(hv, FrameLayout.LayoutParams(-1, hPx))
            frame.setOnLongClickListener { showWidgetMenu(id); true }
            gridHost.addView(frame, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(4), dp(8), dp(4)) })
        }
        if (stale.isNotEmpty()) saveWidgetIds(widgetIds().filter { it !in stale })
        val add = TextView(this).apply {
            text = "+  Add widget"; textSize = 18f; setTextColor(Theme.TEXT); tag = "addwidget"
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isFocusable = true; isClickable = true
            background = focusGlow(context, 8, 2)
            setOnClickListener { showWidgetPicker() }
        }
        gridHost.addView(add, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(4), dp(8), dp(4)) })
    }

    private fun showWidgetMenu(id: Int) = showMenu("Widget", listOf<Pair<String, () -> Unit>>(
        "Remove widget" to { removeWidget(id) },
        "Add widget" to { showWidgetPicker() }
    ))

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
        val chips = listOf("phone" to phoneChip, "msg" to msgChip, "mail" to mailChip)
        if (!listenerEnabled()) {
            // Access not granted yet: show "–" so X on a counter can lead to the settings page.
            chips.forEach { (_, c) -> c.visibility = View.VISIBLE; c.setCount("–") }
            return
        }
        val counts = activeNotifs().mapNotNull { kindOf(it) }.groupingBy { it }.eachCount()
        chips.forEach { (k, c) ->
            val n = counts[k] ?: 0
            c.setCount(n.toString())
            c.visibility = if (n == 0) View.GONE else View.VISIBLE   // OS 7 only shows active indicators
        }
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
        actions += (if (isGame(e)) "Remove from Games" else "Add to Games") to {
            Games.setTag(this, e, !isGame(e)); refreshGameList(); rebuildGrid()
        }
        actions += "Change icon" to {
            IconPicker.show(this, e.key, e.label, onPickImage = { pendingIconKey = e.key; pickImage() }, onChanged = { refreshApps() })
        }
        actions += "Rename" to {
            Dialogs.prompt(this, "Rename app", e.label, "Name") { n ->
                val o = IconStore.labelOverrides(this)
                if (n.isEmpty()) o.remove(e.key) else o.put(e.key, n)
                Prefs.setJson(this, Prefs.LABEL_OVERRIDES, o); Prefs.bumpIcons(this); refreshApps()
            }
        }
        actions += "Hide" to {
            val h = IconStore.hidden(this); h.add(e.key); Prefs.setStrSet(this, Prefs.HIDDEN, h); Prefs.bumpIcons(this)
            Toast.makeText(this, "${e.label} hidden. Show it again in Launcher settings → Apps.", Toast.LENGTH_LONG).show()
            refreshApps()
        }
        actions += "App info" to {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)))
        }
        actions += "Uninstall" to { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null))) }
        showMenu(e.label, actions)
    }

    private fun pickImage() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_IMAGE
        )
    }

    private fun menuIcon(label: String): String? = when {
        label.startsWith("Add to Fav") || label.startsWith("Remove from Fav") -> "ui:tango_emblem-favorite"
        label == "App info" -> "ui:tango_dialog-information"
        label == "Uninstall" || label == "Hide" || label == "Dismiss" || label.startsWith("Dismiss all") ||
            label.startsWith("Clear all") || label == "Remove widget" -> "ui:tango_edit-delete"
        label == "Change icon" -> "ui:elem_preferences-desktop-theme"
        label == "Rename" -> "ui:tango_document-properties"
        label == "Move left" -> "ui:tango_go-previous"
        label == "Add widget" -> "ui:tango_list-add"
        label.startsWith("Rescan") -> "ui:tango_view-refresh"
        label.contains("Games") -> "ui:elem_applications-games"
        else -> null
    }

    private fun showMenu(title: String, actions: List<Pair<String, () -> Unit>>) {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Materials.stitchedLeather(context, 5)
            setPadding(dp(12), dp(12), dp(12), dp(16))
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(Theme.BLUE_LIGHT)
            setTypeface(typeface, Typeface.BOLD); setPadding(dp(12), dp(4), dp(12), dp(8))
        })
        val rows = actions.map { (label, act) ->
            TextView(this).apply {
                text = label; textSize = 18f; setTextColor(Theme.TEXT)
                setPadding(dp(16), dp(11), dp(16), dp(11))
                isFocusable = true; isClickable = true
                gravity = Gravity.CENTER_VERTICAL
                background = focusGlow(context, 6, 1)
                menuIcon(label)?.let { n -> BbIcons.any(context, n) }?.let { ic ->
                    ic.setBounds(0, 0, dp(28), dp(28)); setCompoundDrawables(ic, null, null, null); compoundDrawablePadding = dp(12)
                }
                setOnClickListener { d.dismiss(); act() }
            }
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { list.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) }) }
        val sv = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        box.addView(sv, LinearLayout.LayoutParams(-1, if (rows.size > 7) (resources.displayMetrics.heightPixels * 0.55f).toInt() else -2))
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        d.setOnKeyListener { _, code, ev ->
            when (Prefs.mapKey(this, code)) {
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
        val all = cats()
        val cur = all.indexOf(category).let { if (it < 0) 0 else it }
        setCategory(all[(cur + delta + all.size) % all.size])
    }

    private fun onConfirm() {
        val f = currentFocus
        if (f === searchBar) { submitSearch(); return }
        if (f != null && f.isClickable) f.performClick()
    }

    private fun onOptions() {
        when (val f = currentFocus) {
            is IconTile -> showOptions(f.entry)
            is WidgetFrame -> showWidgetMenu(f.widgetId)
            is HubRow -> f.key?.let { showHubMenu(it) }
            is QuickTile -> { quickSecondary(f.id); refreshQuick() }
            is CoverTile -> gameMenu(f.game)
            is TextView -> if (f.tag == "addwidget") showWidgetPicker()
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
        when (Prefs.mapKey(this, e.keyCode)) {
            KeyEvent.KEYCODE_BUTTON_A -> { if (up) onConfirm(); return true }
            KeyEvent.KEYCODE_BUTTON_B -> { if (up) handleBack(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { if (up) onOptions(); return true }
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START -> {
                if (up) { if (searching) closeSearch() else openSearch("") }
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if (up) startActivity(Intent(this, SettingsActivity::class.java))
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
