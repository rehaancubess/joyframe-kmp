// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import io.github.rehaancubess.joyframe.input.GamepadTuning
import platform.GameController.*

actual object PlatformGamepad {
    private const val TRIGGER_DEADZONE = .05f

    actual fun poll(): GamepadState? {
        val pad = firstController()?.extendedGamepad ?: return null

        val stickX = pad.leftThumbstick.xAxis.value
        val stickY = pad.leftThumbstick.yAxis.value
        var horizontal = stickX
        if (pad.dpad.left.pressed) horizontal = -1f
        if (pad.dpad.right.pressed) horizontal = 1f

        val triggers = trigger(pad.rightTrigger.value) - trigger(pad.leftTrigger.value)
        var vertical = if (kotlin.math.abs(triggers) > TRIGGER_DEADZONE) triggers else stickY
        if (pad.dpad.up.pressed) vertical = 1f
        if (pad.dpad.down.pressed) vertical = -1f

        val south = pad.buttonA.pressed
        val east = pad.buttonB.pressed
        return GamepadMapping.apply(
            GamepadState(
                horizontal = horizontal,
                vertical = vertical,
                action = pad.rightShoulder.pressed || pad.buttonX.pressed,
                pause = pad.buttonMenu.pressed,
                confirm = south,
                cancel = east,
                leftStickX = stickX,
                leftStickY = stickY,
            ),
        )
    }

    actual fun status(): GamepadStatus {
        val controller = firstController()
            ?: return GamepadStatus(false, null, "No controller detected")
        val pad = controller.extendedGamepad
        return GamepadStatus(
            connected = true,
            name = controller.vendorName ?: "Gamepad",
            detail = "Connected",
            leftStickX = pad?.leftThumbstick?.xAxis?.value ?: 0f,
            leftStickY = pad?.leftThumbstick?.yAxis?.value ?: 0f,
        )
    }

    actual fun rumbleTest() {
    }

    actual fun rumble(durationMs: Int, intensity: Float) {
        if (!GamepadTuning.current.gamepadRumbleEnabled) return
    }


    private fun trigger(value: Float): Float = if (value < TRIGGER_DEADZONE) 0f else value

    private var discoveryStarted = false
    private fun firstController(): GCController? {
        if (!discoveryStarted) {
            discoveryStarted = true
            GCController.startWirelessControllerDiscoveryWithCompletionHandler(null)
        }
        return GCController.controllers()
        .filterIsInstance<GCController>()
        .firstOrNull { it.extendedGamepad != null }
    }
}
