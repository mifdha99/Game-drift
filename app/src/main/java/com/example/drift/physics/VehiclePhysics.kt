package com.example.drift.physics

import com.example.drift.math.Math3D
import com.example.drift.model.DriftAssistMode
import com.example.drift.model.GarageUpgrades
import com.example.drift.model.SurfaceType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DriftSystem {
    var driftAngleDeg: Float = 0f
    var slipAngleFrontDeg: Float = 0f
    var slipAngleRearDeg: Float = 0f
    var isDrifting: Boolean = false
    var rearWheelSlipRatio: Float = 0f
    var frontGripRatio: Float = 1f
    var rearGripRatio: Float = 1f

    fun evaluateDriftState(
        longitudinalVel: Float,
        lateralVel: Float,
        speedMps: Float,
        wheelSpinSlip: Float,
        handbrake: Boolean,
        surface: SurfaceType
    ) {
        driftAngleDeg = if (speedMps > 1.5f && longitudinalVel > -0.5f) {
            atan2(-lateralVel, max(1.0f, abs(longitudinalVel))) * Math3D.RAD_TO_DEG
        } else {
            0f
        }

        rearWheelSlipRatio = Math3D.clamp(
            max(wheelSpinSlip, (abs(driftAngleDeg) / 48f) * 0.65f + (if (handbrake) 0.90f else 0f)),
            0f,
            1f
        )

        rearGripRatio = Math3D.clamp(
            surface.gripMultiplier * (1f - rearWheelSlipRatio * 0.58f),
            0.20f,
            1.2f
        )
        frontGripRatio = Math3D.clamp(
            surface.gripMultiplier * (1f - (abs(slipAngleFrontDeg) / 45f) * 0.20f),
            0.50f,
            1.2f
        )

        isDrifting = speedMps > 4.5f && longitudinalVel > 1.2f && abs(driftAngleDeg) > 10.0f
    }
}

