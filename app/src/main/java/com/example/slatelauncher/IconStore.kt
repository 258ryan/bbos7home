package com.example.slatelauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Where every launcher icon comes from, in priority order:
 *   1. a per-icon override the user picked,
 *   2. the active icon pack (Nova / ADW "appfilter.xml" packs),
 *   3. the bundled BB OS 7 icon for well-known apps,
 *   4. the app's own icon, on the glossy plate / as is / on the pack's icon back (setting).
 *
 * Override sources are strings: "bb:<name>", "bball:<NNN>", "ui:<file>", "pack:<pkg>/<drawable>", "file:<name>".
 */
object IconStore {
    class Pack(val pkg: String, val label: String)

    class PackData(
        val pkg: String, val res: Resources,
        val byComponent: Map<String, String>, val byPackage: Map<String, String>,
        val back: List<String>, val upon: String?, val scale: Float
    ) {
        val drawableNames: List<String> get() = byComponent.values.toSortedSet().toList()

        fun drawable(name: String): Drawable? {
            var id = res.getIdentifier(name, "drawable", pkg)
            if (id == 0) id = res.getIdentifier(name, "mipmap", pkg)
            if (id == 0) return null
            return try { res.getDrawable(id, null) } catch (_: Exception) { null }
        }
    }

    private val ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme", "com.anddoes.launcher.THEME")

    @Suppress("DEPRECATION")
    fun packs(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, Pack>()
        for (a in ACTIONS) for (ri in pm.queryIntentActivities(Intent(a), 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg !in seen) seen[pkg] = Pack(pkg, ri.loadLabel(pm).toString())
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    private var cached: PackData? = null

    @Synchronized
    fun activePack(ctx: Context): PackData? {
        val pkg = Prefs.str(ctx, Prefs.ICON_PACK, "")
        if (pkg.isEmpty()) return null
        cached?.let { if (it.pkg == pkg) return it }
        return loadPack(ctx, pkg).also { cached = it }
    }

    @Synchronized fun dropCache() { cached = null }

    fun loadPack(ctx: Context, pkg: String): PackData? {
        val res = try { ctx.packageManager.getResourcesForApplication(pkg) } catch (_: Exception) { return null }
        val byComp = HashMap<String, String>()
        val byPkg = HashMap<String, String>()
        val back = ArrayList<String>()
        var upon: String? = null
        var scale = 1f
        val parser: XmlPullParser = try {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) res.getXml(id)
            else Xml.newPullParser().also { it.setInput(res.assets.open("appfilter.xml"), "utf-8") }
        } catch (_: Exception) { return null }
        try {
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val comp = parser.getAttributeValue(null, "component")
                            val dr = parser.getAttributeValue(null, "drawable")
                            if (comp != null && dr != null) {
                                val inner = comp.substringAfter("{", "").substringBefore("}")
                                if (inner.contains("/")) {
                                    val p = inner.substringBefore("/")
                                    var c = inner.substringAfter("/")
                                    if (c.startsWith(".")) c = p + c
                                    byComp["$p/$c"] = dr
                                    byPkg.putIfAbsent(p, dr)
                                }
                            }
                        }
                        "iconback" -> for (i in 0 until parser.attributeCount) if (parser.getAttributeName(i).startsWith("img")) back.add(parser.getAttributeValue(i))
                        "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                        "scale" -> parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { scale = it }
                    }
                }
                ev = parser.next()
            }
        } catch (_: Exception) { }
        return PackData(pkg, res, byComp, byPkg, back, upon, scale)
    }

    // ── overrides ──

    fun overrides(ctx: Context) = Prefs.json(ctx, Prefs.ICON_OVERRIDES)

    fun setOverride(ctx: Context, key: String, source: String?) {
        val o = overrides(ctx)
        if (source == null) o.remove(key) else o.put(key, source)
        Prefs.setJson(ctx, Prefs.ICON_OVERRIDES, o)
        Prefs.bumpIcons(ctx)
    }

    fun customFile(ctx: Context, name: String) = File(File(ctx.filesDir, "custom_icons").also { it.mkdirs() }, "$name.png")

    /** Copy / scale a user picked image into private storage and return its source string. */
    fun importImage(ctx: Context, uri: android.net.Uri, key: String): String? = try {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val size = 192
        val side = minOf(bmp.width, bmp.height)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, size, size, true)
        val name = "c" + key.hashCode().toUInt().toString(16)
        customFile(ctx, name).outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "file:$name"
    } catch (_: Exception) { null }

    private fun asset(ctx: Context, path: String): Drawable? = try {
        ctx.assets.open(path).use { BitmapFactory.decodeStream(it) }?.let { BitmapDrawable(ctx.resources, it) }
    } catch (_: Exception) { null }

    fun drawableForSource(ctx: Context, src: String): Drawable? {
        val kind = src.substringBefore(":")
        val v = src.substringAfter(":")
        return when (kind) {
            "bb" -> asset(ctx, "bb/$v.png")
            "bball" -> asset(ctx, "bbfull/$v.png")
            "ui" -> asset(ctx, "ui/$v.png")
            "file" -> customFile(ctx, v).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.let { BitmapDrawable(ctx.resources, it) } }
            "pack" -> {
                val pkg = v.substringBefore("/")
                val dr = v.substringAfter("/")
                val data = if (activePack(ctx)?.pkg == pkg) activePack(ctx) else loadPack(ctx, pkg)
                data?.drawable(dr)
            }
            else -> null
        }
    }

    // ── resolving ──

    fun plated(ctx: Context, icon: Drawable): Drawable =
        LayerDrawable(arrayOf(iconPlate(ctx), InsetDrawable(icon, 0.16f)))

    private fun toBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }

    /** The app icon drawn on the pack's icon back (and under its "upon" overlay), like Nova does. */
    private fun onPackBack(ctx: Context, pack: PackData, icon: Drawable): Drawable? {
        val back = pack.back.firstOrNull()?.let { pack.drawable(it) } ?: return null
        val size = 192
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        back.setBounds(0, 0, size, size); back.draw(c)
        val inner = (size * pack.scale.coerceIn(0.3f, 1f) * 0.8f).toInt()
        val off = (size - inner) / 2
        icon.setBounds(off, off, off + inner, off + inner); icon.draw(c)
        pack.upon?.let { u -> pack.drawable(u)?.let { it.setBounds(0, 0, size, size); it.draw(c) } }
        return BitmapDrawable(ctx.resources, b)
    }

    fun resolve(ctx: Context, cn: ComponentName, original: Drawable, themed: String?): Drawable {
        val key = cn.flattenToString()
        overrides(ctx).optString(key, "").takeIf { it.isNotEmpty() }?.let { s -> drawableForSource(ctx, s)?.let { return it } }
        val pack = activePack(ctx)
        if (pack != null) {
            val name = pack.byComponent["${cn.packageName}/${cn.className}"] ?: pack.byPackage[cn.packageName]
            name?.let { n -> pack.drawable(n)?.let { return it } }
        }
        if (Prefs.bool(ctx, Prefs.USE_BB, true) && themed != null) BbIcons.drawable(ctx, themed)?.let { return it }
        return when (Prefs.str(ctx, Prefs.UNMATCHED, "plate")) {
            "original" -> original
            "packback" -> pack?.let { onPackBack(ctx, it, original) } ?: plated(ctx, original)
            else -> plated(ctx, original)
        }
    }

    fun hidden(ctx: Context) = Prefs.strSet(ctx, Prefs.HIDDEN)
    fun labelOverrides(ctx: Context) = Prefs.json(ctx, Prefs.LABEL_OVERRIDES)
}
