package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.ArcGauge
import app.opentaiment.core.ui.BlockMeter
import app.opentaiment.core.ui.OtCard
import app.opentaiment.core.ui.OtNumber
import app.opentaiment.core.ui.SegmentBar
import app.opentaiment.core.ui.UiStyle
import app.opentaiment.core.ui.ecoTint
import app.opentaiment.feature.vehicle.data.VehicleStats

/*
 * The first Drive page (speed + consumption) laid out per style. The other pages and the
 * Classic style keep the plain one-big-number page.
 */

private class DriveValues(stats: VehicleStats) {
    val speed = stats.speedKmh.fmt()
    val speedFraction = ((stats.speedKmh ?: 0.0) / 200.0).toFloat()
    val rpm = stats.rpm.fmt()
    val rpmFraction = ((stats.rpm ?: 0.0) / 7000.0).toFloat()
    val nowValue = stats.instantLPer100Km?.fmt(1) ?: stats.fuelRateLh.fmt(1)
    val nowUnit = if (stats.instantLPer100Km != null) "l/100 km" else "l/h"
    val avg = stats.avgLPer100Km.fmt(1)
    val eco: Int? = stats.eco?.score
    val ecoLabel = when {
        eco == null -> "Ocena za minutę"
        eco >= 75 -> "Spokojnie"
        eco >= 50 -> "Może lżej"
        else -> "Ostro!"
    }
}

@Composable
internal fun StyledSpeedPage(style: UiStyle, stats: VehicleStats) {
    val v = DriveValues(stats)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        when (style) {
            UiStyle.COCKPIT -> CockpitDrive(v, wide)
            UiStyle.GLASS -> GlassDrive(v, wide)
            UiStyle.BIG_TYPE -> BigTypeDrive(v, wide)
            UiStyle.RETRO -> RetroDrive(v, wide)
            UiStyle.CLASSIC -> Unit
        }
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Reading(label: String, value: String, unit: String, modifier: Modifier, size: Int = 40) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(label)
        OtNumber(value, size.sp)
        Caption(unit)
    }
}

// ── Cockpit: dial with the speed inside, eco colours the arc ──

@Composable
private fun CockpitDrive(v: DriveValues, wide: Boolean) {
    val tint = ecoTint(v.eco)
    val dial: @Composable (Modifier) -> Unit = { m ->
        Box(m, contentAlignment = Alignment.Center) {
            ArcGauge(v.speedFraction, tint, Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = wide), outerFraction = v.rpmFraction)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OtNumber(v.speed, 96.sp)
                Caption("KM/H")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.clip(CircleShape).border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                        .background(tint.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("ECO ${v.eco ?: "—"} · ${v.ecoLabel}", style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1) }
            }
        }
    }
    val details: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth()) {
            Reading("Spalanie teraz", v.nowValue, v.nowUnit, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(80.dp).background(MaterialTheme.colorScheme.outline))
            Reading("Średnio", v.avg, "l/100 km", Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))
        Row { Caption("OBROTY", Modifier.weight(1f)); Caption("${v.rpm} obr/min") }
        Spacer(Modifier.height(8.dp))
        SegmentBar(v.rpmFraction, Modifier.fillMaxWidth())
    }
    if (wide) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            dial(Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f), content = details)
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            dial(Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            details()
        }
    }
}

// ── Glass: huge thin number on a halo that turns from green to red with eco ──

@Composable
private fun GlassDrive(v: DriveValues, wide: Boolean) {
    val halo = ecoTint(v.eco)
    Column(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(halo.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2, size.height * 0.36f),
                    radius = size.minDimension * 0.75f,
                ),
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            OtNumber(v.speed, if (wide) 150.sp else 140.sp)
            Text("KM/H", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.clip(CircleShape).background(halo.copy(alpha = 0.22f)).border(1.dp, halo.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Eco ${v.eco ?: "—"} · ${v.ecoLabel}", style = MaterialTheme.typography.titleMedium, maxLines = 1) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Spalanie teraz", v.nowValue, v.nowUnit), Triple("Średnio", v.avg, "l/100 km")).forEach { (l, value, u) ->
                OtCard(Modifier.weight(1f).height(130.dp), padding = 16.dp) {
                    Caption(l)
                    Spacer(Modifier.weight(1f))
                    OtNumber(value, 40.sp)
                    Caption(u)
                }
            }
        }
    }
}

// ── Big type: one yellow slab for speed, two blocks for consumption ──

@Composable
private fun Slab(modifier: Modifier, fill: Color?, content: Color, body: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.then(if (fill != null) Modifier.background(fill) else Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface)).padding(14.dp),
    ) { CompositionLocalProvider(LocalContentColor provides content) { body() } }
}

@Composable
private fun SlabLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun BigTypeDrive(v: DriveValues, wide: Boolean) {
    val cs = MaterialTheme.colorScheme
    val speed: @Composable (Modifier) -> Unit = { m ->
        Slab(m, cs.primary, cs.onPrimary) {
            Row { SlabLabel("km/h", Modifier.weight(1f)); SlabLabel("eco ${v.eco ?: "—"}") }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { OtNumber(v.speed, 230.sp) }
            SlabLabel(v.ecoLabel)
            Spacer(Modifier.height(8.dp))
            BlockMeter(v.eco, Modifier.fillMaxWidth(), color = cs.onPrimary)
        }
    }
    val fuel: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slab(Modifier.weight(1f).fillMaxHeight(), cs.onSurface, cs.background) {
                SlabLabel("Teraz")
                Spacer(Modifier.weight(1f))
                OtNumber(v.nowValue, 58.sp)
                SlabLabel(v.nowUnit)
            }
            Slab(Modifier.weight(1f).fillMaxHeight(), null, cs.onSurface) {
                SlabLabel("Średnio")
                Spacer(Modifier.weight(1f))
                OtNumber(v.avg, 58.sp)
                SlabLabel("l/100 km")
            }
        }
    }
    if (wide) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            speed(Modifier.weight(1.2f).fillMaxHeight())
            fuel(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            speed(Modifier.weight(1f).fillMaxWidth())
            fuel(Modifier.height(170.dp).fillMaxWidth())
        }
    }
}

// ── Retro: one VFD panel with segment digits and bar graphs ──

@Composable
private fun RetroDrive(v: DriveValues, wide: Boolean) {
    val cs = MaterialTheme.colorScheme
    OtCard(Modifier.fillMaxSize(), padding = 18.dp) {
        val line: @Composable (String, String) -> Unit = { l, r ->
            Row(Modifier.fillMaxWidth()) {
                Text(l, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(r, style = MaterialTheme.typography.titleMedium)
            }
        }
        line("SPEED KM/H", "ECO ${v.eco ?: "--"}")
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            OtNumber(v.speed.padStart(3, ' '), if (wide) 190.sp else 150.sp)
        }
        line("RPM", v.rpm)
        Spacer(Modifier.height(6.dp))
        SegmentBar(v.rpmFraction, Modifier.fillMaxWidth(), count = 20, color = cs.primary, hotColor = cs.secondary, height = 18.dp)
        Spacer(Modifier.height(18.dp))
        line("L/100 NOW", "AVG ${v.avg}")
        OtNumber(v.nowValue, 80.sp, color = cs.secondary)
        Spacer(Modifier.height(14.dp))
        line("ECO", v.ecoLabel.uppercase())
        Spacer(Modifier.height(6.dp))
        BlockMeter(v.eco, Modifier.fillMaxWidth(), color = cs.primary, height = 22.dp, outlined = false)
    }
}
