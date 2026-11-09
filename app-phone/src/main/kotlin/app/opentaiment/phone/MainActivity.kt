package app.opentaiment.phone

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opentaiment.core.ui.OpentaimentTheme
import app.opentaiment.core.ui.applyNightBrightness

class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val appearance = (application as OpentaimentPhoneApp).appearance

        setContent {
            val look by appearance.state.collectAsStateWithLifecycle()
            LaunchedEffect(look) { applyNightBrightness(window, look) }
            OpentaimentTheme(accent = look.settings.accent, night = look.isNight) {
                PhoneApp(appearance)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as OpentaimentPhoneApp).appearance.refresh()
    }
}
