// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import kotlin.math.sqrt

enum class GameAction { Interact, Pause, Confirm, Cancel }
/** X is right; Y is forward/up on every platform. */
data class Movement(val x: Float = 0f, val y: Float = 0f)
data class ActionFrame(val movement: Movement, val held: Set<GameAction>, val pressed: Set<GameAction>)

/** UI-thread input combiner. Poll once per frame, including while paused, to retain button edges. */
class ActionInput {
    private val keys = mutableSetOf<Key>()
    private val touch = mutableSetOf<GameAction>()
    private val pending = mutableSetOf<GameAction>()
    private var previous = emptySet<GameAction>()
    var touchMovement = Movement()
    var options = GamepadOptions()
    fun key(key: Key, down: Boolean): Boolean {
        if (key !in supported) return false
        if (down) {
            if(keys.add(key)) when(key) {
                Key.Spacebar -> pending.add(GameAction.Interact)
                Key.P,Key.Escape -> pending.add(GameAction.Pause)
                Key.Enter -> pending.add(GameAction.Confirm)
            }
        } else keys.remove(key)
        return true
    }
    fun touch(action: GameAction, down: Boolean) { if (down) { if(touch.add(action)) pending.add(action) } else touch.remove(action) }
    fun clear() { keys.clear(); touch.clear(); pending.clear(); touchMovement = Movement(); previous = emptySet() }
    fun poll(pad: GamepadState? = null): ActionFrame {
        fun down(first: Key, second: Key = first) = first in keys || second in keys
        fun axis(v: Float, invert: Boolean) = GamepadMapping.axis(v,options.deadzone,options.sensitivity,invert)
        val px = axis(pad?.leftStickX ?: 0f, options.invertHorizontal)
        val py = axis(pad?.leftStickY ?: 0f, options.invertVertical)
        val x = (if(down(Key.D,Key.DirectionRight)) 1f else 0f) -
            (if(down(Key.A,Key.DirectionLeft)) 1f else 0f) + touchMovement.x.finite() + px
        val y = (if(down(Key.W,Key.DirectionUp)) 1f else 0f) -
            (if(down(Key.S,Key.DirectionDown)) 1f else 0f) + touchMovement.y.finite() + py
        val length = sqrt(x*x+y*y).coerceAtLeast(1f)
        val held = touch.toMutableSet()
        if(down(Key.Spacebar) || pad?.action == true) held += GameAction.Interact
        if(down(Key.Escape,Key.P) || pad?.pause == true) held += GameAction.Pause
        val confirm = if(options.swapConfirmCancel) pad?.cancel else pad?.confirm
        val cancel = if(options.swapConfirmCancel) pad?.confirm else pad?.cancel
        if(down(Key.Enter) || confirm == true) held += GameAction.Confirm
        if(cancel == true) held += GameAction.Cancel
        return ActionFrame(Movement(x/length,y/length),held,(held+pending)-previous).also {
            previous = held.toSet(); pending.clear()
        }
    }
    private fun Float.finite() = if(isFinite()) coerceIn(-1f,1f) else 0f
    private val supported = setOf(Key.W,Key.A,Key.S,Key.D,Key.DirectionUp,Key.DirectionDown,
        Key.DirectionLeft,Key.DirectionRight,Key.Spacebar,Key.Escape,Key.P,Key.Enter)
}

/** Attach to a focused game control surface. Does not steal keys from text fields or sliders. */
fun Modifier.gameKeys(input: ActionInput): Modifier = onKeyEvent {
    when(it.type) {
        KeyEventType.KeyDown -> input.key(it.key,true)
        KeyEventType.KeyUp -> input.key(it.key,false)
        else -> false
    }
}
