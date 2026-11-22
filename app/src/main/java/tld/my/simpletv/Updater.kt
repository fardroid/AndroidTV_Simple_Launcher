package tld.my.simpletv

// Updating the launcher from the Update tile (as Simple LG launcher's Update tile does on the TV): the release's APK is downloaded
// into the app's own cache, checked against the size and SHA-256 GitHub gives for it, and handed to Android's package installer
// in a session. Android then asks the user to confirm on the screen (it always does for an app that is not a store), installs it
// over this one (the same signing key) and starts the new version.
//
// Android installs an app from another one only once the user has allowed that app to (Settings → Apps → Special app access →
// Install unknown apps, which `canInstall` reads); until then the tile opens that page for the launcher.

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Updater {
    const val ACTION_STATUS = "tld.my.simpletv.INSTALL_STATUS"
    private const val TAG = "SimpleTVUpdate"

    /** Whether Android lets the launcher install an app (the user's Install unknown apps for it). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens Install unknown apps for the launcher, where the user allows it. */
    fun openAllow(context: Context) {
        val page = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(page) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Downloads the release's APK (blocking: Dispatchers.IO), `progress` 0-1 as it comes; the file, checked, or an exception. */
    fun download(context: Context, u: UpdateInfo, progress: (Float) -> Unit): File {
        require(u.apk.startsWith("https://github.com/") && u.sha256.length == 64) { "the release has no APK with a checksum" }
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }       // an earlier one, finished or not
        val file = File(dir, "launcher-${u.version}.apk")
        val c = URL(u.apk).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", Net.AGENT)
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            val total = if (u.apkSize > 0) u.apkSize else c.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        done += n
                        if (u.apkSize > 0 && done > u.apkSize) throw java.io.IOException("larger than the release says")
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                        if (total > 0) progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != u.sha256.lowercase()) { file.delete(); throw java.io.IOException("the file came damaged") }
            return file
        } finally {
            c.disconnect()
        }
    }

    /** Hands the APK to Android's installer; its answers (the confirmation it asks for, success or failure) come to Status. */
    fun install(context: Context, apk: File) {
        android.util.Log.i(TAG, "installing ${apk.name}, ${apk.length()} bytes")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("launcher.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val status = Intent(context, Status::class.java).setAction(ACTION_STATUS).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, status, flags).intentSender)
        }
    }

    /** What the installer says: it wants the user to confirm (its screen is shown), or it failed (the launcher says so). On
     *  success the launcher is replaced and started again, so nothing is said here. */
    class Status : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.i(TAG, "installer: ${intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -99)} ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
                }
                PackageInstaller.STATUS_SUCCESS -> {}
                else -> onFailed?.invoke(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
            }
        }
    }

    /** Set by the launcher while it runs: the installer said no (the message it gave). */
    @Volatile var onFailed: ((String) -> Unit)? = null
}
