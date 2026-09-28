// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import kotlin.math.sqrt

enum class GameAction { Interact, Pause, Confirm, Cancel }
/** X is right; Y is forward/up on every platform. */
data class Movement(val x: Float = 0f, val y: Float = 0f)
/**
 * One frame of combined input. [movement] is a direction (length at most 1) for twin-stick or
 * map-relative control. [drive] is for vehicles: x steers, y is throttle and also reads the triggers
 * (right forward, left reverse), each clamped to -1..1 independently.
 */
data class ActionFrame(
    val movement: Movement,
    val held: Set<GameAction>,
    val pressed: Set<GameAction>,
    val drive: Movement = movement,
)

/**
 * Which keys drive one [ActionInput]. Use [Default] for a single player, or [Wasd] and [Arrows] to
 * seat two players on one keyboard, each with their own input. A key appears in at most one role.
 */
data class KeyBindings(
    val up: Set<Key>,
    val down: Set<Key>,
    val left: Set<Key>,
    val right: Set<Key>,
    val interact: Set<Key>,
    val pause: Set<Key> = setOf(Key.P, Key.Escape),
    val confirm: Set<Key> = setOf(Key.Enter),
) {
    internal val all: Set<Key> = up + down + left + right + interact + pause + confirm

    companion object {
        val Default = KeyBindings(setOf(Key.W, Key.DirectionUp), setOf(Key.S, Key.DirectionDown),
            setOf(Key.A, Key.DirectionLeft), setOf(Key.D, Key.DirectionRight), setOf(Key.Spacebar))
        /** Player one of a shared keyboard: WASD, Space to act, P or Escape to pause. */
        val Wasd = KeyBindings(setOf(Key.W), setOf(Key.S), setOf(Key.A), setOf(Key.D), setOf(Key.Spacebar),
            confirm = emptySet())
        /** Player two of a shared keyboard: arrows, Enter or right Shift to act, no pause key. */
        val Arrows = KeyBindings(setOf(Key.DirectionUp), setOf(Key.DirectionDown), setOf(Key.DirectionLeft),
            setOf(Key.DirectionRight), setOf(Key.Enter, Key.ShiftRight), pause = emptySet(), confirm = emptySet())
    }
}

/** UI-thread input combiner. Poll once per frame, including while paused, to retain button edges. */
class ActionInput(val bindings: KeyBindings = KeyBindings.Default) {
    private val keys = mutableSetOf<Key>()
    private val touch = mutableSetOf<GameAction>()
    private val pending = mutableSetOf<GameAction>()
    private var previous = emptySet<GameAction>()
    var touchMovement = Movement()
    var options = GamepadOptions()
    fun key(key: Key, down: Boolean): Boolean {
        if (key !in bindings.all) return false
        if (down) {
            if(keys.add(key)) when(key) {
                in bindings.interact -> pending.add(GameAction.Interact)
                in bindings.pause -> pending.add(GameAction.Pause)
                in bindings.confirm -> pending.add(GameAction.Confirm)
            }
        } else keys.remove(key)
        return true
    }
    fun touch(action: GameAction, down: Boolean) { if (down) { if(touch.add(action)) pending.add(action) } else touch.remove(action) }
    fun clear() { keys.clear(); touch.clear(); pending.clear(); touchMovement = Movement(); previous = emptySet() }
    fun poll(pad: GamepadState? = null): ActionFrame {
        fun down(set: Set<Key>) = set.any { it in keys }
        fun axis(v: Float, invert: Boolean) = GamepadMapping.axis(v,options.deadzone,options.sensitivity,invert)
        val px = axis(pad?.leftStickX ?: 0f, options.invertHorizontal)
        val py = axis(pad?.leftStickY ?: 0f, options.invertVertical)
        val dpadX = pad.dpad(GamepadButton.DpadRight) - pad.dpad(GamepadButton.DpadLeft)
        val dpadY = pad.dpad(GamepadButton.DpadUp) - pad.dpad(GamepadButton.DpadDown)
        val keyX = (if(down(bindings.right)) 1f else 0f) - (if(down(bindings.left)) 1f else 0f)
        val keyY = (if(down(bindings.up)) 1f else 0f) - (if(down(bindings.down)) 1f else 0f)
        val x = keyX + touchMovement.x.finite() + px + dpadX
        val y = keyY + touchMovement.y.finite() + py + dpadY
        val length = sqrt(x*x+y*y).coerceAtLeast(1f)
        val triggers = (pad?.rightTrigger ?: 0f).trigger() - (pad?.leftTrigger ?: 0f).trigger()
        val drive = Movement(x.coerceIn(-1f,1f), (y + triggers).coerceIn(-1f,1f))
        val held = touch.toMutableSet()
        if(down(bindings.interact) || pad?.action == true) held += GameAction.Interact
        if(down(bindings.pause) || pad?.pause == true) held += GameAction.Pause
        val confirm = if(options.swapConfirmCancel) pad?.cancel else pad?.confirm
        val cancel = if(options.swapConfirmCancel) pad?.confirm else pad?.cancel
        if(down(bindings.confirm) || confirm == true) held += GameAction.Confirm
        if(cancel == true) held += GameAction.Cancel
        return ActionFrame(Movement(x/length,y/length),held,(held+pending)-previous,drive).also {
            previous = held.toSet(); pending.clear()
        }
    }
    private fun Float.finite() = if(isFinite()) coerceIn(-1f,1f) else 0f
    private fun Float.trigger() = if(isFinite() && this > .05f) coerceAtMost(1f) else 0f
    private fun GamepadState?.dpad(button: GamepadButton) = if(this != null && button in buttons) 1f else 0f
}

/** Attach to a focused game control surface. Does not steal keys from text fields or sliders.
 * Chain one per player (`.gameKeys(one).gameKeys(two)`): a key the first input does not bind passes on.
 */
fun Modifier.gameKeys(input: ActionInput): Modifier = onKeyEvent {
    when(it.type) {
        KeyEventType.KeyDown -> input.key(it.key,true)
        KeyEventType.KeyUp -> input.key(it.key,false)
        else -> false
    }
}
