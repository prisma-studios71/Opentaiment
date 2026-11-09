package app.opentaiment.headunit.car

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional: sees hardware key presses even while navigation or another app is in front, so
 * steering wheel buttons work everywhere. Only keys the user mapped are consumed; everything
 * else passes through untouched. It reads no screen content.
 */
class KeyListenerService : AccessibilityService() {

    private lateinit var performer: KeyActionPerformer

    override fun onServiceConnected() {
        performer = KeyActionPerformer(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (KeyLearning.active.value) {
            if (event.action == KeyEvent.ACTION_UP) KeyLearning.offer(code)
            return !KeyBindings.isProtected(code)
        }
        val action = CarSettingsStore.get(this).settings.value.keyBindings[code] ?: return false
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) performer.perform(action)
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, KeyListenerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
