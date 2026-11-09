package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.DtcDictionary
import app.opentaiment.feature.vehicle.data.DtcKind
import app.opentaiment.feature.vehicle.data.DtcReport

class DiagnosticsActions(
    val onBack: () -> Unit,
    val connect: ObdConnectActions,
    val onRead: () -> Unit,
    val onClear: () -> Unit,
    val onSearchCode: (String) -> Unit,
)

private val gap = 20.dp
private val OkGreen = Color(0xFF66BB6A)

@Composable
fun DiagnosticsScreen(
    connection: ConnectionState,
    state: DiagnosticsState,
    actions: DiagnosticsActions,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val onConnectClick = rememberObdConnector(actions.connect)
    val connected = connection is ConnectionState.Connected

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack, modifier = Modifier.size(72.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Wróć", Modifier.size(40.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Diagnostyka", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(20.dp))
            StatusChip(connection)
            Spacer(Modifier.weight(1f))
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeaderButton(Icons.Rounded.DeleteSweep, "Skasuj błędy", { confirmClear = true })
                    HeaderButton(Icons.Rounded.Refresh, "Odczytaj", actions.onRead, primary = true)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (!connected) {
            ConnectPrompt(connection, onConnectClick, actions.connect.onDemo, Modifier.fillMaxSize())
        } else {
            DiagnosticsBody(state, actions, Modifier.fillMaxSize())
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text("Skasować błędy?") },
            text = {
                Text(
                    "• Zgaśnie kontrolka Check Engine, ale jeśli usterka nadal jest, błąd wróci.\n" +
                        "• Wyzerują się testy gotowości – przez kilkadziesiąt km auto może nie przejść badania spalin.\n" +
                        "• Rób to przy włączonym zapłonie i zgaszonym silniku.\n\n" +
                        "Zanotuj kody, zanim je skasujesz – mechanikowi się przydadzą.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    actions.onClear()
                }) { Text("Skasuj", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun DiagnosticsBody(state: DiagnosticsState, actions: DiagnosticsActions, modifier: Modifier = Modifier) {
    val report = when (state) {
        is DiagnosticsState.Ready -> state.report
        is DiagnosticsState.Failed -> state.previous
        else -> null
    }
    val busy = state == DiagnosticsState.Reading || state == DiagnosticsState.Clearing

    BoxWithConstraints(modifier) {
        val landscape = maxWidth >= maxHeight
        val summary: @Composable (Modifier) -> Unit = { m -> SummaryCard(state, report, busy, actions.onRead, compact = !landscape, modifier = m) }
        val codes: @Composable (Modifier) -> Unit = { m -> CodesList(report, busy, actions.onSearchCode, m) }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                summary(Modifier.weight(1f).fillMaxHeight())
                codes(Modifier.weight(2f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                summary(Modifier.fillMaxWidth().height(220.dp))
                codes(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: DiagnosticsState,
    report: DtcReport?,
    busy: Boolean,
    onRead: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    if (compact && !busy && report != null && state !is DiagnosticsState.Failed) {
        CompactSummary(report, modifier)
        return
    }
    OtCard(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                busy -> {
                    CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (state == DiagnosticsState.Clearing) "Kasuję błędy…" else "Pytam sterowniki o błędy…",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                report == null -> {
                    Text("Nie odczytano jeszcze błędów", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRead, modifier = Modifier.height(64.dp)) {
                        Text("Odczytaj błędy", style = MaterialTheme.typography.titleMedium)
                    }
                }
                else -> {
                    MilIcon(report, Modifier.size(96.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        milLabel(report.milOn),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (report.totalCount == 0) "Brak zapisanych błędów 🎉" else dtcCountLabel(report.totalCount),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    CountRow(DtcKind.STORED, report.stored.size)
                    CountRow(DtcKind.PENDING, report.pending.size)
                    CountRow(DtcKind.PERMANENT, report.permanent.size)
                }
            }
            if (state is DiagnosticsState.Failed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "⚠️ ${state.message}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                if (report == null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRead) { Text("Spróbuj ponownie") }
                }
            }
        }
    }
}

@Composable
private fun CompactSummary(report: DtcReport, modifier: Modifier = Modifier) {
    OtCard(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            MilIcon(report, Modifier.size(88.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(milLabel(report.milOn), style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (report.totalCount == 0) "Brak zapisanych błędów 🎉" else dtcCountLabel(report.totalCount),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.width(220.dp)) {
                CountRow(DtcKind.STORED, report.stored.size)
                CountRow(DtcKind.PENDING, report.pending.size)
                CountRow(DtcKind.PERMANENT, report.permanent.size)
            }
        }
    }
}

@Composable
private fun MilIcon(report: DtcReport, modifier: Modifier = Modifier) {
    val problem = report.milOn == true || report.totalCount > 0
    Icon(
        if (problem) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
        contentDescription = null,
        modifier = modifier,
        tint = when {
            report.milOn == true -> MaterialTheme.colorScheme.error
            problem -> MaterialTheme.colorScheme.secondary
            else -> OkGreen
        },
    )
}

private fun milLabel(milOn: Boolean?): String = when (milOn) {
    true -> "Check Engine świeci"
    false -> "Check Engine nie świeci"
    null -> "Stan kontrolki nieznany"
}

@Composable
private fun CountRow(kind: DtcKind, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(kind.color()))
        Spacer(Modifier.width(10.dp))
        Text(kind.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DtcKind.color(): Color = when (this) {
    DtcKind.STORED -> MaterialTheme.colorScheme.error
    DtcKind.PENDING -> MaterialTheme.colorScheme.secondary
    DtcKind.PERMANENT -> MaterialTheme.colorScheme.primary
}

@Composable
private fun CodesList(report: DtcReport?, busy: Boolean, onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    val sections = report?.let {
        listOf(DtcKind.STORED to it.stored, DtcKind.PENDING to it.pending, DtcKind.PERMANENT to it.permanent)
            .filter { (_, codes) -> codes.isNotEmpty() }
    }.orEmpty()

    if (sections.isEmpty()) {
        OtCard(modifier) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        busy -> "Za chwilę pojawią się tu kody błędów"
                        report == null -> "Kliknij „Odczytaj”, żeby sprawdzić auto"
                        else -> "Auto jest czyste – żaden sterownik nie zgłasza usterek"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        sections.forEach { (kind, codes) ->
            item(key = "header-${kind.name}") {
                Column(Modifier.padding(top = 4.dp)) {
                    Text(kind.label, style = MaterialTheme.typography.titleLarge, color = kind.color())
                    Text(kind.explanation, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(codes, key = { "${kind.name}-$it" }) { code -> DtcItem(code, kind, onSearch) }
        }
    }
}

@Composable
private fun DtcItem(code: String, kind: DtcKind, onSearch: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(kind.color().copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    code,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = kind.color(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(DtcDictionary.describe(code), style = MaterialTheme.typography.titleMedium)
                Text(
                    DtcDictionary.system(code),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onSearch(code) }, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.Search, contentDescription = "Szukaj $code w internecie", Modifier.size(32.dp))
            }
        }
    }
}

/** Polish plural: 1 błąd, 2–4 błędy, 5+ błędów (but 12–14 błędów). */
fun dtcCountLabel(count: Int): String {
    val word = when {
        count == 1 -> "błąd"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "błędy"
        else -> "błędów"
    }
    return "$count $word"
}
