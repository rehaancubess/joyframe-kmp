// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.concurrent.ConcurrentHashMap

actual object PlatformGamepad {
    private const val TRIGGER_DEADZONE = .05f
    private const val SCAN_INTERVAL_MS = 1_000L
    private const val EVENT_FRESH_MS = 2_500L

    /** Latest input per device id. Written by the activity's dispatch methods, read by polling. */
    private class Device(val id: Int) {
        @Volatile var stickX = 0f
        @Volatile var stickY = 0f
        @Volatile var rightX = 0f
        @Volatile var rightY = 0f
        @Volatile var throttleAxis = 0f
        @Volatile var brakeAxis = 0f
        @Volatile var hatX = 0f
        @Volatile var hatY = 0f
        val pressed: MutableSet<Int> = ConcurrentHashMap.newKeySet()
        @Volatile var lastEventAtMs = 0L
    }

    private val devices = ConcurrentHashMap<Int, Device>()

    @Volatile private var lastScanAtMs = 0L
    @Volatile private var connected: List<Pair<Int, String>> = emptyList()

    actual fun poll(): GamepadState? = pollAll().firstOrNull()?.state

    actual fun pollAll(): List<ConnectedGamepad> {
        refreshDevicesIfStale()
        val now = SystemClock.uptimeMillis()
        val known = connected
        val ids = known.map { it.first }.toMutableList()
        // A pad that is sending events counts even before the next device scan notices it.
        devices.values.filter { it.id !in ids && now - it.lastEventAtMs < EVENT_FRESH_MS }.forEach { ids += it.id }
        return ids.map { id ->
            val device = devices.getOrPut(id) { Device(id) }
            val name = known.firstOrNull { it.first == id }?.second ?: "Gamepad"
            ConnectedGamepad("android-$id", name, GamepadMapping.apply(read(device)))
        }
    }

    private fun read(d: Device): GamepadState {
        fun down(code: Int) = code in d.pressed
        val left = d.hatX < -.5f || down(KeyEvent.KEYCODE_DPAD_LEFT)
        val right = d.hatX > .5f || down(KeyEvent.KEYCODE_DPAD_RIGHT)
        val up = d.hatY < -.5f || down(KeyEvent.KEYCODE_DPAD_UP)
        val downPad = d.hatY > .5f || down(KeyEvent.KEYCODE_DPAD_DOWN)
        var horizontal = d.stickX
        if (left) horizontal = -1f
        if (right) horizontal = 1f
        val throttle = if (d.throttleAxis < TRIGGER_DEADZONE) 0f else d.throttleAxis
        val brake = if (d.brakeAxis < TRIGGER_DEADZONE) 0f else d.brakeAxis
        var vertical = throttle - brake
        if (up) vertical = 1f
        if (downPad) vertical = -1f
        val south = down(KeyEvent.KEYCODE_BUTTON_A)
        val west = down(KeyEvent.KEYCODE_BUTTON_X)
        val leftShoulder = down(KeyEvent.KEYCODE_BUTTON_L1)
        val rightShoulder = down(KeyEvent.KEYCODE_BUTTON_R1)
        return GamepadState(
            horizontal = horizontal.coerceIn(-1f, 1f),
            vertical = vertical.coerceIn(-1f, 1f),
            action = west || leftShoulder || rightShoulder,
            pause = down(KeyEvent.KEYCODE_BUTTON_START),
            confirm = south,
            cancel = down(KeyEvent.KEYCODE_BUTTON_B),
            leftStickX = d.stickX,
            leftStickY = -d.stickY,
            rightStickX = d.rightX,
            rightStickY = -d.rightY,
            leftTrigger = brake,
            rightTrigger = throttle,
            buttons = GamepadMapping.buttons(
                south, down(KeyEvent.KEYCODE_BUTTON_B), west, down(KeyEvent.KEYCODE_BUTTON_Y),
                leftShoulder, rightShoulder, brake, throttle,
                down(KeyEvent.KEYCODE_BUTTON_SELECT), down(KeyEvent.KEYCODE_BUTTON_START),
                down(KeyEvent.KEYCODE_BUTTON_THUMBL), down(KeyEvent.KEYCODE_BUTTON_THUMBR),
                up, downPad, left, right,
            ),
        )
    }

    actual fun status(): GamepadStatus {
        val pads = pollAll()
        val first = pads.firstOrNull() ?: return GamepadStatus(false, null, "No controller detected")
        return GamepadStatus(
            connected = true,
            name = first.name,
            detail = if (pads.size > 1) "Connected (${pads.size} controllers)" else "Connected",
            leftStickX = first.state.leftStickX,
            leftStickY = first.state.leftStickY,
        )
    }

    actual fun rumbleTest() {
        rumbleHardware(220, 0.85f)
    }

    actual fun rumble(durationMs: Int, intensity: Float) {
        if (!GamepadTuning.current.gamepadRumbleEnabled) return
        rumbleHardware(durationMs, intensity)
    }

    /** Forward from Activity.dispatchKeyEvent. Returns true when a controller button was consumed. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (!event.isFromGamepad()) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        if (event.keyCode !in HANDLED_KEYS) return false
        val device = devices.getOrPut(event.deviceId) { Device(event.deviceId) }
        if (down) device.pressed += event.keyCode else device.pressed -= event.keyCode
        device.lastEventAtMs = SystemClock.uptimeMillis()
        return true
    }

    /** Forward from Activity.dispatchGenericMotionEvent. Returns true when stick/trigger motion was consumed. */
    fun handleMotionEvent(event: MotionEvent): Boolean {
        val fromJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!fromJoystick || event.action != MotionEvent.ACTION_MOVE) return false
        val device = devices.getOrPut(event.deviceId) { Device(event.deviceId) }
        device.stickX = event.getAxisValue(MotionEvent.AXIS_X)
        device.stickY = event.getAxisValue(MotionEvent.AXIS_Y)
        device.rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        device.rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
        device.throttleAxis = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS),
        )
        device.brakeAxis = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE),
        )
        device.hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        device.hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        device.lastEventAtMs = SystemClock.uptimeMillis()
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
        val id = connected.firstOrNull()?.first ?: return null
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
        val found = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(InputDevice::getDevice)
            .filter { candidate ->
                !candidate.isVirtual && (
                    candidate.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                        candidate.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                    )
            }
            .map { it.id to it.name }
            .toList()
        connected = found
        devices.keys.retainAll(found.map { it.first }.toSet() + devices.filterValues {
            now - it.lastEventAtMs < EVENT_FRESH_MS
        }.keys)
    }

    private val HANDLED_KEYS = setOf(
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
    )
}
