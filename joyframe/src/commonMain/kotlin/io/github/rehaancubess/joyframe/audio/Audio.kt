package io.github.rehaancubess.joyframe.audio

/** User-defined sound identifier. No game-specific sound names are built into Joyframe. */
data class SoundId(val name: String) { init { require(name.isNotBlank()) } }

/** 44.1 kHz, mono, signed 16-bit PCM. Copied at construction so callers cannot mutate playback data. */
class PcmSound(samples: ShortArray) {
    init { require(samples.isNotEmpty()) }
    internal val wav = Wav.encode(samples.copyOf())
    companion object {
        /** Small asset-free cue for prototypes and samples, with a click-reducing envelope. */
        fun tone(frequencyHz: Float = 440f, seconds: Float = .12f, gain: Float = .3f): PcmSound {
            require(frequencyHz.isFinite() && frequencyHz in 20f..20_000f)
            require(seconds.isFinite() && seconds in .01f..10f)
            require(gain.isFinite() && gain in 0f..1f)
            val count = (seconds * 44_100).toInt()
            return PcmSound(ShortArray(count) { i ->
                val envelope = minOf(1f, i / 220f, (count - 1 - i) / 441f)
                (kotlin.math.sin(2.0 * kotlin.math.PI * frequencyHz * i / 44_100) * envelope * gain * 32767).toInt().toShort()
            })
        }
    }
}

/** Process-wide, preloaded audio bank. Configure once on the UI thread before playback.
 * Four overlapping effects maximum. Calls before preload finishes are dropped, not queued.
 * On Android call JoyframeAndroid.initialize(context) first. Web audio needs a user gesture.
 */
object Audio {
    private var installed = false
    private var hasLoop = false
    fun prepare(sounds: Map<SoundId, PcmSound>, loop: PcmSound? = null, loopVolume: Float = .2f) {
        check(!installed) { "Audio.prepare is process-wide; call once with the complete sound bank" }
        require(sounds.isNotEmpty() && sounds.size <= 128)
        require(loopVolume.isFinite() && loopVolume in 0f..1f)
        AudioBank.effects = sounds.mapValues { it.value.wav }
        AudioBank.loopBytes = loop?.wav ?: Wav.encode(shortArrayOf(0))
        AudioBank.loopVolume = loopVolume
        hasLoop = loop != null
        installed = true
        PlatformAudio.prepare()
    }
    fun play(id: SoundId, volume: Float = 1f) {
        require(volume.isFinite())
        if (installed && id in AudioBank.effects) PlatformAudio.play(id, volume.coerceIn(0f, 1f))
    }
    fun setLoopEnabled(enabled: Boolean) = PlatformAudio.setEngineEnabled(enabled && hasLoop)
    fun setForeground(foreground: Boolean) = PlatformAudio.setForeground(foreground)
    fun stop() = PlatformAudio.stop()
}
internal object AudioBank {
    var effects: Map<SoundId, ByteArray> = emptyMap()
    var loopBytes = Wav.encode(shortArrayOf(0))
    var loopVolume = .2f
}
internal expect object PlatformAudio {
    fun prepare()
    fun play(id: SoundId, volume: Float)
    fun setEngineEnabled(enabled: Boolean)
    fun setForeground(value: Boolean)
    fun stop()
}
