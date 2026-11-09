package app.opentaiment.phone

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.opentaiment.core.ui.AppearanceController
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.data.VehicleAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpentaimentPhoneApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var appearance: AppearanceController
        private set

    override fun onCreate() {
        super.onCreate()
        appearance = AppearanceController(this, scope)
        createChannels()

        val vehicle = VehicleHub.get(this)
        // A real car connection (from the phone UI or from Android Auto) runs in a foreground
        // service, so Android doesn't kill it while the phone is locked in its mount.
        scope.launch {
            vehicle.connection.collect { state ->
                val real = (state is ConnectionState.Connecting) ||
                    (state is ConnectionState.Connected && !state.demo)
                if (real) VehicleService.start(this@OpentaimentPhoneApp) else VehicleService.stop(this@OpentaimentPhoneApp)
            }
        }
        scope.launch { vehicle.newAlerts.collect(::notifyAlert) }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CONNECTION, "Połączenie z autem", NotificationManager.IMPORTANCE_LOW),
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Ostrzeżenia o aucie", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    private fun notifyAlert(alert: VehicleAlert) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ ${alert.type.title}")
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(ALERT_ID_BASE + alert.type.ordinal, notification)
    }

    companion object {
        const val CHANNEL_CONNECTION = "connection"
        const val CHANNEL_ALERTS = "alerts"
        private const val ALERT_ID_BASE = 100
    }
}
