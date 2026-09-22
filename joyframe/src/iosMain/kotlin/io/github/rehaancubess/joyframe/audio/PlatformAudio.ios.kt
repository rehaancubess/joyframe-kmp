// SPDX-License-Identifier: Apache-2.0
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.rehaancubess.joyframe.audio

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.concurrent.atomics.AtomicInt
import platform.AVFAudio.*
import platform.Foundation.*
import platform.UIKit.*


internal actual object PlatformAudio {
    private val effects = mutableMapOf<SoundId, List<AVAudioPlayer>>()
    private val playingVoices = ArrayDeque<AVAudioPlayer>()
    private var engine: AVAudioPlayer? = null
    private fun player(wav: ByteArray): AVAudioPlayer {
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        return AVAudioPlayer(data = data, error = null).apply { prepareToPlay() }
    }
    private val worker = QueuedSoundEffects(
        load = {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
            for ((id, wav) in AudioBank.effects) effects[id] = List(2) { player(wav) }
            engine = player(AudioBank.loopBytes).apply { numberOfLoops = -1; volume = AudioBank.loopVolume }
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
    )
    private val observers = listOf(
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationWillResignActiveNotification,
            null, NSOperationQueue.mainQueue) { worker.setForeground(false) },
        NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidBecomeActiveNotification,
            null, NSOperationQueue.mainQueue) { worker.setForeground(true) },
    )

    actual fun setForeground(value: Boolean) = worker.setForeground(value)
    actual fun prepare() = worker.prepare()
    actual fun play(id: SoundId, volume: Float) = worker.play(id, volume)
    actual fun setEngineEnabled(enabled: Boolean) = worker.setEngine(enabled)
    actual fun stop() = worker.stop()

}
