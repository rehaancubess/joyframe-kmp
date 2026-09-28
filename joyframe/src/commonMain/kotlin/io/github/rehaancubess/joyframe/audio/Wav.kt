// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

internal object Wav {
    const val SAMPLE_RATE = 44_100

    fun decode(bytes: ByteArray): ShortArray {
        fun text(offset: Int, count: Int) = bytes.copyOfRange(offset,offset+count).decodeToString()
        fun u16(offset: Int) = (bytes[offset].toInt() and 255) or ((bytes[offset+1].toInt() and 255) shl 8)
        fun u32(offset: Int): Long = (0..3).sumOf { (bytes[offset+it].toLong() and 255) shl (it*8) }
        require(bytes.size >= 12 && text(0,4)=="RIFF" && text(8,4)=="WAVE") { "Expected RIFF/WAVE" }
        val end = u32(4)+8
        require(end in 12L..bytes.size.toLong()) { "Truncated WAV" }
        var cursor = 12L
        var channels = 0
        var data: IntRange? = null
        while(cursor+8 <= end) {
            val start = cursor.toInt()
            val size = u32(start+4)
            val next = cursor+8+size
            require(next <= end) { "Truncated WAV chunk" }
            when(text(start,4)) {
                "fmt " -> {
                    require(size >= 16 && channels == 0) { "Invalid WAV format chunk" }
                    channels = u16(start+10)
                    require(u16(start+8)==1 && channels in 1..2 && u32(start+12)==SAMPLE_RATE.toLong()
                        && u16(start+22)==16 && u16(start+20)==channels*2) {
                        "Only 44.1 kHz PCM16 mono/stereo WAV is supported"
                    }
                }
                "data" -> { require(data==null) { "Multiple WAV data chunks" }; data=(start+8) until next.toInt() }
            }
            cursor=next+(size and 1)
        }
        val range = requireNotNull(data) { "WAV has no data chunk" }
        require(channels in 1..2 && range.count()>0 && range.count()%(channels*2)==0) { "Invalid PCM data" }
        return ShortArray(range.count()/(channels*2)) { frame ->
            val offset = range.first + frame*channels*2
            if(channels==1) u16(offset).toShort()
            else ((u16(offset).toShort().toInt()+u16(offset+2).toShort().toInt())/2).toShort()
        }
    }


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
