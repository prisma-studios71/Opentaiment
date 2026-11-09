package app.opentaiment.feature.vehicle.tpms

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TpmsHub {
    @Volatile
    private var instance: TpmsRepository? = null

    fun get(context: Context): TpmsRepository = instance ?: synchronized(this) {
        instance ?: TpmsRepository(context.applicationContext, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
            .also { instance = it }
    }
}

/** Listens for BLE tyre sensors in the background and keeps the latest reading per wheel. */
class TpmsRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("tpms", Context.MODE_PRIVATE)

    private val readings = LinkedHashMap<String, TpmsReading>()
    private var assigned: Map<String, Wheel> = loadAssigned()
    private var limits = TpmsLimits(
        minBar = prefs.getFloat(KEY_MIN, 1.9f).toDouble(),
        maxBar = prefs.getFloat(KEY_MAX, 3.3f).toDouble(),
    )

    private val _state = MutableStateFlow(TpmsState(limits = limits))
    val state: StateFlow<TpmsState> = _state.asStateFlow()

    private var callback: ScanCallback? = null
    private var ticker: Job? = null

    val enabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)

    fun hasPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(appContext, needed) == PackageManager.PERMISSION_GRANTED
    }

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        if (on) start() else stop()
    }

    /** Starts listening if the user turned TPMS on. Safe to call repeatedly. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (!enabled || callback != null) return
        if (!hasPermission()) {
            _state.value = _state.value.copy(error = "Brak zgody na skanowanie Bluetooth")
            return
        }
        val scanner = appContext.getSystemService(BluetoothManager::class.java)?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            _state.value = _state.value.copy(error = "Bluetooth jest wyłączony")
            return
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
            override fun onScanFailed(errorCode: Int) {
                _state.value = _state.value.copy(scanning = false, error = "Skanowanie nie działa (kod $errorCode)")
                callback = null
            }
        }
        val filter = ScanFilter.Builder().setManufacturerData(TpmsParser.COMPANY_ID, ByteArray(0)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        try {
            scanner.startScan(listOf(filter), settings, cb)
            callback = cb
            _state.value = _state.value.copy(scanning = true, error = null)
        } catch (e: SecurityException) {
            _state.value = _state.value.copy(error = "Brak zgody na skanowanie Bluetooth")
        }
        // Re-evaluate "no signal" even when nothing new arrives.
        ticker = scope.launch {
            while (true) {
                delay(30_000)
                publish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        callback?.let { cb ->
            try {
                appContext.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
            } catch (_: Exception) {
            }
        }
        callback = null
        ticker?.cancel()
        _state.value = _state.value.copy(scanning = false)
    }

    private fun handle(result: ScanResult) {
        val data = result.scanRecord?.getManufacturerSpecificData(TpmsParser.COMPANY_ID) ?: return
        val reading = TpmsParser.parse(data, System.currentTimeMillis()) ?: return
        scope.launch {
            readings[reading.sensorId] = reading
            publish()
        }
    }

    fun assign(sensorId: String, wheel: Wheel) {
        assigned = assigned.filterValues { it != wheel } + (sensorId to wheel)
        saveAssigned()
        publish()
    }

    fun forgetWheel(wheel: Wheel) {
        val ids = assigned.filterValues { it == wheel }.keys
        assigned = assigned - ids
        ids.forEach { readings.remove(it) }
        saveAssigned()
        publish()
    }

    fun setLimits(newLimits: TpmsLimits) {
        limits = newLimits
        prefs.edit {
            putFloat(KEY_MIN, newLimits.minBar.toFloat())
            putFloat(KEY_MAX, newLimits.maxBar.toFloat())
        }
        publish()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val byWheel = HashMap<Wheel, TpmsReading>()
        val unassigned = ArrayList<TpmsReading>()
        readings.values.forEach { r ->
            val wheel = TpmsParser.wheelFor(r, assigned)
            if (wheel != null && (byWheel[wheel]?.timeMs ?: 0) <= r.timeMs) byWheel[wheel] = r
            else if (wheel == null) unassigned += r
        }
        _state.value = _state.value.copy(
            wheels = Wheel.entries.map { w -> WheelStatus(w, byWheel[w], TpmsParser.problems(byWheel[w], limits, now)) },
            unassigned = unassigned.sortedByDescending { it.timeMs },
            limits = limits,
        )
    }

    private fun loadAssigned(): Map<String, Wheel> =
        prefs.getString(KEY_ASSIGNED, null).orEmpty().split(';').mapNotNull { entry ->
            val (id, wheel) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            Wheel.entries.firstOrNull { it.name == wheel }?.let { id to it }
        }.toMap()

    private fun saveAssigned() = prefs.edit { putString(KEY_ASSIGNED, assigned.entries.joinToString(";") { "${it.key}=${it.value.name}" }) }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_ASSIGNED = "assigned"
        const val KEY_MIN = "min_bar"
        const val KEY_MAX = "max_bar"
    }
}
