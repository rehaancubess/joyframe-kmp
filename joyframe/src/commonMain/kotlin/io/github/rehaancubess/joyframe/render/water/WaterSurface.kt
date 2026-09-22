// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.water

import io.github.rehaancubess.joyframe.render.gpu.PackedColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class WaterQuality {

    Low,


    Medium,


    High,


    Ultra,
    ;


    val shaderLevel: Float get() = ordinal.toFloat()
}

expect fun defaultWaterQuality(): WaterQuality

data class WaveTrain(

    val headingDegrees: Float,

    val wavelength: Float,

    val amplitude: Float,

    val speed: Float,

    val setPeriodSeconds: Float = 0f,

    val setDepth: Float = 0f,

    val setPhase: Float = 0f,
) {
    private val waveNumber: Float = TWO_PI / wavelength
    private val heading: Float = headingDegrees * PI.toFloat() / 180f

    val kx: Float = cos(heading) * waveNumber
    val kz: Float = sin(heading) * waveNumber


    val angularSpeed: Float = -speed * waveNumber

    fun heightAt(x: Float, z: Float, time: Float): Float {
        val phase = x * kx + z * kz + time * angularSpeed
        val s = sin(phase)
        return (s + WAVE_PEAK * s * s * s) * amplitude
    }


    fun slopeGain(x: Float, z: Float, time: Float): Float {
        val phase = x * kx + z * kz + time * angularSpeed
        val s = sin(phase)
        return cos(phase) * (1f + 3f * WAVE_PEAK * s * s) * amplitude
    }


    companion object {
        const val WAVE_PEAK = 0.16f
        const val TWO_PI = 6.2831855f
    }


    fun envelopeAt(time: Float): Float {
        if (setPeriodSeconds <= 0f || setDepth <= 0f) return 1f
        return 1f - setDepth * (0.5f + 0.5f * sin(time * TWO_PI / setPeriodSeconds + setPhase))
    }
}

data class WaterSlope(val alongX: Float, val alongZ: Float)

data class WaterConfig(
    val quality: WaterQuality = defaultWaterQuality(),


    val swell: List<WaveTrain> = listOf(
        WaveTrain(
            headingDegrees = 8f, wavelength = 1_720f, amplitude = 4.6f, speed = 88f,
            setPeriodSeconds = 71f, setDepth = 0.45f, setPhase = 0f,
        ),
        WaveTrain(
            headingDegrees = 24f, wavelength = 980f, amplitude = 3.4f, speed = 64f,
            setPeriodSeconds = 43f, setDepth = 0.35f, setPhase = 1.7f,
        ),
        WaveTrain(
            headingDegrees = -47f, wavelength = 620f, amplitude = 2.0f, speed = 51f,
            setPeriodSeconds = 29f, setDepth = 0.40f, setPhase = 3.1f,
        ),
        WaveTrain(
            headingDegrees = 74f, wavelength = 430f, amplitude = 1.2f, speed = 40f,
            setPeriodSeconds = 19f, setDepth = 0.50f, setPhase = 4.4f,
        ),
        WaveTrain(
            headingDegrees = 131f, wavelength = 310f, amplitude = 1.05f, speed = 31f,
            setPeriodSeconds = 13f, setDepth = 0.58f, setPhase = 5.9f,
        ),
    ),


    val shallowColor: PackedColor = PackedColor(0xff4ad4c8.toInt()),


    val deepColor: PackedColor = PackedColor(0xff056a82.toInt()),


    val depthTint: Float = 0.78f,


    val fresnelStrength: Float = 0.58f,


    val fresnelHardness: Float = 0.85f,

    val specularStrength: Float = 1.85f,


    val specularSharpness: Float = 240f,


    val rippleStrength: Float = 0.32f,


    val rippleScale: Float = 0.085f,

    val rippleSpeed: Float = 1.55f,

    val causticIntensity: Float = 0.48f,

    val causticScale: Float = 0.038f,

    val causticSpeed: Float = 0.64f,

    val foamIntensity: Float = 1.45f,


    val shoreFoamCells: Float = 5.2f,


    val shoreFoamSurge: Float = 0.14f,


    val chopPatchScale: Float = 0.14f,


    val shallowCalm: Float = 0.52f,


    val foamCrestStart: Float = 0.40f,
    val foamCrestEnd: Float = 0.86f,


    val detailFadeStart: Float = 1_050f,


    val detailFadeEnd: Float = 3_600f,


    val animationSpeed: Float = 1f,
) {

    val maximumAmplitude: Float = swell.fold(0f) { total, wave -> total + wave.amplitude }


    val inverseMaximumAmplitude: Float = if (maximumAmplitude > 0f) 1f / maximumAmplitude else 0f


    val shoreFoamEdge: Float get() = (shoreFoamCells / WaterField.DEPTH_RAMP_CELLS).coerceIn(0f, 1f)


    private val detailRangeScale: Float get() = if (quality == WaterQuality.Ultra) ULTRA_DETAIL_RANGE else 1f
    val resolvedDetailFadeStart: Float get() = detailFadeStart * detailRangeScale
    val resolvedDetailFadeEnd: Float get() = detailFadeEnd * detailRangeScale

    init {
        require(swell.size == SWELL_TRAINS) {
            "The shaders unroll exactly $SWELL_TRAINS wave trains; got ${swell.size}"
        }
        require(maximumAmplitude < OCEAN_TO_LAGOON_GAP) {
            "Swell peaks at $maximumAmplitude but only $OCEAN_TO_LAGOON_GAP units " +
                "separate the open sea from the lagoon surface"
        }
        require(detailFadeStart < detailFadeEnd) { "Detail LOD range is inverted" }
        require(foamCrestStart < foamCrestEnd) { "Foam crest range is inverted" }
    }


    fun heightAt(x: Float, z: Float, time: Float, energy: Float): Float {
        if (energy <= 0f) return 0f
        val scaled = time * animationSpeed
        var height = 0f
        for (index in swell.indices) {
            val train = swell[index]
            height += train.heightAt(x, z, scaled) * train.envelopeAt(scaled)
        }
        return height * energy
    }


    fun slopeAt(x: Float, z: Float, time: Float, energy: Float): WaterSlope {
        if (energy <= 0f) return WaterSlope(0f, 0f)
        val scaled = time * animationSpeed
        var alongX = 0f
        var alongZ = 0f
        for (index in swell.indices) {
            val train = swell[index]
            val gain = train.slopeGain(x, z, scaled) * train.envelopeAt(scaled)
            alongX += gain * train.kx
            alongZ += gain * train.kz
        }
        return WaterSlope(alongX * energy, alongZ * energy)
    }


    fun packSwell(out: FloatArray, timeSeconds: Float, offset: Int = 0) {
        require(out.size >= offset + SWELL_TRAINS * FLOATS_PER_TRAIN) {
            "Swell needs ${SWELL_TRAINS * FLOATS_PER_TRAIN} floats from $offset, got ${out.size}"
        }
        val scaled = timeSeconds * animationSpeed
        for (index in 0 until SWELL_TRAINS) {
            val train = swell[index]
            val base = offset + index * FLOATS_PER_TRAIN
            out[base] = train.kx
            out[base + 1] = train.kz
            out[base + 2] = train.angularSpeed * animationSpeed
            out[base + 3] = train.amplitude * train.envelopeAt(scaled)
        }
    }

    companion object {

        const val SWELL_TRAINS = 5


        const val MINIMUM_WAVELENGTH = 300f


        const val FLOATS_PER_TRAIN = 4


        const val ULTRA_DETAIL_RANGE = 2.2f


        const val OCEAN_SURFACE_Y = -20f
        const val LAGOON_SURFACE_Y = 0.45f


        const val OCEAN_TO_LAGOON_GAP = 18f
    }
}

