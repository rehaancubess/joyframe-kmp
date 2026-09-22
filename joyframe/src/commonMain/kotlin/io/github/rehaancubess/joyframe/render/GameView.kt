package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rehaancubess.joyframe.render.gpu.GpuSceneFrame

/** Displays a scene. Keep assets/cacheKey stable while moving instances.
 * The caller owns simulation timing; pausing this view never advances gameplay.
 * Native surfaces have platform overlay limitations; see docs/platforms.md.
 */
@Composable
expect fun GameView(frame: GpuSceneFrame, modifier: Modifier = Modifier, active: Boolean = true)
