package com.example

import com.example.drift.model.DriftAssistMode
import com.example.drift.model.GarageUpgrades
import com.example.drift.model.SurfaceType
import com.example.drift.physics.Vehicle
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {
    @Test
    fun vehicle_acceleratesForward_and_shiftsGears() {
        val vehicle = Vehicle()
        val upgrades = GarageUpgrades()
        repeat(240) {
            vehicle.stepPhysics(
                dt = 1f / 120f,
                rawSteer = 0f,
                rawThrottle = 1.0f,
                rawBrake = 0f,
                handbrake = false,
                assistMode = DriftAssistMode.LOW,
                autoShift = true,
                upgrades = upgrades,
                surface = SurfaceType.ASPHALT
            )
        }
        assertTrue("Vehicle should accelerate forward", vehicle.speedKmh > 35f)
        assertTrue("Engine RPM should rise above idle", vehicle.engine.rpm > 2500f)
    }

    @Test
    fun vehicle_initiatesDrift_withPowerOverAndHandbrake() {
        val vehicle = Vehicle()
        val upgrades = GarageUpgrades()
        // Build speed on straight
        repeat(180) {
            vehicle.stepPhysics(
                dt = 1f / 120f,
                rawSteer = 0f,
                rawThrottle = 1.0f,
                rawBrake = 0f,
                handbrake = false,
                assistMode = DriftAssistMode.LOW,
                autoShift = true,
                upgrades = upgrades,
                surface = SurfaceType.ASPHALT
            )
        }
        // Turn in with handbrake tap + full throttle power-over
        repeat(35) {
            vehicle.stepPhysics(
                dt = 1f / 120f,
                rawSteer = 0.85f,
                rawThrottle = 1.0f,
                rawBrake = 0f,
                handbrake = it < 12,
                assistMode = DriftAssistMode.LOW,
                autoShift = true,
                upgrades = upgrades,
                surface = SurfaceType.ASPHALT
            )
        }
        assertTrue(
            "Vehicle should develop non-zero drift angle and rear wheel slip",
            abs(vehicle.driftSystem.driftAngleDeg) > 8f && vehicle.driftSystem.rearWheelSlipRatio > 0.2f
        )
    }

    @Test
    fun vehicle_canBrakeAndReverse() {
        val vehicle = Vehicle()
        val upgrades = GarageUpgrades()
        repeat(150) {
            vehicle.stepPhysics(
                dt = 1f / 120f,
                rawSteer = 0f,
                rawThrottle = 0f,
                rawBrake = 1.0f,
                handbrake = false,
                assistMode = DriftAssistMode.LOW,
                autoShift = true,
                upgrades = upgrades,
                surface = SurfaceType.ASPHALT
            )
        }
        assertTrue("Vehicle should engage reverse and move backward", vehicle.longitudinalVelocity < -1.0f)
    }
}
