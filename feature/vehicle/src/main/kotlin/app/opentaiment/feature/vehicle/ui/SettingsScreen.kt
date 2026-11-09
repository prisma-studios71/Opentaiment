package app.opentaiment.feature.vehicle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.opentaiment.core.ui.AppearanceSettings
import app.opentaiment.core.ui.AppearanceSettingsSection
import app.opentaiment.core.ui.OtCard
import app.opentaiment.feature.vehicle.data.VehicleProfile

/** Opentaiment settings: look & feel and the car profile. [extra] adds app-specific sections. */
@Composable
fun SettingsScreen(
    appearance: AppearanceSettings,
    onAppearanceChange: (AppearanceSettings) -> Unit,
    profile: VehicleProfile,
    onProfileChange: (VehicleProfile) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        ScreenHeader("Ustawienia", connection = null, onBack = onBack) {}
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OtCard(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                Text("Wygląd", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                AppearanceSettingsSection(appearance, onAppearanceChange)
            }
            OtCard(Modifier.fillMaxWidth().widthIn(max = 900.dp)) {
                Text("Twoje auto", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                VehicleProfileEditor(profile, onProfileChange)
            }
            extra?.invoke()
            Spacer(Modifier.height(24.dp))
        }
    }
}
