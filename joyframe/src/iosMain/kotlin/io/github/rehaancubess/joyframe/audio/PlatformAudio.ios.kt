// SPDX-License-Identifier: Apache-2.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.rehaancubess.joyframe.audio

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.concurrent.atomics.AtomicInt
import platform.AVFAudio.*
import platform.Foundation.*
import platform.UIKit.*


internal actual fun createAudioBackend(bank: AudioBank): AudioBackend = IosAudio(bank)
private class IosAudio(private val bank: AudioBank) : AudioBackend {
    private var requestedForeground = true
    private var applicationActive = true
    private val effects = mutableMapOf<SoundId, List<AVAudioPlayer>>()
    private val playingVoices = ArrayDeque<AVAudioPlayer>()
    private var engine: AVAudioPlayer? = null
    private fun player(wav: ByteArray): AVAudioPlayer {
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        return AVAudioPlayer(data = data, error = null).apply { check(prepareToPlay()) { "AVAudioPlayer could not prepare WAV" } }
    }
    private val worker = QueuedSoundEffects(
        load = {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            for ((id, wav) in bank.effects) effects[id] = List(2) { player(wav) }
            engine = bank.loopBytes?.let(::player)?.apply { numberOfLoops = -1; volume = bank.loopVolume }
        },
        playNow = { id, volume ->
            playingVoices.removeAll { !it.playing }
            if (playingVoices.size < 4) {
                val voice = effects[id]?.firstOrNull { !it.playing }
                if (voice != null) {
                    voice.currentTime = 0.0
                    voice.volume = volume
                    if (voice.play()) {
                        playingVoices.addLast(voice)
                    }
                }
            }
        },
        stopNow = {
            playingVoices.forEach { it.stop() }
            playingVoices.clear()
            engine?.stop()
        },
        engineNow = { enabled ->
            if (enabled) {
                AVAudioSession.sharedInstance().setActive(true, null)
                engine?.play()
            } else engine?.pause()
        },
        releaseNow = { effects.clear(); playingVoices.clear(); engine=null },
    )
    private val observers = listOf(
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification,
            null, NSOperationQueue.mainQueue) { applicationActive=false; worker.setForeground(false) },
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification,
            null, NSOperationQueue.mainQueue) { applicationActive=true; worker.setForeground(requestedForeground) },
    )

    override val status get() = worker.status
    override fun setForeground(value: Boolean) { requestedForeground=value; worker.setForeground(value && applicationActive) }
    override fun prepare() = worker.prepare()
    override fun play(id: SoundId, volume: Float) = worker.play(id, volume)
    override fun setEngineEnabled(enabled: Boolean) = worker.setEngine(enabled)
    override fun stop() = worker.stop()
    override fun close() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        worker.close()
    }

}
