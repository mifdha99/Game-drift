package com.example.drift.input

import androidx.lifecycle.ViewModel
import com.example.drift.effects.AudioManager
import com.example.drift.effects.EffectsSystem
import com.example.drift.math.Math3D
import com.example.drift.model.CameraMode
import com.example.drift.model.DriftAssistMode
import com.example.drift.model.GameSettings
import com.example.drift.model.GarageUpgrades
import com.example.drift.model.GraphicsQuality
import com.example.drift.model.ScreenMode
import com.example.drift.model.SurfaceType
import com.example.drift.physics.Vehicle
import com.example.drift.render.CarAndSceneRenderer
import com.example.drift.score.CameraController
import com.example.drift.score.ScoreSystem
import com.example.drift.track.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class InputManager {
    var rawSteeringTouch: Float = 0f // -1.0 .. +1.0
    var isSteeringTouched: Boolean = false
    var smoothedSteering: Float = 0f

    var throttleValue: Float = 0f    // 0.0 .. 1.0
    var brakeValue: Float = 0f       // 0.0 .. 1.0
    var handbrakeActive: Boolean = false

    fun setAnalogSteering(value: Float, active: Boolean) {
        rawSteeringTouch = Math3D.clamp(value, -1f, 1f)
        isSteeringTouched = active
    }

    fun setAnalogThrottle(value: Float) {
        throttleValue = Math3D.clamp(value, 0f, 1f)
    }

    fun setAnalogBrake(value: Float) {
        brakeValue = Math3D.clamp(value, 0f, 1f)
    }

    fun setHandbrake(active: Boolean) {
        handbrakeActive = active
    }

    fun update(dt: Float, steeringSensitivity: Float, controlSensitivity: Float) {
        val targetSteer = if (isSteeringTouched) {
            Math3D.clamp(rawSteeringTouch * steeringSensitivity, -1f, 1f)
        } else {
            0f
        }
        val rate = if (isSteeringTouched) 14f * controlSensitivity else 9.5f
        smoothedSteering = Math3D.lerp(smoothedSteering, targetSteer, Math3D.clamp(dt * rate, 0f, 1f))
        if (!isSteeringTouched && abs(smoothedSteering) < 0.01f) {
            smoothedSteering = 0f
        }
    }
}

data class TelemetryUiState(
    val speedKmh: Int = 0,
    val rpm: Int = 1000,
    val gearLabel: String = "1",
    val driftAngleDeg: Float = 0f,
    val isDrifting: Boolean = false,
    val currentDriftScore: Int = 0,
    val totalScore: Int = 0,
    val comboMultiplier: Float = 1.0f,
    val ratingBanner: String = "",
    val credits: Int = 2500,
    val cameraMode: CameraMode = CameraMode.NORMAL_CHASE,
    val debugEnabled: Boolean = false,
    val screenMode: ScreenMode = ScreenMode.PLAY,
    // Debug telemetry fields
    val throttle: Float = 0f,
    val brake: Float = 0f,
    val steering: Float = 0f,
    val handbrake: Boolean = false,
    val slipAngleFront: Float = 0f,
    val slipAngleRear: Float = 0f,
    val lateralVelocity: Float = 0f,
    val longitudinalVelocity: Float = 0f,
    val yawRate: Float = 0f,
    val frontGrip: Float = 1f,
    val rearGrip: Float = 1f,
    val surface: SurfaceType = SurfaceType.ASPHALT,
    val fps: Int = 60
)

class GameManager : ViewModel() {
    val inputManager = InputManager()
    val vehicle = Vehicle()
    val track = Track()
    val scoreSystem = ScoreSystem()
    val cameraController = CameraController()
    val effectsSystem = EffectsSystem()
    val audioManager = AudioManager()
    val renderer = CarAndSceneRenderer()

    private val _upgrades = MutableStateFlow(GarageUpgrades())
    val upgrades: StateFlow<GarageUpgrades> = _upgrades.asStateFlow()