class Vehicle(
    val engine: Engine = Engine(),
    val transmission: Transmission = Transmission(),
    val tire: Tire = Tire(),
    val driftSystem: DriftSystem = DriftSystem()
) {
    val massKg = 1180f
    val yawInertia = 1260f
    val wheelbase = 2.52f
    val cgToFront = 1.18f
    val cgToRear = 1.34f
    val cgHeight = 0.47f
    val wheelRadius = 0.32f

    var posX: Float = 0f
    var posY: Float = 0f
    var posZ: Float = 0f
    var yaw: Float = 0f

    var velX: Float = 0f
    var velZ: Float = 0f

    var longitudinalVelocity: Float = 0f
    var lateralVelocity: Float = 0f
    var speedMps: Float = 0f
    var speedKmh: Float = 0f
    var yawRate: Float = 0f
    var yawTorque: Float = 0f

    var frontAxleLoadN: Float = massKg * 9.81f * 0.53f
    var rearAxleLoadN: Float = massKg * 9.81f * 0.47f
    var pitchAngleRad: Float = 0f
    var rollAngleRad: Float = 0f

    var steeringInput: Float = 0f
    var effectiveSteerRad: Float = 0f
    var throttleInput: Float = 0f
    var brakeInput: Float = 0f
    var handbrakeInput: Boolean = false

    var frontWheelRotationRad: Float = 0f
    var rearWheelRotationRad: Float = 0f
    var rearWheelAngularVel: Float = 0f
    var engineTorqueNm: Float = 0f
    var currentSurface: SurfaceType = SurfaceType.ASPHALT

    fun resetToTransform(x: Float, z: Float, headingYaw: Float) {
        posX = x
        posY = 0f
        posZ = z
        yaw = headingYaw
        velX = 0f
        velZ = 0f
        longitudinalVelocity = 0f
        lateralVelocity = 0f
        speedMps = 0f
        speedKmh = 0f
        yawRate = 0f
        yawTorque = 0f
        pitchAngleRad = 0f
        rollAngleRad = 0f
        rearWheelAngularVel = 0f
        engine.rpm = engine.idleRpm
        transmission.currentGear = 1
        driftSystem.driftAngleDeg = 0f
        driftSystem.isDrifting = false
        driftSystem.rearWheelSlipRatio = 0f
    }

    fun stepPhysics(
        dt: Float,
        rawSteer: Float,
        rawThrottle: Float,
        rawBrake: Float,
        handbrake: Boolean,
        assistMode: DriftAssistMode,
        autoShift: Boolean,
        upgrades: GarageUpgrades,
        surface: SurfaceType
    ) {
        val safeDt = Math3D.clamp(dt, 0.001f, 0.035f)
        currentSurface = surface

        steeringInput = Math3D.clamp(rawSteer, -1f, 1f)
        throttleInput = Math3D.clamp(rawThrottle, 0f, 1f)
        brakeInput = Math3D.clamp(rawBrake, 0f, 1f)
        handbrakeInput = handbrake

        val forwardX = sin(yaw)
        val forwardZ = cos(yaw)
        val rightX = cos(yaw)
        val rightZ = -sin(yaw)

        longitudinalVelocity = velX * forwardX + velZ * forwardZ
        lateralVelocity = velX * rightX + velZ * rightZ
        speedMps = sqrt(velX * velX + velZ * velZ)
        speedKmh = speedMps * 3.6f

        val velocitySlipRad = if (speedMps > 1.2f && longitudinalVelocity > -0.5f) {
            atan2(lateralVelocity, max(1.0f, abs(longitudinalVelocity)))
        } else {
            0f
        }

        val maxSteerRad = upgrades.maxSteeringDegrees * Math3D.DEG_TO_RAD
        val speedSteerFactor = 1f - Math3D.clamp(speedKmh / 250f, 0f, 0.25f)
        val manualSteerRad = steeringInput * maxSteerRad * speedSteerFactor

        val isPlayerInitiatingTurn = abs(steeringInput) > 0.35f && (steeringInput * velocitySlipRad <= 0.05f)
        val assistCounterSteer = if (isPlayerInitiatingTurn) {
            0f
        } else {
            when (assistMode) {
                DriftAssistMode.OFF -> 0f
                DriftAssistMode.LOW -> if (abs(velocitySlipRad) > 0.16f && longitudinalVelocity > 2.5f) {
                    velocitySlipRad * 0.32f * (1f - abs(steeringInput) * 0.45f)
                } else 0f
                DriftAssistMode.HIGH -> if (abs(velocitySlipRad) > 0.12f && longitudinalVelocity > 2.0f) {
                    velocitySlipRad * 0.60f * (1f - abs(steeringInput) * 0.30f)
                } else 0f
            }
        }
        val targetSteerRad = Math3D.clamp(manualSteerRad + assistCounterSteer, -maxSteerRad, maxSteerRad)
        effectiveSteerRad = Math3D.lerp(effectiveSteerRad, targetSteerRad, safeDt * 20f)

        if (autoShift) {
            transmission.updateAutomatic(
                dt = safeDt,
                rpm = engine.rpm,
                forwardSpeedMps = longitudinalVelocity,
                throttle = throttleInput,
                brake = brakeInput,
                isDrifting = driftSystem.isDrifting,
                wheelSpinRatio = driftSystem.rearWheelSlipRatio
            )
        }

        val totalGearRatio = transmission.getCurrentRatio()
        val absGearRatio = max(1f, abs(totalGearRatio))
        val roadWheelAngularVel = longitudinalVelocity / wheelRadius
        val wheelDrivenRpm = abs(roadWheelAngularVel * totalGearRatio) * (60f / (2f * Math.PI.toFloat()))

        val clutchEngagement = if (handbrakeInput || speedMps < 3.5f || abs(effectiveSteerRad) > 0.20f) 0.25f else 0.75f
        engineTorqueNm = engine.updateAndGetTorque(
            dt = safeDt,
            throttle = throttleInput,
            wheelDrivenRpm = wheelDrivenRpm,
            clutchEngagement = clutchEngagement,
            torqueMultiplier = upgrades.torqueMultiplier,
            turboLevel = upgrades.turboLevel
        )

        val driveDirection = if (transmission.currentGear == -1) -1f else 1f
        val wheelDriveTorque = engineTorqueNm * absGearRatio * 0.90f

        val maxRearLongTractionN = rearAxleLoadN * surface.gripMultiplier * upgrades.tireGripMultiplier * 1.05f
        val requestedDriveForceN = (wheelDriveTorque / wheelRadius) * driveDirection

        // Locked 2-way LSD RWD wheelspin:
        // 1. Raw torque excess in lower gears
        // 2. Power-over slip when throttling while steering or sliding sideways
        val torqueSpin = if (throttleInput > 0.2f) {
            val excess = abs(requestedDriveForceN) * (1f + abs(effectiveSteerRad) * 1.8f) - maxRearLongTractionN * 0.52f
            Math3D.clamp(excess / max(600f, maxRearLongTractionN * 0.7f), 0f, 1f)
        } else 0f

        val lsdCorneringSpin = if (throttleInput > 0.30f && (abs(effectiveSteerRad) > 0.08f || abs(velocitySlipRad) > 0.08f)) {
            throttleInput * Math3D.clamp(abs(effectiveSteerRad) * 1.45f + abs(velocitySlipRad) * 1.25f, 0f, 0.95f)
        } else 0f

        val effectiveWheelSpinRatio = if (handbrakeInput) {
            1.0f
        } else {
            max(torqueSpin, lsdCorneringSpin)
        }

        rearWheelAngularVel = if (handbrakeInput) {
            0f
        } else {
            roadWheelAngularVel + driveDirection * effectiveWheelSpinRatio * 58f * throttleInput
        }
        frontWheelRotationRad = (frontWheelRotationRad + roadWheelAngularVel * safeDt) % (2f * Math.PI.toFloat())
        rearWheelRotationRad = (rearWheelRotationRad + rearWheelAngularVel * safeDt) % (2f * Math.PI.toFloat())

        val maxBrakeForceN = 11200f * upgrades.brakeMultiplier * surface.gripMultiplier
        val longitudinalForceN = if (transmission.currentGear == -1 && brakeInput > 0.2f && throttleInput < 0.2f) {
            -min(abs(requestedDriveForceN) + brakeInput * 4400f, maxRearLongTractionN)
        } else if (longitudinalVelocity > 0.4f && brakeInput > 0.02f) {
            val driveN = Math3D.clamp(requestedDriveForceN, -maxRearLongTractionN, maxRearLongTractionN)
            driveN - brakeInput * maxBrakeForceN
        } else if (longitudinalVelocity <= 0.4f && brakeInput > 0.15f && throttleInput < 0.1f) {
            transmission.currentGear = -1
            -brakeInput * 5000f * surface.gripMultiplier
        } else {
            Math3D.clamp(requestedDriveForceN, -maxRearLongTractionN, maxRearLongTractionN)
        }

        val handbrakeDragN = if (handbrakeInput && abs(longitudinalVelocity) > 0.2f) {
            -Math.signum(longitudinalVelocity) * 3200f * surface.gripMultiplier
        } else 0f

        val aeroDragN = -0.40f * longitudinalVelocity * abs(longitudinalVelocity)
        val rollingDragN = -longitudinalVelocity * 26f * surface.dragMultiplier
        val totalLongForceN = longitudinalForceN + handbrakeDragN + aeroDragN + rollingDragN

        val safeForwardSpeed = max(2.0f, abs(longitudinalVelocity))
        val alphaFront = atan2(lateralVelocity + cgToFront * yawRate, safeForwardSpeed) -
            effectiveSteerRad * (if (longitudinalVelocity >= -0.2f) 1f else -1f)
        val alphaRear = atan2(lateralVelocity - cgToRear * yawRate, safeForwardSpeed)

        val frontLongSlip = brakeInput * 0.40f
        val rearLongSlip = max(effectiveWheelSpinRatio, if (handbrakeInput) 1.0f else brakeInput * 0.45f)

        val fyFront = if (speedMps > 0.3f) {
            tire.computeLateralForce(
                slipAngleRad = alphaFront,
                normalLoadN = frontAxleLoadN,
                surface = surface,
                tireGripMultiplier = upgrades.tireGripMultiplier,
                longitudinalSlipFactor = frontLongSlip,
                isRear = false
            )
        } else 0f

        val fyRear = if (speedMps > 0.3f) {
            tire.computeLateralForce(
                slipAngleRad = alphaRear,
                normalLoadN = rearAxleLoadN,
                surface = surface,
                tireGripMultiplier = upgrades.tireGripMultiplier,
                longitudinalSlipFactor = rearLongSlip,
                isRear = true
            )
        } else 0f

        // Weight Transfer
        val longAccel = totalLongForceN / massKg
        val latAccel = (fyFront + fyRear) / massKg
        val weightShiftLongN = (massKg * longAccel * cgHeight / wheelbase).coerceIn(-3000f, 3000f)
        val staticWeightN = massKg * 9.81f
        frontAxleLoadN = Math3D.lerp(
            frontAxleLoadN,
            staticWeightN * 0.53f - weightShiftLongN,
            safeDt * 12f * upgrades.suspensionStiffness
        )
        rearAxleLoadN = Math3D.lerp(
            rearAxleLoadN,
            staticWeightN * 0.47f + weightShiftLongN,
            safeDt * 12f * upgrades.suspensionStiffness
        )

        val targetPitch = Math3D.clamp(-longAccel * 0.0045f / upgrades.suspensionStiffness, -0.06f, 0.06f)
        val targetRoll = Math3D.clamp(latAccel * 0.0058f / upgrades.suspensionStiffness, -0.09f, 0.09f)
        pitchAngleRad = Math3D.lerp(pitchAngleRad, targetPitch, safeDt * 10f)
        rollAngleRad = Math3D.lerp(rollAngleRad, targetRoll, safeDt * 10f)

        // Power-over & Handbrake Yaw Initiation Torque
        val powerOverYawMoment = if (speedMps > 2.0f && longitudinalVelocity > 0f) {
            val steerSign = if (abs(effectiveSteerRad) > 0.03f) Math.signum(effectiveSteerRad) else Math.signum(yawRate)
            val spinInducer = effectiveWheelSpinRatio * throttleInput * 4400f +
                (if (handbrakeInput && abs(effectiveSteerRad) > 0.03f) 5200f else 0f)
            steerSign * spinInducer * (1f - Math3D.clamp(abs(velocitySlipRad) / 1.05f, 0f, 0.85f))
        } else 0f

        val assistDamping = when (assistMode) {
            DriftAssistMode.OFF -> 460f
            DriftAssistMode.LOW -> 720f
            DriftAssistMode.HIGH -> 1040f
        }
        val extremeAngleRestore = if (abs(velocitySlipRad) > 0.82f) {
            velocitySlipRad * 3100f
        } else 0f

        yawTorque = (cgToFront * fyFront * cos(effectiveSteerRad) - cgToRear * fyRear) +
            powerOverYawMoment + extremeAngleRestore - yawRate * assistDamping

        if (speedMps < 0.6f && throttleInput < 0.05f && brakeInput < 0.05f) {
            velX = 0f
            velZ = 0f
            yawRate = 0f
            yawTorque = 0f
        } else {
            yawRate += (yawTorque / yawInertia) * safeDt
            yawRate = Math3D.clamp(yawRate, -3.5f, 3.5f)
            yaw = Math3D.normalizeAngle(yaw + yawRate * safeDt)

            val totalLatForceN = fyFront * cos(effectiveSteerRad) + fyRear -
                lateralVelocity * (if (surface == SurfaceType.GRASS) 400f else 115f)

            val worldForceX = totalLongForceN * forwardX + totalLatForceN * rightX
            val worldForceZ = totalLongForceN * forwardZ + totalLatForceN * rightZ

            velX += (worldForceX / massKg) * safeDt
            velZ += (worldForceZ / massKg) * safeDt

            val currentSpd = sqrt(velX * velX + velZ * velZ)
            if (currentSpd > 71f) {
                velX = (velX / currentSpd) * 71f
                velZ = (velZ / currentSpd) * 71f
            }

            posX += velX * safeDt
            posZ += velZ * safeDt
        }

        // Recompute local velocities with updated yaw so driftAngleDeg reflects the new heading immediately
        val newForwardX = sin(yaw)
        val newForwardZ = cos(yaw)
        val newRightX = cos(yaw)
        val newRightZ = -sin(yaw)
        longitudinalVelocity = velX * newForwardX + velZ * newForwardZ
        lateralVelocity = velX * newRightX + velZ * newRightZ

        driftSystem.slipAngleFrontDeg = alphaFront * Math3D.RAD_TO_DEG
        driftSystem.slipAngleRearDeg = alphaRear * Math3D.RAD_TO_DEG
        driftSystem.evaluateDriftState(
            longitudinalVel = longitudinalVelocity,
            lateralVel = lateralVelocity,
            speedMps = speedMps,
            wheelSpinSlip = effectiveWheelSpinRatio,
            handbrake = handbrakeInput,
            surface = surface
        )
    }
}
