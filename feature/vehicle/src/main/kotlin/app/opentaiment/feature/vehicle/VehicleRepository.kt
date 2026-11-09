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
import app.opentaiment.feature.vehicle.data.TripHistoryStore
import app.opentaiment.feature.vehicle.data.TripRecord
import app.opentaiment.feature.vehicle.data.VehicleAlert
import app.opentaiment.feature.vehicle.data.VehicleProfile
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.data.ReminderStatus
import app.opentaiment.feature.vehicle.data.ServiceReminder
import app.opentaiment.feature.vehicle.data.ServiceReminders
import app.opentaiment.feature.vehicle.data.TrackPoint
import app.opentaiment.feature.vehicle.data.TrackRecorder
import app.opentaiment.feature.vehicle.data.TrackStore
import app.opentaiment.feature.vehicle.data.TripJson
import app.opentaiment.feature.vehicle.obd.BleObdTransport
import app.opentaiment.feature.vehicle.obd.BluetoothObdTransport
import app.opentaiment.feature.vehicle.race.GpsFix
import app.opentaiment.feature.vehicle.source.ReconnectPolicy
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import app.opentaiment.feature.vehicle.race.GpsSource
import app.opentaiment.feature.vehicle.race.RaceState
import app.opentaiment.feature.vehicle.race.RaceTracker
import app.opentaiment.feature.vehicle.source.ObdDataSource
import app.opentaiment.feature.vehicle.source.ObdRequest
import app.opentaiment.feature.vehicle.source.SimulatedDataSource
import app.opentaiment.feature.vehicle.source.VehicleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class ObdDevice(val name: String, val address: String, val ble: Boolean = false) {
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
    /** [reconnecting]: the app keeps retrying on its own (lost link to a known adapter). */
    data class Failed(val message: String, val reconnecting: Boolean = false) : ConnectionState
}

sealed interface DiagnosticsState {
    data object Idle : DiagnosticsState
    data object Reading : DiagnosticsState
    data object Clearing : DiagnosticsState
    data class Ready(val report: DtcReport) : DiagnosticsState
    data class Failed(val message: String, val previous: DtcReport?) : DiagnosticsState
}

/**
 * One connection to the car per app process, shared by the phone UI, Android Auto screens and
 * the tablet launcher – they all just observe it.
 */
object VehicleHub {
    @Volatile
    private var instance: VehicleRepository? = null

    fun get(context: Context): VehicleRepository = instance ?: synchronized(this) {
        instance ?: VehicleRepository(
            context.applicationContext,
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        ).also { instance = it }
    }
}

