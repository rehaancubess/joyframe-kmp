// SPDX-License-Identifier: Apache-2.0
package example

import io.github.rehaancubess.joyframe.audio.PcmSound
import io.github.rehaancubess.joyframe.audio.SoundId
import kotlin.math.*

/** Every Lake Lab sound is generated in code, so the sample ships no audio assets. */
object LakeSounds {
    val hornOne = SoundId("horn-one")
    val hornTwo = SoundId("horn-two")
    val bell = SoundId("bell")
    val splash = SoundId("splash")

    fun bank() = mapOf(
        hornOne to PcmSound.tone(220f,.25f,.25f),
        hornTwo to PcmSound.tone(294f,.25f,.25f),
        bell to bell(),
        splash to splash(),
    )

    private const val RATE = 44_100

    /** A struck bell: a few inharmonic partials, each ringing down at its own rate. */
    private fun bell(): PcmSound {
        val count = (RATE*2.2f).toInt()
        val partials = listOf(1f to 1f, 2.76f to .5f, 5.4f to .25f, 8.93f to .12f)
        return PcmSound(ShortArray(count) { i ->
            val t = i.toFloat()/RATE
            val attack = min(1f, i/180f)
            val sum = partials.sumOf { (ratio,gain) ->
                (sin(2*PI*660*ratio*t) * gain * exp(-t*(1.6f+ratio*.9f))).toDouble()
            }
            (sum*attack*.22*32767).toInt().coerceIn(-32767,32767).toShort()
        })
    }

    /** A falling rush of filtered noise, for a boat swallowed by a whirlpool. */
    private fun splash(): PcmSound {
        val count = (RATE*.9f).toInt()
        var seed = 0x2545F491
        var low = 0f
        return PcmSound(ShortArray(count) { i ->
            seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
            val noise = (seed and 0xffff)/32768f - 1f
            val t = i.toFloat()/count
            // The low-pass closes as the sound falls away, which reads as water draining down.
            low += (noise-low) * (.35f*(1f-t)+.03f)
            (low*(1f-t)*(1f-t)*min(1f,i/400f)*.55f*32767).toInt().coerceIn(-32767,32767).toShort()
        })
    }
}

/**
 * An eight-second loop: plucked arpeggios over a soft pad, through a common I-vi-IV-V progression.
 * Notes are written into a circular buffer, so tails that run past the end wrap to the start and
 * the loop has no seam.
 */
fun lakeMusic(): PcmSound {
    val rate = 44_100
    val beat = .5f
    val bars = 4
    val count = (rate*beat*4*bars).toInt()
    val mix = FloatArray(count)
    fun hz(midi: Int) = 440f * 2f.pow((midi-69)/12f)
    // Root position triads, from C4.
    val chords = listOf(listOf(60,64,67), listOf(57,60,64), listOf(53,57,60), listOf(55,59,62))
    fun note(startSeconds: Float, midi: Int, seconds: Float, gain: Float, decay: Float, pad: Boolean) {
        val start = (startSeconds*rate).toInt()
        val length = (seconds*rate).toInt()
        val frequency = hz(midi)
        for (i in 0 until length) {
            val t = i.toFloat()/rate
            val envelope = if(pad) min(1f, t/.25f) * min(1f, (seconds-t)/.3f).coerceAtLeast(0f)
                else min(1f, i/90f) * exp(-t*decay)
            val tone = sin(2*PI*frequency*t).toFloat() + (if(pad) .18f else .32f)*sin(4*PI*frequency*t).toFloat()
            mix[(start+i) % count] += tone * envelope * gain
        }
    }
    val pattern = listOf(0,1,2,3,2,1,3,1)
    chords.forEachIndexed { bar, chord ->
        val barStart = bar*beat*4
        chord.forEach { note(barStart, it-12, beat*4, .09f, 0f, pad = true) }
        val tones = chord + (chord[0]+12)
        pattern.forEachIndexed { step, index -> note(barStart+step*beat/2, tones[index]+12, 1.4f, .13f, 3.2f, pad = false) }
        note(barStart, chord[0]-24, beat*2, .16f, 1.4f, pad = false)
    }
    val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-6f)
    return PcmSound(ShortArray(count) { (mix[it]/peak*.5f*32767).toInt().toShort() })
}
