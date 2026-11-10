package app.opentaiment.headunit.car

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.opentaiment.core.ui.AppearanceController
import app.opentaiment.feature.player.PlaybackKind
import app.opentaiment.feature.player.PlayerHub
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.VehicleHub
import app.opentaiment.feature.vehicle.tpms.TpmsHub
import app.opentaiment.headunit.MainActivity
import app.opentaiment.headunit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The "car radio brain", running for as long as the tablet is on: keeps the OBD link and
 * radio alive, reacts to ignition on/off, speed-dependent volume, screen brightness, the
 * overlay bubble, tyre sensors and GPS traces of trips.
 */
class HeadunitService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vehicle by lazy { VehicleHub.get(this) }
    private val player by lazy { PlayerHub.get(this) }
    private val tpms by lazy { TpmsHub.get(this) }
    private val settings by lazy { CarSettingsStore.get(this) }
    private val overlay by lazy { OverlayBubble(this) }
    private val speedVolume by lazy { SpeedVolumeController(AudioVolume(this)) }
    private var withLocation = false
    private var started = false
    private var lastTpmsWarning: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val byPower = settings.settings.value.ignitionByPower
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> vehicle.autoConnect()
                Intent.ACTION_POWER_CONNECTED -> if (byPower) ignitionOn()
                Intent.ACTION_POWER_DISCONNECTED -> if (byPower) ignitionOff()
                // Power may be cut any second now: save the trip.
                Intent.ACTION_SHUTDOWN -> vehicle.onIgnitionOff()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            startLoops()
        }
        if (intent?.getBooleanExtra(EXTRA_IGNITION_ON, false) == true) ignitionOn()
        else vehicle.autoConnect()
        return START_STICKY
    }

    private fun ignitionOn() {
        vehicle.autoConnect()
        tpms.start()
        if (settings.settings.value.resumeRadio) player.resumeLastStation()
    }

    private fun ignitionOff() {
        vehicle.onIgnitionOff()
        player.pause()
        speedVolume.reset()
    }

    private fun startLoops() {
        tpms.start()

        // Once a second: speed volume, GPS needs, overlay and the notification.
        scope.launch {
            var tick = 0
            while (true) {
                tick()
                if (tick++ % 5 == 0) updateNotification()
                delay(1_000)
            }
        }

        // Brightness follows day/night.
        val appearance = AppearanceController(this, scope)
        scope.launch {
            combine(appearance.state, settings.settings) { look, s -> Triple(s.brightnessControl, look.isNight, if (look.isNight) s.nightBrightnessPct else s.dayBrightnessPct) }
                .distinctUntilChanged()
                .collect { (on, _, pct) -> if (on) Brightness.apply(this@HeadunitService, pct) }
        }

        // The launcher came to the front: we may now use GPS in the foreground service.
        scope.launch {
            AppForeground.visible.collect { visible ->
                appearance.refresh()
                if (visible && !withLocation && hasFineLocation()) goForeground()
            }
        }

        // Tyre warnings as a notification (the bubble and home screen show them too).
        scope.launch {
            tpms.state.map { s -> s.problems.firstOrNull()?.let { (w, p) -> "${w.label}: ${p.label}" } }
                .distinctUntilChanged()
                .collect { warning ->
                    if (warning != null && warning != lastTpmsWarning) notifyWarning("Opony", warning)
                    lastTpmsWarning = warning
                }
        }
        scope.launch {
            vehicle.newAlerts.collect { notifyWarning(it.type.title, it.message) }
        }
    }

    private fun tick() {
        val s = settings.settings.value
        val connection = vehicle.connection.value
        val obdConnected = connection is ConnectionState.Connected && !connection.demo
        val fine = hasFineLocation() && withLocation

        // GPS: trip traces while driving with OBD; speed for the volume when OBD is not there.
        if (obdConnected && fine) vehicle.requestGps(GPS_TRACK) else vehicle.releaseGps(GPS_TRACK)
        val needGpsSpeed = s.speedVolume != SpeedVolumeLevel.OFF && !obdConnected && fine
        if (needGpsSpeed) vehicle.requestGps(GPS_VOLUME) else vehicle.releaseGps(GPS_VOLUME)

        val stats = vehicle.stats.value
        val speed = (if (connection is ConnectionState.Connected) stats.speedKmh else null) ?: vehicle.gpsSpeedKmh.value
        if (s.speedVolume == SpeedVolumeLevel.OFF) speedVolume.reset() else speedVolume.update(speed, s.speedVolume)

        // Bubble over other apps.
        if (s.overlay && !AppForeground.visible.value) {
            val tyre = tpms.state.value.problems.firstOrNull()
            val alert = stats.alerts.firstOrNull()?.takeIf { connection is ConnectionState.Connected }
            val parts = listOfNotNull(
                speed?.let { String.format(PL, "%.0f km/h", it) },
                stats.instantLPer100Km?.takeIf { connection is ConnectionState.Connected }?.let { String.format(PL, "%.1f l/100", it) },
                alert?.type?.title?.let { "⚠ $it" },
                tyre?.let { (w, p) -> "⚠ ${w.short} ${p.label.lowercase(PL)}" },
            )
            if (parts.isEmpty()) overlay.hide() else overlay.show(parts.joinToString("  ·  "), warning = alert != null || tyre != null)
        } else {
            overlay.hide()
        }
    }

    private fun goForeground(): Boolean {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        val location = hasFineLocation() && (AppForeground.visible.value || Build.VERSION.SDK_INT < 34)
        if (location) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(statusText()), types)
            withLocation = location
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground with types $types failed", e)
            if (!location) return false
            try {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(statusText()), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                withLocation = false
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun statusText(): String {
        val car = when (val c = vehicle.connection.value) {
            is ConnectionState.Connected -> {
                val st = vehicle.stats.value
                listOfNotNull(
                    st.speedKmh?.let { String.format(PL, "%.0f km/h", it) },
                    st.instantLPer100Km?.let { String.format(PL, "%.1f l/100", it) },
                    String.format(PL, "%.1f km", st.trip.distanceKm),
                ).joinToString(" · ")
            }
            is ConnectionState.Connecting -> "Łączę z ${c.deviceName}…"
            is ConnectionState.Failed -> if (c.reconnecting) "Czekam na adapter OBD…" else "OBD rozłączone"
            ConnectionState.Disconnected -> "OBD rozłączone"
        }
        val music = player.state.value.takeIf { it.kind != PlaybackKind.NONE && it.isPlaying }?.title
        return listOfNotNull(car.ifEmpty { null }, music?.let { "♪ $it" }).joinToString("  ·  ")
    }

    private fun updateNotification() {
        if (hasNotificationPermission()) NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(statusText()))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_STATUS)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Opentaiment")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(openApp())
        .build()

    private fun notifyWarning(title: String, text: String) {
        if (!hasNotificationPermission()) return
        NotificationManagerCompat.from(this).notify(
            WARNING_ID + Math.floorMod(title.hashCode(), 100),
            NotificationCompat.Builder(this, CHANNEL_WARNINGS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun openApp() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_STATUS, "Działanie w tle", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WARNINGS, "Ostrzeżenia z auta", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun hasFineLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun hasNotificationPermission() = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        overlay.hide()
        speedVolume.reset()
        vehicle.releaseGps(GPS_TRACK)
        vehicle.releaseGps(GPS_VOLUME)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HeadunitService"
        private const val NOTIFICATION_ID = 1
        private const val WARNING_ID = 100
        private const val CHANNEL_STATUS = "status"
        private const val CHANNEL_WARNINGS = "warnings"
        private const val EXTRA_IGNITION_ON = "ignition_on"
        private const val GPS_TRACK = "track"
        private const val GPS_VOLUME = "volume"
        private val PL = Locale("pl")

        fun start(context: Context, ignitionOn: Boolean = false) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, HeadunitService::class.java).putExtra(EXTRA_IGNITION_ON, ignitionOn),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Foreground service not allowed right now", e)
            }
        }
    }
}
