package app.opentaiment.headunit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.feature.apps.AppDrawer
import app.opentaiment.feature.apps.AppEntry
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/**
 * Renders the main screens on the JVM (no emulator needed).
 * Record: ./gradlew :app-headunit:recordPaparazziDebug
 */
class HomeScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val coldDay = WeatherState.Ready(
        Weather(temperatureC = 2.4, code = 3, windKmh = 14.0, minC = -1.0, maxC = 5.0, isDay = true, place = "Warszawa"),
    )
    private val clearNight = WeatherState.Ready(
        Weather(temperatureC = 11.0, code = 0, windKmh = 6.0, minC = 8.0, maxC = 17.0, isDay = false, place = "Kraków"),
    )

    // Lazy: bitmaps can only be created after Paparazzi has booted layoutlib (not in the constructor).
    private val song by lazy {
        NowPlaying(
            title = "Nocna jazda", artist = "Opentaiment Band", album = null, art = fakeArt(),
            isPlaying = true, packageName = "com.spotify.music", appLabel = "Spotify",
            durationMs = 215_000, positionMs = 83_000, positionUpdatedAt = 0, speed = 0f,
        )
    }

    private val evening = LocalDateTime.of(2026, 11, 9, 18, 42)

    @Test
    fun homeLandscapePlaying() = home(coldDay, song, hasAccess = true)

    @Test
    fun homeLandscapePaused() = home(clearNight, song.copy(isPlaying = false, title = "Trasa do domu"), hasAccess = true)

    @Test
    fun homeLandscapeNothingPlaying() = home(clearNight, null, hasAccess = true)

    @Test
    fun homeLandscapeNoMediaAccess() = home(coldDay, null, hasAccess = false)

    @Test
    fun homeLandscapeWeatherError() = home(WeatherState.Error, song, hasAccess = true)

    @Test
    fun homePortraitPlaying() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.PORTRAIT))
        home(coldDay, song, hasAccess = true)
    }

    @Test
    fun appDrawer() = render {
        AppDrawer(apps = fakeApps(), onLaunch = {}, onBack = {}, modifier = Modifier.fillMaxSize().padding(20.dp))
    }

    private fun home(weather: WeatherState, nowPlaying: NowPlaying?, hasAccess: Boolean) = render {
        HomeScreen(
            now = evening,
            weather = weather,
            nowPlaying = nowPlaying,
            hasMediaAccess = hasAccess,
            actions = actions,
            modifier = Modifier.fillMaxSize().padding(20.dp),
        )
    }

    private fun render(content: @Composable () -> Unit) {
        paparazzi.snapshot {
            OpentaimentTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

    /** Simple generated "album cover" so screenshots don't need binary fixtures. */
    private fun fakeArt(): ImageBitmap {
        val size = 512
        val bmp = ImageBitmap(size, size)
        val canvas = Canvas(bmp)
        val paint = Paint()
        for (i in 0 until size step 8) {
            val t = i / size.toFloat()
            paint.color = Color(red = 0.05f + 0.25f * t, green = 0.2f + 0.3f * (1 - t), blue = 0.45f + 0.4f * t)
            canvas.drawRect(0f, i.toFloat(), size.toFloat(), i + 8f, paint)
        }
        paint.color = Color(0xFFFFB74D)
        canvas.drawCircle(Offset(size * 0.68f, size * 0.32f), size * 0.13f, paint)
        paint.color = Color(0xFF0B0F14)
        canvas.drawRect(0f, size * 0.72f, size.toFloat(), size.toFloat(), paint)
        paint.color = Color(0xFF4FC3F7)
        canvas.drawRect(size * 0.47f, size * 0.8f, size * 0.53f, size * 0.95f, paint)
        return bmp
    }

    private fun fakeApps(): List<AppEntry> {
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
            AppEntry(label, "pkg.$label", bmp)
        }
    }
}
