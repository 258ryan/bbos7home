package com.example.slatelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.util.LruCache
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** How to start a ROM: [style] = view | retroarch | extra | action, [arg] = core name / extra key / action. */
data class Recipe(val pkg: String, val style: String, val arg: String = "")

class SystemDef(
    val key: String, val label: String, val folders: List<String>, val exts: Set<String>, val recipe: Recipe
)

/** One entry on the Games page: an installed Android game or a ROM file. */
class Game(
    val id: String, val title: String, val systemKey: String, val systemLabel: String,
    val app: AppEntry?, val path: String?, val art: File?
)

object GameSystems {
    private const val RA = "com.retroarch.aarch64"
    // These recipes are starting points: emulator packages, cores and extras vary by build. Editable in settings.
    val all = listOf(
        SystemDef("gba", "Game Boy Advance", listOf("gba", "gameboyadvance"), setOf("gba", "zip", "7z"), Recipe(RA, "retroarch", "mgba")),
        SystemDef("gbc", "Game Boy / Color", listOf("gb", "gbc", "gameboy", "gameboycolor"), setOf("gb", "gbc", "zip", "7z"), Recipe(RA, "retroarch", "gambatte")),
        SystemDef("nes", "NES", listOf("nes", "fc", "famicom"), setOf("nes", "zip", "7z"), Recipe(RA, "retroarch", "fceumm")),
        SystemDef("snes", "SNES", listOf("snes", "sfc", "superfamicom"), setOf("sfc", "smc", "zip", "7z"), Recipe(RA, "retroarch", "snes9x")),
        SystemDef("genesis", "Genesis / Mega Drive", listOf("genesis", "megadrive", "md", "gen"), setOf("md", "gen", "bin", "smd", "zip", "7z"), Recipe(RA, "retroarch", "genesis_plus_gx")),
        SystemDef("sms", "Master System / Game Gear", listOf("mastersystem", "sms", "gamegear", "gg"), setOf("sms", "gg", "zip", "7z"), Recipe(RA, "retroarch", "genesis_plus_gx")),
        SystemDef("pce", "PC Engine", listOf("pcengine", "tg16", "pce"), setOf("pce", "zip", "7z"), Recipe(RA, "retroarch", "mednafen_pce_fast")),
        SystemDef("psx", "PlayStation", listOf("psx", "ps1", "playstation"), setOf("cue", "chd", "pbp", "iso", "m3u"), Recipe(RA, "retroarch", "pcsx_rearmed")),
        SystemDef("n64", "Nintendo 64", listOf("n64"), setOf("z64", "n64", "v64", "zip"), Recipe(RA, "retroarch", "mupen64plus_next")),
        SystemDef("nds", "Nintendo DS", listOf("nds", "ds"), setOf("nds", "zip", "7z"), Recipe(RA, "retroarch", "melonds")),
        SystemDef("arcade", "Arcade", listOf("arcade", "mame", "fbneo", "neogeo", "cps1", "cps2"), setOf("zip", "7z"), Recipe(RA, "retroarch", "fbneo")),
        SystemDef("psp", "PSP", listOf("psp"), setOf("iso", "cso", "pbp"), Recipe("org.ppsspp.ppsspp", "view"))
    )

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    fun recipe(ctx: Context, def: SystemDef): Recipe {
        val o = Prefs.json(ctx, Prefs.RECIPES).optJSONObject(def.key) ?: return def.recipe
        return Recipe(o.optString("pkg", def.recipe.pkg), o.optString("style", def.recipe.style), o.optString("arg", def.recipe.arg))
    }

    fun setRecipe(ctx: Context, key: String, r: Recipe?) {
        val all = Prefs.json(ctx, Prefs.RECIPES)
        if (r == null) all.remove(key) else all.put(key, JSONObject().put("pkg", r.pkg).put("style", r.style).put("arg", r.arg))
        Prefs.setJson(ctx, Prefs.RECIPES, all)
    }
}

object Games {
    private const val IMG = "png jpg jpeg webp"

    // ── roots ──

    fun defaultRoots(): List<String> {
        val out = ArrayList<String>()
        val base = Environment.getExternalStorageDirectory().path
        for (n in listOf("ROMs", "roms", "Roms", "Games/roms", "Games/ROMs")) out.add("$base/$n")
        File("/storage").listFiles()?.forEach { v ->
            if (v.isDirectory && v.name != "emulated" && v.name != "self") for (n in listOf("ROMs", "roms")) out.add("${v.path}/$n")
        }
        return out
    }

    fun roots(ctx: Context): List<File> {
        val custom = Prefs.str(ctx, Prefs.ROM_ROOTS, "").lines().map { it.trim() }.filter { it.isNotEmpty() }
        return (custom.ifEmpty { defaultRoots() }).map { File(it) }.filter { it.isDirectory }
    }

    // ── titles / art ──

    private fun cleanTitle(file: String): String {
        val base = file.substringBeforeLast('.')
        return base.replace(Regex("\\s*[\\(\\[][^)\\]]*[\\)\\]]"), "").replace('_', ' ').trim().ifEmpty { base }
    }

