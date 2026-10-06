package com.example.drift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drift.input.TelemetryUiState
import com.example.drift.model.CarPalette
import com.example.drift.model.DriftAssistMode
import com.example.drift.model.GameSettings
import com.example.drift.model.GarageUpgrades
import com.example.drift.model.GraphicsQuality
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.MidnightAsphalt
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SteelGray
import com.example.ui.theme.TitaniumWhite
import com.example.ui.theme.TougeLime
import java.util.Locale

@Composable
fun GarageOverlayScreen(
    uiState: TelemetryUiState,
    upgrades: GarageUpgrades,
    onPurchaseUpgrade: (String) -> Unit,
    onCycleBodyColor: () -> Unit,
    onCycleRimColor: () -> Unit,
    onBackToPlay: () -> Unit
) {
    BackHandler(onBack = onBackToPlay)

    val bodyColorName = CarPalette.bodyColors[upgrades.carColorIndex % CarPalette.bodyColors.size].first
    val rimColorName = CarPalette.rimColors[upgrades.rimColorIndex % CarPalette.rimColors.size].first

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonSurface.copy(alpha = 0.88f))
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GARAGE // KAZE RS-13 SHINOBI",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )
                Text(
                    text = "RWD Turbo Street Drift Spec • Credits: ${uiState.credits} CR",
                    style = MaterialTheme.typography.labelSmall,
                    color = DriftAmber
                )
            }

            HudActionChip(
                label = "DRIVE TRACK",
                accent = TougeLime,
                testTag = "btn_back_to_drive_from_garage",
                onClick = onBackToPlay
            )
        }

        // Paint & Rim Customization Row floating mid-screen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudActionChip(
                label = "PAINT: $bodyColorName",
                accent = NeonCyan,
                testTag = "btn_cycle_paint",
                onClick = onCycleBodyColor
            )
            HudActionChip(
                label = "RIMS: $rimColorName",
                accent = DriftAmber,
                testTag = "btn_cycle_rims",
                onClick = onCycleRimColor
            )
        }

        // Bottom Tuning Sheet: 6 Physics-Altering Upgrade Categories
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MidnightAsphalt.copy(alpha = 0.90f))
                .border(1.dp, Color(0xFF2D3A4F), RoundedCornerShape(14.dp))
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "PERFORMANCE TUNING (350 CR PER STAGE)",
                style = MaterialTheme.typography.labelSmall,
                color = SteelGray
            )

            GarageUpgradeRow(
                title = "ENGINE",
                effectText = "Torque +${(upgrades.engineLevel - 1) * 10}% (+10% / Lv)",
                level = upgrades.engineLevel,
                testTag = "upgrade_engine_btn",
                onUpgrade = { onPurchaseUpgrade("ENGINE") }
            )
            GarageUpgradeRow(
                title = "TURBO",
                effectText = "Boost Spool & Power +${(upgrades.turboLevel - 1) * 8}%",
                level = upgrades.turboLevel,
                testTag = "upgrade_turbo_btn",
                onUpgrade = { onPurchaseUpgrade("TURBO") }
            )
            GarageUpgradeRow(
                title = "TIRE",
                effectText = "Compound Grip +${(upgrades.tireLevel - 1) * 5}% (+5% / Lv)",
                level = upgrades.tireLevel,
                testTag = "upgrade_tire_btn",
                onUpgrade = { onPurchaseUpgrade("TIRE") }
            )
            GarageUpgradeRow(
                title = "SUSPENSION",
                effectText = "Weight Transfer Stability +${(upgrades.suspensionLevel - 1) * 12}%",
                level = upgrades.suspensionLevel,
                testTag = "upgrade_suspension_btn",
                onUpgrade = { onPurchaseUpgrade("SUSPENSION") }
            )
            GarageUpgradeRow(
                title = "BRAKE",
                effectText = "Front Load Bite +${(upgrades.brakeLevel - 1) * 9}%",
                level = upgrades.brakeLevel,
                testTag = "upgrade_brake_btn",
                onUpgrade = { onPurchaseUpgrade("BRAKE") }
            )
            GarageUpgradeRow(
                title = "STEERING ANGLE",
                effectText = "Max Lock ${upgrades.maxSteeringDegrees.toInt()}° (+5° / Lv)",
                level = upgrades.steeringAngleLevel,
                testTag = "upgrade_steering_btn",
                onUpgrade = { onPurchaseUpgrade("STEERING") }
            )
        }
    }
}

