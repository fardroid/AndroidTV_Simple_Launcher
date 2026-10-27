package tld.my.hearth

// The launcher's one activity. It is the box's Home (see README.md): the Home key brings it back (onNewIntent), and the
// work that keeps the screen fresh runs only while it is in front.
//
// Calendars can also be given from a computer, which is easier than typing a long secret address with the remote:
//   adb shell am start -n tld.my.hearth/.MainActivity --esa calendars "https://…/basic.ics,https://…/basic.ics"

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.lifecycleScope
import tld.my.hearth.ui.DesignScreen
import tld.my.hearth.ui.HomeScreen
import tld.my.hearth.ui.KeyRead
import tld.my.hearth.ui.KeyReader
import tld.my.hearth.ui.KeyTarget
import tld.my.hearth.ui.SettingsPanel
import tld.my.hearth.ui.WidgetFont

class MainActivity : ComponentActivity() {
    private lateinit var state: HearthState
    private val keys = KeyTarget()
    private val reader = KeyReader()
    private var inFront = false

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = state.reloadApps(force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = HearthState(applicationContext, lifecycleScope)
        takeSettings(intent)
        // Back never leaves the home screen (the screens use it themselves)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        registerReceiver(packages, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        setContent {
            DesignScreen {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { keys.refocus = { runCatching { focus.requestFocus() } }; focus.requestFocus() }
                Box(
                    Modifier.fillMaxSize().focusRequester(focus).focusable().onKeyEvent { e ->
                        when (val r = reader.read(e.nativeKeyEvent)) {
                            KeyRead.NotOurs -> false
                            KeyRead.Swallow -> true
                            is KeyRead.Key -> { keys.handler(r.nav, r.repeat); true }
                        }
                    },
                ) {
                    // the widgets' font follows Settings → Widgets → Font
                    val font = state.config.clockFont
                    SideEffect { WidgetFont.id = font }
                    HomeScreen(state, keys, active = state.screen == Screen.HOME)
                    if (state.screen == Screen.SETTINGS) SettingsPanel(state, keys)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSettings(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_FROM_HOME_KEY, false)) state.homePressed(whileShown = inFront)
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        state.resume()
    }

    override fun onPause() {
        inFront = false
        state.pause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packages)
        super.onDestroy()
    }

    /** Settings handed over with adb (see the top of this file). */
    private fun takeSettings(intent: Intent?) {
        val list = intent?.getStringArrayExtra("calendars") ?: return
        val urls = list.map { Cal.normalizeUrl(it) }.filter { Cal.validUrl(it) }
        if (urls.isNotEmpty()) state.update { it.copy(calendars = urls, calendarOn = true) }
        intent.removeExtra("calendars")
    }

    companion object {
        const val EXTRA_FROM_HOME_KEY = "hearth.home"
    }
}
