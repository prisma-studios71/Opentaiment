package app.opentaiment.headunit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.ArcGauge
import app.opentaiment.core.ui.BlockMeter
import app.opentaiment.core.ui.HardwareKey
import app.opentaiment.core.ui.OtCard
import app.opentaiment.core.ui.OtNumber
import app.opentaiment.core.ui.OtTheme
import app.opentaiment.core.ui.SegmentBar
import app.opentaiment.core.ui.SevenSegmentText
import app.opentaiment.core.ui.SpectrumBars
import app.opentaiment.core.ui.TileIconView
import app.opentaiment.core.ui.UiStyle
import app.opentaiment.core.ui.ambientCompanion
import app.opentaiment.core.ui.ecoTint
import app.opentaiment.core.ui.stationMark
import app.opentaiment.feature.player.MusicPlayer
import app.opentaiment.feature.player.PlaybackKind
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.tpms.TpmsProblem
import app.opentaiment.feature.vehicle.tpms.Wheel
import app.opentaiment.feature.weather.WeatherCodes
import app.opentaiment.feature.weather.WeatherState
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Home screens for the non-classic styles. Unlike the classic screen they don't use the
 * configurable widget slots: each style has its own fixed composition (Cockpit: a big gauge,
 * Glass: a big player, Big type: blocks, Retro: VFD panels). The tiles become the dock.
 */

private val PL = Locale("pl")
private val clock = DateTimeFormatter.ofPattern("HH:mm")
private fun Double?.f(decimals: Int = 0): String =
    if (this == null || isNaN()) "—" else String.format(PL, "%,.${decimals}f", this)

private val OkGreen = Color(0xFF66BB6A)

/** Numbers and labels every styled home shows, worked out once. */
private class Readout(val data: WidgetData) {
    val s = data.stats
    val car = data.carConnected
    val speed = if (car) s.speedKmh.f() else "—"
    val speedFraction = if (car) ((s.speedKmh ?: 0.0) / 200.0).toFloat() else 0f
    val rpm = if (car) s.rpm.f() else "—"
    val rpmFraction = if (car) ((s.rpm ?: 0.0) / 7000.0).toFloat() else 0f
    val fuelNow = if (!car) "—" else s.instantLPer100Km?.f(1) ?: s.fuelRateLh.f(1)
    val fuelUnit = if (car && s.instantLPer100Km == null) "l/h" else "l/100"
    val fuelAvg = if (car) s.avgLPer100Km.f(1) else "—"
    val eco: Int? = s.eco?.score?.takeIf { car }
    val ecoLabel = when {
        !car -> "Połącz auto"
        eco == null -> "Ocena za minutę"
        eco >= 75 -> "Spokojnie"
        eco >= 50 -> "Może lżej"
        else -> "Ostro!"
    }
    val distance = if (car) s.trip.distanceKm.f(1) else "—"
    val cost = if (car) s.trip.fuelCost.f(2) else "—"
    val obd = when (val c = data.connection) {
        is ConnectionState.Connected -> if (c.demo) "OBD · Demo" else "OBD · ${c.deviceName}"
        is ConnectionState.Connecting -> "OBD · łączę…"
        is ConnectionState.Failed -> "OBD · błąd"
        ConnectionState.Disconnected -> "OBD · niepołączone"
    }
    val time: String = data.now.format(clock)
    val temp = (data.weather as? WeatherState.Ready)?.weather?.temperatureC?.let { "${it.f()}°" }

    val player = data.player
    val playing = player.isPlaying
    val source = when (player.kind) {
        PlaybackKind.RADIO -> player.subtitle ?: "Radio"
        PlaybackKind.LOCAL -> player.subtitle ?: "Muzyka"
        PlaybackKind.NONE -> data.favorites.firstOrNull()?.name ?: "Radio"
    }
    val song = when (player.kind) {
        PlaybackKind.NONE -> if (data.favorites.isEmpty()) "Dodaj ulubione stacje" else "Stuknij ▶, żeby grać"
        else -> player.error ?: player.title ?: source
    }
    val mark = stationMark(source)

    fun playPause(actions: WidgetActions) {
        if (player.kind == PlaybackKind.NONE) {
            data.favorites.firstOrNull()?.let { actions.onPlayStation(it, data.favorites) } ?: actions.onOpenRadio()
        } else {
            actions.onRadioToggle()
        }
    }

    fun isCurrent(id: String) = player.currentId == MusicPlayer.RADIO_PREFIX + id
}

