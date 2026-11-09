package app.opentaiment.feature.vehicle.data

import kotlin.math.max

/**
 * Measures a speed sprint such as 0–100 or 80–120 km/h. A run starts when speed crosses
 * [fromKmh] upwards (for 0–100: when the car starts moving from a standstill) and ends at
 * [toKmh]. Lifting off noticeably, falling below [fromKmh] or taking too long cancels the run.
 * Crossing times are linearly interpolated between samples, since OBD speed is coarse.
 */
class SprintTimer(
    val fromKmh: Int,
    val toKmh: Int,
    private val timeoutMs: Long = 40_000,
) {
    var lastSec: Double? = null
        private set
    var bestSec: Double? = null
        private set

    private var startTime: Long? = null
    private var runMax = 0.0
    private var prevT: Long? = null
    private var prevV: Double? = null

    fun add(t: Long, v: Double) {
        val pt = prevT
        val pv = prevV
        prevT = t
        prevV = v
        if (pt == null || pv == null || t <= pt) return

        val start = startTime
        if (start == null) {
            if (pv <= fromKmh && v > fromKmh) {
                startTime = interpolate(pt, pv, t, v, fromKmh.toDouble())
                runMax = v
            }
            return
        }

        runMax = max(runMax, v)
        when {
            v >= toKmh -> {
                val end = if (pv < toKmh) interpolate(pt, pv, t, v, toKmh.toDouble()) else t
                val sec = (end - start) / 1000.0
                lastSec = sec
                bestSec = bestSec?.let { minOf(it, sec) } ?: sec
                startTime = null
            }
            v < fromKmh || v < runMax - DROP_TOLERANCE_KMH || t - start > timeoutMs -> startTime = null
        }
    }

    fun state(): SprintState = SprintState(
        fromKmh = fromKmh,
        toKmh = toKmh,
        lastSec = lastSec,
        bestSec = bestSec,
        runningSec = startTime?.let { s -> prevT?.let { (it - s) / 1000.0 } },
    )

    fun reset() {
        lastSec = null
        bestSec = null
        startTime = null
    }

    private fun interpolate(t0: Long, v0: Double, t1: Long, v1: Double, target: Double): Long =
        t0 + ((target - v0) / (v1 - v0) * (t1 - t0)).toLong()

    private companion object {
        /** Gear changes make speed stall or dip by a km/h; more than this means the driver lifted off. */
        const val DROP_TOLERANCE_KMH = 3.0
    }
}
