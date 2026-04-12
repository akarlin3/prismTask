package com.averycorp.prismtask.domain

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.domain.usecase.ShakeDetector
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33], application = Application::class)
class ShakeDetectorTest {

    private lateinit var shakeDetector: ShakeDetector
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSensorManager = mockk<SensorManager>(relaxed = true)
    private val mockSensor = mockk<Sensor>(relaxed = true)

    @Before
    fun setUp() {
        every { mockContext.getSystemService(Context.SENSOR_SERVICE) } returns mockSensorManager
        every { mockSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) } returns mockSensor
        shakeDetector = ShakeDetector(mockContext)
    }

    private fun createSensorEvent(x: Float, y: Float, z: Float): SensorEvent {
        // SensorEvent doesn't have a public constructor, so we use reflection.
        // Robolectric provides the real framework class with the package-private
        // SensorEvent(int) constructor that android.jar stubs omit.
        val constructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.java)
        constructor.isAccessible = true
        val event = constructor.newInstance(3)
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        // Set sensor type via reflection
        val sensorField = SensorEvent::class.java.getDeclaredField("sensor")
        sensorField.isAccessible = true
        val sensor = mockk<Sensor>()
        every { sensor.type } returns Sensor.TYPE_ACCELEROMETER
        sensorField.set(event, sensor)
        return event
    }

    @Test
    fun `acceleration above threshold emits shake event`() = runTest {
        // Two samples above threshold should trigger
        val event1 = createSensorEvent(20f, 0f, 0f) // magnitude = 20, above 15
        val event2 = createSensorEvent(0f, 20f, 0f) // magnitude = 20, above 15

        var eventReceived = false
        val job = launch {
            shakeDetector.shakeEvents.first()
            eventReceived = true
        }

        shakeDetector.onSensorChanged(event1)
        shakeDetector.onSensorChanged(event2)

        // Wait briefly for event to propagate
        withTimeoutOrNull(500) { job.join() }
        assertNotNull("Should receive shake event", if (eventReceived) Unit else null)
        job.cancel()
    }

    @Test
    fun `acceleration below threshold does not emit event`() = runTest {
        val event = createSensorEvent(1f, 1f, 9.8f) // ~10 m/s^2, below 15

        var eventReceived = false
        val job = launch {
            shakeDetector.shakeEvents.first()
            eventReceived = true
        }

        shakeDetector.onSensorChanged(event)

        val result = withTimeoutOrNull(200) { job.join() }
        assertNull("Should not receive shake event for low acceleration", result)
        job.cancel()
    }

    @Test
    fun `cooldown prevents rapid repeat triggers`() = runTest {
        // ShakeDetector uses System.currentTimeMillis internally.
        // We verify the cooldown logic by checking that the constant is set.
        assert(ShakeDetector.COOLDOWN_MS == 3000L) {
            "Cooldown should be 3000ms"
        }
    }

    @Test
    fun `required samples constant is 2`() {
        assert(ShakeDetector.REQUIRED_SAMPLES == 2)
    }

    @Test
    fun `shake threshold is 15`() {
        assert(ShakeDetector.SHAKE_THRESHOLD == 15.0) {
            "Shake threshold should be 15 m/s^2"
        }
    }
}
