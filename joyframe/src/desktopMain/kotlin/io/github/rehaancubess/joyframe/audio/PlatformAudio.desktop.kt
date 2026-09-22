// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl


internal actual object PlatformAudio {
    private val mixer: DesktopMixer = if (
        System.getProperty("os.name").contains("mac", ignoreCase = true)
    ) MacOpenAlMixer() else JavaSoundMixer()
    private val worker = QueuedSoundEffects(
        load = mixer::load,
        playNow = mixer::play,
        stopNow = mixer::stop,
        engineNow = mixer::setEngineEnabled,
    )

    actual fun setForeground(value: Boolean) = worker.setForeground(value)
    actual fun prepare() = worker.prepare()
    actual fun play(id: SoundId, volume: Float) = worker.play(id, volume)
    actual fun setEngineEnabled(enabled: Boolean) = worker.setEngine(enabled)
    actual fun stop() = worker.stop()
}

private interface DesktopMixer {
    fun load()
    fun play(id: SoundId, volume: Float)
    fun stop()
    fun setEngineEnabled(enabled: Boolean)
}


private class JavaSoundMixer : DesktopMixer {
    private val voices = HashMap<SoundId, List<Clip>>()
    private val playingVoices = ArrayDeque<Clip>()
    private var engine: Clip? = null

    private fun openClip(wav: ByteArray): Clip? = runCatching {
        val clip = AudioSystem.getClip()
        try {
            AudioSystem.getAudioInputStream(ByteArrayInputStream(wav)).use(clip::open)
            clip
        } catch (failure: Throwable) {
            clip.close()
            throw failure
        }
    }.getOrNull()

    private fun setVolume(clip: Clip, value: Float) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            gain.value = (20 * kotlin.math.log10(value.coerceAtLeast(.0001f).toDouble())).toFloat()
                .coerceIn(gain.minimum, gain.maximum)
        }
    }

    override fun load() {
        for ((id, wav) in AudioBank.effects) {
            voices[id] = (0 until 2).mapNotNull { openClip(wav) }
        }
        engine = openClip(AudioBank.loopBytes)?.also { setVolume(it, AudioBank.loopVolume) }
    }

    override fun play(id: SoundId, volume: Float) {
        playingVoices.removeAll { !it.isRunning }
        if (playingVoices.size >= MAX_SIMULTANEOUS_VOICES) return
        voices[id]?.firstOrNull { !it.isRunning }?.let { clip ->
            setVolume(clip, volume)
            clip.framePosition = 0
            clip.start()
            playingVoices.addLast(clip)
        }
    }

    override fun stop() {
        playingVoices.forEach { it.stop() }
        playingVoices.clear()
        engine?.stop()
    }

    override fun setEngineEnabled(enabled: Boolean) {
        if (enabled) engine?.loop(Clip.LOOP_CONTINUOUSLY) else engine?.stop()
    }
}


private class MacOpenAlMixer : DesktopMixer {
    private val api by lazy {
        Native.load("/System/Library/Frameworks/OpenAL.framework/OpenAL", MacOpenAl::class.java)
    }
    private var device: Pointer? = null
    private var context: Pointer? = null
    private var ready = false
    private val voices = HashMap<SoundId, IntArray>()
    private val playingVoices = ArrayDeque<Int>()
    private var engineSource = 0

    internal val available: Boolean get() = ready
    internal val hasPlayingVoice: Boolean
        get() = activate() && playingVoices.any { sourceState(it) == AL_PLAYING }

    override fun load() {
        runCatching {
            device = checkNotNull(api.alcOpenDevice(null)) { "OpenAL has no default output device" }
            context = checkNotNull(api.alcCreateContext(device, null)) { "OpenAL could not create a context" }
            check(api.alcMakeContextCurrent(context).toInt() != 0) { "OpenAL could not activate its context" }
            for ((id, wav) in AudioBank.effects) {
                val buffer = makeBuffer(wav)
                voices[id] = IntArray(2) { makeSource(buffer) }
            }
            engineSource = makeSource(makeBuffer(AudioBank.loopBytes)).also { source ->
                api.alSourcei(source, AL_LOOPING, AL_TRUE)
                api.alSourcef(source, AL_GAIN, AudioBank.loopVolume)
            }
            ready = true
            System.err.println("[audio] macOS OpenAL mixer ready")
        }.onFailure { failure ->
            ready = false
            System.err.println("[audio] Could not start the macOS mixer: ${failure.message}")
        }
    }

