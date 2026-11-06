package tld.my.simpletv

// The pictures a wallpaper can be taken from, in folders: the ones that come with the launcher, the box's own folder for the
// launcher, and (once the user allows it) the pictures on the box, by their folders. Settings → Wallpaper → Pictures shows them
// as tiles; the ticked ones are the slideshow (none ticked: the launcher's own and the user's, as before). The webOS version's
// gallery (1.10.0) browses the TV's storage and USB drives; Android's counterpart is the box's picture library.

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** A place of pictures. `id`: what tells it apart ("builtin", "own", or "device:" + the library folder's name). */
data class GalleryFolder(val id: String, val name: String, val pictures: List<String>, val device: Boolean = false)

object Gallery {
    const val BUILTIN = "builtin"
    const val OWN = "own"
    const val DEVICE_PREFIX = "device:"

    /** The permission that lets the launcher read the box's pictures. */
    val PERMISSION: String get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canReadDevice(context: Context) = ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** The pictures of the library, by folder (folder names A to Z, each folder's pictures newest first). */
    fun deviceFolders(context: Context): List<GalleryFolder> {
        if (!canReadDevice(context)) return emptyList()
        val by = LinkedHashMap<String, MutableList<String>>()
        runCatching {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val columns = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            context.contentResolver.query(uri, columns, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val bucket = c.getString(1)?.takeIf { it.isNotEmpty() } ?: "…"
                    by.getOrPut(bucket) { ArrayList() }.add("content://media/external/images/media/$id")
                }
            }
        }
        return by.entries.sortedBy { it.key.lowercase() }.map { GalleryFolder(DEVICE_PREFIX + it.key, it.key, it.value, device = true) }
    }

    /** Every folder with pictures: the built-in ones, the launcher's own folder, then the library's. */
    fun folders(context: Context): List<GalleryFolder> {
        val all = Wallpapers.list(context)
        val built = all.filter { it.startsWith("asset:") }
        val own = all.filter { !it.startsWith("asset:") }
        return listOfNotNull(
            GalleryFolder(BUILTIN, str(R.string.built_in), built).takeIf { built.isNotEmpty() },
            GalleryFolder(OWN, str(R.string.your_own_pictures), own).takeIf { own.isNotEmpty() },
        ) + deviceFolders(context)
    }

    /** The pictures the slideshow goes through: the ticked ones that are still there, else the built-in and the user's own. */
    fun slideshow(context: Context, picks: List<String>): List<String> {
        val base = Wallpapers.list(context)
        if (picks.isEmpty()) return base
        val usable = picks.filter { Wallpapers.exists(context, it) }
        return usable.ifEmpty { base }
    }

    // ----- previews -----

    private val cache = object : android.util.LruCache<String, ImageBitmap>(160) {}
    private val gate = java.util.concurrent.Semaphore(2)

    fun cached(id: String): ImageBitmap? = cache.get(id)

    /** A small copy of a picture for its tile (null when it cannot be read); a couple at a time, so the box stays responsive. */
    fun thumb(context: Context, id: String, w: Int = 320, h: Int = 180): ImageBitmap? {
        cache.get(id)?.let { return it }
        gate.acquire()
        try {
            cache.get(id)?.let { return it }
            val bitmap = Wallpapers.decodeSmall(context, id, w, h)?.asImageBitmap() ?: return null
            cache.put(id, bitmap)
            return bitmap
        } finally {
            gate.release()
        }
    }
}