    private fun findArt(rom: File, root: File, sysDir: File, sysName: String): File? {
        val base = rom.name.substringBeforeLast('.')
        val dirs = listOf(
            sysDir, File(sysDir, "images"), File(sysDir, "covers"), File(sysDir, "media/box2dfront"),
            File(sysDir, "media/covers"), File(sysDir, "media/screenshots"), File(root, "art/$sysName"), File(root, "media/$sysName/covers")
        )
        for (d in dirs) {
            if (!d.isDirectory) continue
            for (ext in IMG.split(" ")) for (suffix in listOf("", "-image", "-thumb")) {
                val f = File(d, "$base$suffix.$ext")
                if (f.isFile) return f
            }
        }
        return null
    }

    // ── scanning ──

    /** ROMs from the configured / default folders. Layout: <root>/<system folder>/<rom file>. */
    fun scanRoms(ctx: Context): List<Game> {
        val out = ArrayList<Game>()
        for (root in roots(ctx)) {
            root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val def = GameSystems.all.firstOrNull { dir.name.lowercase() in it.folders } ?: return@forEach
                dir.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in def.exts }.forEach { f ->
                    out.add(Game("rom:" + f.path, cleanTitle(f.name), def.key, def.label, null, f.path, findArt(f, root, dir, dir.name)))
                }
            }
        }
        return out.sortedBy { it.title.lowercase() }
    }

    fun isAndroidGame(ctx: Context, e: AppEntry): Boolean {
        if (e.key in Prefs.strSet(ctx, Prefs.GAME_ADD)) return true
        return e.category == android.content.pm.ApplicationInfo.CATEGORY_GAME && e.key !in Prefs.strSet(ctx, Prefs.GAME_REMOVE)
    }

    fun androidGames(ctx: Context, apps: List<AppEntry>): List<Game> =
        apps.filter { isAndroidGame(ctx, it) }.map { Game("app:" + it.key, it.label, "android", "Android", it, null, null) }

    fun setTag(ctx: Context, e: AppEntry, game: Boolean) {
        val add = Prefs.strSet(ctx, Prefs.GAME_ADD); val rem = Prefs.strSet(ctx, Prefs.GAME_REMOVE)
        if (game) { add.add(e.key); rem.remove(e.key) } else { add.remove(e.key); rem.add(e.key) }
        Prefs.setStrSet(ctx, Prefs.GAME_ADD, add); Prefs.setStrSet(ctx, Prefs.GAME_REMOVE, rem)
    }

    // ── favourites / stats ──

    fun favs(ctx: Context) = Prefs.strSet(ctx, "game_favs")
    fun toggleFav(ctx: Context, g: Game) {
        val f = favs(ctx); if (!f.add(g.id)) f.remove(g.id); Prefs.setStrSet(ctx, "game_favs", f)
    }
    fun played(ctx: Context, g: Game) = Prefs.int(ctx, "gn_" + g.id, 0)
    fun lastPlayed(ctx: Context, g: Game) = Prefs.sp(ctx).getLong("gt_" + g.id, 0L)

    // ── launching ──

    private fun installed(ctx: Context, pkg: String) = try { ctx.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }

    private fun resolvePkg(ctx: Context, pkg: String): String? {
        val alts = when {
            pkg.startsWith("com.retroarch") -> listOf(pkg, "com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")
            pkg.startsWith("org.ppsspp") -> listOf(pkg, "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold")
            else -> listOf(pkg)
        }
        return alts.firstOrNull { installed(ctx, it) }
    }

    /** Returns an error message, or null when the game was started. */
    fun launch(ctx: Context, g: Game): String? {
        val now = System.currentTimeMillis()
        val intent: Intent
        if (g.app != null) {
            intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(g.app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        } else {
            val def = GameSystems.byKey(g.systemKey) ?: return "Unknown system"
            val r = GameSystems.recipe(ctx, def)
            val pkg = resolvePkg(ctx, r.pkg) ?: return "Install ${r.pkg} (or pick another emulator in Launcher settings → Games)"
            val path = g.path ?: return "Missing file"
            // Emulators expect plain file paths; let them have file:// URIs.
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            intent = when (r.style) {
                "view" -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(File(path)), "application/octet-stream").setPackage(pkg)
                "retroarch" -> Intent().setComponent(ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture"))
                    .putExtra("ROM", path)
                    .putExtra("LIBRETRO", "/data/data/$pkg/cores/${r.arg}_libretro_android.so")
                    .putExtra("CONFIGFILE", "/storage/emulated/0/Android/data/$pkg/files/retroarch.cfg")
                    .putExtra("QUITFOCUS", "")
                "extra" -> (ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return "Can't open $pkg").putExtra(r.arg, path)
                "action" -> Intent(r.arg).setPackage(pkg).setData(Uri.fromFile(File(path)))
                else -> return "Unknown launch style"
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            Prefs.set(ctx, "gn_" + g.id, played(ctx, g) + 1)
            Prefs.sp(ctx).edit().putLong("gt_" + g.id, now).apply()
            null
        } catch (e: Exception) { "Couldn't start: ${e.message}" }
    }
}

/** Cover art decoding off the UI thread, with a small in-memory cache. */
object Covers {
    private val cache = LruCache<String, Bitmap>(48)
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(file: File, px: Int, cb: (Bitmap?) -> Unit) {
        cache.get(file.path)?.let { cb(it); return }
        pool.execute {
            val bmp = try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
            } catch (_: Throwable) { null }
            if (bmp != null) cache.put(file.path, bmp)
            main.post { cb(bmp) }
        }
    }
}
