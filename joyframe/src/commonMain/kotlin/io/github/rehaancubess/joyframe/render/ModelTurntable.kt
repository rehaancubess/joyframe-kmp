// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * A slowly spinning showcase of one model: a character select, a store item, a garage.
 * The camera frames the model's bounds automatically. [extraYawRadians] lets a caller add rotation
 * (for example from a drag gesture beside the view). Stops advancing when [active] is false.
 */
@Composable
fun ModelTurntable(
    assets: GpuSceneAssets,
    modelId: String,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    secondsPerTurn: Float = 9f,
    extraYawRadians: Float = 0f,
    elevationDegrees: Float = 18f,
    materials: Map<String, String> = emptyMap(),
    environment: SceneEnvironment = SceneEnvironment(clearColor = PackedColor(0xff16323d.toInt()),
        fogStart = 1e6f, fogEnd = 2e6f),
    lights: List<DirectionalLight> = listOf(
        DirectionalLight(Vec3(-.45f, -1f, -.35f).normalized(), PackedColor(0xfffff4dc.toInt()), 2.1f, false),
        DirectionalLight(Vec3(.6f, -.3f, .7f).normalized(), PackedColor(0xffbfe6ff.toInt()), .7f, false),
    ),
) {
    require(secondsPerTurn.isFinite() && secondsPerTurn != 0f)
    val framing = remember(assets, modelId, elevationDegrees) { turntableCamera(assets, modelId, elevationDegrees) }
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(active) {
        var last: Long? = null
        while (active && isActive) {
            withFrameNanos { now ->
                last?.let { seconds += ((now - it) / 1e9f).coerceAtMost(.05f) }
                last = now
            }
        }
    }
    val yaw = seconds / secondsPerTurn * 2f * PI.toFloat() + extraYawRadians
    val frame = assets.frame(framing, seconds, environment = environment, lights = lights) {
        instance("turntable", modelId, Transform3D(rotation = Vec3(0f, yaw, 0f)), kind = InstanceKind.Dynamic,
            materials = materials)
    }
    GameView(frame, modifier, active)
}

/** A camera that fits [modelId]'s bounds whatever its yaw, looking slightly down from [elevationDegrees]. */
fun turntableCamera(assets: GpuSceneAssets, modelId: String, elevationDegrees: Float = 18f,
                    verticalFovDegrees: Float = 34f): GpuCamera {
    val bounds = assets.modelBounds(modelId)
    val centre = (bounds.minimum + bounds.maximum) * .5f
    // Any yaw fits inside the horizontal circle through the farthest corner.
    val halfX = maxOf(-bounds.minimum.x, bounds.maximum.x)
    val halfZ = maxOf(-bounds.minimum.z, bounds.maximum.z)
    val radius = maxOf(kotlin.math.sqrt(halfX * halfX + halfZ * halfZ), (bounds.maximum.y - bounds.minimum.y) * .5f, 1f)
    val distance = radius / tan(verticalFovDegrees * .5f * PI.toFloat() / 180f) * 1.15f
    val pitch = elevationDegrees * PI.toFloat() / 180f
    val target = Vec3(0f, centre.y, 0f)
    val eye = target + Vec3(0f, sin(pitch) * distance, cos(pitch) * distance)
    return GpuCamera(eye, target, verticalFovDegrees = verticalFovDegrees, nearPlane = distance * .05f,
        farPlane = distance * 4f)
}
