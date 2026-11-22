package tld.my.simpletv

// Checking for a new version and updating to it (as Simple LG launcher does). Once a day the launcher asks GitHub for the latest
// release of this project. A newer one shows as a line over the row, a banner once and an "Update" tile; pressing the tile
// downloads the release's APK, checks it against the SHA-256 GitHub gives for it, and hands it to Android's installer, which asks
// the user to confirm (Updater.kt). Android first has to be told once that the launcher may install apps (Install unknown apps).

import org.json.JSONObject

/** `apk`, `apkSize` and `sha256`: the release's APK, when it has one with a checksum (GitHub gives "sha256:…" for each file). */
data class UpdateInfo(val version: String, val url: String, val notes: String, val apk: String = "", val apkSize: Long = 0, val sha256: String = "")

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
        // the first .apk of the release with a checksum
        var apk = ""; var size = 0L; var sha = ""
        o.optJSONArray("assets")?.let { a ->
            for (i in 0 until a.length()) {
                val f = a.getJSONObject(i)
                val digest = f.optString("digest")
                val link = f.optString("browser_download_url")
                if (!f.optString("name").endsWith(".apk") || !digest.startsWith("sha256:") || !link.startsWith("https://github.com/")) continue
                apk = link; size = f.optLong("size"); sha = digest.removePrefix("sha256:").lowercase(); break
            }
        }
        UpdateInfo(version, url, plain(o.optString("body")), apk, size, sha)
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
        if (u != null) put("version", u.version).put("url", u.url).put("notes", u.notes).put("apk", u.apk).put("apkSize", u.apkSize).put("sha256", u.sha256)
    }.toString()

    /** The saved result: when it was asked, and the release (null: none, or not newer than the running version). */
    fun fromJson(s: String?): Pair<Long, UpdateInfo?>? = if (s == null) null else runCatching {
        val o = JSONObject(s)
        o.getLong("at") to if (o.has("version")) UpdateInfo(o.getString("version"), o.getString("url"), o.optString("notes"), o.optString("apk"), o.optLong("apkSize"), o.optString("sha256")) else null
    }.getOrNull()
}
