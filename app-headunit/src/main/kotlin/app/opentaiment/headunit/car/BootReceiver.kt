package app.opentaiment.headunit.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.opentaiment.headunit.MainActivity

/**
 * Device booted (car started, on head units that power off with the ignition): start the
 * background service, which reconnects OBD and resumes the radio.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val settings = CarSettingsStore.get(context).settings.value
        if (!settings.autostart) return
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT, ACTION_QUICKBOOT_HTC, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                HeadunitService.start(context, ignitionOn = intent.action != Intent.ACTION_MY_PACKAGE_REPLACED)
                // Android only lets a background app open its screen with the overlay permission.
                if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED && OverlayBubble.canDraw(context)) {
                    try {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_QUICKBOOT_HTC = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
