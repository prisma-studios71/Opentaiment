package app.opentaiment.feature.vehicle.data

import kotlin.math.max

data class QuarterMileState(
    val lastSec: Double?,
    val lastTrapKmh: Double?,
    val bestSec: Double?,
    val runningSec: Double?,
    val runningDistanceM: Double?,
)

/**
 * ¼ mile (402 m) from a standstill: time and trap speed. Distance is integrated from OBD speed,
 * so it is an estimate (wheel-speed based, like a car's own trip meter).
 */
class QuarterMileTimer(private val timeoutMs: Long = 40_000) {
    private var lastSec: Double? = null
    private var lastTrap: Double? = null
    private var bestSec: Double? = null

    private var start: Long? = null
    private var distanceM = 0.0
    private var runMax = 0.0
    private var prevT: Long? = null
    private var prevV: Double? = null

    fun add(t: Long, speedKmh: Double) {
        val pt = prevT
        val pv = prevV
        prevT = t
        prevV = speedKmh
        if (pt == null || pv == null || t <= pt) return

        val s = start
        if (s == null) {
            if (pv <= 0.0 && speedKmh > 0.0) {
                start = pt
                distanceM = (pv + speedKmh) / 2 / 3.6 * (t - pt) / 1000.0
                runMax = speedKmh
            }
            return
        }
        val step = (pv + speedKmh) / 2 / 3.6 * (t - pt) / 1000.0
        runMax = max(runMax, speedKmh)
        if (distanceM + step >= QUARTER_MILE_M) {
            // Interpolate the exact moment we crossed 402 m within this sample.
            val fraction = if (step > 0) (QUARTER_MILE_M - distanceM) / step else 1.0
            val end = pt + ((t - pt) * fraction).toLong()
            val sec = (end - s) / 1000.0
            lastSec = sec
            lastTrap = pv + (speedKmh - pv) * fraction
            bestSec = bestSec?.let { minOf(it, sec) } ?: sec
            start = null
            return
        }
        distanceM += step
        if (speedKmh < runMax - DROP_TOLERANCE_KMH || t - s > timeoutMs) start = null
    }

    fun state(): QuarterMileState = QuarterMileState(
        lastSec = lastSec,
        lastTrapKmh = lastTrap,
        bestSec = bestSec,
        runningSec = start?.let { s -> prevT?.let { (it - s) / 1000.0 } },
        runningDistanceM = if (start != null) distanceM else null,
    )

    fun reset() {
        lastSec = null; lastTrap = null; bestSec = null; start = null
    }

    companion object {
        const val QUARTER_MILE_M = 402.336
        private const val DROP_TOLERANCE_KMH = 3.0
    }
}
