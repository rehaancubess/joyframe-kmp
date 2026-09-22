// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.render.water

import kotlin.math.pow

data class VortexConfig(


    val depth: Float = 6.0f,


    val funnelExponent: Float = 1.50f,


    val rings: Int = 16,


    val spokes: Int = 40,


    val ringBias: Float = 1.0f,


    val meshRadiusScale: Float = 1.06f,


    val lagoonHoleScale: Float = 0.82f,


    val rotationSpeed: Float = 0.46f,


    val inflowSpeed: Float = 0.94f,


    val armCount: Float = 4f,


    val spiralTightness: Float = 1.35f,


    val distortionStrength: Float = 0.72f,


    val turbulence: Float = 0.58f,

    val foamAmount: Float = 1.08f,
    val foamSpeed: Float = 1.15f,


    val foamBreakup: Float = 0.72f,


    val centerDarkness: Float = 0.56f,


    val throatFraction: Float = 0.38f,


    val rippleStrength: Float = 0.52f,


    val highlightIntensity: Float = 0.52f,


    val boatInteraction: Float = 1.15f,


    val boatReach: Float = 210f,


    val deathIntensity: Float = 1f,


    val deathDurationSeconds: Float = 0.85f,


    val dishEnergy: Float = WaterField.LAGOON_ENERGY,


    val dishDepth: Float = 1f,
) {
    init {
        require(rings >= 3) { "A vortex needs at least three rings to shade a funnel" }
        require(spokes >= 12) { "Fewer than twelve spokes reads as a polygon, not a circle" }
        require(lagoonHoleScale < meshRadiusScale) { "The dish must overlap the hole it covers" }
    }


    fun surfaceOffsetAt(normalisedRadius: Float): Float {
        val falloff = (1f - normalisedRadius.coerceIn(0f, 1f)).pow(funnelExponent)
        return -depth * falloff
    }

    companion object {

        const val EYE_CLEARANCE = 2.0f


        fun maximumDepth(water: WaterConfig): Float =
            WaterConfig.LAGOON_SURFACE_Y - (WaterConfig.OCEAN_SURFACE_Y + water.maximumAmplitude) -
                EYE_CLEARANCE
    }
}

data class VortexSurge(
    val hullX: Float = 0f,
    val hullZ: Float = 0f,

    val hullSuction: Float = 0f,

    val deathX: Float = 0f,
    val deathZ: Float = 0f,

    val deathEnvelope: Float = 0f,
)
