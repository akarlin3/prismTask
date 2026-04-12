package com.averycorp.prismtask.domain.usecase

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ShakeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _shakeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val shakeEvents: SharedFlow<Unit> = _shakeEvents.asSharedFlow()

    private var lastShakeTimestamp = 0L
    private var aboveThresholdCount = 0
    private var firstAboveThresholdTime = 0L
    private var isRegistered = false

    fun register() {
        if (isRegistered || accelerometer == null) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        isRegistered = false
        aboveThresholdCount = 0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Remove gravity component (approx 9.8) by computing magnitude and checking excess
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())

        val now = System.currentTimeMillis()

        if (magnitude > SHAKE_THRESHOLD) {
            if (aboveThresholdCount == 0) {
                firstAboveThresholdTime = now
            }
            aboveThresholdCount++

            // Need 2+ samples above threshold within 500ms window
            if (aboveThresholdCount >= REQUIRED_SAMPLES &&
                (now - firstAboveThresholdTime) <= WINDOW_MS
            ) {
                // Respect cooldown
                if (now - lastShakeTimestamp > COOLDOWN_MS) {
                    lastShakeTimestamp = now
                    _shakeEvents.tryEmit(Unit)
                }
                aboveThresholdCount = 0
            }
        } else {
            // Reset if the window has elapsed without enough samples
            if (aboveThresholdCount > 0 && (now - firstAboveThresholdTime) > WINDOW_MS) {
                aboveThresholdCount = 0
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val SHAKE_THRESHOLD = 15.0 // m/s^2
        const val REQUIRED_SAMPLES = 2
        const val WINDOW_MS = 500L
        const val COOLDOWN_MS = 3000L
    }
}
