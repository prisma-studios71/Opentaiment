package app.opentaiment.feature.vehicle

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.opentaiment.feature.vehicle.data.DtcReport
import app.opentaiment.feature.vehicle.data.FuelType
import app.opentaiment.feature.vehicle.data.TripCalculator
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.obd.BluetoothObdTransport
import app.opentaiment.feature.vehicle.source.ObdDataSource
import app.opentaiment.feature.vehicle.source.ObdRequest
import app.opentaiment.feature.vehicle.source.SimulatedDataSource
import app.opentaiment.feature.vehicle.source.VehicleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class ObdDevice(val name: String, val address: String) {
    val looksLikeObd: Boolean
        get() = OBD_NAME_HINTS.any { name.uppercase(Locale.ROOT).contains(it) }

    private companion object {
        val OBD_NAME_HINTS = listOf("OBD", "ELM", "V-LINK", "VLINK", "VGATE", "ICAR", "KONNWEI", "VEEPEAK", "CARISTA")
    }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val deviceName: String) : ConnectionState
    data class Connected(val deviceName: String, val demo: Boolean) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

sealed interface DiagnosticsState {
    data object Idle : DiagnosticsState
    data object Reading : DiagnosticsState
    data object Clearing : DiagnosticsState
    data class Ready(val report: DtcReport) : DiagnosticsState
    data class Failed(val message: String, val previous: DtcReport?) : DiagnosticsState
}

/**
 * Owns the OBD connection (or the demo simulator) and the trip calculator.
 * Lives as long as [scope], so the trip keeps counting while other screens are open.
 */
class VehicleRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vehicle", Context.MODE_PRIVATE)
    private val calculator = TripCalculator(loadProfile())

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _stats = MutableStateFlow(VehicleStats())
    val stats: StateFlow<VehicleStats> = _stats.asStateFlow()

    private val _profile = MutableStateFlow(calculator.profile)
    val profile: StateFlow<VehicleProfile> = _profile.asStateFlow()

    private val _diagnostics = MutableStateFlow<DiagnosticsState>(DiagnosticsState.Idle)
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    private var job: Job? = null
    private var requests: Channel<ObdRequest>? = null

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Already paired Bluetooth devices, likely OBD adapters first. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<ObdDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { ObdDevice(it.name ?: it.address, it.address) }
                .sortedWith(compareByDescending<ObdDevice> { it.looksLikeObd }.thenBy { it.name })
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(device: ObdDevice) {
        prefs.edit {
            putString(KEY_LAST_ADDRESS, device.address)
            putString(KEY_LAST_NAME, device.name)
        }
        start(device.name, demo = false, ObdDataSource({ BluetoothObdTransport.connect(appContext, device.address) }))
    }

    /** Reconnects to the last used adapter – handy when the tablet boots with the car. */
    fun autoConnect() {
        if (job?.isActive == true || !hasBluetoothPermission()) return
        val address = prefs.getString(KEY_LAST_ADDRESS, null) ?: return
        connect(ObdDevice(prefs.getString(KEY_LAST_NAME, null) ?: address, address))
    }

    fun startDemo() = start("Tryb demo", demo = true, SimulatedDataSource())

    fun disconnect() {
        job?.cancel()
        job = null
        requests?.close()
        requests = null
        _connection.value = ConnectionState.Disconnected
    }

    /** Reads trouble codes over the open connection. */
    fun readDtcs() {
        val channel = requests ?: return
        if (_diagnostics.value == DiagnosticsState.Reading || _diagnostics.value == DiagnosticsState.Clearing) return
        val previous = lastReport()
        _diagnostics.value = DiagnosticsState.Reading
        scope.launch {
            val request = ObdRequest.ReadDtcs()
            _diagnostics.value = try {
                channel.send(request)
                DiagnosticsState.Ready(withTimeout(DTC_TIMEOUT_MS) { request.result.await() })
            } catch (e: TimeoutCancellationException) {
                DiagnosticsState.Failed("Auto nie odpowiedziało na czas", previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticsState.Failed(e.message ?: "Nie udało się odczytać błędów", previous)
            }
        }
    }

    /** Clears trouble codes (turns Check Engine off), then reads them again. */
    fun clearDtcs() {
        val channel = requests ?: return
        val previous = lastReport()
        _diagnostics.value = DiagnosticsState.Clearing
        scope.launch {
            val request = ObdRequest.ClearDtcs()
            try {
                channel.send(request)
                withTimeout(DTC_TIMEOUT_MS) { request.result.await() }
                _diagnostics.value = DiagnosticsState.Idle
                readDtcs()
            } catch (e: TimeoutCancellationException) {
                _diagnostics.value = DiagnosticsState.Failed("Auto nie odpowiedziało na czas", previous)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _diagnostics.value = DiagnosticsState.Failed(e.message ?: "Nie udało się skasować błędów", previous)
            }
        }
    }

    private fun lastReport(): DtcReport? = when (val d = _diagnostics.value) {
        is DiagnosticsState.Ready -> d.report
        is DiagnosticsState.Failed -> d.previous
        else -> null
    }

    fun resetTrip() {
        calculator.resetTrip()
        _stats.value = calculator.current
    }

    fun updateProfile(profile: VehicleProfile) {
        calculator.profile = profile
        _profile.value = profile
        prefs.edit {
            putString(KEY_FUEL, profile.fuelType.name)
            putFloat(KEY_MASS, profile.massKg.toFloat())
            putFloat(KEY_DISPLACEMENT, profile.displacementL.toFloat())
        }
    }

    private fun start(name: String, demo: Boolean, source: VehicleDataSource) {
        job?.cancel()
        requests?.close()
        val channel = Channel<ObdRequest>(Channel.UNLIMITED)
        requests = channel
        _diagnostics.value = DiagnosticsState.Idle
        _connection.value = if (demo) ConnectionState.Connected(name, demo = true) else ConnectionState.Connecting(name)
        job = scope.launch {
            try {
                var first = true
                source.samples(channel).collect { sample ->
                    if (_connection.value !is ConnectionState.Connected) {
                        _connection.value = ConnectionState.Connected(name, demo)
                    }
                    _stats.value = calculator.add(sample)
                    if (first) {
                        first = false
                        readDtcs() // check for trouble codes right away, so the home tile can warn
                    }
                }
                _connection.value = ConnectionState.Disconnected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connection.value = ConnectionState.Failed(e.message ?: "Nie udało się połączyć z adapterem")
            } finally {
                // Link is gone: nobody will answer queued requests any more.
                channel.close()
                if (requests === channel) requests = null
            }
        }
    }

    private fun loadProfile(): VehicleProfile {
        val defaults = VehicleProfile()
        return defaults.copy(
            fuelType = prefs.getString(KEY_FUEL, null)
                ?.let { name -> FuelType.entries.firstOrNull { it.name == name } }
                ?: defaults.fuelType,
            massKg = prefs.getFloat(KEY_MASS, defaults.massKg.toFloat()).toDouble(),
            displacementL = prefs.getFloat(KEY_DISPLACEMENT, defaults.displacementL.toFloat()).toDouble(),
        )
    }

    private companion object {
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_FUEL = "fuel"
        const val KEY_MASS = "mass"
        const val KEY_DISPLACEMENT = "displacement"
        const val DTC_TIMEOUT_MS = 30_000L
    }
}
