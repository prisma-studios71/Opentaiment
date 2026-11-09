package app.opentaiment.feature.vehicle.data

/** One polling cycle of live data. Any value can be null (not supported or not read this cycle). */
data class ObdSample(
    val timeMs: Long,
    val speedKmh: Double? = null,
    val rpm: Double? = null,
    val mafGs: Double? = null,
    val mapKpa: Double? = null,
    val intakeTempC: Double? = null,
    val coolantTempC: Double? = null,
    val oilTempC: Double? = null,
    val ambientTempC: Double? = null,
    val throttlePct: Double? = null,
    val engineLoadPct: Double? = null,
    val fuelRateLh: Double? = null,
    val fuelLevelPct: Double? = null,
    val voltage: Double? = null,
) {
    /** Keeps the last known value for anything [newer] didn't read (slow PIDs are polled less often). */
    fun mergedWith(newer: ObdSample) = ObdSample(
        timeMs = newer.timeMs,
        speedKmh = newer.speedKmh ?: speedKmh,
        rpm = newer.rpm ?: rpm,
        mafGs = newer.mafGs ?: mafGs,
        mapKpa = newer.mapKpa ?: mapKpa,
        intakeTempC = newer.intakeTempC ?: intakeTempC,
        coolantTempC = newer.coolantTempC ?: coolantTempC,
        oilTempC = newer.oilTempC ?: oilTempC,
        ambientTempC = newer.ambientTempC ?: ambientTempC,
        throttlePct = newer.throttlePct ?: throttlePct,
        engineLoadPct = newer.engineLoadPct ?: engineLoadPct,
        fuelRateLh = newer.fuelRateLh ?: fuelRateLh,
        fuelLevelPct = newer.fuelLevelPct ?: fuelLevelPct,
        voltage = newer.voltage ?: voltage,
    )
}

enum class FuelType(val label: String, val stoichAfr: Double, val densityGPerL: Double) {
    PETROL("Benzyna", 14.7, 745.0),
    DIESEL("Diesel", 14.5, 832.0),
    LPG("LPG", 15.5, 540.0),
}

/** What we need to know about the car to turn raw OBD data into fuel use and power. */
data class VehicleProfile(
    val fuelType: FuelType = FuelType.PETROL,
    /** Car + driver, kg. Drives the power estimate. */
    val massKg: Double = 1400.0,
    /** Engine displacement, litres. Used for fuel estimate when the car has no MAF sensor. */
    val displacementL: Double = 1.6,
    /** Drag coefficient × frontal area, m². ~0.6–0.75 for typical cars. */
    val dragAreaM2: Double = 0.7,
    val rollingResistance: Double = 0.012,
    val drivetrainEfficiency: Double = 0.85,
    val volumetricEfficiency: Double = 0.85,
)

enum class FuelSource(val label: String) {
    FUEL_RATE("z komputera silnika"),
    MAF("z przepływomierza (MAF)"),
    SPEED_DENSITY("z ciśnienia w kolektorze (MAP)"),
    NONE("brak danych o spalaniu"),
}

data class SprintState(
    val fromKmh: Int,
    val toKmh: Int,
    val lastSec: Double?,
    val bestSec: Double?,
    /** Elapsed seconds while a run is in progress, otherwise null. */
    val runningSec: Double?,
) {
    val label: String get() = "$fromKmh–$toKmh km/h"
}

data class TripStats(
    val distanceKm: Double = 0.0,
    val durationMs: Long = 0,
    val movingMs: Long = 0,
    val maxSpeedKmh: Double = 0.0,
    val maxRpm: Double = 0.0,
    val fuelUsedL: Double = 0.0,
) {
    val avgSpeedKmh: Double?
        get() = if (movingMs > 0) distanceKm / (movingMs / 3_600_000.0) else null
}

data class HistoryPoint(val timeMs: Long, val speedKmh: Double, val powerKw: Double)

data class VehicleStats(
    val hasData: Boolean = false,
    val speedKmh: Double? = null,
    val rpm: Double? = null,
    val throttlePct: Double? = null,
    val engineLoadPct: Double? = null,
    val coolantTempC: Double? = null,
    val intakeTempC: Double? = null,
    val oilTempC: Double? = null,
    val ambientTempC: Double? = null,
    val fuelLevelPct: Double? = null,
    val voltage: Double? = null,
    val fuelRateLh: Double? = null,
    val fuelSource: FuelSource = FuelSource.NONE,
    val instantLPer100Km: Double? = null,
    val avgLPer100Km: Double? = null,
    val accelMs2: Double? = null,
    val maxAccelMs2: Double = 0.0,
    val powerKw: Double? = null,
    val peakPowerKw: Double = 0.0,
    val torqueNm: Double? = null,
    val sprints: List<SprintState> = emptyList(),
    val trip: TripStats = TripStats(),
    val history: List<HistoryPoint> = emptyList(),
)
