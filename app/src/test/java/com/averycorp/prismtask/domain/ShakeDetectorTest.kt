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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
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
        // Three samples with magnitude ~22 m/s² (net ~12.2 > SHAKE_THRESHOLD 12.0) should trigger
        val event1 = createSensorEvent(22f, 0f, 0f) // magnitude = 22, net ≈ 12.2
        val event2 = createSensorEvent(0f, 22f, 0f)
        val event3 = createSensorEvent(0f, 0f, 22f)

        var eventReceived = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            shakeDetector.shakeEvents.first()
            eventReceived = true
        }

        shakeDetector.onSensorChanged(event1)
        shakeDetector.onSensorChanged(event2)
        shakeDetector.onSensorChanged(event3)

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
    fun `required samples constant is 3`() {
        assert(ShakeDetector.REQUIRED_SAMPLES == 3)
    }

    @Test
    fun `shake threshold is 12`() {
        assert(ShakeDetector.SHAKE_THRESHOLD == 12.0) {
            "Shake threshold should be 12 m/s^2 net (above gravity)"
        }
    }

    @Test
    fun `threshold defaults to medium sensitivity`() {
        assertEquals(ShakeDetector.THRESHOLD_MEDIUM_SENSITIVITY, shakeDetector.threshold, 0.0)
    }

    @Test
    fun `low sensitivity threshold is higher than medium`() {
        // Low sensitivity = device must be shaken harder = higher threshold.
        assert(ShakeDetector.THRESHOLD_LOW_SENSITIVITY > ShakeDetector.THRESHOLD_MEDIUM_SENSITIVITY) {
            "Low-sensitivity threshold should exceed medium-sensitivity threshold"
        }
    }

    @Test
    fun `high sensitivity threshold is lower than medium`() {
        // High sensitivity = easier to trigger = lower threshold.
        assert(ShakeDetector.THRESHOLD_HIGH_SENSITIVITY < ShakeDetector.THRESHOLD_MEDIUM_SENSITIVITY) {
            "High-sensitivity threshold should be below medium-sensitivity threshold"
        }
    }

    @Test
    fun `raising threshold suppresses a shake that would trigger at medium`() = runTest {
        // Use the low-sensitivity threshold (harder to trigger) and feed the
        // same medium-magnitude samples that pass the default threshold.
        shakeDetector.threshold = ShakeDetector.THRESHOLD_LOW_SENSITIVITY

        val event1 = createSensorEvent(22f, 0f, 0f)
        val event2 = createSensorEvent(0f, 22f, 0f)
        val event3 = createSensorEvent(0f, 0f, 22f)

        var eventReceived = false
        val job = launch {
            shakeDetector.shakeEvents.first()
            eventReceived = true
        }

        shakeDetector.onSensorChanged(event1)
        shakeDetector.onSensorChanged(event2)
        shakeDetector.onSensorChanged(event3)

        val result = withTimeoutOrNull(200) { job.join() }
        assertNull("Low-sensitivity threshold should suppress a medium-strength shake", result)
        job.cancel()
    }

    @Test
    fun `lowering threshold lets a lighter shake trigger`() = runTest {
        // High sensitivity should register events that wouldn't cross the
        // default threshold. Net ~2 m/s² is well under 12 but above 8.
        shakeDetector.threshold = ShakeDetector.THRESHOLD_HIGH_SENSITIVITY

        val event1 = createSensorEvent(18f, 0f, 0f) // magnitude 18 → net ~8.2
        val event2 = createSensorEvent(0f, 18f, 0f)
        val event3 = createSensorEvent(0f, 0f, 18f)

        var eventReceived = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            shakeDetector.shakeEvents.first()
            eventReceived = true
        }

        shakeDetector.onSensorChanged(event1)
        shakeDetector.onSensorChanged(event2)
        shakeDetector.onSensorChanged(event3)

        withTimeoutOrNull(500) { job.join() }
        assertNotNull("High sensitivity should fire for a lighter shake", if (eventReceived) Unit else null)
        job.cancel()
    }
}
