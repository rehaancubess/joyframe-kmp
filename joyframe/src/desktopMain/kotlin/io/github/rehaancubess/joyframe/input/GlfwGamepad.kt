// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import java.util.concurrent.atomic.AtomicBoolean
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.glfw.GLFWGamepadState
import org.lwjgl.system.MemoryStack

internal object GlfwGamepad : DesktopGamepadBackend {

    private const val TRIGGER_DEADZONE = .05f

    @Volatile private var latestPads: List<ConnectedGamepad> = emptyList()
    @Volatile private var latestStatus = GamepadStatus(false, null, "Starting controller service")
    private val started = AtomicBoolean(false)

    override fun pollAll(): List<ConnectedGamepad> {
        ensureStarted()
        return latestPads
    }

    override fun status(): GamepadStatus {
        ensureStarted()
        val stick = latestPads.firstOrNull()?.state
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
        var lastIds = emptySet<String>()
        while (true) {
            try {
                GLFW.glfwPollEvents()
                val pads = readGamepads()
                latestPads = pads
                val ids = pads.mapTo(mutableSetOf()) { it.id }
                if (ids != lastIds) {
                    pads.filter { it.id !in lastIds }.forEach { System.err.println("[gamepad] connected: ${it.name}") }
                    if ((lastIds - ids).isNotEmpty()) System.err.println("[gamepad] controller disconnected")
                    lastIds = ids
                }
                val first = pads.firstOrNull()
                latestStatus =
                    if (first != null) GamepadStatus(true, first.name,
                        if (pads.size > 1) "Connected (${pads.size} controllers)" else "Connected")
                    else GamepadStatus(false, null, "No controller detected")
            } catch (t: Throwable) {
                System.err.println("[gamepad] polling stopped: $t")
                latestPads = emptyList()
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

    private fun readGamepads(): List<ConnectedGamepad> = buildList {
        for (joystick in GLFW.GLFW_JOYSTICK_1..GLFW.GLFW_JOYSTICK_LAST) {
            if (!GLFW.glfwJoystickPresent(joystick) || !GLFW.glfwJoystickIsGamepad(joystick)) continue
            MemoryStack.stackPush().use { stack ->
                val state = GLFWGamepadState.calloc(stack)
                if (!GLFW.glfwGetGamepadState(joystick, state)) return@use
                fun button(id: Int) = state.buttons(id).toInt() == GLFW.GLFW_PRESS

                val stickX = state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X)
                val stickY = -state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y)
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
                val leftTrigger = trigger(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER)
                val rightTrigger = trigger(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER)
                add(ConnectedGamepad("glfw-$joystick", friendlyName(joystick), GamepadState(
                    horizontal = horizontal,
                    vertical = vertical,
                    action = button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER) ||
                        button(GLFW.GLFW_GAMEPAD_BUTTON_X),
                    pause = button(GLFW.GLFW_GAMEPAD_BUTTON_START),
                    confirm = south,
                    cancel = east,
                    leftStickX = stickX,
                    leftStickY = stickY,
                    rightStickX = state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X),
                    rightStickY = -state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y),
                    leftTrigger = leftTrigger,
                    rightTrigger = rightTrigger,
                    buttons = GamepadMapping.buttons(
                        south, east, button(GLFW.GLFW_GAMEPAD_BUTTON_X), button(GLFW.GLFW_GAMEPAD_BUTTON_Y),
                        button(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER), button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER),
                        leftTrigger, rightTrigger,
                        button(GLFW.GLFW_GAMEPAD_BUTTON_BACK), button(GLFW.GLFW_GAMEPAD_BUTTON_START),
                        button(GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB), button(GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB),
                        button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP), button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN),
                        button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT), button(GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT),
                    ),
                )))
            }
        }
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
