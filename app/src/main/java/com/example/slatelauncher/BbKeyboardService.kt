package com.example.slatelauncher

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * The system keyboard. While it is shown it intercepts gamepad input (Android hands key events to the
 * input method before the app):
 *   D-pad  move highlight     A  type        B  backspace       X  space      Y  shift (twice = caps lock)
 *   Start  enter / action     Select  hide   L1 / R1  cursor left / right     L2 / R2  previous / next layer
 * Up from the top letter row jumps to the suggestion strip.
 */
class BbKeyboardService : InputMethodService(), BbKeyboardView.Listener {

    private lateinit var kb: BbKeyboardView
    private var shifted = false
    private var caps = false
    private var lastShiftAt = 0L
    private var noSuggest = false
    private var enterAction = EditorInfo.IME_ACTION_NONE
    private var words: List<String> = emptyList()
    private val swallowed = HashSet<Int>()
    private val prefs by lazy { getSharedPreferences("bbkb", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        Thread {
            words = try {
                assets.open("words.txt").bufferedReader().readLines().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            } catch (_: Exception) { emptyList() }
        }.start()
    }

    override fun onCreateInputView(): View {
        kb = BbKeyboardView(this)
        kb.listener = this
        return kb
    }

    // Show even when a hardware keyboard / gamepad is connected.
    override fun onEvaluateInputViewShown(): Boolean { super.onEvaluateInputViewShown(); return true }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val passwordLike = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        noSuggest = cls != InputType.TYPE_CLASS_TEXT || passwordLike ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        enterAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) enterAction = EditorInfo.IME_ACTION_NONE
        kb.enterLabel = when (enterAction) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "↵"
        }
        caps = false
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        kb.setLayer(if (numeric) BbKeyboardView.Layer.SYM1 else BbKeyboardView.Layer.LETTERS)
        autoShift()
        updateSuggestions()
    }

    override fun onUpdateSelection(oss: Int, ose: Int, nss: Int, nse: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(oss, ose, nss, nse, cs, ce)
        if (!::kb.isInitialized) return
        autoShift()
        updateSuggestions()
    }

    // ───────────── typing logic ─────────────

    private fun autoShift() {
        if (caps) return
        val mode = currentInputConnection?.getCursorCapsMode(currentInputEditorInfo?.inputType ?: 0) ?: 0
        shifted = mode != 0
        kb.setShift(shifted, caps)
    }

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        return before.takeLastWhile { it.isLetter() || it == '\'' }
    }

    private fun learn(word: String) {
        if (noSuggest || word.length < 2) return
        val k = "u_" + word.lowercase()
        prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
    }

    private fun updateSuggestions() {
        if (noSuggest) { kb.setSuggestions(emptyList()); return }
        val prefix = currentWord()
        if (prefix.isEmpty()) { kb.setSuggestions(emptyList()); return }
        val lower = prefix.lowercase()
        val user = prefs.all.entries
            .filter { it.key.startsWith("u_") && it.key.substring(2).startsWith(lower) }
            .sortedByDescending { (it.value as? Int) ?: 0 }
            .map { it.key.substring(2) }
        val dict = words.asSequence().filter { it.startsWith(lower) }.take(6).toList()
        val out = (user + dict).distinct().filter { it != lower }.take(3).map {
            when {
                prefix.length > 1 && prefix.all { ch -> !ch.isLetter() || ch.isUpperCase() } -> it.uppercase()
                prefix[0].isUpperCase() -> it.replaceFirstChar { c -> c.uppercase() }
                else -> it
            }
        }
        kb.setSuggestions(out)
    }

    override fun onChar(text: String) {
        val ic = currentInputConnection ?: return
        if (text.length == 1 && !text[0].isLetter() && text != "'") learn(currentWord())
        ic.commitText(text, 1)
        if (shifted && !caps) { shifted = false; kb.setShift(false, false) }
    }

    override fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val prefix = currentWord()
        ic.deleteSurroundingText(prefix.length, 0)
        ic.commitText("$word ", 1)
        learn(word)
    }

    override fun onAction(a: BbKeyboardView.Act) {
        when (a) {
            BbKeyboardView.Act.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                if (shifted && !caps && now - lastShiftAt < 400) caps = true
                else if (caps) { caps = false; shifted = false }
                else shifted = !shifted
                lastShiftAt = now
                kb.setShift(shifted, caps)
            }
            BbKeyboardView.Act.DEL -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            BbKeyboardView.Act.SPACE -> { learn(currentWord()); currentInputConnection?.commitText(" ", 1) }
            BbKeyboardView.Act.ENTER -> {
                learn(currentWord())
                if (enterAction != EditorInfo.IME_ACTION_NONE) currentInputConnection?.performEditorAction(enterAction)
                else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
            BbKeyboardView.Act.LAYER_SYM -> kb.setLayer(BbKeyboardView.Layer.SYM1)
            BbKeyboardView.Act.LAYER_SYM2 -> kb.setLayer(BbKeyboardView.Layer.SYM2)
            BbKeyboardView.Act.LAYER_ABC -> kb.setLayer(BbKeyboardView.Layer.LETTERS)
            BbKeyboardView.Act.LAYER_EMOJI -> kb.setLayer(BbKeyboardView.Layer.EMOJI)
            BbKeyboardView.Act.CHAR -> {}
        }
    }

    // ───────────── gamepad input ─────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!::kb.isInitialized || !isInputViewShown) return super.onKeyDown(keyCode, event)
        val first = event.repeatCount == 0
        val gamepad = (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { kb.move(-1, 0); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { kb.move(1, 0); true }
            KeyEvent.KEYCODE_DPAD_UP -> { kb.move(0, -1); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { kb.move(0, 1); true }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER -> { if (first) kb.pressFocused(); true }
            KeyEvent.KEYCODE_BUTTON_B -> { onAction(BbKeyboardView.Act.DEL); true }
            // Some devices send B as BACK; treat it as backspace unless disabled in the setup screen.
            KeyEvent.KEYCODE_BACK ->
                if (gamepad && prefs.getBoolean("b_delete", true)) { onAction(BbKeyboardView.Act.DEL); true } else false
            KeyEvent.KEYCODE_BUTTON_X -> { if (first) onAction(BbKeyboardView.Act.SPACE); true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (first) onAction(BbKeyboardView.Act.SHIFT); true }
            KeyEvent.KEYCODE_BUTTON_START -> { if (first) onAction(BbKeyboardView.Act.ENTER); true }
            KeyEvent.KEYCODE_BUTTON_SELECT -> { if (first) requestHideSelf(0); true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT); true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT); true }
            KeyEvent.KEYCODE_BUTTON_L2 -> { if (first) kb.cycleLayer(-1); true }
            KeyEvent.KEYCODE_BUTTON_R2 -> { if (first) kb.cycleLayer(1); true }
            else -> false
        }
        if (handled) { swallowed.add(keyCode); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (swallowed.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
}
