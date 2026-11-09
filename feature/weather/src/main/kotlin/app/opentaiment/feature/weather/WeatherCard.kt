package app.opentaiment.feature.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentaiment.core.ui.OtCard
import kotlin.math.roundToInt

@Composable
fun WeatherCard(state: WeatherState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    OtCard(modifier, onClick = onRefresh) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            when (state) {
                WeatherState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                WeatherState.Error -> {
                    Text("Pogoda", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Brak danych – stuknij, żeby spróbować ponownie", style = MaterialTheme.typography.bodyLarge)
                }
                is WeatherState.Ready -> WeatherContent(state.weather)
            }
        }
    }
}

@Composable
private fun WeatherContent(w: Weather) {
    Text(
        w.place ?: "Pogoda",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(WeatherCodes.emoji(w.code, w.isDay), fontSize = 56.sp)
        Spacer(Modifier.width(16.dp))
        Text("${w.temperatureC.roundToInt()}°", style = MaterialTheme.typography.displayMedium)
    }
    Text(WeatherCodes.describe(w.code), style = MaterialTheme.typography.titleLarge, maxLines = 1)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val style = MaterialTheme.typography.bodyLarge
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        if (w.minC != null && w.maxC != null) {
            Text("↓${w.minC.roundToInt()}° ↑${w.maxC.roundToInt()}°", style = style, color = color)
        }
        Text("💨 ${w.windKmh.roundToInt()} km/h", style = style, color = color)
    }
    if (WeatherCodes.isHazardous(w.code) || (w.temperatureC <= 3.0)) {
        Spacer(Modifier.height(4.dp))
        Text(
            "⚠️ Uwaga, może być ślisko",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
