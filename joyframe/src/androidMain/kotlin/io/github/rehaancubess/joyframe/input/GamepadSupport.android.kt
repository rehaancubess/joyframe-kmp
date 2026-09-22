// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.rehaancubess.joyframe.input.GamepadTuning

actual object PlatformGamepad {
    private const val TRIGGER_DEADZONE = .05f
    private const val SCAN_INTERVAL_MS = 1_000L
    private const val EVENT_FRESH_MS = 2_500L

    @Volatile private var stickX = 0f
    @Volatile private var stickY = 0f
    @Volatile private var throttleAxis = 0f
    @Volatile private var brakeAxis = 0f
    @Volatile private var hatX = 0f
    @Volatile private var hatY = 0f
    @Volatile private var dpadLeft = false
    @Volatile private var dpadRight = false
    @Volatile private var dpadUp = false
    @Volatile private var dpadDown = false
    @Volatile private var primaryDown = false
    @Volatile private var eastDown = false
    @Volatile private var bumperDown = false
    @Volatile private var abilityDown = false
    @Volatile private var startDown = false

    @Volatile private var lastEventAtMs = 0L
    @Volatile private var lastScanAtMs = 0L
    @Volatile private var connectedName: String? = null
    @Volatile private var connectedDeviceId: Int = -1

    actual fun poll(): GamepadState? {
        refreshDevicesIfStale()
        val eventsFresh = SystemClock.uptimeMillis() - lastEventAtMs < EVENT_FRESH_MS
        if (connectedName == null && !eventsFresh) return null

        var horizontal = stickX
        if (hatX < -.5f || dpadLeft) horizontal = -1f
        if (hatX > .5f || dpadRight) horizontal = 1f

        var vertical = (if (throttleAxis < TRIGGER_DEADZONE) 0f else throttleAxis) -
            (if (brakeAxis < TRIGGER_DEADZONE) 0f else brakeAxis)
        if (hatY < -.5f || dpadUp) vertical = 1f
        if (hatY > .5f || dpadDown) vertical = -1f

        return GamepadMapping.apply(
            GamepadState(
                horizontal = horizontal.coerceIn(-1f, 1f),
                vertical = vertical.coerceIn(-1f, 1f),
                action = abilityDown || bumperDown,
                pause = startDown,
                confirm = primaryDown,
                cancel = eastDown,
                leftStickX = stickX,
                leftStickY = stickY,
            ),
        )
    }

    actual fun status(): GamepadStatus {
        refreshDevicesIfStale()
        val name = connectedName
        return when {
            name != null -> GamepadStatus(
                connected = true,
                name = name,
                detail = "Connected",
                leftStickX = stickX,
                leftStickY = stickY,
            )
            else -> GamepadStatus(false, null, "No controller detected")
        }
    }

    actual fun rumbleTest() {
        rumbleHardware(220, 0.85f)
    }

    actual fun rumble(durationMs: Int, intensity: Float) {
        if (!GamepadTuning.current.gamepadRumbleEnabled) return
        rumbleHardware(durationMs, intensity)
    }


    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!event.isFromGamepad()) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> primaryDown = down
            KeyEvent.KEYCODE_BUTTON_B -> eastDown = down
            KeyEvent.KEYCODE_BUTTON_R1 -> bumperDown = down
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_L1 -> abilityDown = down
            KeyEvent.KEYCODE_BUTTON_START -> startDown = down
            KeyEvent.KEYCODE_DPAD_LEFT -> dpadLeft = down
            KeyEvent.KEYCODE_DPAD_RIGHT -> dpadRight = down
            KeyEvent.KEYCODE_DPAD_UP -> dpadUp = down
            KeyEvent.KEYCODE_DPAD_DOWN -> dpadDown = down
            else -> return false
        }
        lastEventAtMs = SystemClock.uptimeMillis()
        return true
    }


    fun handleMotionEvent(event: MotionEvent): Boolean {
        val fromJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!fromJoystick || event.action != MotionEvent.ACTION_MOVE) return false
        stickX = event.getAxisValue(MotionEvent.AXIS_X)
        stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        throttleAxis = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS),
        )
        brakeAxis = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE),
        )
        hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        lastEventAtMs = SystemClock.uptimeMillis()
        return true
    }

    private fun KeyEvent.isFromGamepad(): Boolean =
        source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK) != 0

    private fun rumbleHardware(durationMs: Int, intensity: Float) {
        val vibrator = connectedVibrator() ?: return
        val duration = durationMs.toLong().coerceIn(1L, 1_000L)
        val amplitude = (intensity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255)
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } catch (_: Throwable) {
        }
    }

    private fun connectedVibrator(): Vibrator? {
        refreshDevicesIfStale()
        val id = connectedDeviceId
        if (id < 0) return null
        val device = InputDevice.getDevice(id) ?: return null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            device.vibrator
        }
        return vibrator.takeIf { it.hasVibrator() }
    }

    private fun refreshDevicesIfStale() {
        val now = SystemClock.uptimeMillis()
        if (now - lastScanAtMs < SCAN_INTERVAL_MS) return
        lastScanAtMs = now
        val device = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(InputDevice::getDevice)
            .firstOrNull { candidate ->
                !candidate.isVirtual && (
                    candidate.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                        candidate.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                    )
            }
        connectedName = device?.name
        connectedDeviceId = device?.id ?: -1
    }
}
