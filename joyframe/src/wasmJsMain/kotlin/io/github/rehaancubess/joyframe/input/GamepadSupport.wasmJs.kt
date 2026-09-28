// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import io.github.rehaancubess.joyframe.input.GamepadTuning

actual object PlatformGamepad {


    private const val BUTTON_SOUTH = 0
    private const val BUTTON_EAST = 1
    private const val BUTTON_WEST = 2
    private const val BUTTON_LEFT_TRIGGER = 6
    private const val BUTTON_RIGHT_TRIGGER = 7
    private const val BUTTON_START = 9
    private const val BUTTON_RIGHT_SHOULDER = 5
    private const val BUTTON_DPAD_UP = 12
    private const val BUTTON_DPAD_DOWN = 13
    private const val BUTTON_DPAD_LEFT = 14
    private const val BUTTON_DPAD_RIGHT = 15

    private const val AXIS_LEFT_X = 0
    private const val AXIS_LEFT_Y = 1


    private const val TRIGGER_DEADZONE = .05f

    actual fun poll(): GamepadState? {
        val pad = firstGamepad() ?: return null

        var horizontal = axis(pad, AXIS_LEFT_X)
        if (buttonPressed(pad, BUTTON_DPAD_LEFT)) horizontal = -1f
        if (buttonPressed(pad, BUTTON_DPAD_RIGHT)) horizontal = 1f

        var vertical = trigger(buttonValue(pad, BUTTON_RIGHT_TRIGGER)) -
            trigger(buttonValue(pad, BUTTON_LEFT_TRIGGER))
        if (buttonPressed(pad, BUTTON_DPAD_UP)) vertical = 1f
        if (buttonPressed(pad, BUTTON_DPAD_DOWN)) vertical = -1f

        val south = buttonPressed(pad, BUTTON_SOUTH)
        val east = buttonPressed(pad, BUTTON_EAST)
        return GamepadMapping.apply(
            GamepadState(
                horizontal = horizontal,
                vertical = vertical,
                action = buttonPressed(pad, BUTTON_RIGHT_SHOULDER) || buttonPressed(pad, BUTTON_WEST),
                pause = buttonPressed(pad, BUTTON_START),
                confirm = south,
                cancel = east,
                leftStickX = horizontal,
                leftStickY = -axis(pad, AXIS_LEFT_Y),
            ),
        )
    }

    actual fun status(): GamepadStatus {
        if (!gamepadApiAvailable()) {
            return GamepadStatus(false, null, "This browser has no gamepad support")
        }
        val pad = firstGamepad()
            ?: return GamepadStatus(false, null, "No controller detected — press a button to wake it")
        return GamepadStatus(
            connected = true,
            name = gamepadId(pad),
            detail = "Connected",
            leftStickX = axis(pad, AXIS_LEFT_X),
            leftStickY = -axis(pad, AXIS_LEFT_Y),
        )
    }

    actual fun rumbleTest() = rumble(durationMs = 220, intensity = .7f)

    actual fun rumble(durationMs: Int, intensity: Float) {
        if (!GamepadTuning.current.gamepadRumbleEnabled) return
        val pad = firstGamepad() ?: return
        runCatching { vibrate(pad, durationMs.coerceIn(1, 5_000), intensity.coerceIn(0f, 1f)) }
    }

    private fun trigger(value: Float): Float = if (value < TRIGGER_DEADZONE) 0f else value
}

internal external interface Gamepad : JsAny

private fun gamepadApiAvailable(): Boolean = js("!!navigator.getGamepads")

private fun firstGamepad(): Gamepad? = js(
    """(function() {
        if (!navigator.getGamepads) return null;
        var pads = navigator.getGamepads();
        for (var i = 0; i < pads.length; i++) {
            if (pads[i] && pads[i].connected && pads[i].mapping === 'standard') return pads[i];
        }
        for (var j = 0; j < pads.length; j++) {
            if (pads[j] && pads[j].connected) return pads[j];
        }
        return null;
    })()"""
)

private fun axis(pad: Gamepad, index: Int): Float = js("(pad.axes[index] || 0)")

private fun buttonValue(pad: Gamepad, index: Int): Float =
    js("(pad.buttons[index] ? pad.buttons[index].value : 0)")

private fun buttonPressed(pad: Gamepad, index: Int): Boolean =
    js("!!(pad.buttons[index] && pad.buttons[index].pressed)")

private fun gamepadId(pad: Gamepad): String = js("(pad.id || 'Gamepad')")

private fun vibrate(pad: Gamepad, durationMs: Int, intensity: Float) {
    js(
        """(function() {
            var actuator = pad.vibrationActuator;
            if (!actuator || !actuator.playEffect) return;
            actuator.playEffect('dual-rumble', {
                duration: durationMs,
                strongMagnitude: intensity,
                weakMagnitude: intensity,
            });
        })()"""
    )
}
