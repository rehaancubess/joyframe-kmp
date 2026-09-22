// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

internal object Wav {
    const val SAMPLE_RATE = 44_100


    fun encode(samples: ShortArray): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArray(44 + dataSize)
        var cursor = 0
        fun ascii(text: String) {
            text.forEach { out[cursor++] = it.code.toByte() }
        }
        fun int32(value: Int) {
            out[cursor++] = (value and 0xff).toByte()
            out[cursor++] = ((value ushr 8) and 0xff).toByte()
            out[cursor++] = ((value ushr 16) and 0xff).toByte()
            out[cursor++] = ((value ushr 24) and 0xff).toByte()
        }
        fun int16(value: Int) {
            out[cursor++] = (value and 0xff).toByte()
            out[cursor++] = ((value ushr 8) and 0xff).toByte()
        }
        ascii("RIFF")
        int32(36 + dataSize)
        ascii("WAVE")
        ascii("fmt ")
        int32(16)
        int16(1) // PCM
        int16(1) // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2)
        int16(2)
        int16(16)
        ascii("data")
        int32(dataSize)
        samples.forEach { sample ->
            out[cursor++] = (sample.toInt() and 0xff).toByte()
            out[cursor++] = ((sample.toInt() ushr 8) and 0xff).toByte()
        }
        return out
    }
}
