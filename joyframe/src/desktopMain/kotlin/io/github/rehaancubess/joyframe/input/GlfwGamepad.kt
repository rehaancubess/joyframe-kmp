// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import java.util.concurrent.atomic.AtomicBoolean
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryStack

internal object GlfwGamepad : DesktopGamepadBackend {

    private const val TRIGGER_DEADZONE = .05f

    @Volatile private var latestState: GamepadState? = null
    @Volatile private var latestStatus = GamepadStatus(false, null, "Starting controller service")
    private val started = AtomicBoolean(false)

    override fun poll(): GamepadState? {
        ensureStarted()
        return latestState
    }

    override fun status(): GamepadStatus {
        ensureStarted()
        val stick = latestState
        return latestStatus.copy(
            leftStickX = stick?.leftStickX ?: 0f,
            leftStickY = stick?.leftStickY ?: 0f,
        )
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        Thread(::pollLoop, "gamepad-poller").apply {
            isDaemon = true
            start()
        }
    }

    private fun pollLoop() {
        val initialized = try {
            GLFWErrorCallback.createPrint(System.err).set()
            GLFW.glfwInit()
        } catch (t: Throwable) {
            System.err.println("[gamepad] controller support unavailable: $t")
            false
        }
        if (!initialized) {
            latestStatus = GamepadStatus(false, null, "Controller support unavailable")
            return
        }
        var lastName: String? = null
        while (true) {
            try {
                GLFW.glfwPollEvents()
                val found = readFirstGamepad()
                latestState = found?.second
                val name = found?.first
                if (name != lastName) {
                    System.err.println(if (name != null) "[gamepad] connected: $name" else "[gamepad] controller disconnected")
                    lastName = name
                }
                latestStatus =
                    if (name != null) GamepadStatus(true, name, "Connected")
                    else GamepadStatus(false, null, "No controller detected")
            } catch (t: Throwable) {
                System.err.println("[gamepad] polling stopped: $t")
                latestState = null
                latestStatus = GamepadStatus(false, null, "Controller support unavailable")
                return
            }
            try {
                Thread.sleep(8)
            } catch (interrupted: InterruptedException) {
                return
            }
        }
    }

    private fun readFirstGamepad(): Pair<String, GamepadState>? {
        for (joystick in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST) {
            if (!GLFW.glfwJoystickPresent(joystick) || !GLFW.glfwJoystickIsGamepad(joystick)) continue
            MemoryStack.stackPush().use { stack ->
                val state = GLFWGamepadState.calloc(stack)
                if (!GLFW.glfwGetGamepadState(joystick, state)) return@use
                fun button(id: Int) = state.buttons(id).toInt() == GLFW.GLFW_PRESS

                val stickX = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X)
                val stickY = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y)
                var horizontal = stickX
                if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT)) horizontal = -1f
                if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT)) horizontal = 1f
                fun trigger(axis: Int): Float {
                    val value = (state.axes(axis) + 1f) * .5f
                    return if (value < TRIGGER_DEADZONE) 0f else value
                }
                var vertical = trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) -
                    trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)
                if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP)) vertical = 1f
                if (button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN)) vertical = -1f

                val south = button(GLFW.GLFW_GAMEPAD_BUTTON_A)
                val east = button(GLFW.GLFW_GAMEPAD_BUTTON_B)
                return friendlyName(joystick) to GamepadState(
                    horizontal = horizontal,
                    vertical = vertical,
                    action = button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) ||
                        button(GLFW.GLFW_GAMEPAD_BUTTON_X),
                    pause = button(GLFW.GLFW_GAMEPAD_BUTTON_START),
                    confirm = south,
                    cancel = east,
                    leftStickX = stickX,
                    leftStickY = stickY,
                )
            }
        }
        return null
    }


    private fun friendlyName(joystick: Int): String {
        val mappedName = GLFW.glfwGetGamepadName(joystick)
        return if (mappedName == null || mappedName.contains("GLFW")) {
            GLFW.glfwGetJoystickName(joystick) ?: "Gamepad"
        } else {
            mappedName
        }
    }
}
