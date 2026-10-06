package com.example.drift.physics

import com.example.drift.math.Math3D
import com.example.drift.model.SurfaceType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Engine {
    val idleRpm = 1000f
    val maxRpm = 8000f
    val redlineRpm = 7400f

    var rpm: Float = idleRpm
    var turboBoostBar: Float = 0f
    var limiterCutTimer: Float = 0f
    var backfireTrigger: Boolean = false

    fun updateAndGetTorque(
        dt: Float,
        throttle: Float,
        wheelDrivenRpm: Float,
        clutchEngagement: Float,
        torqueMultiplier: Float,
        turboLevel: Int
    ): Float {
        backfireTrigger = false

        if (rpm >= 7920f && throttle > 0.2f) {
            limiterCutTimer = 0.05f
            backfireTrigger = true
        }
        val effectiveThrottle = if (limiterCutTimer > 0f) {
            limiterCutTimer = max(0f, limiterCutTimer - dt)
            0.0f
        } else {
            Math3D.clamp(throttle, 0f, 1f)
        }

        val spoolRate = 3.2f + (turboLevel - 1) * 0.5f
        val targetBoost = if (effectiveThrottle > 0.3f && rpm > 2200f) {
            val rpmFactor = Math3D.clamp((rpm - 2200f) / 3400f, 0f, 1f)
            effectiveThrottle * rpmFactor * (0.95f + (turboLevel - 1) * 0.14f)
        } else {
            0f
        }
        if (targetBoost < turboBoostBar - 0.35f && turboBoostBar > 0.45f) {
            backfireTrigger = true
        }
        turboBoostBar = Math3D.lerp(turboBoostBar, targetBoost, dt * spoolRate * 2.8f)

        val freeRevTarget = idleRpm + effectiveThrottle * (maxRpm - idleRpm)
        val freeRevRate = if (effectiveThrottle > 0.1f) 10500f else 5400f
        val unclutchedRpm = if (freeRevTarget > rpm) {
            min(freeRevTarget, rpm + freeRevRate * dt)
        } else {
            max(idleRpm, rpm - 4500f * dt)
        }

        val targetCoupledRpm = Math3D.clamp(wheelDrivenRpm, idleRpm, maxRpm)
        rpm = Math3D.clamp(
            Math3D.lerp(targetCoupledRpm, unclutchedRpm, 1f - clutchEngagement),
            idleRpm,
            maxRpm
        )

        val normRpm = (rpm - 1000f) / 7000f
        val baseCurve = 320f + 260f * sin(normRpm * 2.55f).coerceAtLeast(0f) - 40f * normRpm * normRpm
        val boostBonus = 1f + turboBoostBar * 0.38f
        val driveTorque = baseCurve * boostBonus * torqueMultiplier * effectiveThrottle

        val engineBraking = (1f - effectiveThrottle) * (35f + 90f * normRpm)
        return driveTorque - engineBraking
    }
}

class Transmission {
    var currentGear: Int = 1
    var shiftCooldown: Float = 0f

    private val gearRatios = floatArrayOf(
        3.38f, // 1st
        2.24f, // 2nd
        1.64f, // 3rd
        1.28f, // 4th
        1.02f, // 5th
        0.84f  // 6th
    )
    private val reverseRatio = -3.15f
    val finalDrive = 4.15f

    fun getCurrentRatio(): Float {
        return if (currentGear == -1) {
            reverseRatio * finalDrive
        } else {
            val idx = (currentGear - 1).coerceIn(0, gearRatios.lastIndex)
            gearRatios[idx] * finalDrive
        }
    }

    fun getGearDisplay(): String = if (currentGear == -1) "R" else currentGear.toString()

    fun shiftUp() {
        if (currentGear == -1) {
            currentGear = 1
            shiftCooldown = 0.22f
        } else if (currentGear < 6) {
            currentGear++
            shiftCooldown = 0.22f
        }
    }

    fun shiftDown() {
        if (currentGear > 1) {
            currentGear--
            shiftCooldown = 0.22f
        } else if (currentGear == 1) {
            currentGear = -1
            shiftCooldown = 0.22f
        }
    }

    fun updateAutomatic(
        dt: Float,
        rpm: Float,
        forwardSpeedMps: Float,
        throttle: Float,
        brake: Float,
        isDrifting: Boolean,
        wheelSpinRatio: Float
    ) {
        if (shiftCooldown > 0f) {
            shiftCooldown = max(0f, shiftCooldown - dt)
            return
        }

        if (currentGear != -1 && forwardSpeedMps < 0.8f && brake > 0.6f && throttle < 0.1f) {
            currentGear = -1
            shiftCooldown = 0.32f
            return
        }
        if (currentGear == -1 && throttle > 0.25f && brake < 0.2f && forwardSpeedMps > -2.5f) {
            currentGear = 1
            shiftCooldown = 0.32f
            return
        }
        if (currentGear == -1) return

        val upshiftRpm = if (isDrifting || wheelSpinRatio > 0.25f) 7500f else 7000f
        val downshiftRpm = if (throttle > 0.5f) 3600f else 2700f

        if (rpm > upshiftRpm && currentGear < 6 && wheelSpinRatio < 0.70f) {
            currentGear++
            shiftCooldown = 0.35f
        } else if (rpm < downshiftRpm && currentGear > 1) {
            currentGear--
            shiftCooldown = 0.30f
        }
    }
}

class Tire {
    /**
     * Computes lateral tire force with realistic RWD friction-circle dropoff when rear wheels spin or lock.
     */
    fun computeLateralForce(
        slipAngleRad: Float,
        normalLoadN: Float,
        surface: SurfaceType,
        tireGripMultiplier: Float,
        longitudinalSlipFactor: Float,
        isRear: Boolean
    ): Float {
        val absAngle = abs(slipAngleRad)
        val peakAngle = if (isRear) 0.16f else 0.18f

        val normalizedForce = if (absAngle <= peakAngle) {
            sin((absAngle / peakAngle) * (Math.PI.toFloat() * 0.5f))
        } else {
            val excess = absAngle - peakAngle
            val plateau = if (isRear) 0.58f else 0.88f
            plateau + (1f - plateau) * exp(-excess * 3.2f)
        }

        val tractionCircle = if (isRear) {
            (1f - 0.78f * Math3D.clamp(longitudinalSlipFactor, 0f, 1f))
        } else {
            (1f - 0.15f * Math3D.clamp(longitudinalSlipFactor, 0f, 1f))
        }

        val baseMu = if (isRear) 0.92f else 1.08f
        val mu = baseMu * surface.gripMultiplier * tireGripMultiplier * tractionCircle
        val sign = if (slipAngleRad >= 0f) -1f else 1f
        return sign * normalizedForce * mu * normalLoadN
    }
}
