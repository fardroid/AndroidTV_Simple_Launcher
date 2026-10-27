package tld.my.hearth

// Whether the launcher is the box's Home, and the optional accessibility helper that takes the Home key when the stock launcher stays on.

import android.accessibilityservice.AccessibilityService
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

object HomeRole {
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** The launcher is the box's home app: chosen in Settings → Default home screen (the Home role, Android 10 and later), or
     *  the only home app left. Resolving the Home intent alone is not enough: on Google TV it still names the stock launcher
     *  after another home app has been chosen. */
    fun isDefault(context: Context): Boolean {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)) return true
        return context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == context.packageName
    }

    /** Opens the box's own choice of home app (Settings → Default home screen); the general settings when there is none. */
    fun openHomeSettings(context: Context) {
        val tries = listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)
        for (action in tries) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    /** The stock home app still switched on, if any. */
    fun stockHome(context: Context): String? =
        context.packageManager.queryIntentActivities(homeIntent(), 0).map { it.activityInfo.packageName }.firstOrNull { it in Apps.STOCK_HOMES }

    fun watchOn(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
    }

    /** The Home key really opens the launcher. Google TV sends the Home key to its own launcher whichever home app is chosen,
     *  so there it takes the Home key helper too, unless the stock launcher is switched off. */
    fun homeKeyOpensLauncher(context: Context): Boolean = watchOn(context) || (isDefault(context) && stockHome(context) == null)

    /** Opens the accessibility settings, where the Home key helper (Simple TV launcher) is switched on. Android TV's own link
     *  first: on Google TV the standard one opens an empty stub. */
    fun openAccessibility(context: Context) {
        for (action in listOf("android.settings.ACCESSIBILITY_TV_OEM_LINK", Settings.ACTION_ACCESSIBILITY_SETTINGS)) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager)?.className?.contains("Stub") == true) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    fun openLauncher(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_FROM_HOME_KEY, true)
        runCatching { context.startActivity(intent) }
    }
}

object BuildConfigInfo {
    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}

/** Opens the launcher on the Home key, and whenever the stock Google TV home comes to the front anyway. Only runs when the user
 *  switches it on in the box's accessibility settings; it reads no window contents. */
class HomeWatchService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() in Apps.STOCK_HOMES - "com.android.tv.settings") HomeRole.openLauncher(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP) HomeRole.openLauncher(this)
        return true
    }

    override fun onInterrupt() {}
}
