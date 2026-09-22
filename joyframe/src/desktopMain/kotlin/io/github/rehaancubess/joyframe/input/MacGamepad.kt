// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

internal object MacGamepad : DesktopGamepadBackend {

    private const val TRIGGER_DEADZONE = .05f


    private const val BUTTON_THRESHOLD = .5f


    private const val RESCAN_INTERVAL_NANOS = 250_000_000L

    private val objc: Bridge? by lazy {
        try {
            Bridge()
        } catch (t: Throwable) {
            System.err.println("[gamepad] GameController.framework unavailable: $t")
            null
        }
    }

    private var pad: Pad? = null


    private var nextScanAt = System.nanoTime()
    private var unavailable: String? = null

    override fun poll(): GamepadState? {
        val pad = currentPad() ?: return null

        val stickX = pad.leftStickX.value()
        val stickY = pad.leftStickY.value()
        var horizontal = stickX
        if (pad.dpadLeft.pressed()) horizontal = -1f
        if (pad.dpadRight.pressed()) horizontal = 1f

        var vertical = pad.rightTrigger.trigger() - pad.leftTrigger.trigger()
        if (pad.dpadUp.pressed()) vertical = 1f
        if (pad.dpadDown.pressed()) vertical = -1f

        val south = pad.buttonA.pressed()
        val east = pad.buttonB.pressed()
        return GamepadState(
            horizontal = horizontal,
            vertical = vertical,
            action = pad.rightShoulder.pressed() || pad.buttonX.pressed(),
            pause = pad.buttonMenu.pressed(),
            confirm = south,
            cancel = east,
            leftStickX = stickX,
            leftStickY = stickY,
        )
    }

    override fun status(): GamepadStatus {
        val pad = currentPad()
        unavailable?.let { return GamepadStatus(false, null, it) }
        return if (pad == null) {
            GamepadStatus(false, null, "No controller detected")
        } else {
            GamepadStatus(
                connected = true,
                name = pad.name,
                detail = "Connected",
                leftStickX = pad.leftStickX.value(),
                leftStickY = pad.leftStickY.value(),
            )
        }
    }


    internal fun diagnostics(): String =
        try {
            val bridge = objc ?: return "GameController bridge unavailable"
            val pool = bridge.pushAutoreleasePool()
            try {
                val controller = currentPad()?.controller
                    ?: return bridge.describeControllers()
                bridge.describeElements(controller)
            } finally {
                bridge.popAutoreleasePool(pool)
            }
        } catch (t: Throwable) {
            "diagnostics failed: $t"
        }


    private fun Pointer?.trigger(): Float = value().let { if (it < TRIGGER_DEADZONE) 0f else it }

    private fun Pointer?.pressed(): Boolean = value() >= BUTTON_THRESHOLD

    private fun Pointer?.value(): Float {
        val element = this ?: return 0f
        val bridge = objc ?: return 0f
        return bridge.floatProperty(element, bridge.selValue)
    }

    private fun currentPad(): Pad? {
        if (unavailable != null) return null
        val now = System.nanoTime()
        if (now - nextScanAt >= 0) {
            nextScanAt = now + RESCAN_INTERVAL_NANOS
            rescan()
        }
        return pad
    }

    private fun rescan() {
        val bridge = objc ?: run {
            unavailable = "Controller support unavailable"
            return
        }
        try {
            val pool = bridge.pushAutoreleasePool()
            try {
                val found = bridge.firstExtendedController()
                val existing = pad
                when {
                    found == null -> {
                        if (existing != null) {
                            System.err.println("[gamepad] controller disconnected")
                            bridge.release(existing.controller)
                        }
                        pad = null
                    }
                    existing != null && existing.controller == found -> Unit
                    else -> {
                        if (existing != null) bridge.release(existing.controller)
                        pad = bridge.readPad(found).also {
                            System.err.println("[gamepad] connected: ${it.name}")
                        }
                    }
                }
            } finally {
                bridge.popAutoreleasePool(pool)
            }
        } catch (t: Throwable) {
            System.err.println("[gamepad] controller support unavailable: $t")
            unavailable = "Controller support unavailable"
            pad = null
        }
    }


