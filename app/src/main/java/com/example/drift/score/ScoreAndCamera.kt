package com.example.drift.score

import com.example.drift.math.Math3D
import com.example.drift.math.Vec3
import com.example.drift.model.CameraMode
import com.example.drift.model.SurfaceType
import com.example.drift.physics.Vehicle
import com.example.drift.track.CollisionResult
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class ScoreSystem {
    var totalScore: Int = 0
    var currentDriftScore: Float = 0f
    var comboMultiplier: Float = 1.0f
    var driftDurationSec: Float = 0f
    var idleDriftTimer: Float = 0f
    var ratingBanner: String = ""
    var ratingBannerTimer: Float = 0f
    var lastDriftDirection: Int = 0 // -1 left, +1 right
    var prevDriftAngle: Float = 0f
    var credits: Int = 2500 // Starter drift credits so player can immediately test garage upgrades too

    fun resetSession() {
        currentDriftScore = 0f
        comboMultiplier = 1.0f
        driftDurationSec = 0f
        idleDriftTimer = 0f
        lastDriftDirection = 0
        ratingBanner = ""
    }

    fun update(
        dt: Float,
        vehicle: Vehicle,
        surface: SurfaceType,
        collision: CollisionResult
    ) {
        if (ratingBannerTimer > 0f) {
            ratingBannerTimer = max(0f, ratingBannerTimer - dt)
            if (ratingBannerTimer == 0f) ratingBanner = ""
        }

        val absAngle = abs(vehicle.driftSystem.driftAngleDeg)
        val speedKmh = vehicle.speedKmh
        val isDrifting = vehicle.driftSystem.isDrifting

        // Barrier impact penalty
        if (collision.hitBarrier && collision.impactIntensity > 3.5f) {
            if (currentDriftScore > 0f) {
                // Hard crash cuts current combo in half or resets if severe
                if (collision.impactIntensity > 8.5f) {
                    currentDriftScore = 0f
                    comboMultiplier = 1.0f
                    driftDurationSec = 0f
                    setBanner("WALL TAP! COMBO LOST", 1.2f)
                } else {
                    comboMultiplier = max(1.0f, comboMultiplier - 0.5f)
                    currentDriftScore *= 0.65f
                    setBanner("BARRIER SCRAPE!", 0.9f)
                }
            }
        }

        if (isDrifting) {
            idleDriftTimer = 0f
            driftDurationSec += dt

            // Detect drift direction transition (left <-> right whip)
            val currentDir = if (vehicle.driftSystem.driftAngleDeg > 0f) 1 else -1
            if (lastDriftDirection != 0 && currentDir != lastDriftDirection && absAngle > 15f) {
                comboMultiplier = min(10.0f, comboMultiplier + 0.35f)
                currentDriftScore += 180f * comboMultiplier
                setBanner("TRANSITION! +180", 1.1f)
            }
            lastDriftDirection = currentDir

            // Smoothness factor (steady angle modulation scores higher than erratic jerking)
            val angleRate = abs(absAngle - prevDriftAngle) / max(0.001f, dt)
            val smoothnessFactor = Math3D.clamp(1.25f - (angleRate / 180f) * 0.4f, 0.7f, 1.25f)
            prevDriftAngle = absAngle

            // Surface multiplier (off-track grass reduces score rate as requested)
            val surfaceScoreFactor = when (surface) {
                SurfaceType.ASPHALT -> 1.0f
                SurfaceType.PAINT -> 1.05f
                SurfaceType.GRASS -> 0.35f
            }

            // Proximity & Clipping Zone bonuses
            val proximityFactor = (if (collision.nearBarrierProximity) 1.35f else 1.0f) *
                (if (collision.inClippingZone) 1.55f else 1.0f)

            // Combo buildup
            val comboRate = if (surface == SurfaceType.GRASS) 0.04f else 0.18f
            comboMultiplier = min(10.0f, comboMultiplier + comboRate * dt * proximityFactor)

            // Base points per second from Angle x Speed x Smoothness x Proximity
            val anglePoints = Math3D.clamp((absAngle - 10f) * 2.4f, 5f, 145f)
            val speedPoints = Math3D.clamp(speedKmh / 42f, 0.5f, 3.8f)
            val framePoints = anglePoints * speedPoints * smoothnessFactor * proximityFactor * surfaceScoreFactor * dt * 4.2f
            currentDriftScore += framePoints

            // Trigger callout badges ("GOOD", "GREAT", "PERFECT")
            when {
                collision.inClippingZone && absAngle > 24f -> setBanner("PERFECT CLIPPING!", 0.85f)
                absAngle >= 38f && speedKmh >= 65f -> setBanner("PERFECT DRIFT!!", 0.85f)
                absAngle >= 25f && speedKmh >= 45f -> if (ratingBanner.isEmpty() || ratingBanner == "GOOD DRIFT") {
                    setBanner("GREAT DRIFT!", 0.8f)
                }
                absAngle >= 14f -> if (ratingBanner.isEmpty()) {
                    setBanner("GOOD DRIFT", 0.75f)
                }
            }
        } else {
            // Grace window so transitions and brief straights don't instantly kill the combo
            if (currentDriftScore > 0f) {
                idleDriftTimer += dt
                if (idleDriftTimer > 1.85f) {
                    val banked = (currentDriftScore * comboMultiplier).toInt()
                    totalScore += banked
                    credits += max(15, banked / 18)
                    setBanner("BANKED +$banked PTS", 1.3f)
                    currentDriftScore = 0f
                    comboMultiplier = 1.0f
                    driftDurationSec = 0f
                    lastDriftDirection = 0
                }
            }
        }
    }

    private fun setBanner(text: String, duration: Float) {
        ratingBanner = text
        ratingBannerTimer = duration
    }
}

