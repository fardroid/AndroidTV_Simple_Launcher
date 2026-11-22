package tld.my.simpletv

// The wallpaper collection (as Simple LG launcher 2.2 has it): photos of mountains, nature, space and abstract pictures, listed
// in the webOS project's repo/wallpapers.json on GitHub with their size and SHA-256. The gallery shows them by group from their
// small previews; a photo ticked there is downloaded in the background into the launcher's own storage, checked, and joins the
// slideshow when it has come. Photos no longer ticked are deleted when the gallery closes.
//
// In the settings and the slideshow a photo of the collection is "collection:" + its id.

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.launch

data class CollectionPhoto(
    val id: String, val group: String, val title: String, val url: String, val size: Long, val sha256: String,
    val thumb: String, val thumbSize: Long, val thumbSha256: String,
)

object Collection {
    const val FEED = "https://raw.githubusercontent.com/fardroid/Simple-LG-launcher/main/repo/wallpapers.json"
    const val PREFIX = "collection:"
    val GROUPS = listOf("mountains", "nature", "space", "abstract")
    private val ID = Regex("^[a-z0-9_-]{1,40}$")

    fun groupName(group: String) = when (group) {
        "mountains" -> str(R.string.collection_mountains)
        "nature" -> str(R.string.collection_nature)
        "space" -> str(R.string.collection_space)
        else -> str(R.string.collection_abstract)
    }

    /** The list out of its JSON: only photos with a plain id, a known group, https addresses and their checksums. */
    fun parse(json: String): List<CollectionPhoto> = runCatching {
        val a = JSONObject(json).getJSONArray("photos")
        val seen = HashSet<String>()
        (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            val c = CollectionPhoto(
                p.optString("id"), p.optString("group"), p.optString("title"), p.optString("url"), p.optLong("size"), p.optString("sha256").lowercase(),
                p.optString("thumb"), p.optLong("thumbSize"), p.optString("thumbSha256").lowercase(),
            )
            val ok = ID.matches(c.id) && c.group in GROUPS && c.url.startsWith("https://") && c.thumb.startsWith("https://") &&
                c.sha256.length == 64 && c.thumbSha256.length == 64 && seen.add(c.id)
            if (ok) c.copy(title = c.title.ifEmpty { c.id }) else null
        }
    }.getOrDefault(emptyList())

    fun dir(context: Context) = File(context.filesDir, "collection")
    fun file(context: Context, id: String) = File(dir(context), "$id.jpg")
    private fun thumbFile(context: Context, id: String) = File(File(context.cacheDir, "collection-thumbs"), "$id.jpg")

    fun idOf(pick: String) = pick.removePrefix(PREFIX)
    fun isPhoto(pick: String) = pick.startsWith(PREFIX)

    /** A photo's file once it is on the box (null: not yet). */
    fun onBox(context: Context, pick: String): File? = file(context, idOf(pick)).takeIf { it.isFile }

    /** Downloads `url` into `to`, checked against its size and SHA-256 (blocking). False when it could not, or came damaged. */
    fun fetch(url: String, size: Long, sha256: String, to: File): Boolean {
        to.parentFile?.mkdirs()
        val part = File(to.path + ".part")
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 20_000; c.readTimeout = 30_000; c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", Net.AGENT)
            if (c.responseCode !in 200..299) return false
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { input -> part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    done += n
                    if (size > 0 && done > size) return false
                    digest.update(buffer, 0, n); out.write(buffer, 0, n)
                }
            } }
            if (digest.digest().joinToString("") { "%02x".format(it) } != sha256) return false
            part.renameTo(to)
        } catch (_: Exception) {
            false
        } finally {
            c.disconnect()
            part.delete()
        }
    }

    /** The preview of a photo for the gallery's tile: the photo itself once it is on the box, else its small copy (downloaded once). */
    fun preview(context: Context, p: CollectionPhoto): File? {
        file(context, p.id).takeIf { it.isFile }?.let { return it }
        val t = thumbFile(context, p.id)
        if (t.isFile || fetch(p.thumb, p.thumbSize, p.thumbSha256, t)) return t
        return null
    }
}

/** The collection on the box: its list (read from GitHub when the gallery opens) and the downloads of the ticked photos, one at
 *  a time in the background. */
class CollectionStore(private val context: Context, private val scope: kotlinx.coroutines.CoroutineScope) {
    @Volatile var photos: List<CollectionPhoto> = emptyList()
        private set
    /** the photos still to come (their picks), in order */
    private val queue = ArrayDeque<String>()
    private var running = false
    /** a photo came (its pick): the slideshow takes it in */
    var onArrived: (String) -> Unit = {}

    /** Reads the list (blocking: Dispatchers.IO); kept for the launcher's life once read. */
    fun list(): List<CollectionPhoto> {
        if (photos.isEmpty()) runCatching { photos = Collection.parse(Net.fetchText(Collection.FEED, 15_000, 1 shl 20)) }
        return photos
    }

    fun photo(pick: String) = photos.firstOrNull { it.id == Collection.idOf(pick) }

    /** The ticked photos not yet on the box. */
    fun missing(picks: List<String>) = picks.count { Collection.isPhoto(it) && Collection.onBox(context, it) == null }

    /** The ticked photos are made to come (the ones not on the box yet), and the ones no longer ticked are deleted. */
    fun want(picks: List<String>) {
        val wanted = picks.filter { Collection.isPhoto(it) }
        val keep = wanted.map { Collection.idOf(it) + ".jpg" }.toSet()
        Collection.dir(context).listFiles()?.forEach { if (it.name !in keep) it.delete() }
        synchronized(queue) {
            queue.clear()
            queue.addAll(wanted.filter { Collection.onBox(context, it) == null })
        }
        pump()
    }

    private fun pump() {
        if (running) return
        running = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (synchronized(queue) { queue.isNotEmpty() }) list()
            while (true) {
                val pick = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                val p = photo(pick) ?: continue
                val ok = Collection.fetch(p.url, p.size, p.sha256, Collection.file(context, p.id))
                if (ok) kotlinx.coroutines.withContext(androidx.compose.ui.platform.AndroidUiDispatcher.Main) { onArrived(pick) }
            }
            running = false
        }
    }
}