/**
 * Owns the OBD connection (or the demo simulator), the trip calculator and the trip history.
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

    private val history = TripHistoryStore(java.io.File(appContext.filesDir, "trips.json"), scope)
    /** Finished trips, newest first. */
    val trips: StateFlow<List<TripRecord>> = history.trips

    private val _newAlerts = MutableSharedFlow<VehicleAlert>(extraBufferCapacity = 8)
    /** Fires once each time a new warning appears (overheating, no charging, low fuel…). */
    val newAlerts: SharedFlow<VehicleAlert> = _newAlerts.asSharedFlow()

    private val raceTracker = RaceTracker()
    private val gps = GpsSource(appContext)
    private var raceClock: Job? = null
    private val _race = MutableStateFlow(RaceState())
    /** Lap timer and g-meter (GPS). */
    val race: StateFlow<RaceState> = _race.asStateFlow()

    private val tracks = TrackStore(File(appContext.filesDir, "tracks"))
    private val trackRecorder = TrackRecorder()
    private val inProgressFile = File(appContext.filesDir, "trip_in_progress.json")

    private val _gpsSpeedKmh = MutableStateFlow<Double?>(null)
    /** Speed from GPS (when something keeps GPS on) – a fallback when the car isn't connected. */
    val gpsSpeedKmh: StateFlow<Double?> = _gpsSpeedKmh.asStateFlow()
    private val gpsUsers = mutableSetOf<String>()

    private val _reminders = MutableStateFlow(ServiceReminders.decode(prefs.getString(KEY_REMINDERS, null)))
    val reminders: StateFlow<List<ServiceReminder>> = _reminders.asStateFlow()

    private val _odometerBase = MutableStateFlow(
        prefs.getFloat(KEY_ODO_BASE, -1f).takeIf { it >= 0 }?.toDouble() to prefs.getLong(KEY_ODO_BASE_TIME, 0L),
    )

    /** Best known odometer reading (from the car, or the user's reading plus trips since). */
    val odometerKm: StateFlow<Double?> = combine(_stats, history.trips, _odometerBase) { stats, trips, (base, baseTime) ->
        stats.odometerKm?.also { carKm -> rememberCarOdometer(carKm) }
            ?: ServiceReminders.odometer(null, base, baseTime, trips)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Service reminders, most urgent first. */
    val reminderStatuses: StateFlow<List<ReminderStatus>> = combine(_reminders, odometerKm) { list, odo ->
        ServiceReminders.statuses(list, odo, ServiceReminders.today())
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The adapter the user wants to stay connected to; null after "Rozłącz" or in demo mode. */
    private var wanted: ObdDevice? = null
    private var reconnectJob: Job? = null
    private var checkpointJob: Job? = null
    private var lastSampleWallMs = 0L

    /** No data for longer than the engine-off split: whatever comes next is a new trip. */
    private fun tripLooksOver() = System.currentTimeMillis() - lastSampleWallMs > ENGINE_OFF_SPLIT_MS

    private var job: Job? = null
    private var requests: Channel<ObdRequest>? = null
    private var tripStartEpochMs: Long? = null
    private var tripIsDemo = false
    private var engineOffSinceMs: Long? = null

    init {
        recoverInterruptedTrip()
    }

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
                .map { ObdDevice(it.name ?: it.address, it.address, ble = it.type == BluetoothDevice.DEVICE_TYPE_LE) }
                .sortedWith(compareByDescending<ObdDevice> { it.looksLikeObd }.thenBy { it.name })
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connect(device: ObdDevice) {
        prefs.edit {
            putString(KEY_LAST_ADDRESS, device.address)
            putString(KEY_LAST_NAME, device.name)
            putBoolean(KEY_LAST_BLE, device.ble)
        }
        wanted = device
        reconnectJob?.cancel()
        open(device, attempt = 0)
    }

    private fun open(device: ObdDevice, attempt: Int) {
        val source = ObdDataSource({
            if (device.ble) BleObdTransport.connect(appContext, device.address)
            else BluetoothObdTransport.connect(appContext, device.address)
        })
        start(
            device.name, demo = false, source,
            onLost = { message -> scheduleReconnect(device, attempt, message) },
            keepTrip = attempt > 0,
        )
    }

    /** Keeps trying the same adapter after the link dropped – the car may just be starting. */
    private fun scheduleReconnect(device: ObdDevice, attempt: Int, message: String) {
        if (wanted != device || !autoReconnectEnabled) {
            _connection.value = ConnectionState.Failed(message)
            return
        }
        _connection.value = ConnectionState.Failed(message, reconnecting = true)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(ReconnectPolicy.delayMs(attempt))
            if (tripLooksOver()) finishTrip() // car parked with the adapter unpowered
            if (wanted == device && job?.isActive != true) open(device, attempt + 1)
        }
    }

    /** Reconnect on its own after a dropped link (on by default). */
    var autoReconnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_RECONNECT, value) }

    /** The adapter used last time, if any. */
    fun lastDevice(): ObdDevice? {
        val address = prefs.getString(KEY_LAST_ADDRESS, null) ?: return null
        return ObdDevice(prefs.getString(KEY_LAST_NAME, null) ?: address, address, prefs.getBoolean(KEY_LAST_BLE, false))
    }

    /** Reconnects to the last used adapter – handy when the tablet boots with the car. */
    fun autoConnect() {
        if (job?.isActive == true || !hasBluetoothPermission()) return
        if (_connection.value is ConnectionState.Connected) return
        lastDevice()?.let(::connect)
    }

    fun startDemo() {
        wanted = null
        reconnectJob?.cancel()
        start("Tryb demo", demo = true, SimulatedDataSource())
    }

    fun disconnect() {
        wanted = null
        reconnectJob?.cancel()
        finishTrip()
        job?.cancel()
        job = null
        requests?.close()
        requests = null
        _connection.value = ConnectionState.Disconnected
    }

    /**
     * Ignition off / head unit going to sleep: save the trip right away (power may vanish any
     * second), but keep wanting the adapter so the next ignition-on reconnects.
     */
    fun onIgnitionOff() {
        val device = wanted
        reconnectJob?.cancel()
        finishTrip()
        job?.cancel()
        job = null
        requests?.close()
        requests = null
        wanted = device
        _connection.value = ConnectionState.Disconnected
    }

    // ---------- BLE adapters ----------

    fun hasBleScanPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN
        else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(appContext, needed) == PackageManager.PERMISSION_GRANTED
    }

    /** Looks for BLE OBD adapters nearby for a few seconds (they usually don't need pairing). */
    @SuppressLint("MissingPermission")
    suspend fun scanBleAdapters(durationMs: Long = 6_000): List<ObdDevice> {
        if (!hasBleScanPermission() || !hasBluetoothPermission()) return emptyList()
        val scanner = appContext.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return emptyList()
        val found = LinkedHashMap<String, ObdDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: try { result.device.name } catch (e: SecurityException) { null }
                if (name.isNullOrBlank()) return
                found[result.device.address] = ObdDevice(name, result.device.address, ble = true)
            }
        }
        try {
            scanner.startScan(callback)
            delay(durationMs)
        } catch (e: SecurityException) {
            return emptyList()
        } finally {
            try { scanner.stopScan(callback) } catch (_: Exception) {}
        }
        return found.values.sortedWith(compareByDescending<ObdDevice> { it.looksLikeObd }.thenBy { it.name })
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

    /** "Nowa trasa": saves the current trip to history and starts counting from zero. */
    fun resetTrip() {
        finishTrip()
    }

    // ---------- race mode (GPS) ----------

    fun hasGpsPermission(): Boolean = gps.hasPermission()

    /** Starts GPS for laps and the g-meter. Returns false without permission or with GPS off. */
    fun startRaceGps(): Boolean {
        val started = requestGps(RACE_USER)
        raceTracker.setGpsActive(started)
        _race.value = raceTracker.state
        if (started && raceClock?.isActive != true) {
            raceClock = scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(200)
                    raceTracker.tick(GpsSource.now())
                    _race.value = raceTracker.state
                }
            }
        }
        return started
    }

    fun stopRaceGps() {
        releaseGps(RACE_USER)
        raceClock?.cancel()
        raceTracker.setGpsActive(false)
        _race.value = raceTracker.state
    }

    fun setFinishLineHere(): Boolean = raceTracker.setFinishLineHere().also { _race.value = raceTracker.state }

    fun resetRace() {
        raceTracker.resetLaps()
        _race.value = raceTracker.state
    }

    fun deleteTrip(id: Long) {
        history.delete(id)
        tracks.delete(id)
    }

    fun clearTrips() {
        history.clear()
        tracks.clear()
    }

    /** GPS trace of a saved trip (empty when GPS was off). */
    fun trackOf(tripId: Long): List<TrackPoint> = tracks.load(tripId)
    fun hasTrack(tripId: Long): Boolean = tracks.has(tripId)

    // ---------- service reminders ----------

    fun updateReminder(reminder: ServiceReminder) = saveReminders(
        _reminders.value.map { if (it.id == reminder.id) reminder else it }.let { list ->
            if (list.any { it.id == reminder.id }) list else list + reminder
        },
    )

    fun deleteReminder(id: String) = saveReminders(_reminders.value.filterNot { it.id == id })

    /** "Zrobione dziś": resets the reminder to the current odometer and date. */
    fun markReminderDone(id: String) = saveReminders(
        _reminders.value.map {
            if (it.id == id) it.copy(lastDoneKm = odometerKm.value, lastDoneEpochDay = ServiceReminders.today().toEpochDay()) else it
        },
    )

    private fun saveReminders(list: List<ServiceReminder>) {
        _reminders.value = list
        prefs.edit { putString(KEY_REMINDERS, ServiceReminders.encode(list)) }
    }

    /** The user typed in what the dashboard odometer shows now. */
    fun setOdometer(km: Double) {
        prefs.edit {
            putFloat(KEY_ODO_BASE, km.toFloat())
            putLong(KEY_ODO_BASE_TIME, System.currentTimeMillis())
        }
        _odometerBase.value = km to System.currentTimeMillis()
    }

    private fun rememberCarOdometer(km: Double) {
        // Keeps the estimate close to the truth if the adapter is later swapped for one without
        // PID A6. Written at most once per kilometre.
        if (kotlin.math.abs((_odometerBase.value.first ?: -1.0) - km) > 1.0) setOdometer(km)
    }

    // ---------- GPS (shared by race mode, trip traces and speed-dependent volume) ----------

    /** Keeps GPS on while at least one user (e.g. "track", "volume") wants it. */
    fun requestGps(user: String): Boolean {
        gpsUsers += user
        return ensureGps()
    }

    fun releaseGps(user: String) {
        gpsUsers -= user
        if (gpsUsers.isEmpty()) {
            gps.stop()
            _gpsSpeedKmh.value = null
        }
    }

    private fun ensureGps(): Boolean = gps.start(::onGpsFix)

    private fun onGpsFix(fix: GpsFix) {
        _gpsSpeedKmh.value = fix.speedMs?.let { it * 3.6 }
        if (RACE_USER in gpsUsers) {
            raceTracker.addFix(fix, obdAccelAvailable = _connection.value is ConnectionState.Connected)
            _race.value = raceTracker.state
        }
        if (tripStartEpochMs != null && !tripIsDemo) {
            trackRecorder.add(TrackPoint(System.currentTimeMillis(), fix.lat, fix.lon, fix.speedMs?.let { it * 3.6 }), fix.accuracyM)
        }
    }

    /** Saves the trip in progress (if it is long enough to matter) and resets the counters. */
    private fun finishTrip() {
        val record = currentTripRecord()
        if (record != null && record.distanceKm >= MIN_TRIP_KM) {
            history.add(record)
            tracks.save(record.id, trackRecorder.snapshot())
        }
        trackRecorder.clear()
        checkpointJob?.cancel()
        checkpointJob = null
        inProgressFile.delete()
        tripStartEpochMs = null
        engineOffSinceMs = null
        calculator.resetTrip()
        _stats.value = calculator.current
    }

    /** The trip so far, as it would be saved now. */
    private fun currentTripRecord(): TripRecord? {
        val stats = calculator.current
        val start = tripStartEpochMs ?: return null
        return TripRecord(
            startEpochMs = start,
            endEpochMs = System.currentTimeMillis(),
            distanceKm = stats.trip.distanceKm,
            durationMs = stats.trip.durationMs,
            movingMs = stats.trip.movingMs,
            fuelUsedL = stats.trip.fuelUsedL,
            fuelPricePerL = calculator.profile.fuelPricePerL,
            maxSpeedKmh = stats.trip.maxSpeedKmh,
            ecoScore = stats.eco?.score,
            best0to100Sec = stats.best0to100Sec,
            quarterMileSec = stats.quarterMile?.bestSec,
            demo = tripIsDemo,
        )
    }

    /**
     * Car head units often lose power without warning. Every so often the trip in progress is
     * written to disk; after an unclean shutdown it is added to the history on next start.
     */
    private fun checkpointLoop() = scope.launch {
        while (true) {
            delay(CHECKPOINT_MS)
            val record = currentTripRecord() ?: continue
            if (record.distanceKm < MIN_TRIP_KM) continue
            val json = TripJson.encode(listOf(record))
            val points = trackRecorder.snapshot()
            launch(Dispatchers.IO) {
                val tmp = File(inProgressFile.parentFile, inProgressFile.name + ".tmp")
                tmp.writeText(json)
                tmp.renameTo(inProgressFile)
                tracks.save(record.id, points)
            }
        }
    }

    private fun recoverInterruptedTrip() {
        if (!inProgressFile.exists()) return
        val record = try { TripJson.decode(inProgressFile.readText()).firstOrNull() } catch (e: Exception) { null }
        if (record != null && record.distanceKm >= MIN_TRIP_KM && trips.value.none { it.id == record.id }) history.add(record)
        inProgressFile.delete()
    }

    fun updateProfile(profile: VehicleProfile) {
        calculator.profile = profile
        _profile.value = profile
        prefs.edit {
            putString(KEY_FUEL, profile.fuelType.name)
            putFloat(KEY_MASS, profile.massKg.toFloat())
            putFloat(KEY_DISPLACEMENT, profile.displacementL.toFloat())
            putFloat(KEY_PRICE, profile.fuelPricePerL.toFloat())
        }
    }

    /**
     * Starts reading from any [source] – used by [connect] and [startDemo], and by tests or future
     * transports (e.g. BLE adapters) that bring their own data source.
     */
    fun start(
        name: String,
        demo: Boolean,
        source: VehicleDataSource,
        onLost: ((String) -> Unit)? = null,
        keepTrip: Boolean = false,
    ) {
        // Switching cars / to demo: keep what was driven so far. A quick reconnect continues the trip.
        if (!keepTrip || tripLooksOver()) finishTrip()
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
                    lastSampleWallMs = System.currentTimeMillis()
                    if (_connection.value !is ConnectionState.Connected) {
                        _connection.value = ConnectionState.Connected(name, demo)
                    }
                    if (tripStartEpochMs == null) {
                        tripStartEpochMs = System.currentTimeMillis()
                        tripIsDemo = demo
                        trackRecorder.clear()
                        if (!demo) checkpointJob = checkpointLoop()
                    }
                    val previousAlerts = _stats.value.alerts.map { it.type }.toSet()
                    val stats = calculator.add(sample)
                    _stats.value = stats
                    stats.accelMs2?.let {
                        raceTracker.addObdAcceleration(it)
                        _race.value = raceTracker.state
                    }
                    stats.alerts.filter { it.type !in previousAlerts }.forEach { _newAlerts.tryEmit(it) }
                    splitTripWhenEngineOff(sample.timeMs, sample.rpm)
                    if (first) {
                        first = false
                        readDtcs() // check for trouble codes right away, so the home tile can warn
                    }
                }
                finishTrip()
                _connection.value = ConnectionState.Disconnected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the trip open across a short drop-out; it closes after the engine-off timeout
                // or when the user disconnects. A lost link with nothing driven yet saves nothing anyway.
                val message = e.message ?: "Nie udało się połączyć z adapterem"
                if (onLost == null) finishTrip()
                _connection.value = ConnectionState.Failed(message)
                onLost?.invoke(message)
            } finally {
                // Link is gone: nobody will answer queued requests any more.
                channel.close()
                if (requests === channel) requests = null
            }
        }
    }

    /** A parked car with the adapter still powered: close the trip after a few minutes with the engine off. */
    private fun splitTripWhenEngineOff(timeMs: Long, rpm: Double?) {
        if (rpm != null && rpm >= 100) {
            engineOffSinceMs = null
            return
        }
        val since = engineOffSinceMs ?: timeMs.also { engineOffSinceMs = it }
        if (timeMs - since >= ENGINE_OFF_SPLIT_MS && calculator.current.trip.distanceKm > 0) finishTrip()
    }

    private fun loadProfile(): VehicleProfile {
        val defaults = VehicleProfile()
        return defaults.copy(
            fuelType = prefs.getString(KEY_FUEL, null)
                ?.let { name -> FuelType.entries.firstOrNull { it.name == name } }
                ?: defaults.fuelType,
            massKg = prefs.getFloat(KEY_MASS, defaults.massKg.toFloat()).toDouble(),
            displacementL = prefs.getFloat(KEY_DISPLACEMENT, defaults.displacementL.toFloat()).toDouble(),
            fuelPricePerL = prefs.getFloat(KEY_PRICE, defaults.fuelPricePerL.toFloat()).toDouble(),
        )
    }

    private companion object {
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_LAST_BLE = "last_ble"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_REMINDERS = "reminders"
        const val KEY_ODO_BASE = "odometer_base"
        const val KEY_ODO_BASE_TIME = "odometer_base_time"
        const val RACE_USER = "race"
        const val CHECKPOINT_MS = 20_000L
        const val KEY_FUEL = "fuel"
        const val KEY_MASS = "mass"
        const val KEY_DISPLACEMENT = "displacement"
        const val KEY_PRICE = "fuel_price"
        const val DTC_TIMEOUT_MS = 30_000L
        const val MIN_TRIP_KM = 0.2
        const val ENGINE_OFF_SPLIT_MS = 3 * 60_000L
    }
}
