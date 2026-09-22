package io.github.rehaancubess.joyframe.input

/** Arcade mapping of the first connected standard controller; poll once per frame on the UI thread.
 * Horizontal uses left X / D-pad; vertical uses RT-LT / D-pad (iOS also uses left Y).
 * This alpha does not expose every physical button or multiple simultaneous controllers.
 */
data class GamepadState(
    val horizontal: Float = 0f,
    val vertical: Float = 0f,
    val action: Boolean = false,
    val pause: Boolean = false,
    val confirm: Boolean = false,
    val cancel: Boolean = false,
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
)
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
    fun poll(): GamepadState?
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
}
