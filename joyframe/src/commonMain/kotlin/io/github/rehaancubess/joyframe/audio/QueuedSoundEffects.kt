// SPDX-License-Identifier: Apache-2.0
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.rehaancubess.joyframe.audio

import kotlin.concurrent.atomics.AtomicInt
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


internal class QueuedSoundEffects(
    private val load: () -> Unit,
    private val playNow: (SoundId, Float) -> Unit,
    private val stopNow: () -> Unit,
    private val engineNow: (Boolean) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Cue(val id: SoundId?, val volume: Float, val epoch: Int) {
        val queuedAt = TimeSource.Monotonic.markNow()
    }
    private val epoch = AtomicInt(0)
    private val ready = AtomicInt(0)
    private val engineWanted = AtomicInt(0)
    private val foreground = AtomicInt(1)
    private val pending = Channel<Cue>(4, BufferOverflow.DROP_OLDEST)
    private val job = scope.launch(start = CoroutineStart.LAZY) {
        runCatching(load)
        ready.store(1)
        var appliedEpoch = 0
        var enginePlaying = false
        for (cue in pending) {
            val generation = epoch.load()
            if (cue.epoch != generation) continue
            runCatching {
                if (appliedEpoch != generation) {
                    stopNow()
                    enginePlaying = false
                    appliedEpoch = generation
                }
                val wantsEngine = engineWanted.load() == 1 && foreground.load() == 1
                if (wantsEngine != enginePlaying) {
                    engineNow(wantsEngine)
                    enginePlaying = wantsEngine
                }
                if (cue.id != null && foreground.load() == 1 &&
                    cue.queuedAt.elapsedNow().inWholeMilliseconds < 100) playNow(cue.id, cue.volume)
            }
        }
    }

    fun prepare() { job.start() }
    fun play(id: SoundId, volume: Float) {
        if (ready.load() == 0 || foreground.load() == 0) return
        pending.trySend(Cue(id, volume.coerceIn(0f, 1f), epoch.load()))
    }
    fun setEngine(enabled: Boolean) {
        val next = if (enabled) 1 else 0
        if (engineWanted.exchange(next) != next) wake()
    }
    fun setForeground(value: Boolean) {
        val next = if (value) 1 else 0
        if (foreground.exchange(next) == next) return
        if (!value) epoch.addAndFetch(1)
        wake()
    }
    fun stop() {
        engineWanted.store(0)
        epoch.addAndFetch(1)
        wake()
    }
    private fun wake() { pending.trySend(Cue(null, 0f, epoch.load())) }
}
