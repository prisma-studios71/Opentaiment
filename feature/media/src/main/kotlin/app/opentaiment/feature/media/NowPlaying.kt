package app.opentaiment.feature.media

import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap

data class NowPlaying(
    val title: String,
    val artist: String?,
    val album: String?,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val packageName: String,
    val appLabel: String,
    val durationMs: Long,
    /** Position reported by the player at [positionUpdatedAt] (elapsedRealtime). */
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
) {
    /** Extrapolates playback position so the progress bar moves smoothly between updates. */
    fun currentPositionMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        val base = positionMs.coerceAtLeast(0)
        if (!isPlaying) return base
        val pos = base + ((nowElapsed - positionUpdatedAt) * speed).toLong()
        return if (durationMs > 0) pos.coerceIn(0, durationMs) else pos.coerceAtLeast(0)
    }
}