    private val _settings = MutableStateFlow(GameSettings())
    val settings: StateFlow<GameSettings> = _settings.asStateFlow()

    private val _uiState = MutableStateFlow(TelemetryUiState())
    val uiState: StateFlow<TelemetryUiState> = _uiState.asStateFlow()

    private var physicsAccumulator = 0f
    private val fixedTimeStep = 1f / 120f
    private var uiRefreshTimer = 0f
    private var smoothedFps = 60f
    var garageTurntableYaw = 0.55f

    init {
        resetCarToCheckpoint(fullReset = true)
        audioManager.start()
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.stop()
    }

    /**
     * Master Fixed-Timestep Game Loop:
     * INPUT -> VEHICLE PHYSICS -> DRIFT CALCULATION -> COLLISION -> SCORE -> CAMERA -> EFFECTS -> UI -> RENDER
     */
    fun stepFrame(rawDeltaSec: Float) {
        val dt = Math3D.clamp(rawDeltaSec, 0.001f, 0.05f)
        val currentFps = 1f / dt
        smoothedFps = Math3D.lerp(smoothedFps, currentFps, 0.08f)

        val currentScreen = _uiState.value.screenMode
        val cfg = _settings.value
        val upg = _upgrades.value

        if (currentScreen == ScreenMode.GARAGE) {
            garageTurntableYaw = (garageTurntableYaw + dt * 0.65f) % (2f * Math.PI.toFloat())
            audioManager.isPaused = true
            return
        }
        audioManager.isPaused = (currentScreen != ScreenMode.PLAY)

        // 1. INPUT
        inputManager.update(dt, cfg.steeringSensitivity, cfg.controlSensitivity)

        // 2. FIXED-TIMESTEP VEHICLE PHYSICS + DRIFT CALCULATION + COLLISION + SCORE
        physicsAccumulator += dt
        var subSteps = 0
        var latestSurface = vehicle.currentSurface
        var latestCollision = com.example.drift.track.CollisionResult()

        while (physicsAccumulator >= fixedTimeStep && subSteps < 6) {
            physicsAccumulator -= fixedTimeStep
            subSteps++

            val eval = track.evaluateSurfaceAndCollisions(fixedTimeStep, vehicle)
            latestSurface = eval.first
            latestCollision = eval.second

            vehicle.stepPhysics(
                dt = fixedTimeStep,
                rawSteer = inputManager.smoothedSteering,
                rawThrottle = inputManager.throttleValue,
                rawBrake = inputManager.brakeValue,
                handbrake = inputManager.handbrakeActive,
                assistMode = cfg.driftAssist,
                autoShift = cfg.autoTransmission,
                upgrades = upg,
                surface = latestSurface
            )

            scoreSystem.update(
                dt = fixedTimeStep,
                vehicle = vehicle,
                surface = latestSurface,
                collision = latestCollision
            )
        }

        // 3. CAMERA
        cameraController.update(dt, vehicle, cfg.cameraSensitivity)

        // 4. EFFECTS & AUDIO
        effectsSystem.update(dt, vehicle, latestSurface, latestCollision, cfg.graphicsQuality)
        audioManager.targetRpm = vehicle.engine.rpm
        audioManager.targetThrottle = inputManager.throttleValue
        audioManager.targetTireSlip = vehicle.driftSystem.rearWheelSlipRatio
        audioManager.soundVolume = cfg.soundVolume
        audioManager.musicVolume = cfg.musicVolume

        // 5. UI TELEMETRY SNAPSHOT (throttled ~30Hz for ultra-smooth Compose UI)
        uiRefreshTimer += dt
        if (uiRefreshTimer >= 0.032f) {
            uiRefreshTimer = 0f
            publishTelemetryState()
        }
    }

