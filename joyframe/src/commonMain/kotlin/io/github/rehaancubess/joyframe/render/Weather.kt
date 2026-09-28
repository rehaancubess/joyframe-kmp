// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render

import io.github.rehaancubess.joyframe.render.gpu.*
import io.github.rehaancubess.joyframe.render.math.Vec3
import kotlin.math.floor
import kotlin.math.sin

/**
 * Camera-following precipitation. Particles live in three depth layers that wrap around the eye,
 * so a small, fixed number of instances fills the view wherever the camera goes, and near particles
 * are larger so the storm has depth without hiding the scene.
 *
 * Register the particle meshes once with [weatherParticles], then add [weather] to each frame and,
 * optionally, tint the frame's environment and sunlight with [environment] and [sunlight].
 */
enum class Weather(val sunlight: Float) {
    Clear(1f), Snow(.82f), Rain(.55f), Sand(.9f);

    /** Fog and ambient tuned to read as this weather, derived from [base]. */
    fun environment(base: SceneEnvironment): SceneEnvironment = when (this) {
        Clear -> base
        Snow -> base.copy(fogColor = PackedColor(0xffe6f0f5.toInt()), fogStart = base.fogStart * .55f,
            fogEnd = base.fogEnd * .7f, ambientIntensity = base.ambientIntensity * 1.08f,
            clearColor = PackedColor(0xffc9dce6.toInt()))
        Rain -> base.copy(fogColor = PackedColor(0xff8b9ca6.toInt()), fogStart = base.fogStart * .45f,
            fogEnd = base.fogEnd * .65f, ambientIntensity = base.ambientIntensity * .85f,
            clearColor = PackedColor(0xff7d8f99.toInt()))
        Sand -> base.copy(fogColor = PackedColor(0xffe2c595.toInt()), fogStart = base.fogStart * .5f,
            fogEnd = base.fogEnd * .72f, clearColor = PackedColor(0xffe8cfa0.toInt()))
    }

    internal val modelId: String get() = "joyframe.weather.${name.lowercase()}"
}

/** Adds the unit-sized particle meshes, materials and models that [weather] instances use. */
fun SceneBuilder.weatherParticles() {
    material(GpuMaterial("joyframe.weather.snow", PackedColor.White, emissive = PackedColor.White,
        emissiveStrength = .65f, roughness = 1f))
    material(GpuMaterial("joyframe.weather.rain", PackedColor(0xffd8ecf5.toInt()),
        emissive = PackedColor(0xffc4e0ee.toInt()), emissiveStrength = .4f, roughness = .2f))
    material(GpuMaterial("joyframe.weather.sand", PackedColor(0xffe3c28d.toInt()),
        emissive = PackedColor(0xffd9b67c.toInt()), emissiveStrength = .3f))
    mesh(Primitives.octahedron("joyframe.weather.snow", .6f, "joyframe.weather.snow"))
    box("joyframe.weather.rain", Vec3(1f, 1f, 1f), "joyframe.weather.rain")
    mesh(Primitives.octahedron("joyframe.weather.sand", .6f, "joyframe.weather.sand"))
    Weather.entries.filter { it != Weather.Clear }.forEach { model(it.modelId, ModelPart(it.modelId)) }
}

/**
 * Adds [kind]'s particles in front of [camera] at [seconds]. Each depth layer is centred ahead of the
 * eye so particles fill the view rather than the space behind or above it, and particles right at the
 * lens shrink away instead of filling the screen. [density] multiplies the particle count and [scale]
 * the layer sizes, speeds and particles (1 suits a scene whose vehicles are about 80 units long).
 * Sand drifts in a band just above [groundY]. Instances are Effects: no shadows, no gameplay.
 */
