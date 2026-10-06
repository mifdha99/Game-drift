package com.example.drift.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drift.input.GameManager
import com.example.drift.input.TelemetryUiState
import com.example.drift.math.Math3D
import com.example.drift.model.ScreenMode
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DriftAmber
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MidnightAsphalt
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.SteelGray
import com.example.ui.theme.TitaniumWhite
import com.example.ui.theme.TougeLime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun ProjectDriftMainScreen(
    gameManager: GameManager,
    modifier: Modifier = Modifier
) {
    val uiState by gameManager.uiState.collectAsState()
    val upgrades by gameManager.upgrades.collectAsState()
    val settings by gameManager.settings.collectAsState()

    var frameTick by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // Continuous 60fps requestAnimationFrame-equivalent game loop using withFrameNanos
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        var lastTimeNanos = 0L
        while (true) {
            withFrameNanos { nowNanos ->
                val dt = if (lastTimeNanos == 0L) {
                    1f / 60f
                } else {
                    ((nowNanos - lastTimeNanos) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
                }
                lastTimeNanos = nowNanos
                gameManager.stepFrame(dt)
                frameTick++
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightAsphalt)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown
                when (event.key) {
                    Key.W, Key.DirectionUp -> {
                        gameManager.inputManager.setAnalogThrottle(if (down) 1f else 0f)
                        true
                    }
                    Key.S, Key.DirectionDown -> {
                        gameManager.inputManager.setAnalogBrake(if (down) 1f else 0f)
                        true
                    }
                    Key.A, Key.DirectionLeft -> {
                        gameManager.inputManager.setAnalogSteering(if (down) -1f else 0f, down)
                        true
                    }
                    Key.D, Key.DirectionRight -> {
                        gameManager.inputManager.setAnalogSteering(if (down) 1f else 0f, down)
                        true
                    }
                    Key.Spacebar -> {
                        gameManager.inputManager.setHandbrake(down)
                        true
                    }
                    else -> false
                }
            }
    ) {
        // 3D Low-Poly Scene Canvas (reads frameTick so it redraws every frame)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("drift_3d_viewport")
        ) {
            val unusedTick = frameTick
            if (uiState.screenMode == ScreenMode.GARAGE) {
                gameManager.renderer.renderGarageScene(
                    drawScope = this,
                    turntableYaw = gameManager.garageTurntableYaw,
                    upgrades = upgrades
                )
            } else {
                gameManager.renderer.renderDrivingScene(
                    drawScope = this,
                    vehicle = gameManager.vehicle,
                    track = gameManager.track,
                    camera = gameManager.cameraController,
                    effects = gameManager.effectsSystem,
                    upgrades = upgrades,
                    quality = settings.graphicsQuality
                )
            }
        }

        // Foreground UI Layer based on ScreenMode
        when (uiState.screenMode) {
            ScreenMode.PLAY -> {
                DrivingHudAndControlsOverlay(
                    uiState = uiState,
                    onShiftUp = { gameManager.shiftGearUp() },
                    onShiftDown = { gameManager.shiftGearDown() },
                    onCycleCamera = { gameManager.cycleCamera() },
                    onResetCar = { gameManager.resetCarToCheckpoint(fullReset = false) },
                    onToggleDebug = { gameManager.toggleDebugPanel() },
                    onOpenGarage = { gameManager.setScreenMode(ScreenMode.GARAGE) },
                    onOpenSettings = { gameManager.setScreenMode(ScreenMode.SETTINGS) },
                    onSteerChange = { value, active ->
                        gameManager.inputManager.setAnalogSteering(value, active)
                    },
                    onThrottleChange = { value ->
                        gameManager.inputManager.setAnalogThrottle(value)
                    },
                    onBrakeChange = { value ->
                        gameManager.inputManager.setAnalogBrake(value)
                    },
                    onHandbrakeChange = { active ->
                        gameManager.inputManager.setHandbrake(active)
                    }
                )
            }
            ScreenMode.GARAGE -> {
                GarageOverlayScreen(
                    uiState = uiState,
                    upgrades = upgrades,
                    onPurchaseUpgrade = { gameManager.purchaseUpgrade(it) },
                    onCycleBodyColor = { gameManager.cycleCarColor() },
                    onCycleRimColor = { gameManager.cycleRimColor() },
                    onBackToPlay = { gameManager.setScreenMode(ScreenMode.PLAY) }
                )
            }
            ScreenMode.SETTINGS -> {
                SettingsOverlayScreen(
                    settings = settings,
                    onUpdateSettings = { gameManager.updateSettings(it) },
                    onBackToPlay = { gameManager.setScreenMode(ScreenMode.PLAY) }
                )
            }
        }
    }
}

