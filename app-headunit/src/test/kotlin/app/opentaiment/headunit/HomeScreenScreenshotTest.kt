package app.opentaiment.headunit

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.feature.media.NowPlaying
import app.opentaiment.feature.weather.Weather
import app.opentaiment.feature.weather.WeatherState
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

/** Renders the home screen on the JVM (no emulator). Run: ./gradlew :app-headunit:recordPaparazziDebug */
class HomeScreenScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_C.copy(orientation = ScreenOrientation.LANDSCAPE),
        showSystemUi = false,
    )

    private val actions = HomeActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private val weather = WeatherState.Ready(
        Weather(temperatureC = 2.4, code = 3, windKmh = 14.0, minC = -1.0, maxC = 5.0, isDay = true, place = "Warszawa"),
    )

    private val song = NowPlaying(
        title = "Nocna jazda", artist = "Opentaiment Band", album = null, art = null,
        isPlaying = true, packageName = "com.spotify.music", appLabel = "Spotify",
        durationMs = 215_000, positionMs = 83_000, positionUpdatedAt = 0, speed = 0f,
    )

    @Test
    fun homeLandscapePlaying() = snap(hasAccess = true, nowPlaying = song)

    @Test
    fun homeLandscapeNoMediaAccess() = snap(hasAccess = false, nowPlaying = null)

    private fun snap(hasAccess: Boolean, nowPlaying: NowPlaying?) {
        paparazzi.snapshot {
            OpentaimentTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen(
                        now = LocalDateTime.of(2026, 11, 9, 18, 42),
                        weather = weather,
                        nowPlaying = nowPlaying,
                        hasMediaAccess = hasAccess,
                        actions = actions,
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                    )
                }
            }
        }
    }
}