fun SceneFrameBuilder.weather(kind: Weather, camera: GpuCamera, seconds: Float, density: Float = 1f,
                              scale: Float = 1f, groundY: Float = 0f) {
    require(density.isFinite() && density in 0f..4f && scale.isFinite() && scale > 0f && seconds.isFinite())
    val base = when (kind) { Weather.Clear -> return; Weather.Snow -> 84; Weather.Rain -> 120; Weather.Sand -> 72 }
    val eye = camera.eye
    val forward = (camera.target - eye).normalized()
    val near = 110f * scale
    repeat((base * density).toInt()) { index ->
        val layer = index % 3
        val hash = index * 1_103_515_245 + when (kind) { Weather.Rain -> 777; Weather.Sand -> 54_321; else -> 12_345 }
        val ox = ((hash ushr 3) and 1023) / 1023f
        val oy = ((hash ushr 13) and 1023) / 1023f
        val oz = ((hash ushr 23) and 1023) / 1023f
        var position: Vec3
        var rotation: Vec3
        var size: Vec3
        var opacity: Float
        when (kind) {
            Weather.Snow -> {
                val half = scale * when (layer) { 0 -> 240f; 1 -> 640f; else -> 1_180f }
                val centre = eye + forward * half
                val height = half * 1.15f
                val fall = scale * (78f + (hash and 31))
                val drift = scale * (22f + (hash ushr 5 and 27))
                val flake = scale * when (layer) { 0 -> 6.4f + hash % 5; 1 -> 4.2f; else -> 2.6f }
                position = Vec3(wrap(centre.x + (ox - .5f) * half * 2f + seconds * drift, centre.x, half),
                    wrap(centre.y + height * .55f - seconds * fall - oy * height, centre.y, height * .5f),
                    wrap(centre.z + (oz - .5f) * half * 2f + seconds * drift * .35f, centre.z, half))
                rotation = Vec3(seconds * .7f + index * .13f, seconds * 1.1f, .2f)
                size = Vec3(flake, flake * 1.35f, flake)
                opacity = when (layer) { 0 -> .58f; 1 -> .4f; else -> .24f }
            }
            Weather.Rain -> {
                val half = scale * when (layer) { 0 -> 200f; 1 -> 520f; else -> 980f }
                val centre = eye + forward * half
                val height = half * 1.2f
                val fall = scale * (920f + (hash and 127))
                val length = scale * when (layer) { 0 -> 34f; 1 -> 26f; else -> 18f }
                val width = scale * when (layer) { 0 -> 1.4f; 1 -> 1.1f; else -> .8f }
                position = Vec3(wrap(centre.x + (ox - .5f) * half * 2f + seconds * 70f * scale, centre.x, half),
                    wrap(centre.y + height * .5f - seconds * fall - oy * height, centre.y, height * .5f),
                    wrap(centre.z + (oz - .5f) * half * 2f, centre.z, half))
                // Leaning into the wind so streaks read as falling, not hanging.
                rotation = Vec3(0f, 0f, .075f)
                size = Vec3(width, length, width)
                opacity = when (layer) { 0 -> .42f; 1 -> .3f; else -> .18f }
            }
            else -> {
                val half = scale * when (layer) { 0 -> 220f; 1 -> 580f; else -> 1_040f }
                val centre = eye + forward * half
                val drift = scale * (96f + (hash and 31))
                val lift = scale * (10f + (hash ushr 7 and 36))
                val grain = scale * when (layer) { 0 -> 5.8f + hash % 4; 1 -> 3.6f; else -> 2.2f }
                position = Vec3(wrap(centre.x + (ox - .5f) * half * 2f + seconds * drift, centre.x, half),
                    groundY + lift + oy * 18f * scale + sin(seconds * .55f + index) * 5f * scale,
                    wrap(centre.z + (oz - .5f) * half * 2f + seconds * drift * .18f, centre.z, half))
                rotation = Vec3(.15f, seconds * .4f + index * .09f, .08f)
                size = Vec3(grain * 2.4f, grain * .42f, grain)
                opacity = when (layer) { 0 -> .32f; 1 -> .2f; else -> .12f }
            }
        }
        val distance = (position - eye).length()
        if (distance < near) {
            val shrink = (distance / near).coerceIn(0f, 1f)
            size = size * shrink
            opacity *= shrink
        }
        if (opacity <= .01f) return@repeat
        instance("joyframe.weather.$index", kind.modelId, Transform3D(position, rotation, size), kind = InstanceKind.Effect,
            opacity = opacity)
    }
}

private fun wrap(value: Float, center: Float, half: Float): Float {
    val span = half * 2f
    if (span <= 1f) return center
    var t = (value - (center - half)) / span
    t -= floor(t)
    return center - half + t * span
}
