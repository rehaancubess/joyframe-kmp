// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.input

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.sqrt

/** Place beside, not over, GameView for native-surface compatibility. Releases on cancellation. */
@Composable
fun TouchStick(input: ActionInput, modifier: Modifier = Modifier, onEngaged: () -> Unit = {}) {
    var position by remember { mutableStateOf(Movement()) }
    val engaged by rememberUpdatedState(onEngaged)
    DisposableEffect(input) { onDispose { input.touchMovement = Movement() } }
    Canvas(modifier.semantics { contentDescription = "Drag to steer; keyboard WASD also supported" }
        .pointerInput(input) {
            awaitEachGesture {
                val down = awaitFirstDown()
                engaged()
                fun update(p: Offset) {
                    val radius = (minOf(size.width,size.height) * .36f).coerceAtLeast(1f)
                    val x = (p.x-size.width/2f)/radius
                    val y = -(p.y-size.height/2f)/radius
                    val length = sqrt(x*x+y*y).coerceAtLeast(1f)
                    position = Movement(x/length,y/length)
                    input.touchMovement = position
                }
                try {
                    update(down.position); down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if(!pointer.pressed) break
                        update(pointer.position); pointer.consume()
                    } while(true)
                } finally { position = Movement(); input.touchMovement = position }
            }
        }) {
        val radius = size.minDimension * .36f
        drawCircle(Color(0xff213d49),radius)
        drawCircle(Color(0xff46616b),radius*.9f,style=androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(Color(0xff46616b),center-Offset(radius*.7f,0f),center+Offset(radius*.7f,0f),2f)
        drawLine(Color(0xff46616b),center-Offset(0f,radius*.7f),center+Offset(0f,radius*.7f),2f)
        drawCircle(Color(0xff82ead0),radius*.3f,center+Offset(position.x*radius,-position.y*radius))
    }
}
