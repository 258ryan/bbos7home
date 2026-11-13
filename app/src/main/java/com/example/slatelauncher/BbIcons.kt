package com.example.slatelauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Maps well-known apps to the BlackBerry OS 7 theme icons bundled in assets/bb. */
object BbIcons {
    // Order matters: first rule with a matching word wins.
    private val rules: List<Pair<String, List<String>>> = listOf(
        "contacts" to listOf("contacts", "contact"),
        "phone" to listOf("phone", "dialer", "telecom"),
        "messages" to listOf("messaging", "messages", "message", "sms", "mms"),
        "mail" to listOf("mail", "email", "gmail", "gm", "outlook", "mailbox"),
        "camera" to listOf("camera"),
        "calendar" to listOf("calendar"),
        "clock" to listOf("clock", "deskclock", "alarm", "timer"),
        "calc" to listOf("calculator", "calc"),
        "settings" to listOf("settings"),
        "music" to listOf("music", "spotify"),
        "video" to listOf("video", "videos", "movies", "netflix", "youtube", "vlc", "plex"),
        "gallery" to listOf("gallery", "photos", "pictures", "images"),
        "notes" to listOf("notes", "note", "keep", "memo", "notepad", "notion"),
        "tasks" to listOf("tasks", "todo", "reminders", "reminder"),
        "maps" to listOf("maps", "navigation", "waze"),
        "files" to listOf("files", "documentsui", "documents", "filemanager"),
        "chat" to listOf("whatsapp", "telegram", "signal", "messenger", "discord", "slack", "teams", "viber", "wechat"),
        "wallet" to listOf("wallet", "pay"),
        "vault" to listOf("vault", "authenticator", "bitwarden", "keepass", "password", "passwords", "lastpass", "authy"),
        "recorder" to listOf("recorder", "voice", "dictaphone"),
        "bluetooth" to listOf("bluetooth"),
        "news" to listOf("news", "flipboard", "feedly"),
        "compass" to listOf("compass"),
        "tools" to listOf("toolbox", "tools", "utilities"),
        "cleaner" to listOf("cleaner", "clean", "junk"),
        "games" to listOf("games", "game"),
        "wiki" to listOf("wikipedia"),
        "bing" to listOf("bing"),
        "browser" to listOf("browser", "chrome", "firefox", "brave", "opera", "edge", "internet", "vivaldi", "duckduckgo"),
        "search" to listOf("search", "googlequicksearchbox")
    )

    private val cache = HashMap<String, Bitmap?>()

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }.toSet()

    /** Theme icon name for an app, or null if it has no match. */
    fun nameFor(pkg: String, label: String): String? {
        val w = words("$pkg $label")
        return rules.firstOrNull { (_, kws) -> kws.any { it in w } }?.first
    }

    @Synchronized
    fun drawable(ctx: Context, name: String): Drawable? {
        val bmp = cache.getOrPut(name) {
            try { ctx.assets.open("bb/$name.png").use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
        } ?: return null
        return BitmapDrawable(ctx.resources, bmp)
    }
}
