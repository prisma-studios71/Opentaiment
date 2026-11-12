package app.opentaiment.feature.vehicle.ui

import app.opentaiment.core.ui.OtNumber
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtTheme
import app.opentaiment.core.ui.UiStyle
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.data.VehicleStats

/**
 * Distraction-free driving view: one huge number per page, swipe sideways for the next page.
 * Page 1 speed + consumption, page 2 eco + cost, page 3 performance.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveScreen(
    connection: ConnectionState,
    stats: VehicleStats,
    onConnect: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    if (connection !is ConnectionState.Connected || !stats.hasData) {
        ConnectPrompt(connection, onConnect, onDemo, modifier)
        return
    }
    val pager = rememberPagerState(initialPage = initialPage) { PAGES }
    val style = OtTheme.style.style
    Column(modifier) {
        AlertBanner(stats.alerts, Modifier.padding(bottom = 12.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            when (page) {
                0 -> if (style != UiStyle.CLASSIC) StyledSpeedPage(style, stats) else BigPage(
                    value = stats.speedKmh.fmt(), unit = "km/h",
                    left = stats.instantLPer100Km?.let { Side("Spalanie teraz", it.fmt(1), "l/100 km") }
                        ?: Side("Spalanie teraz", stats.fuelRateLh.fmt(1), "l/h"),
                    right = Side("Średnio", stats.avgLPer100Km.fmt(1), "l/100 km"),
                )
                1 -> BigPage(
                    value = stats.eco?.score?.toString() ?: NO_VALUE, unit = "eco-score",
                    left = Side("Koszt trasy", stats.trip.fuelCost.fmt(2), "zł"),
                    right = Side("Dystans", stats.trip.distanceKm.fmt(1), "km"),
                    note = stats.eco?.tips?.firstOrNull() ?: "Eco-score pojawi się po minucie jazdy.",
                    valueColor = ecoColor(stats.eco?.score),
                )
                else -> BigPage(
                    value = stats.powerKw?.let(::kwToHp).fmt(), unit = "KM",
                    left = Side("0–100", stats.sprints.firstOrNull()?.let { (it.runningSec ?: it.lastSec) }.fmt(1), "s"),
                    right = Side("Przyspieszenie", ((stats.accelMs2 ?: 0.0) / 9.81).fmt(2), "g"),
                )
            }
        }
        // Page dots – swipe hint.
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES) { i ->
                Box(
                    Modifier.padding(horizontal = 6.dp).size(if (i == pager.currentPage) 14.dp else 10.dp).clip(CircleShape)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

private const val PAGES = 3

@Composable
internal fun ecoColor(score: Int?): Color = when {
    score == null -> MaterialTheme.colorScheme.onSurface
    score >= 80 -> ConnectedGreen
    score >= 50 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}

private class Side(val label: String, val value: String, val unit: String)

@Composable
private fun BigPage(
    value: String,
    unit: String,
    left: Side,
    right: Side,
    note: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < NarrowWidth
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OtNumber(value, if (narrow) 120.sp else 150.sp, color = valueColor)
        Text(unit, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf(left, right).forEach { side ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(side.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    OtNumber(side.value, if (narrow) 44.sp else 56.sp)
                    Text(side.unit, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(24.dp))
            Text(
                note,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
    }
}
