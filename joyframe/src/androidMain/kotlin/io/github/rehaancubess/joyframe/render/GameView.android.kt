package io.github.rehaancubess.joyframe.render

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.opengl.GLSurfaceView
import android.os.PowerManager
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rehaancubess.joyframe.render.gl.*
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame
import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
actual fun GameView(frame: GpuSceneFrame, modifier: Modifier, active: Boolean, options: GameViewOptions) {
    SplitGameView(listOf(frame), modifier, active, options = options)
}

/**
 * The GL thread runs on its own and draws the newest published frame. Composition only publishes;
 * it never pumps the renderer. Chaining the two into one serial path is what held the source game at
 * 39-60 fps on a mid-range phone even though each side fitted in half a frame on its own.
 */
@Composable
actual fun SplitGameView(frames: List<GpuSceneFrame>, modifier: Modifier, active: Boolean, gutter: PackedColor,
                         gapDp: Float, options: GameViewOptions) {
    require(frames.size in 1..SplitLayout.MAX_PANES) { "SplitGameView needs 1 to ${SplitLayout.MAX_PANES} frames" }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val renderer = remember { SceneRenderer(frames) }
    val view = remember { SceneSurface(context, renderer) }
    SideEffect {
        view.options = options
        // The buffer is scaled, so the gutter is too.
        renderer.publish(frames, gutter, (gapDp * density * options.renderScale).roundToInt())
    }
    DisposableEffect(view, active) {
        if (active) view.onResume() else { renderer.wake(); view.onPause() }
        onDispose { renderer.wake(); view.onPause() }
    }
    DisposableEffect(view, options.holdSixtyHz, options.sustainedPerformance) {
        val restore = listOfNotNull(
            if (options.holdSixtyHz) SixtyHz.hold(view) else null,
            if (options.sustainedPerformance) SustainedPerformance.hold(view) else null,
        )
        onDispose { restore.forEach { it() } }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

private class SceneSurface(context: Context, private val renderer: SceneRenderer) : GLSurfaceView(context) {
    var options = GameViewOptions.Default
        set(value) { if (field != value) { field = value; applySize() } }

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        applySize()
    }

    /** Resizes the surface buffer, not the view: the compositor upscales and touch stays at full size. */
    private fun applySize() {
        if (width <= 0 || height <= 0) return
        val (w, h) = options.renderSize(width, height)
        holder.setFixedSize(w, h)
    }
}

private class SceneRenderer(initial: List<GpuSceneFrame>) : GLSurfaceView.Renderer {
    private val lock = ReentrantLock()
    private val arrived = lock.newCondition()
    private var frames = initial
    private var gutter = SplitLayout.DefaultGutter
    private var gapPixels = 0
    private var published = 0L
    // Starts behind so the first draw takes the opening frame without waiting.
    private var taken = -1L
    private var woken = false
    private var backend: GlSceneBackend? = null
    private var width = 1
    private var height = 1

    fun publish(next: List<GpuSceneFrame>, gutter: PackedColor, gapPixels: Int) = lock.withLock {
        frames = next; this.gutter = gutter; this.gapPixels = gapPixels
        published++
        arrived.signal()
    }

    /** Lets a waiting draw return at once, so pausing or tearing down is not held up. */
    fun wake() = lock.withLock { woken = true; arrived.signalAll() }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        backend = GlSceneBackend.create(AndroidGl(), GlslDialect.Es300)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { this.width = width; this.height = height }
    override fun onDrawFrame(gl: GL10?) {
        // Wait briefly for a newer frame; while nothing moves this bounds the redraw rate instead of spinning.
        val (current, colour, gap) = lock.withLock {
            var remaining = MAX_WAIT_NANOS
            try {
                while (published == taken && !woken && remaining > 0L) remaining = arrived.awaitNanos(remaining)
            } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            woken = false
            taken = published
            Triple(frames, gutter, gapPixels)
        }
        backend?.renderPanes(current, width.coerceAtLeast(1), height.coerceAtLeast(1), colour, gap)
    }

    private companion object { const val MAX_WAIT_NANOS = 50_000_000L }
}

/** Asks for the panel's own 60 Hz mode while the view is on screen; panels without one are left alone. */
private object SixtyHz {
    fun hold(view: View): (() -> Unit)? {
        val window = view.context.findActivity()?.window ?: return null
        @Suppress("DEPRECATION")
        val display = view.display ?: window.windowManager.defaultDisplay ?: return null
        val current = display.mode
        val target = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { abs(it.refreshRate - 60f) }
            ?.takeIf { abs(it.refreshRate - 60f) < 1f } ?: return null
        val previous = window.attributes.preferredDisplayModeId
        if (previous == target.modeId) return null
        window.attributes = window.attributes.apply { preferredDisplayModeId = target.modeId }
        return { window.attributes = window.attributes.apply { preferredDisplayModeId = previous } }
    }
}

/** Sustained performance mode where the device supports it, restored when the view leaves. */
private object SustainedPerformance {
    fun hold(view: View): (() -> Unit)? {
        val window = view.context.findActivity()?.window ?: return null
        val power = view.context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        if (!power.isSustainedPerformanceModeSupported) return null
        window.setSustainedPerformanceMode(true)
        return { window.setSustainedPerformanceMode(false) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
