package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import kotlinx.coroutines.isActive
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import kotlin.math.roundToInt

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean, options: GameViewOptions) {
    SplitGameView(listOf(frame), modifier, active, options = options)
}

@Composable
actual fun SplitGameView(frames: List<GpuSceneFrame>, modifier: Modifier, active: Boolean, gutter: PackedColor,
                         gapDp: Float, options: GameViewOptions) {
    require(frames.size in 1..SplitLayout.MAX_PANES) { "SplitGameView needs 1 to ${SplitLayout.MAX_PANES} frames" }
    if (isMac) {
        MacGameView(frames, modifier, active, gutter, gapDp)
        return
    }
    val canvas = remember { SceneCanvas(frames) }
    SideEffect { canvas.frames = frames; canvas.gutter = gutter; canvas.gapDp = gapDp }
    LaunchedEffect(canvas, active, if(active) null else frames) {
        do {
            withFrameNanos { }
            if (canvas.isValid && canvas.isShowing) canvas.render()
        } while (active && isActive)
    }
    DisposableEffect(canvas) { onDispose { canvas.release() } }
    SwingPanel(factory = { canvas }, modifier = modifier)
}
internal val isMac = System.getProperty("os.name").contains("mac", true)
private class SceneCanvas(var frames: List<GpuSceneFrame>) : AWTGLCanvas(contextData()) {
    var gutter = SplitLayout.DefaultGutter
    var gapDp = 3f
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
        val sx = scale?.scaleX ?: 1.0
        val sy = scale?.scaleY ?: 1.0
        backend?.renderPanes(frames, (width * sx).toInt().coerceAtLeast(1), (height * sy).toInt().coerceAtLeast(1),
            gutter, (gapDp * sx).roundToInt())
        swapBuffers()
    }
    fun release() {
        if (api != null) runInContext { backend?.dispose(); api?.dispose() }
        backend = null; api = null
        disposeCanvas()
    }
}
private fun contextData() = GLData().apply {
    majorVersion = if (isMac) 4 else 3
    minorVersion = if (isMac) 1 else 3
    // lwjgl3-awt's explicit CORE selection maps to 3.2 on macOS; null + 4.1 selects 4.1 Core.
    profile = if (isMac) null else GLData.Profile.CORE
    samples = 4
    swapInterval = 1
}
