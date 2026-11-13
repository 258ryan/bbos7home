package com.example.slatelauncher

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Enable / select the Slate keyboard and try it out. */
class KeyboardSetupActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bbkb", MODE_PRIVATE)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        fun label(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }
        col.addView(label("Slate Keyboard", 26f, bold = true))
        status = label("", 14f, Theme.BLUE_LIGHT)
        col.addView(status)
        col.addView(label("1. Turn the keyboard on", 16f, bold = true))
        col.addView(button("Open keyboard settings") { startActivity(android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
        col.addView(label("2. Make it the current keyboard", 16f, bold = true))
        col.addView(button("Choose keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        col.addView(label("3. Try it", 16f, bold = true))
        col.addView(EditText(this).apply {
            hint = "Type here"; setTextColor(Color.WHITE); setHintTextColor(Theme.TEXT_DIM)
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(CheckBox(this).apply {
            text = "Show the keyboard automatically when a text field is selected"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("auto_show", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("auto_show", v).apply() }
        })
        col.addView(CheckBox(this).apply {
            text = "B button = backspace even when the device sends it as Back"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("b_delete", true)
            setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean("b_delete", v).apply() }
        })
        col.addView(label(
            "D-pad: move   A: type   B: backspace   X: space   Y: shift (twice = caps lock)\n" +
                "Start: enter   Select: hide   L1/R1: cursor   L2/R2: letters / symbols / emoji\n" +
                "Up from the top row: word suggestions", 13f, Theme.TEXT_DIM))
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#101114"))
            addView(col, ViewGroup.LayoutParams(-1, -2))
        })
    }

    override fun onResume() {
        super.onResume()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith(packageName) == true
        status.text = when {
            current -> "Status: active ✓"
            enabled -> "Status: on, but not selected yet"
            else -> "Status: not turned on"
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