class CameraController {
    var mode: CameraMode = CameraMode.NORMAL_CHASE
    var camPos = Vec3(0f, 3f, -7f)
    var lookAtPos = Vec3(0f, 1f, 4f)
    var currentYaw: Float = 0f
    var currentFov: Float = 62f
    private var shakeTimer: Float = 0f

    fun cycleCameraMode(): CameraMode {
        mode = when (mode) {
            CameraMode.NORMAL_CHASE -> CameraMode.CLOSER_CHASE
            CameraMode.CLOSER_CHASE -> CameraMode.WIDE_CINEMATIC
            CameraMode.WIDE_CINEMATIC -> CameraMode.NORMAL_CHASE
        }
        return mode
    }

    fun snapToVehicle(vehicle: Vehicle) {
        currentYaw = vehicle.yaw
        val backX = -sin(currentYaw) * mode.distance
        val backZ = -cos(currentYaw) * mode.distance
        camPos.set(vehicle.posX + backX, vehicle.posY + mode.height, vehicle.posZ + backZ)
        lookAtPos.set(
            vehicle.posX + sin(currentYaw) * 4.5f,
            vehicle.posY + 0.9f,
            vehicle.posZ + cos(currentYaw) * 4.5f
        )
    }

    fun update(
        dt: Float,
        vehicle: Vehicle,
        cameraSensitivity: Float
    ) {
        val safeDt = Math3D.clamp(dt, 0.001f, 0.05f)
        shakeTimer += safeDt * (8f + vehicle.speedKmh * 0.18f)

        // Blend between car heading yaw and velocity direction so the camera reveals the drift angle naturally
        val driftRad = vehicle.driftSystem.driftAngleDeg * Math3D.DEG_TO_RAD
        val targetYaw = vehicle.yaw + driftRad * 0.42f
        val followSpeed = (5.2f + (vehicle.speedKmh / 120f) * 2.5f) * cameraSensitivity
        currentYaw = Math3D.lerpAngle(currentYaw, targetYaw, safeDt * followSpeed)

        // Dynamic FOV widens slightly with speed
        val speedFovBonus = Math3D.clamp((vehicle.speedKmh / 190f) * 11f, 0f, 12f)
        currentFov = Math3D.lerp(currentFov, mode.baseFov + speedFovBonus, safeDt * 6f)

        // Subtle camera vibration during high-speed drift or curb riding (never excessive)
        val slipShake = vehicle.driftSystem.rearWheelSlipRatio * (vehicle.speedKmh / 160f) * 0.045f
        val shakeOffsetX = sin(shakeTimer * 2.3f) * slipShake
        val shakeOffsetY = cos(shakeTimer * 3.1f) * slipShake * 0.7f

        val targetCamX = vehicle.posX - sin(currentYaw) * mode.distance + shakeOffsetX
        val targetCamY = vehicle.posY + mode.height + shakeOffsetY
        val targetCamZ = vehicle.posZ - cos(currentYaw) * mode.distance

        camPos.x = Math3D.lerp(camPos.x, targetCamX, safeDt * 12f)
        camPos.y = Math3D.lerp(camPos.y, targetCamY, safeDt * 10f)
        camPos.z = Math3D.lerp(camPos.z, targetCamZ, safeDt * 12f)

        // Look slightly ahead of the car along the blend of car nose and drift trajectory
        val lookYaw = vehicle.yaw + driftRad * 0.28f
        val targetLookX = vehicle.posX + sin(lookYaw) * 4.2f
        val targetLookY = vehicle.posY + 0.85f
        val targetLookZ = vehicle.posZ + cos(lookYaw) * 4.2f

        lookAtPos.x = Math3D.lerp(lookAtPos.x, targetLookX, safeDt * 15f)
        lookAtPos.y = Math3D.lerp(lookAtPos.y, targetLookY, safeDt * 15f)
        lookAtPos.z = Math3D.lerp(lookAtPos.z, targetLookZ, safeDt * 15f)
    }
}
