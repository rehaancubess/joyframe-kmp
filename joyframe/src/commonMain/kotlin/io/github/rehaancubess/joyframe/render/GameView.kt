package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame

/** Displays a scene. Keep assets/cacheKey stable while moving instances.
 * The caller owns simulation timing; pausing this view never advances gameplay.
 * [options] sets render resolution and device pacing; the defaults are tuned for phones.
 * Native surfaces have platform overlay limitations; see docs/platforms.md.
 * [onFirstFrame] runs once, on the UI thread, when the first frame has been drawn: until then a native
 * surface shows black while it compiles shaders and uploads the assets, so a loader can stay up over it.
 */
@Composable
expect fun GameView(frame: GpuSceneFrame, modifier: Modifier = Modifier, active: Boolean = true,
                    options: GameViewOptions = GameViewOptions.Default, onFirstFrame: () -> Unit = {})