enum class WaterShaping {

    None,


    Lagoon,


    LagoonDressing,


    OpenSea,


    Vortex,
    ;

    val isWater: Boolean get() = this != None


    val sampledFromShore: Boolean get() = this == Lagoon || this == LagoonDressing


    val isVortex: Boolean get() = this == Vortex


    val shade: Float
        get() = when (this) {
            None -> 0f
            LagoonDressing -> DRESSING_SHADE
            Lagoon, OpenSea, Vortex -> 1f
        }

    companion object {

        const val DRESSING_SHADE = 0.35f
    }
}

class WaterField(
    private val shoreDistance: Array<IntArray>,
    private val cellSize: Float,
) {
    private val columns = shoreDistance.size
    private val rows = if (columns > 0) shoreDistance[0].size else 0


    fun energyAt(x: Float, z: Float): Float {
        val distance = shoreDistanceAt(x, z)
        if (distance <= 0f) return 0f
        val ramp = ((distance - 1f) / SHORE_RAMP_CELLS).coerceIn(0f, 1f)
        return LAGOON_ENERGY * ramp * ramp * (3f - 2f * ramp)
    }


    fun depthAt(x: Float, z: Float): Float {
        val distance = shoreDistanceAt(x, z)
        if (distance <= 0f) return 0f
        return (distance / DEPTH_RAMP_CELLS).coerceIn(0f, 1f)
    }


    private fun shoreDistanceAt(x: Float, z: Float): Float {
        val fx = x / cellSize - 0.5f
        val fz = z / cellSize - 0.5f
        val ix = floor(fx).toInt()
        val iz = floor(fz).toInt()
        val tx = fx - ix
        val tz = fz - iz
        val near = cellAt(ix, iz) + (cellAt(ix + 1, iz) - cellAt(ix, iz)) * tx
        val far = cellAt(ix, iz + 1) + (cellAt(ix + 1, iz + 1) - cellAt(ix, iz + 1)) * tx
        return near + (far - near) * tz
    }


    private fun cellAt(ix: Int, iz: Int): Float =
        if (ix in 0 until columns && iz in 0 until rows) shoreDistance[ix][iz].toFloat() else 0f

    companion object {

        const val SHORE_RAMP_CELLS = 7f


        const val LAGOON_ENERGY = 0.94f


        const val DEPTH_RAMP_CELLS = 20f
    }
}
