package app.opentaiment.headunit

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentaiment.feature.apps.AppDrawer

private enum class Screen { Home, Apps }

@Composable
fun OpentaimentApp(viewModel: HomeViewModel, isDefaultHome: () -> Boolean) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshWeather() }

    LaunchedEffect(Unit) {
        if (!viewModel.hasLocationPermission()) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // Back from the app drawer returns home; on home a launcher swallows Back.
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    BackHandler(enabled = screen == Screen.Home && isDefaultHome()) { }

    val now by viewModel.now.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val hasMediaAccess by viewModel.hasMediaAccess.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val content = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)

        when (screen) {
            Screen.Home -> HomeScreen(
                now = now,
                weather = weather,
                nowPlaying = nowPlaying,
                hasMediaAccess = hasMediaAccess,
                actions = HomeActions(
                    onRefreshWeather = viewModel::refreshWeather,
                    onGrantMediaAccess = viewModel::grantMediaAccess,
                    onPlayPause = viewModel::playPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onOpenPlayer = viewModel::openPlayer,
                    onNavigation = { viewModel.openNavigation() },
                    onPhone = { viewModel.openDialer() },
                    onApps = { screen = Screen.Apps },
                    onBluetooth = { viewModel.openBluetooth() },
                    onSettings = { viewModel.openSettings() },
                ),
                modifier = content,
            )
            Screen.Apps -> AppDrawer(
                apps = apps,
                onLaunch = {
                    viewModel.launch(it)
                    screen = Screen.Home
                },
                onBack = { screen = Screen.Home },
                modifier = content,
            )
        }
    }
}
