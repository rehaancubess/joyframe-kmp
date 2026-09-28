package io.github.rehaancubess.joyframe.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

/** Use ComposeViewport with a dedicated div, not body: its shadow root otherwise hides this canvas.
 * The DOM canvas occupies the viewport rectangle; Compose overlays on top are not supported yet.
 */
@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    val canvas = remember { (document.createElement("canvas") as HTMLCanvasElement).apply {
        style.position = "fixed"
        style.setProperty("pointer-events", "none")
        // Compose's Skia canvas has its own stacking layer. Keep this bounded viewport above it.
        style.zIndex = "10"
        style.display = "block"
        setAttribute("data-joyframe-renderer","WebGL2")
    } }
    val latest = rememberUpdatedState(frame)
    val running = rememberUpdatedState(active)
    var failure by remember { mutableStateOf<String?>(null) }
    DisposableEffect(canvas) {
        document.body?.appendChild(canvas)
        var handle = 0
        var disposed = false
        var backend: GlSceneBackend? = null
        try {
            val context = checkNotNull(webGl2Context(canvas, true)) { "Joyframe requires WebGL2" }
            val api=WebGl(context)
            backend = checkNotNull(GlSceneBackend.create(api, GlslDialect.Es300)) { "WebGL shader initialization failed" }
            var previous: GpuSceneFrame? = null
            var width = 0
            var height = 0
            fun draw() {
                if(disposed) return
                try {
                    val current=latest.value
                    if(running.value || current != previous || width != canvas.width || height != canvas.height) {
                        width=canvas.width; height=canvas.height
                        backend?.render(current,width.coerceAtLeast(1),height.coerceAtLeast(1))
                        if(previous==null) {
                            check(api.getError()==0) { "WebGL reported an error while drawing the first frame" }
                            canvas.setAttribute("data-joyframe-state","ready")
                        }
                        previous=current
                    }
                    handle=window.requestAnimationFrame { draw() }
                } catch(error: Throwable) {
                    failure=error.message ?: "WebGL draw failed"
                    canvas.style.display="none"
                }
            }
            handle=window.requestAnimationFrame { draw() }
        } catch(error: Throwable) {
            failure=error.message ?: "WebGL unavailable"
            canvas.style.display="none"
        }
        onDispose {
            disposed=true; window.cancelAnimationFrame(handle)
            backend?.dispose(); canvas.parentNode?.removeChild(canvas)
        }
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
    }) { failure?.let { BasicText("Renderer unavailable: $it") } }
}
