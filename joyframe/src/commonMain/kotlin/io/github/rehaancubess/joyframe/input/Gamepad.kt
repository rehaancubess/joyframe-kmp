package io.github.rehaancubess.joyframe.input

/** Physical buttons by position, so the same code reads Xbox, PlayStation, Switch and MFi pads.
 * South is A / Cross, East is B / Circle, West is X / Square, North is Y / Triangle.
 */
enum class GamepadButton {
    South, East, West, North,
    LeftShoulder, RightShoulder, LeftTrigger, RightTrigger,
    Select, Start, LeftStick, RightStick,
    DpadUp, DpadDown, DpadLeft, DpadRight,
}

/** One controller's state, polled once per frame on the UI thread.
 * The arcade fields ([horizontal], [vertical], [action], ...) keep the original mapping: horizontal uses
 * left X / D-pad, vertical uses RT-LT / D-pad (iOS also uses left Y). The raw fields report every stick,
 * trigger and button; triggers above half travel also appear in [buttons].
 */
data class GamepadState(
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    val action: Boolean = false,
    val pause: Boolean = false,
    val confirm: Boolean = false,
    val cancel: Boolean = false,
    /** Raw stick axes: positive X is right; positive Y is up on every backend. */
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    /** Raw trigger travel, 0 released to 1 fully pulled. */
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
    val buttons: Set<GamepadButton> = emptySet(),
) {
    fun isPressed(button: GamepadButton): Boolean = button in buttons
}

/** A connected controller. [id] is stable while it stays connected, so it can own a player seat. */
data class ConnectedGamepad(val id: String, val name: String, val state: GamepadState)

data class GamepadStatus(
    val connected: Boolean,
    val name: String?,
    val detail: String,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
) {
    val isConnected get() = connected
    val displayName get() = name ?: "No controller"
}
expect object PlatformGamepad {
    /** The first connected controller, with [GamepadTuning] applied to the arcade fields. */
    fun poll(): GamepadState?
    /** Every connected controller in a stable order, with [GamepadTuning] applied. See [GamepadSeats]. */
    fun pollAll(): List<ConnectedGamepad>
    fun status(): GamepadStatus
    fun rumbleTest()
    /** Best effort. Unsupported devices/platforms are a no-op; see the support matrix. */
    fun rumble(durationMs: Int, intensity: Float)
}
data class GamepadOptions(
    val deadzone: Float = .15f,
    val sensitivity: Float = 1f,
    val invertHorizontal: Boolean = false,
    val invertVertical: Boolean = false,
    val swapConfirmCancel: Boolean = false,
    val gamepadRumbleEnabled: Boolean = true,
) {
    init {
        require(deadzone.isFinite() && deadzone in 0f..0.95f)
        require(sensitivity.isFinite() && sensitivity in 0f..4f)
    }
}
object GamepadTuning {
    @kotlin.concurrent.Volatile var current = GamepadOptions()
}
object GamepadMapping {
    fun apply(raw: GamepadState, options: GamepadOptions = GamepadTuning.current): GamepadState = raw.copy(
        horizontal = axis(raw.horizontal, options.deadzone, options.sensitivity, options.invertHorizontal),
        vertical = axis(raw.vertical, options.deadzone, options.sensitivity, options.invertVertical),
        confirm = if (options.swapConfirmCancel) raw.cancel else raw.confirm,
        cancel = if (options.swapConfirmCancel) raw.confirm else raw.cancel,
    )
    fun axis(value: Float, deadzone: Float = .15f, sensitivity: Float = 1f, invert: Boolean = false): Float {
        require(deadzone.isFinite() && deadzone in 0f..0.95f)
        require(sensitivity.isFinite() && sensitivity >= 0f)
        if (!value.isFinite()) return 0f
        val magnitude = kotlin.math.abs(value).coerceAtMost(1f)
        if (magnitude <= deadzone) return 0f
        val sign = if ((value < 0) xor invert) -1f else 1f
        return sign * (((magnitude - deadzone) / (1f - deadzone)) * sensitivity).coerceAtMost(1f)
    }

    /** The raw button set implied by the classic fields, for backends that read them individually. */
    internal fun buttons(
        south: Boolean, east: Boolean, west: Boolean, north: Boolean,
        leftShoulder: Boolean, rightShoulder: Boolean, leftTrigger: Float, rightTrigger: Float,
        select: Boolean, start: Boolean, leftStick: Boolean, rightStick: Boolean,
        up: Boolean, down: Boolean, left: Boolean, right: Boolean,
    ): Set<GamepadButton> = buildSet {
        if (south) add(GamepadButton.South); if (east) add(GamepadButton.East)
        if (west) add(GamepadButton.West); if (north) add(GamepadButton.North)
        if (leftShoulder) add(GamepadButton.LeftShoulder); if (rightShoulder) add(GamepadButton.RightShoulder)
        if (leftTrigger > .5f) add(GamepadButton.LeftTrigger); if (rightTrigger > .5f) add(GamepadButton.RightTrigger)
        if (select) add(GamepadButton.Select); if (start) add(GamepadButton.Start)
        if (leftStick) add(GamepadButton.LeftStick); if (rightStick) add(GamepadButton.RightStick)
        if (up) add(GamepadButton.DpadUp); if (down) add(GamepadButton.DpadDown)
        if (left) add(GamepadButton.DpadLeft); if (right) add(GamepadButton.DpadRight)
    }
}

/**
 * Stable local-player seats for couch multiplayer. The first controller seen takes seat 0, the next
 * seat 1, and so on. A controller keeps its seat while connected. When one disconnects only its seat
 * is freed, and the next new controller fills the lowest free seat, so nobody else is reshuffled.
 */
class GamepadSeats(val maxSeats: Int = 4) {
    init { require(maxSeats in 1..8) }
    private val owners = arrayOfNulls<String>(maxSeats)
    private val latest = arrayOfNulls<ConnectedGamepad>(maxSeats)

    /** Call once per frame with [PlatformGamepad.pollAll]. */
    fun update(pads: List<ConnectedGamepad>) {
        val present = pads.associateBy { it.id }
        for (seat in 0 until maxSeats) {
            val owner = owners[seat]
            if (owner != null && owner !in present) owners[seat] = null
        }
        pads.forEach { pad ->
            if (pad.id !in owners) {
                val free = owners.indexOfFirst { it == null }
                if (free >= 0) owners[free] = pad.id
            }
        }
        for (seat in 0 until maxSeats) latest[seat] = owners[seat]?.let(present::get)
    }

    /** The controller in [seat], or null when that seat has none. */
    fun pad(seat: Int): ConnectedGamepad? = latest.getOrNull(seat)
    fun state(seat: Int): GamepadState? = pad(seat)?.state
    val occupied: Int get() = latest.count { it != null }
    fun clear() { owners.fill(null); latest.fill(null) }
}
