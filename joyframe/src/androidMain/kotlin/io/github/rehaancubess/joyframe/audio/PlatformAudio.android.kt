// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap


internal actual object PlatformAudio {
    @Volatile private var appContext: Context? = null
    private val soundIds = ConcurrentHashMap<SoundId, Int>()
    private val loaded = ConcurrentHashMap.newKeySet<Int>()
    private val streams = ArrayDeque<Int>()
    private var engine: MediaPlayer? = null
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val soundPool by lazy {
        SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes()).build().also { pool ->
            pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        }
    }
    private val worker = QueuedSoundEffects(
        load = {
            val context = checkNotNull(appContext)
            for ((id, wav) in AudioBank.effects) {
                val file = File(context.cacheDir, "joyframe-${id.name.hashCode()}.wav")
                file.writeBytes(wav)
                soundIds[id] = soundPool.load(file.absolutePath, 1)
            }
            val file = File(context.cacheDir, "joyframe-loop.wav")
            file.writeBytes(AudioBank.loopBytes)
            engine = MediaPlayer().apply {
                setAudioAttributes(attributes())
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(AudioBank.loopVolume, AudioBank.loopVolume)
                prepare()
            }
        },
        playNow = { id, volume ->
            val sound = soundIds[id]
            if (sound != null && sound in loaded) {
                val stream = soundPool.play(sound, volume, volume, 1, 0, 1f)
                if (stream != 0) {
                    if (streams.size == 4) streams.removeFirst()
                    streams.addLast(stream)
                }
            }
        },
        stopNow = {
            streams.forEach { soundPool.stop(it) }
            streams.clear()
            engine?.let { if (it.isPlaying) it.pause() }
        },
        engineNow = { enabled ->
            engine?.let { if (enabled) it.start() else if (it.isPlaying) it.pause() }
        },
    )
    fun initialize(context: Context) { appContext = context.applicationContext }
    actual fun setForeground(value: Boolean) = worker.setForeground(value)
    actual fun prepare() { if (appContext != null) worker.prepare() }
    actual fun play(id: SoundId, volume: Float) = worker.play(id, volume)
    actual fun setEngineEnabled(enabled: Boolean) = worker.setEngine(enabled)
    actual fun stop() = worker.stop()
}
