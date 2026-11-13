package com.example.slatelauncher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Leather dialogs shared by the launcher and its settings: lists, text prompts, confirmations. */
object Dialogs {

    fun skin(act: Activity, content: View, gravity: Int = Gravity.CENTER, tall: Boolean = false): Dialog {
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = Materials.stitchedLeather(act, 5)
            setPadding(act.dp(12), act.dp(12), act.dp(12), act.dp(14))
            addView(content, LinearLayout.LayoutParams(-1, if (tall) -1 else -2))
        }
        d.setContentView(box)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = act.resources.displayMetrics
            val w = minOf(dm.widthPixels - act.dp(24), act.dp(560))
            setLayout(w, if (tall) (dm.heightPixels * 0.86f).toInt() else ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(gravity)
        }
        d.setOnKeyListener { _, code, ev ->
            when (Prefs.mapKey(act, code)) {
                KeyEvent.KEYCODE_BUTTON_A -> { if (ev.action == KeyEvent.ACTION_UP) d.currentFocus?.performClick(); true }
                KeyEvent.KEYCODE_BUTTON_B -> { if (ev.action == KeyEvent.ACTION_UP) d.dismiss(); true }
                else -> false
            }
        }
        return d
    }

    fun title(act: Activity, text: String) = TextView(act).apply {
        this.text = text; textSize = 13f; setTextColor(Theme.BLUE_LIGHT)
        setTypeface(typeface, Typeface.BOLD); setPadding(act.dp(12), act.dp(4), act.dp(12), act.dp(8))
    }

    fun row(act: Activity, text: String, icon: Drawable? = null, onClick: () -> Unit) = TextView(act).apply {
        this.text = text; textSize = 17f; setTextColor(Theme.TEXT)
        setPadding(act.dp(14), act.dp(11), act.dp(14), act.dp(11))
        isFocusable = true; isClickable = true
        background = focusGlow(act, 6, 1)
        gravity = Gravity.CENTER_VERTICAL
        if (icon != null) {
            icon.setBounds(0, 0, act.dp(28), act.dp(28))
            setCompoundDrawables(icon, null, null, null)
            compoundDrawablePadding = act.dp(12)
        }
        setOnClickListener { onClick() }
    }

    /** A scrolling list of rows. [icons] maps a label to a "ui:" / BB icon name (optional). */
    fun list(act: Activity, title: String, items: List<Triple<String, String?, () -> Unit>>): Dialog {
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(title(act, title))
        lateinit var d: Dialog
        val rows = items.map { (label, icon, run) ->
            row(act, label, icon?.let { BbIcons.any(act, it) }) { d.dismiss(); run() }
        }
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { list.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(2) }) }
        val sv = ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(list) }
        col.addView(sv, LinearLayout.LayoutParams(-1, if (rows.size > 7) (act.resources.displayMetrics.heightPixels * 0.55f).toInt() else -2))
        d = skin(act, col, Gravity.CENTER)
        d.setOnShowListener { rows.firstOrNull()?.requestFocus() }
        d.show()
        return d
    }

    /** Text prompt with a recessed field. Start / Enter / the OK row confirm. */
    fun prompt(act: Activity, title: String, initial: String, hint: String, onOk: (String) -> Unit) {
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(title(act, title))
        val input = EditText(act).apply {
            setText(initial); setSelection(initial.length)
            this.hint = hint; setHintTextColor(Theme.TEXT_DIM); setTextColor(Theme.TEXT); textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = Materials.recessed(act, 5f)
            setPadding(act.dp(12), act.dp(9), act.dp(12), act.dp(9))
        }
        col.addView(input, LinearLayout.LayoutParams(-1, -2).apply { setMargins(act.dp(4), 0, act.dp(4), act.dp(8)) })
        lateinit var d: Dialog
        val ok = row(act, "OK") { d.dismiss(); onOk(input.text.toString().trim()) }
        val cancel = row(act, "Cancel") { d.dismiss() }
        input.setOnEditorActionListener { _, _, _ -> d.dismiss(); onOk(input.text.toString().trim()); true }
        col.addView(ok, LinearLayout.LayoutParams(-1, -2))
        col.addView(cancel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = act.dp(2) })
        d = skin(act, col, Gravity.CENTER)
        d.setOnShowListener { input.requestFocus() }
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        d.show()
    }

    fun message(act: Activity, title: String, text: String) {
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        col.addView(title(act, title))
        col.addView(TextView(act).apply {
            this.text = text; textSize = 14f; setTextColor(Theme.TEXT); setPadding(act.dp(12), act.dp(4), act.dp(12), act.dp(10))
        })
        lateinit var d: Dialog
        val ok = row(act, "OK") { d.dismiss() }
        col.addView(ok)
        d = skin(act, col)
        d.setOnShowListener { ok.requestFocus() }
        d.show()
    }
}
