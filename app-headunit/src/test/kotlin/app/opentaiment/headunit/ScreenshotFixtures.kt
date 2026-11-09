package app.opentaiment.headunit

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.feature.apps.AppEntry
import app.opentaiment.feature.vehicle.ConnectionState
import app.opentaiment.feature.vehicle.DiagnosticsState
import app.opentaiment.feature.vehicle.data.VehicleStats
import app.opentaiment.feature.vehicle.ui.ObdConnectActions
import app.opentaiment.headunit.tiles.TileList

/** Shared fake data for screenshot tests. Bitmaps must be created inside a test (after Paparazzi boots). */
object Fixtures {

    val homeActions = HomeActions({}, {}, {}, {}, {}, {}, {})

    val connectActions = ObdConnectActions(
        pairedDevices = { emptyList() }, hasBluetoothPermission = { true },
        onConnect = {}, onDemo = {}, onOpenBluetoothSettings = {},
    )

    fun tiles(
        ids: List<String> = TileList.DEFAULT,
        apps: List<AppEntry> = emptyList(),
        connection: ConnectionState = ConnectionState.Disconnected,
        stats: VehicleStats = VehicleStats(),
        diagnostics: DiagnosticsState = DiagnosticsState.Idle,
    ): List<HomeTile> = homeTiles(ids, apps, connection, stats, diagnostics, onBuiltin = {}, onApp = {})

    fun fakeApps(): List<AppEntry> {
        val names = listOf(
            "Google Maps" to 0xFF34A853, "Waze" to 0xFF33CCFF, "Spotify" to 0xFF1DB954,
            "YouTube Music" to 0xFFFF0000, "Telefon" to 0xFF4285F4, "Wiadomości" to 0xFF1A73E8,
            "Aparat" to 0xFF5F6368, "Chrome" to 0xFFFBBC04, "OsmAnd" to 0xFFFF8800,
            "Podcasty" to 0xFF8E24AA, "Pogoda" to 0xFF29B6F6, "Ustawienia" to 0xFF78909C,
            "Kalendarz" to 0xFF1E88E5, "Tidal" to 0xFF000000, "Zegar" to 0xFF26A69A,
        )
        return names.map { (label, color) ->
            val bmp = ImageBitmap(144, 144)
            val paint = Paint().apply { this.color = Color(color) }
            Canvas(bmp).drawCircle(Offset(72f, 72f), 66f, paint)
            paint.color = Color.White.copy(alpha = 0.9f)
            Canvas(bmp).drawCircle(Offset(72f, 72f), 24f, paint)
            AppEntry(label, "pkg.${label.lowercase().replace(" ", "")}", bmp)
        }
    }
}

fun Paparazzi.render(content: @Composable () -> Unit) {
    snapshot {
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoOpRegistryOwner) {
            OpentaimentTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }
}

private object NoOpRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) = Unit
    }
}
