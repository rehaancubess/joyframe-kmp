// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.rehaancubess.joyframe.audio.PlatformAudio

actual object DeviceTilt : SensorEventListener {
    private val tracker = TiltTracker()
    private var listening = false
    private val manager: SensorManager?
        get() = PlatformAudio.appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    // Gravity is the accelerometer with shake filtered out; without it the raw accelerometer reads the same axes.
    private val sensor: Sensor?
        get() = manager?.let { it.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: it.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }

    actual val available: Boolean get() = sensor != null

    actual fun start(fullLockDegrees: Float) {
        tracker.fullLockDegrees = fullLockDegrees
        if (listening) return
        val target = sensor ?: return
        tracker.recenter()
        // SENSOR_DELAY_GAME is the ~50 Hz this needs; faster only adds noise.
        listening = manager?.registerListener(this, target, SensorManager.SENSOR_DELAY_GAME) == true
    }

    actual fun stop() {
        if (listening) manager?.unregisterListener(this)
        listening = false
        tracker.recenter()
    }

    actual fun recenter() = tracker.recenter()

    actual val steer: Float get() = if (listening) tracker.steer else 0f

    // No display-rotation correction: the player sees the screen from the same side in either
    // landscape, so the same wheel motion is the same rotation about the same axis.
    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size >= 2) tracker.reading(event.values[0].toDouble(), event.values[1].toDouble())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