    private class Pad(
        val controller: Pointer,
        val name: String,
        val leftStickX: Pointer?,
        val leftStickY: Pointer?,
        val dpadLeft: Pointer?,
        val dpadRight: Pointer?,
        val dpadUp: Pointer?,
        val dpadDown: Pointer?,
        val leftTrigger: Pointer?,
        val rightTrigger: Pointer?,
        val buttonA: Pointer?,
        val buttonB: Pointer?,
        val buttonX: Pointer?,
        val rightShoulder: Pointer?,
        val buttonMenu: Pointer?,
    )


    private class Bridge {
        private val runtime: NativeLibrary = NativeLibrary.getInstance("/usr/lib/libobjc.A.dylib")
        private val msgSend: Function = runtime.getFunction("objc_msgSend")
        private val poolPush: Function = runtime.getFunction("objc_autoreleasePoolPush")
        private val poolPop: Function = runtime.getFunction("objc_autoreleasePoolPop")
        private val registerName: Function = runtime.getFunction("sel_registerName")

        private val gcControllerClass: Pointer

        private val selControllers = selector("controllers")
        private val selCount = selector("count")
        private val selObjectAtIndex = selector("objectAtIndex:")
        private val selExtendedGamepad = selector("extendedGamepad")
        private val selVendorName = selector("vendorName")
        private val selUtf8String = selector("UTF8String")
        private val selRetain = selector("retain")
        private val selRelease = selector("release")
        private val selDpad = selector("dpad")
        private val selLeftThumbstick = selector("leftThumbstick")
        private val selXAxis = selector("xAxis")
        private val selYAxis = selector("yAxis")
        private val selRespondsToSelector = selector("respondsToSelector:")
        val selValue = selector("value")

        init {
            val framework = listOf(
                "/System/Library/Frameworks/GameController.framework/GameController",
                "GameController",
            ).firstNotNullOfOrNull { candidate ->
                runCatching { NativeLibrary.getInstance(candidate) }.getOrNull()
            }
            requireNotNull(framework) { "GameController.framework could not be loaded" }

            val getClass = runtime.getFunction("objc_getClass")
            gcControllerClass = requireNotNull(getClass.invokePointer(arrayOf<Any>("GCController"))) {
                "GCController is not registered; this macOS is too old for GameController"
            }
        }

        fun pushAutoreleasePool(): Pointer? = poolPush.invokePointer(emptyArray<Any>())

        fun popAutoreleasePool(pool: Pointer?) = poolPop.invokeVoid(arrayOf<Any?>(pool))

        fun release(target: Pointer) = msgSend.invokeVoid(arrayOf<Any>(target, selRelease))

        fun floatProperty(target: Pointer, selector: Pointer): Float =
            msgSend.invokeFloat(arrayOf<Any>(target, selector))


        fun firstExtendedController(): Pointer? {
            val controllers = objectProperty(gcControllerClass, selControllers) ?: return null
            val count = msgSend.invokeLong(arrayOf<Any>(controllers, selCount))
            for (index in 0 until count) {
                val controller =
                    msgSend.invokePointer(arrayOf<Any>(controllers, selObjectAtIndex, index))
                        ?: continue
                if (objectProperty(controller, selExtendedGamepad) != null) return controller
            }
            return null
        }


