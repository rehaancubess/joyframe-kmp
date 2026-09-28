package io.github.rehaancubess.joyframe.render

import android.opengl.GLSurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    SplitGameView(listOf(frame), modifier, active)
}

@Composable
actual fun SplitGameView(frames: List<GpuSceneFrame>, modifier: Modifier, active: Boolean, gutter: PackedColor, gapDp: Float) {
    require(frames.size in 1..SplitLayout.MAX_PANES) { "SplitGameView needs 1 to ${SplitLayout.MAX_PANES} frames" }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember { SceneRenderer(frames) }
    val view = remember { GLSurfaceView(context).apply {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    } }
    SideEffect {
        renderer.frames = frames; renderer.gutter = gutter; renderer.gapPixels = (gapDp * density).roundToInt()
        if (active) view.requestRender()
    }
    DisposableEffect(view, active) {
        if (active) view.onResume() else view.onPause()
        onDispose { view.onPause() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
private class SceneRenderer(@Volatile var frames: List<GpuSceneFrame>) : GLSurfaceView.Renderer {
    @Volatile var gutter = SplitLayout.DefaultGutter
    @Volatile var gapPixels = 0
    private var backend: GlSceneBackend? = null
    private var width = 1
    private var height = 1
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backend = GlSceneBackend.create(AndroidGl(), GlslDialect.Es300)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height }
    override fun onDrawFrame(gl: GL10?) {
        backend?.renderPanes(frames, width.coerceAtLeast(1), height.coerceAtLeast(1), gutter, gapPixels)
    }
}
