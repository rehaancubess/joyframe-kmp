// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

actual object PlatformGamepad {
    private val backend: DesktopGamepadBackend =
        if (System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)) {
            MacGamepad
        } else {
            GlfwGamepad
        }

    actual fun poll(): GamepadState? = backend.pollAll().firstOrNull()?.state?.let(GamepadMapping::apply)

    actual fun pollAll(): List<ConnectedGamepad> =
        backend.pollAll().map { it.copy(state = GamepadMapping.apply(it.state)) }

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
    fun pollAll(): List<ConnectedGamepad>

    fun status(): GamepadStatus


    fun rumble(durationMs: Int, intensity: Float) {}
}