@Composable
private fun DrivingHudAndControlsOverlay(
    uiState: TelemetryUiState,
    onShiftUp: () -> Unit,
    onShiftDown: () -> Unit,
    onCycleCamera: () -> Unit,
    onResetCar: () -> Unit,
    onToggleDebug: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenSettings: () -> Unit,
    onSteerChange: (Float, Boolean) -> Unit,
    onThrottleChange: (Float) -> Unit,
    onBrakeChange: (Float) -> Unit,
    onHandbrakeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP SECTION: Action Strip + Minimalist Drift HUD + Optional Debug Panel
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Top Utility & Mode Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Title + Garage / Settings buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudActionChip(
                        label = "GARAGE",
                        accent = NeonCyan,
                        testTag = "btn_open_garage",
                        onClick = onOpenGarage
                    )
                    HudActionChip(
                        label = "SETTINGS",
                        accent = SteelGray,
                        testTag = "btn_open_settings",
                        onClick = onOpenSettings
                    )
                }

                // Camera, Reset, Debug buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudActionChip(
                        label = uiState.cameraMode.label,
                        accent = NeonCyan,
                        testTag = "btn_camera_switch",
                        onClick = onCycleCamera
                    )
                    HudActionChip(
                        label = "RESET",
                        accent = DriftAmber,
                        testTag = "btn_reset_car",
                        onClick = onResetCar
                    )
                    HudActionChip(
                        label = if (uiState.debugEnabled) "DBG ON" else "DEBUG",
                        accent = if (uiState.debugEnabled) TougeLime else SteelGray,
                        testTag = "btn_toggle_debug",
                        onClick = onToggleDebug
                    )
                }
            }

            // Minimalist Street Drift Telemetry HUD (SPEED, GEAR, RPM, ANGLE, SCORE, COMBO)
            StreetDriftTelemetryHud(uiState = uiState)

            // Drift Callout Banner ("GOOD DRIFT", "GREAT DRIFT!", "PERFECT DRIFT!!")
            AnimatedVisibility(
                visible = uiState.ratingBanner.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    color = CarbonSurface.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.5.dp, DriftAmber, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = uiState.ratingBanner,
                        style = MaterialTheme.typography.titleMedium,
                        color = DriftAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Optional Live Physics Debug Panel
            if (uiState.debugEnabled) {
                PhysicsDebugPanel(uiState = uiState)
            }
        }

        // BOTTOM SECTION: Two-Thumb Mobile Touch Controls
        // Left: Analog Virtual Steering Wheel + Gear Up/Down
        // Right: Large Throttle Pedal, Brake Pedal below/beside, Handbrake next to Brake
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // LEFT THUMB ZONE: Gear Shift Buttons + Analog Virtual Steering Wheel
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudActionChip(
                        label = "GEAR -",
                        accent = SteelGray,
                        testTag = "btn_gear_down",
                        onClick = onShiftDown
                    )
                    HudActionChip(
                        label = "GEAR +",
                        accent = NeonCyan,
                        testTag = "btn_gear_up",
                        onClick = onShiftUp
                    )
                }

                AnalogVirtualSteeringWheel(
                    currentSteer = uiState.steering,
                    onSteerChange = onSteerChange,
                    modifier = Modifier
                        .size(148.dp)
                        .testTag("steering_wheel_control")
                )
            }

            // RIGHT THUMB ZONE: Handbrake + Brake + Large Analog Throttle Pedal
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Handbrake + Brake Column
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Handbrake (E-BRAKE) button
                    HandbrakeTouchButton(
                        isActive = uiState.handbrake,
                        onStateChange = onHandbrakeChange,
                        modifier = Modifier
                            .width(86.dp)
                            .height(58.dp)
                            .testTag("handbrake_button")
                    )

                    // Analog Brake Pedal
                    AnalogPedalControl(
                        label = "BRAKE",
                        currentValue = uiState.brake,
                        activeColor = RedlineCrimson,
                        onValueChange = onBrakeChange,
                        modifier = Modifier
                            .width(86.dp)
                            .height(78.dp)
                            .testTag("brake_button")
                    )
                }

                // Large Analog Throttle Pedal
                AnalogPedalControl(
                    label = "THROTTLE",
                    currentValue = uiState.throttle,
                    activeColor = NeonCyan,
                    onValueChange = onThrottleChange,
                    modifier = Modifier
                        .width(84.dp)
                        .height(146.dp)
                        .testTag("throttle_button")
                )
            }
        }
    }
}