        fun describeElements(controller: Pointer): String {
            val gamepad = objectProperty(controller, selExtendedGamepad) ?: return "extendedGamepad=nil"
            val dpad = objectProperty(gamepad, selDpad)
            val held = mutableListOf<String>()
            val missing = mutableListOf<String>()
            PROFILE_BUTTONS.forEach { name ->
                val button = element(gamepad, name)
                if (button == null) missing += name
                else if (floatProperty(button, selValue) >= BUTTON_THRESHOLD) held += name
            }
            DPAD_BUTTONS.forEach { name ->
                val button = element(dpad, name)
                if (button == null) missing += "dpad.$name"
                else if (floatProperty(button, selValue) >= BUTTON_THRESHOLD) held += "dpad.$name"
            }
            return "held=[${held.joinToString(" ")}] absent=[${missing.joinToString(" ")}]"
        }


        fun describeControllers(): String {
            val controllers =
                objectProperty(gcControllerClass, selControllers) ?: return "controllers=nil"
            val count = msgSend.invokeLong(arrayOf<Any>(controllers, selCount))
            if (count == 0L) return "controllers=[] count=0"
            val entries = (0 until count).joinToString("; ") { index ->
                val controller =
                    msgSend.invokePointer(arrayOf<Any>(controllers, selObjectAtIndex, index))
                if (controller == null) {
                    "[$index]=nil"
                } else {
                    val extended = objectProperty(controller, selExtendedGamepad)
                    "[$index] vendor=${string(objectProperty(controller, selVendorName))} " +
                        "extendedGamepad=${if (extended == null) "nil" else "yes"}"
                }
            }
            return "count=$count $entries"
        }

        fun readPad(controller: Pointer): Pad {
            msgSend.invokeVoid(arrayOf<Any>(controller, selRetain))
            val gamepad = objectProperty(controller, selExtendedGamepad)
            val dpad = gamepad?.let { objectProperty(it, selDpad) }
            return Pad(
                controller = controller,
                name = string(objectProperty(controller, selVendorName)) ?: "Gamepad",
                leftStickX = gamepad
                    ?.let { objectProperty(it, selLeftThumbstick) }
                    ?.let { objectProperty(it, selXAxis) },
                leftStickY = gamepad
                    ?.let { objectProperty(it, selLeftThumbstick) }
                    ?.let { objectProperty(it, selYAxis) },
                dpadLeft = element(dpad, "left"),
                dpadRight = element(dpad, "right"),
                dpadUp = element(dpad, "up"),
                dpadDown = element(dpad, "down"),
                leftTrigger = element(gamepad, "leftTrigger"),
                rightTrigger = element(gamepad, "rightTrigger"),
                buttonA = element(gamepad, "buttonA"),
                buttonB = element(gamepad, "buttonB"),
                buttonX = element(gamepad, "buttonX"),
                rightShoulder = element(gamepad, "rightShoulder"),
                buttonMenu = element(gamepad, "buttonMenu"),
            )
        }


        fun element(owner: Pointer?, name: String): Pointer? {
            val target = owner ?: return null
            val sel = selector(name)
            return if (responds(target, sel)) objectProperty(target, sel) else null
        }


        private fun responds(target: Pointer, selector: Pointer): Boolean =
            (msgSend.invokeInt(arrayOf<Any>(target, selRespondsToSelector, selector)) and 0xff) != 0

        private fun objectProperty(target: Pointer, selector: Pointer): Pointer? =
            msgSend.invokePointer(arrayOf<Any>(target, selector))

        private fun string(nsString: Pointer?): String? {
            val utf8 = nsString?.let { objectProperty(it, selUtf8String) } ?: return null
            return utf8.getString(0, "UTF-8")
        }


        private fun selector(name: String): Pointer =
            requireNotNull(registerName.invokePointer(arrayOf<Any>(name))) {
                "could not register selector $name"
            }

        private companion object {

            val PROFILE_BUTTONS = listOf(
                "buttonA", "buttonB", "buttonX", "buttonY",
                "leftShoulder", "rightShoulder", "leftTrigger", "rightTrigger",
                "leftThumbstickButton", "rightThumbstickButton",
                "buttonMenu", "buttonOptions", "buttonHome",
            )
            val DPAD_BUTTONS = listOf("left", "right", "up", "down")
        }
    }
}
