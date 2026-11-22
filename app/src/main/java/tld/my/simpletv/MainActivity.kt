package tld.my.simpletv

// The launcher's one activity. It is the box's Home (see README.md): the Home key brings it back (onNewIntent), and the
// work that keeps the screen fresh runs only while it is in front.
//
// Calendars can also be given from a computer, which is easier than typing a long secret address with the remote:
//   adb shell am start -n tld.my.simpletv/.MainActivity --esa calendars "https://…/basic.ics,https://…/basic.ics"

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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.lifecycle.lifecycleScope
import tld.my.simpletv.ui.DesignScreen
import tld.my.simpletv.ui.HomeScreen
import tld.my.simpletv.ui.KeyRead
import tld.my.simpletv.ui.KeyReader
import tld.my.simpletv.ui.KeyTarget
import tld.my.simpletv.ui.SettingsPanel
import tld.my.simpletv.ui.WidgetFont

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState
    private val keys = KeyTarget()
    private val reader = KeyReader()
    private var inFront = false
    /** keys that woke the screen saver, until they are let go */
    private val wokeWith = mutableSetOf<Int>()

    private val askPhotos = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { state.photosAsked() }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = state.reloadApps(force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = LauncherState(applicationContext, lifecycleScope)
        state.requestPhotos = { askPhotos.launch(Gallery.PERMISSION) }
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
                        // the screen saver: any key only wakes it, and its repeats and release do nothing else
                        val ne = e.nativeKeyEvent
                        if (ne.keyCode in wokeWith) {
                            if (ne.action == android.view.KeyEvent.ACTION_UP) wokeWith.remove(ne.keyCode)
                            return@onKeyEvent true
                        }
                        if (ne.action == android.view.KeyEvent.ACTION_DOWN && state.input()) { wokeWith.add(ne.keyCode); return@onKeyEvent true }
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
                    if (state.screen == Screen.SETTINGS) Box(Modifier.fillMaxSize().graphicsLayer {
                        // Settings fades whole under the screen saver
                        val now = state.saver.frameMs
                        alpha = if (now == 0L) 1f else 1f - state.saver.progress(now)
                    }) { SettingsPanel(state, keys) }
                    // the banners, over every screen
                    tld.my.simpletv.ui.Notices(state.notices, state.wall, state.cardLook, state.cardLookBefore, state.cardGlass, state.config.shadows)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSettings(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_FROM_HOME_KEY, false)) {
            // Home over the screen saver only wakes it
            if (inFront && state.input()) return
            state.homePressed(whileShown = inFront)
        }
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

    /** Settings handed over with adb (see the top of this file), and for trying things out without a remote:
     *  `--es notice "text"` shows a banner, `--ez saver true` starts the screen saver now. */
    private fun takeSettings(intent: Intent?) {
        intent?.getStringExtra("notice")?.let { state.notices.show(tld.my.simpletv.ui.NoticeKind.INFO, it, intent.getStringExtra("noticeText").orEmpty()); intent.removeExtra("notice") }
        if (intent?.getBooleanExtra("saver", false) == true) { state.saver.start(System.currentTimeMillis()); intent.removeExtra("saver") }
        val list = intent?.getStringArrayExtra("calendars") ?: return
        val urls = list.map { Cal.normalizeUrl(it) }.filter { Cal.validUrl(it) }
        if (urls.isNotEmpty()) state.update { it.copy(calendars = urls, calendarOn = true) }
        intent.removeExtra("calendars")
    }

    companion object {
        const val EXTRA_FROM_HOME_KEY = "launcher.home"
    }
}
