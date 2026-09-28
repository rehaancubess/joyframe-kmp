// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import platform.GameController.*

actual object PlatformGamepad {
    private const val TRIGGER_DEADZONE = .05f

    actual fun poll(): GamepadState? = controllers().firstOrNull()?.extendedGamepad?.let(::read)

    actual fun pollAll(): List<ConnectedGamepad> = controllers().mapNotNull { controller ->
        val pad = controller.extendedGamepad ?: return@mapNotNull null
        ConnectedGamepad("gc-${controller.hash()}", controller.vendorName ?: "Gamepad", read(pad))
    }

    private fun read(pad: GCExtendedGamepad): GamepadState {
        val stickX = pad.leftThumbstick.xAxis.value
        val stickY = pad.leftThumbstick.yAxis.value
        var horizontal = stickX
        if (pad.dpad.left.pressed) horizontal = -1f
        if (pad.dpad.right.pressed) horizontal = 1f

        val leftTrigger = trigger(pad.leftTrigger.value)
        val rightTrigger = trigger(pad.rightTrigger.value)
        val triggers = rightTrigger - leftTrigger
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
                rightStickX = pad.rightThumbstick.xAxis.value,
                rightStickY = pad.rightThumbstick.yAxis.value,
                leftTrigger = leftTrigger,
                rightTrigger = rightTrigger,
                buttons = GamepadMapping.buttons(
                    south, east, pad.buttonX.pressed, pad.buttonY.pressed,
                    pad.leftShoulder.pressed, pad.rightShoulder.pressed, leftTrigger, rightTrigger,
                    pad.buttonOptions?.pressed == true, pad.buttonMenu.pressed,
                    pad.leftThumbstickButton?.pressed == true, pad.rightThumbstickButton?.pressed == true,
                    pad.dpad.up.pressed, pad.dpad.down.pressed, pad.dpad.left.pressed, pad.dpad.right.pressed,
                ),
            ),
        )
    }

    actual fun status(): GamepadStatus {
        val all = controllers()
        val controller = all.firstOrNull()
            ?: return GamepadStatus(false, null, "No controller detected")
        val pad = controller.extendedGamepad
        return GamepadStatus(
            connected = true,
            name = controller.vendorName ?: "Gamepad",
            detail = if (all.size > 1) "Connected (${all.size} controllers)" else "Connected",
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
    private fun controllers(): List<GCController> {
        if (!discoveryStarted) {
            discoveryStarted = true
            GCController.startWirelessControllerDiscoveryWithCompletionHandler(null)
        }
        return GCController.controllers()
            .filterIsInstance<GCController>()
            .filter { it.extendedGamepad != null && !it.isSimulatorStandIn() }
    }

    private val runningInSimulator: Boolean =
        platform.Foundation.NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null

    /** The iOS Simulator always offers a synthetic MFi "Gamepad"; real pads it forwards keep their names. */
    private fun GCController.isSimulatorStandIn(): Boolean =
        runningInSimulator && vendorName == "Gamepad" && productCategory == "MFi"
}
