package app.opentaiment.headunit.car

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import app.opentaiment.feature.apps.AppsRepository
import app.opentaiment.headunit.MainActivity

/** Media volume through AudioManager. */
class AudioVolume(context: Context) : VolumeControl {
    private val audio = context.getSystemService(AudioManager::class.java)
    override val volume: Int get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    override val maxVolume: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    override fun setVolume(value: Int) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
}

/** System-wide screen brightness (the window-level dimming only works inside our own app). */
object Brightness {
    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    fun permissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun apply(context: Context, percent: Int): Boolean {
        if (!canWrite(context)) return false
        return try {
            val resolver = context.contentResolver
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (percent.coerceIn(2, 100) * 255 / 100))
            true
        } catch (e: SecurityException) {
            false
        }
    }
}

/** Runs a [KeyAction] – from the launcher window or from the accessibility service. */
class KeyActionPerformer(context: Context) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val apps = AppsRepository(appContext)

    fun perform(action: KeyAction) {
        when (action) {
            KeyAction.PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            KeyAction.NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            KeyAction.PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            KeyAction.VOLUME_UP -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            KeyAction.VOLUME_DOWN -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            KeyAction.MUTE -> audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            KeyAction.HOME -> openApp(null)
            KeyAction.NAVIGATION -> apps.openNavigation()
            KeyAction.NAVIGATE_HOME -> {
                val home = CarSettingsStore.get(appContext).settings.value.homeAddress
                if (home.isNotBlank()) apps.navigateTo(home) else apps.openNavigation()
            }
            KeyAction.RADIO -> openApp(MainActivity.SCREEN_PLAYER)
            KeyAction.DRIVE -> openApp(MainActivity.SCREEN_DRIVE)
            KeyAction.PHONE -> apps.openDialer()
            KeyAction.VOICE -> start(Intent(Intent.ACTION_VOICE_COMMAND))
            KeyAction.IGNORE -> Unit
        }
    }

    /** Goes to whichever app owns the active media session (our radio, Spotify…). */
    private fun mediaKey(code: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun openApp(screen: String?) {
        start(
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .apply { if (screen != null) putExtra(MainActivity.EXTRA_SCREEN, screen) },
        )
    }

    private fun start(intent: Intent) {
        try {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
