package tld.my.simpletv

// Checking for a new version (the counterpart of the webOS version's 2.0.0 check). Once a day the launcher asks GitHub for the
// latest release of this project. A newer one shows as a note in Settings and an "Update" tile on the home row; pressing it
// shows the release with a QR code of its page. Nothing is downloaded or installed: Android only installs an app the user
// allows to be installed, so the new APK is fetched from that page, as the first one was.

import org.json.JSONObject

data class UpdateInfo(val version: String, val url: String, val notes: String)

object Updates {
    const val LATEST = "https://api.github.com/repos/fardroid/AndroidTV_Simple_Launcher/releases/latest"
    const val DAY_MS = 24 * 3_600_000L

    /** The release out of GitHub's answer (the tag "v1.2.0" is version 1.2.0); null when it is not one. */
    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.getString("tag_name").trim().removePrefix("v").removePrefix("V")
        val url = o.getString("html_url")
        if (version.isEmpty() || !url.startsWith("https://github.com/")) return null
        UpdateInfo(version, url, plain(o.optString("body")))
    }.getOrNull()

    /** The release notes as plain text for the screen: the marks of Markdown and the links' addresses left out, cut at 600 characters. */
    fun plain(markdown: String): String = markdown
        .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("(?m)^\\s*#+\\s*"), "")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
        .replace(Regex("[*_`]+"), "")
        .replace(Regex("\\r"), "")
        .replace(Regex("\\n{2,}"), "\n")
        .trim().take(600)

    /** Whether version `a` is newer than `b` (numbers separated by dots; anything else ends the comparison, as in the webOS version). */
    fun newer(a: String, b: String): Boolean {
        val x = a.trim().split('.'); val y = b.trim().split('.')
        for (i in 0 until maxOf(x.size, y.size)) {
            val nx = x.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val ny = y.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (nx != ny) return nx > ny
            if ((x.getOrNull(i)?.any { !it.isDigit() } == true) || (y.getOrNull(i)?.any { !it.isDigit() } == true)) break
        }
        return false
    }

    fun toJson(u: UpdateInfo?, at: Long): String = JSONObject().put("at", at).apply {
        if (u != null) put("version", u.version).put("url", u.url).put("notes", u.notes)
    }.toString()

    /** The saved result: when it was asked, and the release (null: none, or not newer than the running version). */
    fun fromJson(s: String?): Pair<Long, UpdateInfo?>? = if (s == null) null else runCatching {
        val o = JSONObject(s)
        o.getLong("at") to if (o.has("version")) UpdateInfo(o.getString("version"), o.getString("url"), o.optString("notes")) else null
    }.getOrNull()
}
