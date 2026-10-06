package com.example.drift.model

import androidx.compose.ui.graphics.Color

enum class DriftAssistMode(val label: String, val description: String) {
    OFF("OFF", "100% Manual RWD Physics"),
    LOW("LOW", "Subtle Counter-Steer Self-Alignment (Default)"),
    HIGH("HIGH", "Active Slip Stabilization & Angle Hold")
}

enum class CameraMode(val label: String, val distance: Float, val height: Float, val baseFov: Float) {
    NORMAL_CHASE("CAM 1: CHASE", 7.4f, 2.75f, 62f),
    CLOSER_CHASE("CAM 2: CLOSE", 5.3f, 1.95f, 68f),
    WIDE_CINEMATIC("CAM 3: WIDE", 10.2f, 4.10f, 56f)
}

enum class GraphicsQuality(
    val label: String,
    val maxParticles: Int,
    val maxSkidMarks: Int,
    val drawDistance: Float
) {
    LOW("LOW", 45, 90, 120f),
    MEDIUM("MEDIUM", 90, 180, 185f),
    HIGH("HIGH", 160, 280, 260f)
}

enum class SurfaceType(val label: String, val gripMultiplier: Float, val dragMultiplier: Float) {
    ASPHALT("ASPHALT", 1.00f, 1.0f),
    PAINT("CURB PAINT", 0.86f, 1.15f),
    GRASS("OFF-TRACK GRASS", 0.56f, 2.8f)
}

enum class ScreenMode {
    PLAY,
    GARAGE,
    SETTINGS
}

data class GarageUpgrades(
    val engineLevel: Int = 1,       // +10% torque per level (1..5)
    val turboLevel: Int = 1,        // +8% boost torque & faster RPM spool (1..5)
    val tireLevel: Int = 1,         // +5% tire grip per level (1..5)
    val suspensionLevel: Int = 1,   // Weight transfer stability & roll stiffness (1..5)
    val brakeLevel: Int = 1,        // +8% brake force & front load bite (1..5)
    val steeringAngleLevel: Int = 1,// +5 deg max steering lock per level (1..5)
    val carColorIndex: Int = 0,
    val rimColorIndex: Int = 0
) {
    val torqueMultiplier: Float
        get() = 1.0f + (engineLevel - 1) * 0.10f + (turboLevel - 1) * 0.08f

    val tireGripMultiplier: Float
        get() = 1.0f + (tireLevel - 1) * 0.05f

    val suspensionStiffness: Float
        get() = 1.0f + (suspensionLevel - 1) * 0.12f

    val brakeMultiplier: Float
        get() = 1.0f + (brakeLevel - 1) * 0.09f

    val maxSteeringDegrees: Float
        get() = 38f + (steeringAngleLevel - 1) * 5f
}

data class GameSettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val controlSensitivity: Float = 1.0f,
    val steeringSensitivity: Float = 1.0f,
    val driftAssist: DriftAssistMode = DriftAssistMode.LOW,
    val cameraSensitivity: Float = 1.0f,
    val soundVolume: Float = 0.75f,
    val musicVolume: Float = 0.45f,
    val autoTransmission: Boolean = true
)

object CarPalette {
    val bodyColors = listOf(
        "Fuji Pearl White" to Color(0xFFEAEFF5),
        "Shibuya Cyber Cyan" to Color(0xFF00D8F0),
        "Hakone Sunset Orange" to Color(0xFFFF6D00),
        "Midnight Touge Purple" to Color(0xFF7C4DFF),
        "Akina Crimson Red" to Color(0xFFFF1744),
        "Wangan Stealth Gunmetal" to Color(0xFF37474F)
    )

    val rimColors = listOf(
        "Bronze Volk Spec" to Color(0xFFD49A45),
        "Titanium Silver" to Color(0xFFCFD8DC),
        "Neon Drift Lime" to Color(0xFF00E676),
        "Matte Carbon Black" to Color(0xFF263238)
    )
}
