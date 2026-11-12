package app.opentaiment.headunit

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.core.ui.applyNightBrightness
import android.view.KeyEvent
import app.opentaiment.headunit.car.AppForeground
import app.opentaiment.headunit.car.CarSettingsStore
import app.opentaiment.headunit.car.HeadunitService
import app.opentaiment.headunit.car.KeyActionPerformer
import app.opentaiment.headunit.car.KeyBindings
import app.opentaiment.headunit.car.KeyLearning

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val keyPerformer by lazy { KeyActionPerformer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // In a car the screen should never go dark on its own.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        // First start in this process ≈ the car (and head unit) just woke up: resume radio etc.
        HeadunitService.start(this, ignitionOn = !coldStartHandled)
        coldStartHandled = true
        handleIntent(intent)

        setContent {
            val look by viewModel.appearance.collectAsStateWithLifecycle()
            val car by viewModel.carSettings.collectAsStateWithLifecycle()
            // With system-wide brightness control on, don't dim our window a second time.
            LaunchedEffect(look, car.brightnessControl) {
                applyNightBrightness(window, if (car.brightnessControl) look.copy(isNight = false) else look)
            }
            OpentaimentTheme(accent = look.settings.accent, night = look.isNight, style = look.settings.style) {
                OpentaimentApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SCREEN)?.let(viewModel::requestScreen)
    }

    override fun onStart() {
        super.onStart()
        AppForeground.set(true)
    }

    override fun onStop() {
        AppForeground.set(false)
        super.onStop()
    }

    /** Steering wheel / remote buttons while the launcher is on screen. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (KeyLearning.active.value && !KeyBindings.isProtected(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_UP) KeyLearning.offer(event.keyCode)
            return true
        }
        val action = CarSettingsStore.get(this).settings.value.keyBindings[event.keyCode]
        if (action != null) {
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) keyPerformer.perform(action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        viewModel.onResume()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private var coldStartHandled = false
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_PLAYER = "player"
        const val SCREEN_DRIVE = "drive"
    }
}
