// SPDX-License-Identifier: Apache-2.0
package io.github.rehaancubess.joyframe.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

internal object PlatformAudio {
    @Volatile var appContext: Context? = null; private set
    fun initialize(context: Context) { appContext = context.applicationContext }
}
internal actual fun createAudioBackend(bank: AudioBank): AudioBackend = AndroidAudio(bank)
private class AndroidAudio(private val bank: AudioBank) : AudioBackend {
    private val soundIds = mutableMapOf<SoundId,Int>()
    private val loaded = ConcurrentHashMap<Int,Int>()
    private val streams = ArrayDeque<Int>()
    private val files = mutableListOf<File>()
    private var engine: MediaPlayer? = null
    private var pool: SoundPool? = null
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private fun asset(context: Context, bytes: ByteArray): File =
        File.createTempFile("joyframe-", ".wav",context.cacheDir).also { files += it; it.writeBytes(bytes) }
    private val worker = QueuedSoundEffects(
        load = {
            val context = checkNotNull(PlatformAudio.appContext) { "Call JoyframeAndroid.initialize(context) first" }
            val sounds = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes()).build()
            pool = sounds
            sounds.setOnLoadCompleteListener { _,id,status -> loaded[id]=status }
            for((id,wav) in bank.effects) {
                val sound = sounds.load(asset(context,wav).absolutePath,1)
                check(sound != 0) { "SoundPool rejected ${id.name}" }
                soundIds[id]=sound
            }
            withTimeout(10_000) {
                while(!soundIds.values.all { loaded.containsKey(it) }) delay(10)
            }
            check(soundIds.values.all { loaded[it] == 0 }) { "SoundPool could not decode a sound" }
            bank.loopBytes?.let { wav ->
                val player = MediaPlayer()
                engine = player
                player.also {
                    it.setAudioAttributes(attributes())
                    it.setDataSource(asset(context,wav).absolutePath)
                    it.isLooping=true; it.setVolume(bank.loopVolume,bank.loopVolume); it.prepare()
                }
            }
            files.forEach { it.delete() }; files.clear()
        },
        playNow = { id,volume,pan ->
            soundIds[id]?.let { sound ->
                // Balance law: centre keeps full volume in both ears, as before pan existed.
                val left = volume * minOf(1f, 1f - pan)
                val right = volume * minOf(1f, 1f + pan)
                val stream = pool?.play(sound,left,right,1,0,1f) ?: 0
                if(stream != 0) { if(streams.size==4) streams.removeFirst(); streams.addLast(stream) }
            }
        },
        stopNow = {
            streams.forEach { pool?.stop(it) }; streams.clear()
            engine?.let { if(it.isPlaying) it.pause() }
        },
        engineNow = { enabled -> engine?.let { if(enabled) it.start() else if(it.isPlaying) it.pause() } },
        releaseNow = {
            pool?.release(); pool=null; engine?.release(); engine=null
            soundIds.clear(); loaded.clear(); files.forEach { it.delete() }; files.clear()
        },
        loopVolumeNow = { volume -> engine?.setVolume(volume,volume) },
    )
    override val status get() = worker.status
    override fun prepare() = worker.prepare()
    override fun play(id: SoundId, volume: Float, pan: Float) = worker.play(id,volume,pan)
    override fun setLoopVolume(volume: Float) = worker.setLoopVolume(volume)
    override fun setForeground(value: Boolean) = worker.setForeground(value)
    override fun setEngineEnabled(enabled: Boolean) = worker.setEngine(enabled)
    override fun stop() = worker.stop()
    override fun close() = worker.close()
}