    private fun publishTelemetryState() {
        _uiState.value = _uiState.value.copy(
            speedKmh = vehicle.speedKmh.toInt(),
            rpm = vehicle.engine.rpm.toInt(),
            gearLabel = vehicle.transmission.getGearDisplay(),
            driftAngleDeg = vehicle.driftSystem.driftAngleDeg,
            isDrifting = vehicle.driftSystem.isDrifting,
            currentDriftScore = scoreSystem.currentDriftScore.toInt(),
            totalScore = scoreSystem.totalScore,
            comboMultiplier = scoreSystem.comboMultiplier,
            ratingBanner = scoreSystem.ratingBanner,
            credits = scoreSystem.credits,
            cameraMode = cameraController.mode,
            throttle = vehicle.throttleInput,
            brake = vehicle.brakeInput,
            steering = vehicle.steeringInput,
            handbrake = vehicle.handbrakeInput,
            slipAngleFront = vehicle.driftSystem.slipAngleFrontDeg,
            slipAngleRear = vehicle.driftSystem.slipAngleRearDeg,
            lateralVelocity = vehicle.lateralVelocity,
            longitudinalVelocity = vehicle.longitudinalVelocity,
            yawRate = vehicle.yawRate,
            frontGrip = vehicle.driftSystem.frontGripRatio,
            rearGrip = vehicle.driftSystem.rearGripRatio,
            surface = vehicle.currentSurface,
            fps = smoothedFps.toInt().coerceIn(1, 120)
        )
    }

    fun resetCarToCheckpoint(fullReset: Boolean = false) {
        if (fullReset) {
            track.lastCheckpointSegment = 0
            track.resetCones()
            effectsSystem.clear()
        }
        val (cx, cz, heading) = track.getCheckpointTransform()
        vehicle.resetToTransform(cx, cz, heading)
        cameraController.snapToVehicle(vehicle)
        scoreSystem.resetSession()
        publishTelemetryState()
    }

    fun shiftGearUp() {
        vehicle.transmission.shiftUp()
        publishTelemetryState()
    }

    fun shiftGearDown() {
        vehicle.transmission.shiftDown()
        publishTelemetryState()
    }

    fun cycleCamera() {
        cameraController.cycleCameraMode()
        publishTelemetryState()
    }

    fun toggleDebugPanel() {
        _uiState.value = _uiState.value.copy(debugEnabled = !_uiState.value.debugEnabled)
    }

    fun setScreenMode(mode: ScreenMode) {
        _uiState.value = _uiState.value.copy(screenMode = mode)
    }

    // Garage upgrade actions
    fun purchaseUpgrade(category: String): Boolean {
        val current = _upgrades.value
        val cost = 350
        if (scoreSystem.credits < cost) return false

        val updated = when (category) {
            "ENGINE" -> if (current.engineLevel < 5) current.copy(engineLevel = current.engineLevel + 1) else return false
            "TURBO" -> if (current.turboLevel < 5) current.copy(turboLevel = current.turboLevel + 1) else return false
            "TIRE" -> if (current.tireLevel < 5) current.copy(tireLevel = current.tireLevel + 1) else return false
            "SUSPENSION" -> if (current.suspensionLevel < 5) current.copy(suspensionLevel = current.suspensionLevel + 1) else return false
            "BRAKE" -> if (current.brakeLevel < 5) current.copy(brakeLevel = current.brakeLevel + 1) else return false
            "STEERING" -> if (current.steeringAngleLevel < 5) current.copy(steeringAngleLevel = current.steeringAngleLevel + 1) else return false
            else -> return false
        }
        scoreSystem.credits -= cost
        _upgrades.value = updated
        publishTelemetryState()
        return true
    }

    fun cycleCarColor() {
        val cur = _upgrades.value
        _upgrades.value = cur.copy(carColorIndex = (cur.carColorIndex + 1) % 6)
    }

    fun cycleRimColor() {
        val cur = _upgrades.value
        _upgrades.value = cur.copy(rimColorIndex = (cur.rimColorIndex + 1) % 4)
    }

    // Settings update actions
    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        _settings.value = transform(_settings.value)
    }
}
