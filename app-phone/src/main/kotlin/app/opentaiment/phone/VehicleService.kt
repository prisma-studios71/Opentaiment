package app.opentaiment.phone

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.VehicleHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Foreground service that only exists to keep the process (and the OBD link held by
 * [VehicleHub]) alive in the background. Shows live speed/consumption in its notification.
 */
class VehicleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationLoop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            VehicleHub.get(this).disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Łączę z autem…"), foregroundTypes())
        } catch (e: Exception) {
            // Missing permission or started from the background on a strict Android version:
            // the connection still works while the app or Android Auto is open.
            Log.w(TAG, "Could not start foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (notificationLoop?.isActive != true) notificationLoop = updateNotificationLoop()
        return START_STICKY
    }

    private fun updateNotificationLoop() = scope.launch {
        val vehicle = VehicleHub.get(this@VehicleService)
        while (true) {
            val text = when (val c = vehicle.connection.value) {
                is ConnectionState.Connected -> {
                    val s = vehicle.stats.value
                    listOfNotNull(
                        s.speedKmh?.let { String.format(PL, "%.0f km/h", it) },
                        s.instantLPer100Km?.let { String.format(PL, "%.1f l/100", it) },
                        String.format(PL, "%.1f km", s.trip.distanceKm),
                    ).joinToString(" · ").ifEmpty { "Połączono z ${c.deviceName}" }
                }
                is ConnectionState.Connecting -> "Łączę z ${c.deviceName}…"
                else -> "Rozłączono"
            }
            if (hasNotificationPermission()) {
                NotificationManagerCompat.from(this@VehicleService).notify(NOTIFICATION_ID, notification(text))
            }
            delay(5_000)
        }
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, OpentaimentPhoneApp.CHANNEL_CONNECTION)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Opentaiment · połączono z autem")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            0, "Rozłącz",
            PendingIntent.getService(
                this, 1, Intent(this, VehicleService::class.java).setAction(ACTION_DISCONNECT),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun foregroundTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return types
    }

    private fun hasNotificationPermission() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS)

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VehicleService"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "app.opentaiment.DISCONNECT"
        private val PL = Locale("pl")

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, VehicleService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service not allowed right now", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VehicleService::class.java))
        }
    }
}