@Composable
private fun StreetDriftTelemetryHud(uiState: TelemetryUiState) {
    val absAngle = abs(uiState.driftAngleDeg).toInt()
    val rpmFraction = ((uiState.rpm - 1000f) / 7000f).coerceIn(0f, 1f)
    val isRedline = uiState.rpm >= 7300

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface.copy(alpha = 0.82f))
            .border(1.dp, Color(0xFF283447), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // RPM Tachometer Bar (1000 - 8000 RPM)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${uiState.rpm} RPM",
                style = MaterialTheme.typography.labelSmall,
                color = if (isRedline) RedlineCrimson else SteelGray,
                modifier = Modifier.width(68.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MidnightAsphalt)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(rpmFraction)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeonCyan, TougeLime, DriftAmber, RedlineCrimson)
                            )
                        )
                )
            }
            Text(
                text = uiState.surface.label,
                style = MaterialTheme.typography.labelSmall,
                color = when (uiState.surface) {
                    com.example.drift.model.SurfaceType.ASPHALT -> TougeLime
                    com.example.drift.model.SurfaceType.PAINT -> DriftAmber
                    com.example.drift.model.SurfaceType.GRASS -> RedlineCrimson
                }
            )
        }

        // Primary Metrics Row: SPEED | GEAR | ANGLE | SCORE | COMBO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SPEED
            Column {
                Text("SPEED", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.US, "%3d", uiState.speedKmh),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TitaniumWhite,
                        modifier = Modifier.testTag("hud_speed_value")
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("KM/H", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                }
            }

            // GEAR
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GEAR", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background( if (isRedline) RedlineCrimson.copy(alpha = 0.28f) else MidnightAsphalt)
                        .border(1.dp, if (isRedline) RedlineCrimson else NeonCyan, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.gearLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (isRedline) RedlineCrimson else NeonCyan,
                        modifier = Modifier.testTag("hud_gear_value")
                    )
                }
            }

            // DRIFT ANGLE
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ANGLE", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                Text(
                    text = "${absAngle}°",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (uiState.isDrifting) DriftAmber else TitaniumWhite,
                    modifier = Modifier.testTag("hud_angle_value")
                )
            }

            // DRIFT SCORE + TOTAL SCORE
            Column(horizontalAlignment = Alignment.End) {
                Text("SCORE", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                Text(
                    text = String.format(
                        Locale.US,
                        "%,d",
                        uiState.totalScore + (uiState.currentDriftScore * uiState.comboMultiplier).toInt()
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (uiState.currentDriftScore > 0) DriftAmber else TitaniumWhite,
                    modifier = Modifier.testTag("hud_score_value")
                )
            }

            // COMBO MULTIPLIER
            Column(horizontalAlignment = Alignment.End) {
                Text("COMBO", style = MaterialTheme.typography.labelSmall, color = SteelGray)
                Text(
                    text = String.format(Locale.US, "x%.1f", uiState.comboMultiplier),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (uiState.comboMultiplier > 1.05f) TougeLime else SteelGray,
                    modifier = Modifier.testTag("hud_combo_value")
                )
            }
        }
    }
}

@Composable
private fun PhysicsDebugPanel(uiState: TelemetryUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MidnightAsphalt.copy(alpha = 0.88f))
            .border(1.dp, TougeLime.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("debug_telemetry_panel"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "LIVE RWD VEHICLE TELEMETRY (${uiState.fps} FPS)",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TougeLime
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                DebugLine("Speed", "${uiState.speedKmh} km/h")
                DebugLine("RPM / Gear", "${uiState.rpm} / G:${uiState.gearLabel}")
                DebugLine("Throttle", String.format(Locale.US, "%.2f", uiState.throttle))
                DebugLine("Brake / HB", String.format(Locale.US, "%.2f / %s", uiState.brake, if (uiState.handbrake) "ON" else "OFF"))
                DebugLine("Steering", String.format(Locale.US, "%+.2f", uiState.steering))
            }
            Column {
                DebugLine("Drift Angle", String.format(Locale.US, "%+.1f°", uiState.driftAngleDeg))
                DebugLine("Slip F / R", String.format(Locale.US, "%.1f° / %.1f°", uiState.slipAngleFront, uiState.slipAngleRear))
                DebugLine("Vel Long/Lat", String.format(Locale.US, "%.1f / %.1f m/s", uiState.longitudinalVelocity, uiState.lateralVelocity))
                DebugLine("Yaw Rate", String.format(Locale.US, "%+.2f rad/s", uiState.yawRate))
                DebugLine("Grip F/R", String.format(Locale.US, "%.2f / %.2f", uiState.frontGrip, uiState.rearGrip))
            }
        }
        DebugLine("Active Drift Score", "${uiState.currentDriftScore} pts (Total: ${uiState.totalScore})")
    }
}

@Composable
private fun DebugLine(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$key:",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = SteelGray
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TitaniumWhite
        )
    }
}

