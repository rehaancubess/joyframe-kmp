// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import io.github.rehaancubess.joyframe.input.GamepadTuning

actual object PlatformGamepad {
    private val backend: DesktopGamepadBackend =
        if (System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)) {
            MacGamepad
        } else {
            GlfwGamepad
        }

    actual fun poll(): GamepadState? = backend.poll()?.let(GamepadMapping::apply)

    actual fun status(): GamepadStatus = backend.status()

    actual fun rumbleTest() {
        backend.rumble(220, 0.85f)
    }

    actual fun rumble(durationMs: Int, intensity: Float) {
        if (!GamepadTuning.current.gamepadRumbleEnabled) return
        backend.rumble(durationMs, intensity)
    }
}

internal interface DesktopGamepadBackend {
    fun poll(): GamepadState?

    fun status(): GamepadStatus


    fun rumble(durationMs: Int, intensity: Float) {}
}