private class Tyre(val wheel: Wheel, val value: String, val color: Color, val bad: Boolean)

@Composable
private fun tyres(data: WidgetData): List<Tyre> = Wheel.entries.map { w ->
    val status = data.tpms.wheels.first { it.wheel == w }
    val bad = status.problems.any { it != TpmsProblem.LOW_BATTERY && it != TpmsProblem.STALE }
    val color = when {
        status.reading == null -> MaterialTheme.colorScheme.onSurfaceVariant
        bad -> MaterialTheme.colorScheme.error
        status.problems.isNotEmpty() -> MaterialTheme.colorScheme.secondary
        else -> OkGreen
    }
    Tyre(w, status.reading?.pressureBar?.let { String.format(PL, "%.1f", it) } ?: "—", color, bad)
}

@Composable
internal fun StyledHomeScreen(
    style: UiStyle,
    data: WidgetData,
    tiles: List<HomeTile>,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val r = Readout(data)
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= maxHeight
        when (style) {
            UiStyle.COCKPIT -> CockpitHome(r, tiles, actions, wide)
            UiStyle.GLASS -> GlassHome(r, tiles, actions, wide)
            UiStyle.BIG_TYPE -> BigTypeHome(r, tiles, actions, wide)
            UiStyle.RETRO -> RetroHome(r, tiles, actions, wide)
            UiStyle.CLASSIC -> Unit
        }
    }
}

// ───────────────────────────── shared bits ─────────────────────────────

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Tiles as one scrollable row of buttons. Long press opens the tile editor, as on the classic grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(
    tiles: List<HomeTile>,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    item: @Composable (HomeTile, Modifier) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tiles.forEach { tile -> item(tile, Modifier.combinedClickable(onClick = tile.onClick, onLongClick = onEdit)) }
            if (tiles.isEmpty()) item(HomeTile("add", "Dodaj kafelki", app.opentaiment.core.ui.TileIcon.Vector(Icons.Rounded.Home), onClick = onEdit), Modifier.clickable(onClick = onEdit))
        }
        trailing()
    }
}

@Composable
private fun RoundControl(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit, filled: Boolean = false, fill: Color = MaterialTheme.colorScheme.primary, tint: Color = MaterialTheme.colorScheme.onPrimary) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (filled) Modifier.background(fill) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(size * 0.5f), tint = if (filled) tint else LocalContentColor.current)
    }
}

@Composable
private fun PlayerControls(r: Readout, actions: WidgetActions, size: Dp, fill: Color = MaterialTheme.colorScheme.primary, tint: Color = MaterialTheme.colorScheme.onPrimary) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundControl(Icons.Rounded.SkipPrevious, "Poprzednia", size * 0.8f, actions.onRadioPrevious)
        RoundControl(
            if (r.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (r.playing) "Pauza" else "Graj",
            size, { r.playPause(actions) }, filled = true, fill = fill, tint = tint,
        )
        RoundControl(Icons.Rounded.SkipNext, "Następna", size * 0.8f, actions.onRadioNext)
    }
}

/** Cover-art stand-in: a gradient square with the station's short mark. */
@Composable
private fun StationArt(r: Readout, modifier: Modifier, corner: Dp, fontSize: androidx.compose.ui.unit.TextUnit) {
    val a = MaterialTheme.colorScheme.primary
    Box(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, ambientCompanion(a)))),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            r.mark, Modifier.padding(corner * 0.7f), color = Color.White, fontSize = fontSize,
            fontWeight = FontWeight.SemiBold, maxLines = 1, lineHeight = fontSize,
        )
    }
}

// ───────────────────────────── Cockpit ─────────────────────────────