@Composable
private fun GarageUpgradeRow(
    title: String,
    effectText: String,
    level: Int,
    testTag: String,
    onUpgrade: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TitaniumWhite)
                Text("LV.$level/5", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
            }
            Text(effectText, style = MaterialTheme.typography.labelSmall, color = SteelGray)
        }

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(if (level < 5) NeonCyan.copy(alpha = 0.2f) else Color(0xFF1E293B))
                .border(1.dp, if (level < 5) NeonCyan else SteelGray, RoundedCornerShape(8.dp))
                .clickable(enabled = level < 5, onClick = onUpgrade)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (level < 5) "UPGRADE" else "MAXED",
                style = MaterialTheme.typography.labelSmall,
                color = if (level < 5) NeonCyan else SteelGray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SettingsOverlayScreen(
    settings: GameSettings,
    onUpdateSettings: ((GameSettings) -> GameSettings) -> Unit,
    onBackToPlay: () -> Unit
) {
    BackHandler(onBack = onBackToPlay)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightAsphalt.copy(alpha = 0.86f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CarbonSurface)
                .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SETTINGS // TELEMETRY & CONTROLS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NeonCyan
                )
                HudActionChip(
                    label = "RESUME",
                    accent = TougeLime,
                    testTag = "btn_close_settings",
                    onClick = onBackToPlay
                )
            }

            // DRIFT ASSIST (OFF / LOW / HIGH)
            Text("DRIFT ASSIST (${settings.driftAssist.description})", style = MaterialTheme.typography.labelSmall, color = SteelGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DriftAssistMode.entries.forEach { mode ->
                    val selected = settings.driftAssist == mode
                    HudActionChip(
                        label = "ASSIST: ${mode.label}",
                        accent = if (selected) DriftAmber else SteelGray,
                        testTag = "btn_assist_${mode.name.lowercase()}",
                        onClick = { onUpdateSettings { it.copy(driftAssist = mode) } }
                    )
                }
            }

            // GRAPHICS QUALITY (LOW / MEDIUM / HIGH)
            Text("GRAPHICS QUALITY", style = MaterialTheme.typography.labelSmall, color = SteelGray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphicsQuality.entries.forEach { q ->
                    val selected = settings.graphicsQuality == q
                    HudActionChip(
                        label = q.label,
                        accent = if (selected) NeonCyan else SteelGray,
                        testTag = "btn_graphics_${q.name.lowercase()}",
                        onClick = { onUpdateSettings { it.copy(graphicsQuality = q) } }
                    )
                }
            }

            // TRANSMISSION MODE (AUTO / MANUAL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TRANSMISSION SHIFTING", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                HudActionChip(
                    label = if (settings.autoTransmission) "AUTOMATIC" else "MANUAL",
                    accent = if (settings.autoTransmission) TougeLime else DriftAmber,
                    testTag = "btn_toggle_transmission",
                    onClick = { onUpdateSettings { it.copy(autoTransmission = !it.autoTransmission) } }
                )
            }

            SettingSliderRow(
                label = "Control Sensitivity",
                value = settings.controlSensitivity,
                valueRange = 0.5f..2.0f,
                display = String.format(Locale.US, "%.2fx", settings.controlSensitivity),
                onValueChange = { v -> onUpdateSettings { it.copy(controlSensitivity = v) } }
            )

            SettingSliderRow(
                label = "Steering Sensitivity",
                value = settings.steeringSensitivity,
                valueRange = 0.5f..2.0f,
                display = String.format(Locale.US, "%.2fx", settings.steeringSensitivity),
                onValueChange = { v -> onUpdateSettings { it.copy(steeringSensitivity = v) } }
            )

            SettingSliderRow(
                label = "Camera Sensitivity",
                value = settings.cameraSensitivity,
                valueRange = 0.5f..2.0f,
                display = String.format(Locale.US, "%.2fx", settings.cameraSensitivity),
                onValueChange = { v -> onUpdateSettings { it.copy(cameraSensitivity = v) } }
            )

            SettingSliderRow(
                label = "Engine & Tire Sound Volume",
                value = settings.soundVolume,
                valueRange = 0.0f..1.0f,
                display = "${(settings.soundVolume * 100).toInt()}%",
                onValueChange = { v -> onUpdateSettings { it.copy(soundVolume = v) } }
            )

            SettingSliderRow(
                label = "Synthwave BGM Volume",
                value = settings.musicVolume,
                valueRange = 0.0f..1.0f,
                display = "${(settings.musicVolume * 100).toInt()}%",
                onValueChange = { v -> onUpdateSettings { it.copy(musicVolume = v) } }
            )
        }
    }
}

@Composable
private fun SettingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TitaniumWhite)
            Text(display, style = MaterialTheme.typography.labelSmall, color = NeonCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = MidnightAsphalt
            )
        )
    }
}
