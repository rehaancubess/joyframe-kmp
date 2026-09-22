package io.github.rehaancubess.joyframe.render

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.isActive
import org.w3c.dom.HTMLCanvasElement

/** The DOM canvas occupies the viewport rectangle; Compose overlays on top are not supported yet. */
@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    val canvas = remember { (document.createElement("canvas") as HTMLCanvasElement).apply {
        style.position = "fixed"
        style.setProperty("pointer-events", "none")
        style.zIndex = "1"
    } }
    val latest = rememberUpdatedState(frame)
    DisposableEffect(canvas) {
        document.body?.appendChild(canvas)
        onDispose { canvas.parentNode?.removeChild(canvas) }
    }
    LaunchedEffect(canvas, active) {
        if (!active) return@LaunchedEffect
        val context = checkNotNull(webGl2Context(canvas, true)) { "Joyframe requires WebGL2" }
        val backend = checkNotNull(GlSceneBackend.create(WebGl(context), GlslDialect.Es300))
        try {
            while (isActive) {
                withFrameNanos { }
                backend.render(latest.value, canvas.width.coerceAtLeast(1), canvas.height.coerceAtLeast(1))
            }
        } finally { backend.dispose() }
    }
    Box(modifier.onGloballyPositioned {
        val position = it.positionInWindow()
        val ratio = window.devicePixelRatio
        canvas.style.left = "${position.x / ratio}px"
        canvas.style.top = "${position.y / ratio}px"
        canvas.style.width = "${it.size.width / ratio}px"
        canvas.style.height = "${it.size.height / ratio}px"
        if (canvas.width != it.size.width) canvas.width = it.size.width
        if (canvas.height != it.size.height) canvas.height = it.size.height
    })
}
