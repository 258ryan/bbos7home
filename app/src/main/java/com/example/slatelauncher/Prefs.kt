package com.example.slatelauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Typed access to the launcher's settings (one SharedPreferences file, easy to export / import). */
object Prefs {
    const val FILE = "slate"

    const val ICON_PACK = "icon_pack"            // package name, "" = none
    const val UNMATCHED = "unmatched_style"      // plate | original | packback
    const val USE_BB = "use_bb_icons"
    const val TEXTURES = "textures"
    const val DIM = "dim"                        // 0..100 (opacity of the leather dimmer behind expanded panels)
    const val COLS = "cols_override"             // 0 = auto
    const val BAR_LABELS = "bar_labels"
    const val SWAP_AB = "swap_ab"
    const val HINTS = "show_hints"
    const val HIDDEN = "hidden"                  // component keys
    const val FOLDERS_HIDDEN = "folders_hidden"  // Category names
    const val ICON_OVERRIDES = "icon_overrides"  // JSON: component key -> source
    const val LABEL_OVERRIDES = "label_overrides"
    const val ICON_REV = "icon_rev"              // bumped whenever icons / names / visibility change
    const val GAME_ADD = "game_tag_add"          // apps the user marked as games
    const val GAME_REMOVE = "game_tag_remove"
    const val ROM_ROOTS = "rom_roots"            // newline separated
    const val RECIPES = "recipes"                // JSON: system key -> recipe

    fun sp(ctx: Context): SharedPreferences = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun bool(ctx: Context, k: String, def: Boolean) = sp(ctx).getBoolean(k, def)
    fun int(ctx: Context, k: String, def: Int) = sp(ctx).getInt(k, def)
    fun str(ctx: Context, k: String, def: String) = sp(ctx).getString(k, def) ?: def
    fun set(ctx: Context, k: String, v: Boolean) = sp(ctx).edit().putBoolean(k, v).apply()
    fun set(ctx: Context, k: String, v: Int) = sp(ctx).edit().putInt(k, v).apply()
    fun set(ctx: Context, k: String, v: String) = sp(ctx).edit().putString(k, v).apply()

    fun strSet(ctx: Context, k: String): MutableSet<String> = HashSet(sp(ctx).getStringSet(k, emptySet()) ?: emptySet())
    fun setStrSet(ctx: Context, k: String, v: Set<String>) = sp(ctx).edit().putStringSet(k, v).apply()

    fun json(ctx: Context, k: String): JSONObject = try { JSONObject(str(ctx, k, "{}")) } catch (_: Exception) { JSONObject() }
    fun setJson(ctx: Context, k: String, v: JSONObject) = set(ctx, k, v.toString())

    /** Honours the "swap A and B" setting for gamepad key codes. */
    fun mapKey(ctx: Context, code: Int): Int {
        if (!bool(ctx, SWAP_AB, false)) return code
        return when (code) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> android.view.KeyEvent.KEYCODE_BUTTON_B
            android.view.KeyEvent.KEYCODE_BUTTON_B -> android.view.KeyEvent.KEYCODE_BUTTON_A
            else -> code
        }
    }

    fun bumpIcons(ctx: Context) = set(ctx, ICON_REV, int(ctx, ICON_REV, 0) + 1)

    // ── backup ──

    /** All settings (including favourites, launch counts, game tags) as JSON text. */
    fun export(ctx: Context): String {
        val o = JSONObject()
        for ((k, v) in sp(ctx).all) {
            when (v) {
                is Set<*> -> o.put(k, org.json.JSONArray(v.map { it.toString() }))
                else -> o.put(k, v)
            }
        }
        return o.toString(2)
    }

    fun import(ctx: Context, text: String): Boolean = try {
        val o = JSONObject(text)
        val e = sp(ctx).edit().clear()
        for (k in o.keys()) {
            when (val v = o.get(k)) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is String -> e.putString(k, v)
                is org.json.JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                else -> {}
            }
        }
        e.apply()
        bumpIcons(ctx)
        true
    } catch (_: Exception) { false }
}
