package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import kotlinx.coroutines.isActive
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    if (System.getProperty("os.name").contains("mac", true)) {
        MacGameView(frame, modifier, active)
        return
    }
    val canvas = remember { SceneCanvas(frame) }
    SideEffect { canvas.frame = frame }
    LaunchedEffect(canvas, active, if(active) null else frame) {
        do {
            withFrameNanos { }
            if (canvas.isValid && canvas.isShowing) canvas.render()
        } while (active && isActive)
    }
    DisposableEffect(canvas) { onDispose { canvas.release() } }
    SwingPanel(factory = { canvas }, modifier = modifier)
}
private class SceneCanvas(var frame: GpuSceneFrame) : AWTGLCanvas(contextData()) {
    private var api: LwjglGl? = null
    private var backend: GlSceneBackend? = null
    override fun initGL() {
        GL.createCapabilities()
        val gl = LwjglGl()
        api = gl
        backend = checkNotNull(GlSceneBackend.create(gl, GlslDialect.Core330)) { "Joyframe: GL shader initialization failed" }
    }
    override fun paintGL() {
        val scale = graphicsConfiguration?.defaultTransform
        backend?.render(frame, (width * (scale?.scaleX ?: 1.0)).toInt().coerceAtLeast(1),
            (height * (scale?.scaleY ?: 1.0)).toInt().coerceAtLeast(1))
        swapBuffers()
    }
    fun release() {
        if (api != null) runInContext { backend?.dispose(); api?.dispose() }
        backend = null; api = null
        disposeCanvas()
    }
}
private fun contextData() = GLData().apply {
    val mac = System.getProperty("os.name").contains("mac", true)
    majorVersion = if (mac) 4 else 3
    minorVersion = if (mac) 1 else 3
    // lwjgl3-awt's explicit CORE selection maps to 3.2 on macOS; null + 4.1 selects 4.1 Core.
    profile = if (mac) null else GLData.Profile.CORE
    samples = 4
    swapInterval = 1
}
