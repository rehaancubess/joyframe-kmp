package io.github.rehaancubess.joyframe.render

import android.opengl.GLSurfaceView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean) {
    val context = LocalContext.current
    val renderer = remember { SceneRenderer(frame) }
    val view = remember { GLSurfaceView(context).apply {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    } }
    SideEffect { renderer.frame = frame; if (active) view.requestRender() }
    DisposableEffect(view, active) {
        if (active) view.onResume() else view.onPause()
        onDispose { view.onPause() }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
private class SceneRenderer(@Volatile var frame: GpuSceneFrame) : GLSurfaceView.Renderer {
    private var backend: GlSceneBackend? = null
    private var width = 1
    private var height = 1
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backend = GlSceneBackend.create(AndroidGl(), GlslDialect.Es300)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height }
    override fun onDrawFrame(gl: GL10?) { backend?.render(frame, width.coerceAtLeast(1), height.coerceAtLeast(1)) }
}
