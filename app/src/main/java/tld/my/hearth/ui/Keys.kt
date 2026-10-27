package tld.my.hearth.ui

// The remote's keys, as the screens see them: the arrows (with whether the key is being held), OK, OK held (or Menu), Back.

import android.view.KeyEvent

enum class Nav { LEFT, RIGHT, UP, DOWN, OK, HOLD, BACK }

sealed interface KeyRead {
    /** not one of our keys: the system gets it */
    data object NotOurs : KeyRead
    /** our key, but nothing happens on this half of it */
    data object Swallow : KeyRead
    data class Key(val nav: Nav, val repeat: Boolean) : KeyRead
}

/** Turns the box's key events into Nav keys. OK is reported when it is let go, unless it was held long enough to count as HOLD. */
class KeyReader {
    private var okHeld = false

    fun read(e: KeyEvent): KeyRead {
        val down = e.action == KeyEvent.ACTION_DOWN
        fun arrow(n: Nav) = if (down) KeyRead.Key(n, e.repeatCount > 0) else KeyRead.Swallow
        return when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> arrow(Nav.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> arrow(Nav.RIGHT)
            KeyEvent.KEYCODE_DPAD_UP -> arrow(Nav.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> arrow(Nav.DOWN)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> when {
                down && e.repeatCount == 0 -> { okHeld = false; KeyRead.Swallow }
                down -> if (!okHeld && e.eventTime - e.downTime >= HOLD_MS) { okHeld = true; KeyRead.Key(Nav.HOLD, false) } else KeyRead.Swallow
                okHeld -> { okHeld = false; KeyRead.Swallow }
                else -> KeyRead.Key(Nav.OK, false)
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> if (down) KeyRead.Swallow else KeyRead.Key(Nav.BACK, false)
            KeyEvent.KEYCODE_MENU -> if (down) KeyRead.Swallow else KeyRead.Key(Nav.HOLD, false)
            else -> KeyRead.NotOurs
        }
    }

    companion object {
        const val HOLD_MS = 600L
    }
}

/** The screen that takes the keys now (each screen puts its handler here while it is shown). */
class KeyTarget {
    var handler: (Nav, Boolean) -> Unit = { _, _ -> }
    /** gives the keys back to the screen (after typing in a text field) */
    var refocus: () -> Unit = {}
}
