package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.drift.input.GameManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify game loop step`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Project Drift", appName)

        val manager = GameManager()
        manager.inputManager.setAnalogThrottle(1.0f)
        manager.inputManager.setAnalogSteering(0.6f, true)
        repeat(60) {
            manager.stepFrame(1f / 60f)
        }
        assertTrue(manager.uiState.value.speedKmh > 0)
        manager.audioManager.stop()
    }
}
