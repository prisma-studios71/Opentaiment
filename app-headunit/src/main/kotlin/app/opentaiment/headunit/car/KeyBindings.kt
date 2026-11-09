package app.opentaiment.headunit.car

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a steering wheel (or any hardware) button can do. */
enum class KeyAction(val label: String) {
    PLAY_PAUSE("Graj / pauza"),
    NEXT("Następny utwór / stacja"),
    PREVIOUS("Poprzedni utwór / stacja"),
    VOLUME_UP("Głośniej"),
    VOLUME_DOWN("Ciszej"),
    MUTE("Wycisz"),
    HOME("Ekran główny"),
    NAVIGATION("Nawigacja"),
    NAVIGATE_HOME("Prowadź do domu"),
    RADIO("Radio i muzyka"),
    DRIVE("Ekran Jazda"),
    PHONE("Telefon"),
    VOICE("Asystent głosowy"),
    IGNORE("Nic (zablokuj przycisk)"),
}

object KeyBindings {
    fun encode(bindings: Map<Int, KeyAction>): String = bindings.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun parse(stored: String?): Map<Int, KeyAction> = stored.orEmpty().split(';').mapNotNull { entry ->
        val parts = entry.split('=')
        if (parts.size != 2) return@mapNotNull null
        val code = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
        val action = KeyAction.entries.firstOrNull { it.name == parts[1].trim() } ?: return@mapNotNull null
        code to action
    }.toMap()

    /** "MEDIA_NEXT" rather than "KEYCODE_MEDIA_NEXT"; unknown vendor codes show their number. */
    fun keyName(code: Int): String {
        val name = KeyEvent.keyCodeToString(code)
        return if (name.startsWith("KEYCODE_")) name.removePrefix("KEYCODE_") else "Klawisz $code"
    }

    /** Keys that must never be swallowed, or the device becomes unusable. */
    fun isProtected(code: Int): Boolean = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_POWER
}

/**
 * Bridge between whatever sees key presses (the launcher window, or the accessibility service
 * when another app is in front) and the settings screen's "press a button to learn it" dialog.
 */
object KeyLearning {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _learned = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val learned: SharedFlow<Int> = _learned.asSharedFlow()

    fun start() { _active.value = true }
    fun stop() { _active.value = false }

    /** Returns true when the key was captured for learning (and must not do anything else). */
    fun offer(code: Int): Boolean {
        if (!_active.value || KeyBindings.isProtected(code)) return false
        _learned.tryEmit(code)
        return true
    }
}
