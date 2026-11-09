package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import app.opentaiment.feature.vehicle.data.TrackPoint
import kotlin.math.cos
import kotlin.math.max

/**
 * The route as a line, coloured by speed (green slow → red fast). No map tiles – works offline
 * and shows the shape of the drive; open the GPX in a map app for streets.
 */
@Composable
fun RouteMap(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.error
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(bg), contentAlignment = Alignment.Center) {
        if (points.size < 2) {
            Text(
                "Brak śladu GPS dla tej trasy.\nŚlad zapisuje się, gdy Opentaiment ma dostęp do lokalizacji.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }
        Canvas(Modifier.matchParentSize()) {
            val pad = 24.dp.toPx()
            val minLat = points.minOf { it.lat }
            val maxLat = points.maxOf { it.lat }
            val minLon = points.minOf { it.lon }
            val maxLon = points.maxOf { it.lon }
            // Longitude degrees shrink towards the poles.
            val lonScale = cos(Math.toRadians((minLat + maxLat) / 2))
            val spanX = max((maxLon - minLon) * lonScale, 1e-5)
            val spanY = max(maxLat - minLat, 1e-5)
            val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
            val offX = (size.width - spanX * scale) / 2
            val offY = (size.height - spanY * scale) / 2
            fun at(p: TrackPoint) = Offset(
                (offX + (p.lon - minLon) * lonScale * scale).toFloat(),
                (size.height - offY - (p.lat - minLat) * scale).toFloat(),
            )
            val maxSpeed = points.mapNotNull { it.speedKmh }.maxOrNull()?.coerceAtLeast(30.0) ?: 1.0
            points.zipWithNext().forEach { (a, b) ->
                val v = ((a.speedKmh ?: 0.0) / maxSpeed).toFloat().coerceIn(0f, 1f)
                drawLine(speedColor(v), at(a), at(b), strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
            }
            drawCircle(start, 10.dp.toPx(), at(points.first()))
            drawCircle(end, 10.dp.toPx(), at(points.last()), style = Stroke(5.dp.toPx()))
        }
    }
}

private fun speedColor(v: Float): Color {
    // green → yellow → red
    val r = (v * 2).coerceAtMost(1f)
    val g = (2 - v * 2).coerceAtMost(1f)
    return Color(0.25f + 0.7f * r, 0.35f + 0.55f * g, 0.3f)
}
