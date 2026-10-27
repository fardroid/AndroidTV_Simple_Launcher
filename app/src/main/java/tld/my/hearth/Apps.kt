package tld.my.hearth

// The apps on the home row: every app with a TV (leanback) or phone launcher entry, with its name, icon and TV banner drawn
// once into bitmaps of the size the tiles need.

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

data class AppEntry(
    val pkg: String,
    val label: String,
    val component: ComponentName,
    /** the icon filling a square tile (adaptive icons edge to edge), or null */
    val icon: ImageBitmap?,
    /** true when the icon is a full-bleed square (an adaptive icon); otherwise it is drawn smaller on the tile's own colour */
    val iconFull: Boolean,
    /** the TV banner (16:9), or null when the app has none */
    val banner: ImageBitmap?,
)

object Apps {
    /** Tiles that are not apps: they open the launcher's settings and the box's own settings. */
    const val HEARTH_SETTINGS = "hearth:settings"
    const val TV_SETTINGS = "hearth:tvsettings"

    /** Every launchable app except the launcher itself and stock home screens. `iconPx`/`bannerW`x`bannerH`: bitmap sizes in pixels. */
    fun load(context: Context, iconPx: Int, bannerW: Int, bannerH: Int): List<AppEntry> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ActivityInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (r in pm.queryIntentActivities(intent, 0)) {
                val a = r.activityInfo ?: continue
                if (a.packageName == context.packageName || a.packageName in STOCK_HOMES) continue
                found.putIfAbsent(a.packageName, a)
            }
        }
        return found.values.map { a ->
            val label = a.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { a.packageName }
            val (icon, full) = runCatching { iconBitmap(a.loadIcon(pm), iconPx) }.getOrDefault(null to false)
            val bannerDrawable = runCatching { a.loadBanner(pm) ?: a.applicationInfo.loadBanner(pm) }.getOrNull()
            val banner = bannerDrawable?.let { runCatching { draw(it, bannerW, bannerH) }.getOrNull() }
            AppEntry(a.packageName, label, ComponentName(a.packageName, a.name), icon, full, banner)
        }
    }

    /** The user's order first, then the rest A to Z; hidden apps left out. */
    fun ordered(apps: List<AppEntry>, config: Config): List<AppEntry> {
        val byPkg = apps.associateBy { it.pkg }
        val first = config.appOrder.mapNotNull { byPkg[it] }
        val rest = apps.filter { it.pkg !in config.appOrder }.sortedBy { it.label.lowercase() }
        return (first + rest).filter { it.pkg !in config.hidden }
    }

    fun launch(context: Context, app: AppEntry): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.pkg) ?: pm.getLaunchIntentForPackage(app.pkg)
            ?: Intent(Intent.ACTION_MAIN).setComponent(app.component)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openTvSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAppInfo(context: Context, pkg: String) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun uninstall(context: Context, pkg: String) {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, android.net.Uri.fromParts("package", pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** The stock home screens of Android TV and Google TV (never shown as tiles). */
    val STOCK_HOMES = setOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
        "com.mitv.tvhome.atv",
        "com.android.tv.settings",          // it has its own tile ("TV settings")
    )

    /** An adaptive icon edge to edge (its outer margin, which a launcher's mask always hides, cut off); any other icon centred at 72 %. */
    private fun iconBitmap(d: Drawable, size: Int): Pair<ImageBitmap, Boolean> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            val pad = (size * 18f / 72f).roundToInt()
            for (layer in listOf(d.background, d.foreground)) {
                layer ?: continue
                layer.setBounds(-pad, -pad, size + pad, size + pad)
                layer.draw(canvas)
            }
            return bmp.asImageBitmap() to true
        }
        val inner = (size * 0.72f).roundToInt()
        val off = (size - inner) / 2
        d.setBounds(off, off, off + inner, off + inner)
        d.draw(canvas)
        return bmp.asImageBitmap() to false
    }

    private fun draw(d: Drawable, w: Int, h: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, w, h)
        d.draw(Canvas(bmp))
        return bmp.asImageBitmap()
    }
}
