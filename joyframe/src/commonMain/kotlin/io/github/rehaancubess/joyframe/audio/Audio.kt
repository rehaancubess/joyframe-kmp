package io.github.rehaancubess.joyframe.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** User-defined sound identifier. No game-specific sound names are built into Joyframe. */
data class SoundId(val name: String) { init { require(name.isNotBlank()) } }

/** 44.1 kHz, mono, signed 16-bit PCM. Copied at construction so callers cannot mutate playback data. */
class PcmSound(samples: ShortArray) {
    init { require(samples.isNotEmpty()) }
    internal val wav = Wav.encode(samples.copyOf())
    val sampleCount: Int = samples.size
    val durationSeconds: Float get() = sampleCount / Wav.SAMPLE_RATE.toFloat()
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
    /** [pan] runs from -1 (left) through 0 (centre) to 1 (right). */
    fun play(id: SoundId, volume: Float = 1f, pan: Float = 0f) {
        require(volume.isFinite() && pan.isFinite())
        if(id in ids) backend.play(id,volume.coerceIn(0f,1f),pan.coerceIn(-1f,1f))
    }
    /** Plays at a position: volume and pan from [mix], for example `SpatialMix.of(...)` or `camera.hear(...)`. */
    fun play(id: SoundId, mix: SpatialMix, volume: Float = 1f) {
        if(mix.volume > 0f) play(id, volume.coerceIn(0f,1f) * mix.volume, mix.pan)
    }
    fun setLoopEnabled(enabled: Boolean) = backend.setEngineEnabled(enabled && hasLoop)
    /** Changes the loop's volume while it plays, for example an engine that grows louder with speed. */
    fun setLoopVolume(volume: Float) {
        require(volume.isFinite())
        backend.setLoopVolume(volume.coerceIn(0f,1f))
    }
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
    fun play(id: SoundId, volume: Float = 1f, pan: Float = 0f) { player?.play(id,volume,pan) }
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
    fun play(id: SoundId, volume: Float, pan: Float)
    fun setEngineEnabled(enabled: Boolean)
    fun setLoopVolume(volume: Float)
    fun setForeground(value: Boolean)
    fun stop()
    fun close()
}

/**
 * Volume and stereo pan for a sound at a position, heard by a listener on the ground plane.
 * Loudness falls off smoothly to silence at the range; pan follows the angle to the listener's right.
 */
data class SpatialMix(val volume: Float, val pan: Float) {
    init { require(volume.isFinite() && volume in 0f..1f && pan.isFinite() && pan in -1f..1f) }
    companion object {
        val Centre = SpatialMix(1f, 0f)

        /**
         * A listener at ([listenerX], [listenerZ]) facing ([forwardX], [forwardZ]) hears a sound at
         * ([sourceX], [sourceZ]). Full volume within [nearDistance], silent beyond [range]. Uses the
         * same right-hand convention as the renderer: facing +X, the listener's right is +Z.
         */
        fun of(listenerX: Float, listenerZ: Float, forwardX: Float, forwardZ: Float,
               sourceX: Float, sourceZ: Float, range: Float, nearDistance: Float = range * .08f): SpatialMix {
            require(range.isFinite() && range > 0f && nearDistance.isFinite() && nearDistance in 0f..range)
            val dx = sourceX - listenerX
            val dz = sourceZ - listenerZ
            val distance = sqrt(dx * dx + dz * dz)
            if (!distance.isFinite() || distance >= range) return SpatialMix(0f, 0f)
            val fade = if (range <= nearDistance) 1f else (1f - (distance - nearDistance) / (range - nearDistance)).coerceIn(0f, 1f)
            val forward = sqrt(forwardX * forwardX + forwardZ * forwardZ)
            val pan = if (distance < 1e-3f || forward < 1e-6f) 0f
                else ((dx * -forwardZ + dz * forwardX) / (distance * forward)).coerceIn(-1f, 1f)
            // Narrowed when close, so a sound right beside you does not sit entirely in one ear.
            val width = (distance / nearDistance.coerceAtLeast(1e-3f)).coerceIn(0f, 1f)
            return SpatialMix(fade * fade, pan * .85f * width)
        }
    }
}

/**
 * Background music on its own channel, independent of any [AudioPlayer]: looped, with volume and
 * fades. The track is decoded into memory like other Joyframe sounds (44.1 kHz PCM16), so keep loops
 * short; streaming and compressed formats are not supported yet. Browsers start it after a gesture.
 */
class MusicPlayer(track: PcmSound, volume: Float = .5f) {
    private val backend: AudioBackend
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Fades finish on a background dispatcher, so shared fields are volatile.
    @kotlin.concurrent.Volatile private var fade: Job? = null
    @kotlin.concurrent.Volatile private var level: Float
    @kotlin.concurrent.Volatile private var target: Float
    val status: StateFlow<AudioStatus> get() = backend.status
    /** True between [play] and [pause], including while fading out. */
    @kotlin.concurrent.Volatile var playing = false; private set

    init {
        require(volume.isFinite() && volume in 0f..1f)
        level = volume
        target = volume
        backend = createAudioBackend(AudioBank(emptyMap(), track.wav, volume))
        backend.prepare()
    }

    /** The resting volume. Setting it cancels any fade and applies at once. */
    var volume: Float
        get() = target
        set(value) {
            require(value.isFinite())
            fade?.cancel()
            target = value.coerceIn(0f, 1f)
            level = target
            backend.setLoopVolume(level)
        }

    fun play(fadeInSeconds: Float = 0f) {
        require(fadeInSeconds.isFinite() && fadeInSeconds >= 0f)
        fade?.cancel()
        if (!playing) {
            playing = true
            if (fadeInSeconds > 0f) level = 0f
            backend.setLoopVolume(level)
            backend.setEngineEnabled(true)
        }
        ramp(target, fadeInSeconds) {}
    }

    fun pause(fadeOutSeconds: Float = 0f) {
        require(fadeOutSeconds.isFinite() && fadeOutSeconds >= 0f)
        if (!playing) return
        fade?.cancel()
        ramp(0f, fadeOutSeconds) {
            playing = false
            backend.setEngineEnabled(false)
            level = target
            backend.setLoopVolume(level)
        }
    }

    /** Silences the music while the app is backgrounded or the page hidden, and resumes after. */
    fun setForeground(foreground: Boolean) = backend.setForeground(foreground)

    fun close() {
        fade?.cancel()
        scope.cancel()
        backend.close()
    }

    private fun ramp(to: Float, seconds: Float, done: () -> Unit) {
        if (seconds <= 0f) {
            level = to
            backend.setLoopVolume(level)
            done()
            return
        }
        val from = level
        fade = scope.launch {
            // 20 steps a second is smooth to the ear and keeps native volume calls rare.
            val steps = (seconds * 20f).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                delay((seconds * 1000f / steps).toLong().coerceAtLeast(1L))
                level = from + (to - from) * step / steps
                backend.setLoopVolume(level)
            }
            done()
        }
    }
}
