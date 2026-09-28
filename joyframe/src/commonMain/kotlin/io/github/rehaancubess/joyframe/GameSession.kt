// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe

import io.github.rehaancubess.joyframe.input.*

data class GameStep(val deltaSeconds: Float, val seconds: Float, val tick: Long, val input: ActionFrame)

/** UI-thread simulation clock and input lifecycle. Rendering and audio remain host-owned. */
class GameSession(val input: ActionInput = ActionInput()) {
    private val clock = FrameClock()
    var paused = false
        set(value) { if(field != value) clock.reset(); field = value }
    var foreground = true
        set(value) { if(field != value) { clock.reset(); input.clear() }; field = value }
    var closed = false; private set
    var seconds = 0f; private set
    var tick = 0L; private set
    val active get() = !closed && foreground && !paused
    fun step(nanos: Long, controller: GamepadState? = null): GameStep {
        check(!closed) { "GameSession is closed" }
        val actions = if(foreground) input.poll(controller) else ActionFrame(Movement(),emptySet(),emptySet())
        if(GameAction.Pause in actions.pressed) paused = !paused
        val dt = if(active) clock.advance(nanos) else { clock.reset(); 0f }
        if(dt > 0) { seconds += dt; tick++ }
        return GameStep(dt,seconds,tick,actions)
    }
    fun reset() { clock.reset(); input.clear(); seconds = 0f; tick = 0 }
    fun close() { closed = true; input.clear(); clock.reset() }
}
