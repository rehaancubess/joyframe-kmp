package io.github.rehaancubess.joyframe.audio

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** User-defined sound identifier. No game-specific sound names are built into Joyframe. */
data class SoundId(val name: String) { init { require(name.isNotBlank()) } }

/** 44.1 kHz, mono, signed 16-bit PCM. Copied at construction so callers cannot mutate playback data. */
class PcmSound(samples: ShortArray) {
    init { require(samples.isNotEmpty()) }
    internal val wav = Wav.encode(samples.copyOf())
    companion object {
        /** Read a RIFF/WAVE asset: PCM16 mono/stereo at 44.1 kHz. Stereo is mixed to mono. */
        fun fromWav(bytes: ByteArray): PcmSound = PcmSound(Wav.decode(bytes))
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

sealed class AudioStatus {
    data object Loading : AudioStatus()
    data object AwaitingGesture : AudioStatus()
    data object Ready : AudioStatus()
    data class Failed(val message: String) : AudioStatus()
    data object Closed : AudioStatus()
}

/** Scene-owned sound bank. Call close on disposal; native cleanup completes on the audio worker.
 * Four overlapping effects maximum. Early cues are dropped, never replayed in a burst.
 * Android requires JoyframeAndroid.initialize; browsers require an actual user gesture.
 */
class AudioPlayer(sounds: Map<SoundId,PcmSound>, loop: PcmSound? = null, loopVolume: Float = .2f) {
    private val backend: AudioBackend
    private val ids = sounds.keys.toSet()
    private val hasLoop = loop != null
    val status: StateFlow<AudioStatus> get() = backend.status
    init {
        require(sounds.isNotEmpty() && sounds.size <= 128)
        require(loopVolume.isFinite() && loopVolume in 0f..1f)
        backend = createAudioBackend(AudioBank(sounds.mapValues { it.value.wav },loop?.wav,loopVolume))
        backend.prepare()
    }
    /** Suspends until ready, failed or closed; AwaitingGesture may wait indefinitely without interaction. */
    suspend fun awaitReady(): AudioStatus = status.first {
        it is AudioStatus.Ready || it is AudioStatus.Failed || it is AudioStatus.Closed
    }
    fun play(id: SoundId, volume: Float = 1f) {
        require(volume.isFinite())
        if(id in ids) backend.play(id,volume.coerceIn(0f,1f))
    }
    fun setLoopEnabled(enabled: Boolean) = backend.setEngineEnabled(enabled && hasLoop)
    fun setForeground(foreground: Boolean) = backend.setForeground(foreground)
    fun stop() = backend.stop()
    fun close() = backend.close()
}

/** Optional process-wide facade; prefer AudioPlayer for scene ownership. UI-thread calls only. */
object Audio {
    private var player: AudioPlayer? = null
    fun prepare(sounds: Map<SoundId,PcmSound>, loop: PcmSound? = null, loopVolume: Float = .2f) {
        val next = AudioPlayer(sounds,loop,loopVolume)
        player?.close(); player = next
    }
    fun play(id: SoundId, volume: Float = 1f) { player?.play(id,volume) }
    fun setLoopEnabled(enabled: Boolean) { player?.setLoopEnabled(enabled) }
    fun setForeground(foreground: Boolean) { player?.setForeground(foreground) }
    fun stop() { player?.stop() }
    fun close() { player?.close(); player=null }
}
internal data class AudioBank(val effects: Map<SoundId,ByteArray>,val loopBytes: ByteArray?,val loopVolume: Float)
internal expect fun createAudioBackend(bank: AudioBank): AudioBackend
internal interface AudioBackend {
    val status: StateFlow<AudioStatus>
    fun prepare()
    fun play(id: SoundId, volume: Float)
    fun setEngineEnabled(enabled: Boolean)
    fun setForeground(value: Boolean)
    fun stop()
    fun close()
}