    override fun play(id: SoundId, volume: Float) {
        if (!activate()) return
        playingVoices.removeAll { sourceState(it) != AL_PLAYING }
        if (playingVoices.size >= MAX_SIMULTANEOUS_VOICES) return
        voices[id]?.firstOrNull { sourceState(it) != AL_PLAYING }?.let { source ->
            api.alSourcef(source, AL_GAIN, volume.coerceIn(0f, 1f))
            api.alSourceRewind(source)
            api.alSourcePlay(source)
            playingVoices.addLast(source)
        }
    }

    override fun stop() {
        if (!activate()) return
        playingVoices.forEach(api::alSourceStop)
        playingVoices.clear()
        if (engineSource != 0) api.alSourceStop(engineSource)
    }

    override fun setEngineEnabled(enabled: Boolean) {
        if (!activate() || engineSource == 0) return
        if (enabled) api.alSourcePlay(engineSource) else api.alSourceStop(engineSource)
    }

    private fun activate(): Boolean = ready && api.alcMakeContextCurrent(context).toInt() != 0

    private fun makeBuffer(wav: ByteArray): Int {
        check(wav.size > WAV_HEADER_BYTES) { "Audio buffer is empty" }
        val buffer = IntByReference()
        api.alGenBuffers(1, buffer)
        val pcm = wav.copyOfRange(WAV_HEADER_BYTES, wav.size)
        api.alBufferData(buffer.value, AL_FORMAT_MONO16, pcm, pcm.size, Wav.SAMPLE_RATE)
        check(api.alGetError() == AL_NO_ERROR) { "OpenAL rejected an audio buffer" }
        return buffer.value
    }

    private fun makeSource(buffer: Int): Int {
        val source = IntByReference()
        api.alGenSources(1, source)
        api.alSourcei(source.value, AL_BUFFER, buffer)
        check(api.alGetError() == AL_NO_ERROR) { "OpenAL rejected an audio source" }
        return source.value
    }

    private fun sourceState(source: Int): Int = IntByReference().also {
        api.alGetSourcei(source, AL_SOURCE_STATE, it)
    }.value

    private companion object {
        const val WAV_HEADER_BYTES = 44
        const val AL_NO_ERROR = 0
        const val AL_TRUE = 1
        const val AL_FORMAT_MONO16 = 0x1101
        const val AL_BUFFER = 0x1009
        const val AL_GAIN = 0x100A
        const val AL_LOOPING = 0x1007
        const val AL_SOURCE_STATE = 0x1010
        const val AL_PLAYING = 0x1012
    }
}

private interface MacOpenAl : Library {
    fun alcOpenDevice(deviceName: String?): Pointer?
    fun alcCreateContext(device: Pointer?, attributes: IntArray?): Pointer?
    fun alcMakeContextCurrent(context: Pointer?): Byte
    fun alGenBuffers(count: Int, buffers: IntByReference)
    fun alBufferData(buffer: Int, format: Int, data: ByteArray, size: Int, frequency: Int)
    fun alGenSources(count: Int, sources: IntByReference)
    fun alSourcei(source: Int, parameter: Int, value: Int)
    fun alSourcef(source: Int, parameter: Int, value: Float)
    fun alSourcePlay(source: Int)
    fun alSourceStop(source: Int)
    fun alSourceRewind(source: Int)
    fun alGetSourcei(source: Int, parameter: Int, value: IntByReference)
    fun alGetError(): Int
}

private const val MAX_SIMULTANEOUS_VOICES = 4