@Composable
private fun CockpitHome(r: Readout, tiles: List<HomeTile>, actions: HomeActions, wide: Boolean) {
    val a = actions.widgets
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (r.car) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.width(8.dp))
            Label(r.obd, color = if (r.car) OkGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Label(listOfNotNull(r.time, r.temp).joinToString(" · "))
        }
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CockpitGauge(r, a, Modifier.weight(1.45f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CockpitNowPlaying(r, a, Modifier.fillMaxWidth().height(186.dp))
                    CockpitCarCard(r, a, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            CockpitGauge(r, a, Modifier.weight(1.3f).fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            CockpitNowPlaying(r, a, Modifier.fillMaxWidth().height(186.dp))
            Spacer(Modifier.height(14.dp))
            CockpitCarCard(r, a, Modifier.weight(0.8f).fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Dock(tiles, actions.onEditTiles, Modifier.fillMaxWidth().height(80.dp).padding(top = 10.dp)) { tile, m ->
            Row(
                m.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).height(60.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TileIconView(tile.icon, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(tile.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CockpitGauge(r: Readout, a: WidgetActions, modifier: Modifier) {
    val tint = ecoTint(r.eco)
    Column(modifier.clickable(onClick = a.onOpenDrive)) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ArcGauge(r.speedFraction, tint, Modifier.fillMaxHeight().aspectRatio(1f), outerFraction = r.rpmFraction)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OtNumber(r.speed, 120.sp)
                Label("KM/H")
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.clip(CircleShape).border(1.dp, tint.copy(alpha = 0.5f), CircleShape)
                        .background(tint.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("ECO ${r.eco ?: "—"} · ${r.ecoLabel}", style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf(
                Triple("Spalanie teraz", r.fuelNow, r.fuelUnit),
                Triple("Średnio", r.fuelAvg, "l/100"),
                Triple("Obroty", r.rpm, "rpm"),
            ).forEachIndexed { i, (label, value, unit) ->
                if (i > 0) Box(Modifier.width(1.dp).height(64.dp).background(MaterialTheme.colorScheme.outline))
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Label(label.uppercase())
                    Row(verticalAlignment = Alignment.Bottom) {
                        OtNumber(value, 34.sp)
                        Spacer(Modifier.width(6.dp))
                        Label(unit, Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CockpitNowPlaying(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenRadio, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StationArt(r, Modifier.size(72.dp), 14.dp, 20.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Label(r.source.uppercase())
                Text(r.song, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerControls(r, a, 52.dp)
            Spacer(Modifier.weight(1f))
            if (r.player.kind == PlaybackKind.RADIO) Label("● NA ŻYWO", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Tyres on a top-down car when TPMS is on, otherwise the current trip. */
@Composable
private fun CockpitCarCard(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = if (r.data.tpmsEnabled) a.onOpenTpms else a.onOpenStats, padding = 16.dp) {
        if (r.data.tpmsEnabled) {
            val t = tyres(r.data)
            Label("OPONY · BAR")
            Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    TyreValue(t[0], Alignment.Start); Spacer(Modifier.height(20.dp)); TyreValue(t[2], Alignment.Start)
                }
                CarTopView(t, Modifier.fillMaxHeight().aspectRatio(0.55f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.End) {
                    TyreValue(t[1], Alignment.End); Spacer(Modifier.height(20.dp)); TyreValue(t[3], Alignment.End)
                }
            }
        } else {
            Label("TRASA")
            Spacer(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("Trasa" to "${r.distance} km", "Koszt" to "${r.cost} zł", "Eco" to (r.eco?.toString() ?: "—")).forEach { (l, v) ->
                Column {
                    Label(l.uppercase())
                    OtNumber(v, 22.sp)
                }
            }
        }
    }
}

@Composable
private fun TyreValue(t: Tyre, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Label(t.wheel.short + if (t.bad) " · niskie" else "")
        OtNumber(t.value, 30.sp, color = if (t.bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CarTopView(t: List<Tyre>, modifier: Modifier) {
    val body = MaterialTheme.colorScheme.surfaceVariant
    val line = MaterialTheme.colorScheme.outline
    val glass = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val tyreColors = t.map { if (it.bad) it.color else line }
    Canvas(modifier.padding(vertical = 6.dp)) {
        val w = size.width
        val h = size.height
        val bw = w * 0.62f
        val left = (w - bw) / 2
        drawRoundRect(body, Offset(left, 0f), Size(bw, h), CornerRadius(bw * 0.38f))
        drawRoundRect(line, Offset(left, 0f), Size(bw, h), CornerRadius(bw * 0.38f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawRoundRect(glass, Offset(left + bw * 0.12f, h * 0.24f), Size(bw * 0.76f, h * 0.15f), CornerRadius(12f))
        drawRoundRect(glass, Offset(left + bw * 0.15f, h * 0.72f), Size(bw * 0.7f, h * 0.1f), CornerRadius(12f))
        val tw = w * 0.13f
        val th = h * 0.17f
        listOf(
            Offset(left - tw * 0.7f, h * 0.15f), Offset(left + bw - tw * 0.3f, h * 0.15f),
            Offset(left - tw * 0.7f, h * 0.66f), Offset(left + bw - tw * 0.3f, h * 0.66f),
        ).forEachIndexed { i, o -> drawRoundRect(tyreColors[i], o, Size(tw, th), CornerRadius(6f)) }
    }
}

// ───────────────────────────── Glass ─────────────────────────────

@Composable
private fun GlassHome(r: Readout, tiles: List<HomeTile>, actions: HomeActions, wide: Boolean) {
    val a = actions.widgets
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassPlayer(r, a, Modifier.weight(1.25f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    GlassDrive(r, a, Modifier.fillMaxWidth().height(150.dp))
                    GlassNav(r, a, Modifier.fillMaxWidth().height(100.dp))
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GlassWeather(r, a, Modifier.weight(1f).fillMaxHeight())
                        GlassTyres(r, a, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        } else {
            GlassPlayer(r, a, Modifier.weight(1.3f).fillMaxWidth())
            GlassDrive(r, a, Modifier.fillMaxWidth().height(150.dp))
            Row(Modifier.weight(0.6f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassNav(r, a, Modifier.weight(1f).fillMaxHeight())
                GlassTyres(r, a, Modifier.weight(1f).fillMaxHeight())
            }
        }
        val style = OtTheme.style
        Box(
            Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(26.dp)).background(style.barColor)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp)).padding(horizontal = 10.dp),
        ) {
            Dock(
                tiles, actions.onEditTiles, Modifier.fillMaxSize(),
                trailing = { Text(r.time, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 16.dp, end = 10.dp)) },
            ) { tile, m ->
                Row(
                    m.clip(RoundedCornerShape(18.dp)).height(58.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TileIconView(tile.icon, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(tile.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun GlassPlayer(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenRadio) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val art = minOf(maxHeight, maxWidth * 0.45f, 260.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StationArt(r, Modifier.size(art), 22.dp, (art.value * 0.2f).sp)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                    Label(r.source.uppercase())
                    Text(r.song, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(18.dp))
                    PlayerControls(r, a, 64.dp, fill = Color.White, tint = Color(0xFF111111))
                }
            }
        }
        if (r.data.favorites.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            r.data.favorites.take(6).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { st ->
                        val on = r.isCurrent(st.id)
                        Text(
                            st.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (on) FontWeight.SemiBold else null,
                            color = if (on) Color(0xFF111111) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(if (on) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.07f))
                                .clickable { a.onPlayStation(st, r.data.favorites) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GlassDrive(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenDrive, padding = 20.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            OtNumber(r.speed, 84.sp)
            Spacer(Modifier.width(8.dp))
            Label("km/h", Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Label("teraz")
                OtNumber(r.fuelNow, 32.sp)
                Label(r.fuelUnit)
            }
        }
    }
}

@Composable
private fun GlassNav(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onNavigateHome, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Home, null, Modifier.size(28.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Do domu", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Label(r.data.homeAddress.ifBlank { "Ustaw adres w Ustawieniach" })
            }
        }
    }
}

@Composable
private fun GlassWeather(r: Readout, a: WidgetActions, modifier: Modifier) {
    val w = (r.data.weather as? WeatherState.Ready)?.weather
    OtCard(modifier, onClick = a.onRefreshWeather, padding = 18.dp) {
        Label(w?.place ?: "Pogoda")
        Spacer(Modifier.weight(1f))
        OtNumber(w?.temperatureC?.let { "${it.f()}°" } ?: "—", 48.sp)
        Label(w?.let { WeatherCodes.describe(it.code) } ?: "Brak danych")
    }
}

@Composable
private fun GlassTyres(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenTpms, padding = 18.dp) {
        Label("Opony · bar")
        Spacer(Modifier.weight(1f))
        if (!r.data.tpmsEnabled) {
            Text("Czujniki wyłączone", style = MaterialTheme.typography.titleMedium)
        } else {
            val t = tyres(r.data)
            t.chunked(2).forEach { row ->
                Row {
                    row.forEach { tyre ->
                        Text(
                            tyre.value, Modifier.weight(1f), fontSize = 24.sp,
                            color = if (tyre.bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (tyre.bad) FontWeight.SemiBold else FontWeight.Light,
                        )
                    }
                }
            }
            t.firstOrNull { it.bad }?.let { Label("${it.wheel.label}: za niskie", color = MaterialTheme.colorScheme.error) }
        }
    }
}

// ───────────────────────────── Big type ─────────────────────────────

/** A hard-edged block: filled with [fill] or outlined in the text colour. */
@Composable
private fun Block(
    modifier: Modifier,
    fill: Color? = null,
    content: Color = if (fill != null) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .then(if (fill != null) Modifier.background(fill) else Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) { body() }
    }
}

@Composable
private fun BigLabel(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun BigTypeHome(r: Readout, tiles: List<HomeTile>, actions: HomeActions, wide: Boolean) {
    val a = actions.widgets
    val gap = 8.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                BigSpeed(r, a, Modifier.weight(1.25f).fillMaxHeight())
                Column(Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        BigFuel(r, a, Modifier.weight(1f).fillMaxHeight())
                        BigTyres(r, a, Modifier.weight(1f).fillMaxHeight())
                    }
                    BigRadio(r, a, Modifier.weight(1f).fillMaxWidth())
                }
            }
        } else {
            BigSpeed(r, a, Modifier.weight(1.2f).fillMaxWidth())
            Row(Modifier.weight(0.7f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                BigFuel(r, a, Modifier.weight(1f).fillMaxHeight())
                BigTyres(r, a, Modifier.weight(1f).fillMaxHeight())
            }
            BigRadio(r, a, Modifier.weight(0.8f).fillMaxWidth())
        }
        Dock(tiles, actions.onEditTiles, Modifier.fillMaxWidth().height(64.dp)) { tile, m ->
            Box(
                m.height(64.dp).border(3.dp, MaterialTheme.colorScheme.onSurface).padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(tile.label, style = MaterialTheme.typography.headlineSmall, fontSize = 20.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BigSpeed(r: Readout, a: WidgetActions, modifier: Modifier) {
    Block(modifier, fill = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary, onClick = a.onOpenDrive) {
        Row { BigLabel("Prędkość", Modifier.weight(1f)); BigLabel("km/h") }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) { OtNumber(r.speed, 230.sp) }
        Row { BigLabel("Eco ${r.eco ?: "—"}/100", Modifier.weight(1f)); BigLabel(r.ecoLabel) }
        Spacer(Modifier.height(8.dp))
        BlockMeter(r.eco, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun BigFuel(r: Readout, a: WidgetActions, modifier: Modifier) {
    Block(modifier, fill = MaterialTheme.colorScheme.onSurface, onClick = a.onOpenStats) {
        BigLabel("Spalanie teraz · ${r.fuelUnit}")
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { OtNumber(r.fuelNow, 96.sp) }
        BigLabel("Średnio ${r.fuelAvg} · trasa ${r.distance} km")
    }
}

@Composable
private fun BigTyres(r: Readout, a: WidgetActions, modifier: Modifier) {
    Block(modifier, onClick = if (r.data.tpmsEnabled) a.onOpenTpms else a.onOpenTrips) {
        if (!r.data.tpmsEnabled) {
            BigLabel("Trasa · koszt")
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { OtNumber(r.cost, 72.sp) }
            BigLabel("zł za ${r.distance} km")
            return@Block
        }
        BigLabel("Opony · bar")
        Spacer(Modifier.weight(1f))
        tyres(r.data).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { t ->
                    Column(Modifier.weight(1f)) {
                        Box(if (t.bad) Modifier.background(MaterialTheme.colorScheme.error).padding(horizontal = 6.dp) else Modifier) {
                            OtNumber(t.value, 44.sp, color = if (t.bad) MaterialTheme.colorScheme.background else LocalContentColor.current)
                        }
                        BigLabel(t.wheel.short + if (t.bad) " · pompuj" else "")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BigRadio(r: Readout, a: WidgetActions, modifier: Modifier) {
    Block(modifier, onClick = a.onOpenRadio) {
        Row { BigLabel("Radio · ${if (r.playing) "na żywo" else "pauza"}", Modifier.weight(1f)); BigLabel(r.time) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text(r.source.uppercase(), style = MaterialTheme.typography.displayMedium, maxLines = 1, overflow = TextOverflow.Clip)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(LocalContentColor.current))
        Text(
            r.song.uppercase(), Modifier.fillMaxWidth().basicMarquee().padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleLarge, maxLines = 1,
        )
        Box(Modifier.fillMaxWidth().height(3.dp).background(LocalContentColor.current))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BigButton("◀◀", false, onClick = a.onRadioPrevious)
            BigButton(if (r.playing) "Pauza" else "Graj", true) { r.playPause(a) }
            BigButton("▶▶", false, onClick = a.onRadioNext)
            Spacer(Modifier.weight(1f))
            r.data.favorites.take(5).forEach { st ->
                BigButton(stationMark(st.name), r.isCurrent(st.id), small = true) { a.onPlayStation(st, r.data.favorites) }
            }
        }
    }
}

@Composable
private fun BigButton(text: String, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val fg = LocalContentColor.current
    Text(
        text.uppercase(), maxLines = 1,
        style = MaterialTheme.typography.titleMedium,
        fontSize = if (small) 14.sp else 18.sp,
        color = if (on) MaterialTheme.colorScheme.background else fg,
        modifier = Modifier.border(3.dp, fg).background(if (on) fg else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = if (small) 10.dp else 16.dp, vertical = 10.dp),
    )
}

// ───────────────────────────── Retro VFD ─────────────────────────────

@Composable
private fun RetroHome(r: Readout, tiles: List<HomeTile>, actions: HomeActions, wide: Boolean) {
    val a = actions.widgets
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RetroTop(r, a, Modifier.fillMaxWidth().height(176.dp), wide)
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RetroDrive(r, a, Modifier.weight(1.15f).fillMaxHeight())
                RetroSpectrum(r, a, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            RetroDrive(r, a, Modifier.weight(1f).fillMaxWidth())
            RetroSpectrum(r, a, Modifier.weight(0.7f).fillMaxWidth())
        }
        val controls = listOf<Pair<String, () -> Unit>>(
            "SEEK ◀" to a.onRadioPrevious,
            (if (r.playing) "PAUSE" else "PLAY") to { r.playPause(a) },
            "SEEK ▶" to a.onRadioNext,
        )
        val buttons = controls.map { (label, click) -> RetroKey(label, click, null, active = label == "PAUSE") } +
            tiles.map { RetroKey(it.label, it.onClick, actions.onEditTiles, active = false) }
        val perRow = if (wide) 7 else 4
        buttons.take(if (wide) perRow else perRow * 2).chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { HardwareKey(it.label, it.onClick, Modifier.weight(1f).fillMaxHeight(), active = it.active, onLongClick = it.onLongClick) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class RetroKey(val label: String, val onClick: () -> Unit, val onLongClick: (() -> Unit)?, val active: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RetroTop(r: Readout, a: WidgetActions, modifier: Modifier, wide: Boolean) {
    OtCard(modifier, onClick = a.onOpenRadio, padding = 18.dp) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    listOf(
                        "NET" to (r.player.kind == PlaybackKind.RADIO), "ST" to true, "▶ PLAY" to r.playing,
                        "OBD" to r.car, "LOUD" to true,
                    ).forEach { (t, on) ->
                        Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = if (on) 1f else 0.18f))
                    }
                }
                Text(r.source.uppercase(), style = MaterialTheme.typography.displaySmall, maxLines = 1, overflow = TextOverflow.Clip)
                Text(r.song.uppercase(), Modifier.basicMarquee(), style = MaterialTheme.typography.titleLarge, maxLines = 1)
            }
            if (wide) {
                Spacer(Modifier.width(16.dp))
                SevenSegmentText(r.time.let { if (it.startsWith("0")) " " + it.drop(1) else it }, 76.dp, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RetroDrive(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenDrive, padding = 18.dp) {
        Row { Text("SPEED", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("ECO ${r.eco ?: "--"}", style = MaterialTheme.typography.titleMedium) }
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OtNumber(r.speed.padStart(3, ' '), 170.sp)
            Spacer(Modifier.width(12.dp))
            Text("KM/H", style = MaterialTheme.typography.headlineSmall)
        }
        Row { Text("RPM ×1000", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text(r.rpm, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(6.dp))
        SegmentBar(r.rpmFraction * 7f / 8f, Modifier.fillMaxWidth(), count = 32, color = MaterialTheme.colorScheme.primary, hotColor = MaterialTheme.colorScheme.secondary, hotFrom = 0.72f, height = 18.dp)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "2", "4", "6", "8").forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RetroSpectrum(r: Readout, a: WidgetActions, modifier: Modifier) {
    OtCard(modifier, onClick = a.onOpenStats, padding = 18.dp) {
        Row {
            Text("SPECTRUM", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("L/100 ${r.fuelNow} · AVG ${r.fuelAvg}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        SpectrumBars(r.playing, Modifier.weight(1f).fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (r.data.tpmsEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                tyres(r.data).forEach { t ->
                    Text(
                        "${t.wheel.short} ${t.value.replace(',', '.')}${if (t.bad) "!" else ""}",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (t.bad) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            Text("TRIP ${r.distance} KM · ${r.cost} ZŁ", style = MaterialTheme.typography.titleLarge)
        }
    }
}