@Composable
private fun AnalogVirtualSteeringWheel(
    currentSteer: Float,
    onSteerChange: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent()
                    val firstChange = down.changes.firstOrNull() ?: return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val centerX = w * 0.5f

                    fun computeSteer(pos: Offset): Float {
                        // Horizontal displacement normalized across wheel radius with slight angular curve
                        val dx = (pos.x - centerX) / (w * 0.42f)
                        return Math3D.clamp(dx, -1f, 1f)
                    }

                    onSteerChange(computeSteer(firstChange.position), true)

                    while (true) {
                        val event = awaitPointerEvent()
                        val activeChange = event.changes.firstOrNull { it.pressed }
                        if (activeChange == null) {
                            onSteerChange(0f, false)
                            break
                        } else {
                            activeChange.consume()
                            onSteerChange(computeSteer(activeChange.position), true)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.44f
            val center = Offset(size.width * 0.5f, size.height * 0.5f)
            val rotationDeg = currentSteer * 135f

            // Outer reference ring
            drawCircle(
                color = CarbonSurface.copy(alpha = 0.72f),
                radius = radius * 1.06f,
                center = center
            )

            rotate(degrees = rotationDeg, pivot = center) {
                // Deep-dish steering wheel rim
                drawCircle(
                    color = Color(0xFF1E2636),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 14.dp.toPx())
                )
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.75f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Top yellow/cyan center marker stripe on the wheel rim
                drawLine(
                    color = DriftAmber,
                    start = Offset(center.x, center.y - radius - 7.dp.toPx()),
                    end = Offset(center.x, center.y - radius + 7.dp.toPx()),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 3-Spoke JDM Drift Wheel Spokes
                val spokeColor = Color(0xFF94A3B8)
                drawLine(
                    color = spokeColor,
                    start = Offset(center.x - radius * 0.88f, center.y + radius * 0.15f),
                    end = center,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = spokeColor,
                    start = Offset(center.x + radius * 0.88f, center.y + radius * 0.15f),
                    end = center,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = spokeColor,
                    start = Offset(center.x, center.y + radius * 0.90f),
                    end = center,
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center horn button
                drawCircle(
                    color = MidnightAsphalt,
                    radius = radius * 0.26f,
                    center = center
                )
                drawCircle(
                    color = DriftAmber,
                    radius = radius * 0.10f,
                    center = center
                )
            }
        }

        Text(
            text = String.format(Locale.US, "%+.0f%%", currentSteer * 100f),
            style = MaterialTheme.typography.labelSmall,
            color = TitaniumWhite,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(MidnightAsphalt.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AnalogPedalControl(
    label: String,
    currentValue: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonSurface.copy(alpha = 0.82f))
            .border(
                width = if (currentValue > 0.05f) 2.dp else 1.dp,
                color = if (currentValue > 0.05f) activeColor else Color(0xFF334155),
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent()
                    val first = down.changes.firstOrNull() ?: return@awaitEachGesture
                    val h = size.height.toFloat().coerceAtLeast(1f)

                    fun computeAnalogFromY(y: Float): Float {
                        // Touching anywhere activates at least 0.35, dragging toward top reaches 1.0,
                        // or tapping upper 70% gives full 1.0 while bottom edge allows fine modulation
                        val ratio = 1f - (y / h)
                        return Math3D.clamp(0.35f + ratio * 0.85f, 0.25f, 1.0f)
                    }

                    onValueChange(computeAnalogFromY(first.position.y))

                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.firstOrNull { it.pressed }
                        if (active == null) {
                            onValueChange(0f)
                            break
                        } else {
                            active.consume()
                            onValueChange(computeAnalogFromY(active.position.y))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Analog fill gauge rising from bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(currentValue.coerceIn(0f, 1f))
                .align(Alignment.BottomCenter)
                .background(activeColor.copy(alpha = 0.36f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (currentValue > 0.05f) activeColor else TitaniumWhite
            )
            Text(
                text = "${(currentValue * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = SteelGray
            )
        }
    }
}

@Composable
private fun HandbrakeTouchButton(
    isActive: Boolean,
    onStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) DriftAmber.copy(alpha = 0.35f) else CarbonSurface.copy(alpha = 0.85f))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) DriftAmber else Color(0xFF475569),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitPointerEvent()
                    onStateChange(true)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            onStateChange(false)
                            break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "E-BRAKE",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) DriftAmber else TitaniumWhite,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isActive) "LOCKED" else "PULL",
                fontSize = 9.sp,
                fontFamily = JetBrainsMonoFamily,
                color = if (isActive) DriftAmber else SteelGray
            )
        }
    }
}

@Composable
fun HudActionChip(
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(CarbonSurface.copy(alpha = 0.86f))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
