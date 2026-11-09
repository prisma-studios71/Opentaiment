package app.opentaiment.feature.vehicle.race

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat

/** Plain platform GPS (no Play Services needed), ~2 fixes per second. */
class GpsSource(context: Context) {
    private val appContext = context.applicationContext
    // Looked up only when race mode actually needs GPS.
    private val manager: LocationManager? by lazy { appContext.getSystemService(LocationManager::class.java) }
    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked in hasPermission()
    fun start(onFix: (GpsFix) -> Unit): Boolean {
        if (listener != null) return true
        val lm = manager
        if (!hasPermission() || lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return false
        val l = LocationListener { location -> onFix(location.toFix()) }
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, l, Looper.getMainLooper())
        listener = l
        return true
    }

    fun stop() {
        listener?.let { manager?.removeUpdates(it) }
        listener = null
    }

    private fun Location.toFix() = GpsFix(
        timeMs = elapsedRealtimeNanos / 1_000_000,
        lat = latitude,
        lon = longitude,
        speedMs = if (hasSpeed()) speed.toDouble() else null,
        bearingDeg = if (hasBearing()) bearing.toDouble() else null,
        accuracyM = if (hasAccuracy()) accuracy.toDouble() else null,
    )

    companion object {
        fun now() = SystemClock.elapsedRealtime()
    }
}
