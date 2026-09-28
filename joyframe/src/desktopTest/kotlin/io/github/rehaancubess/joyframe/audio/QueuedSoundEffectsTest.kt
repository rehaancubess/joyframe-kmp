package io.github.rehaancubess.joyframe.audio

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlin.test.*

class QueuedSoundEffectsTest {
    @Test fun failedLoadIsNotReportedAsReadyAndReleasesResources() = runBlocking {
        val released=AtomicInteger(0)
        val plays=AtomicInteger(0)
        val worker=QueuedSoundEffects(load={ error("No device") },playNow={ _,_->plays.incrementAndGet() },
            stopNow={},releaseNow={ released.incrementAndGet() })
        worker.prepare()
        withTimeout(2000) { while(released.get()==0) delay(5) }
        assertIs<AudioStatus.Failed>(worker.status.value)
        worker.play(SoundId("cue"),1f); assertEquals(0,plays.get())
        worker.close(); assertEquals(AudioStatus.Closed,worker.status.value)
    }

    @Test fun closeDuringPreloadDoesNotPublishReadyOrPlayQueuedSound() = runBlocking {
        val entered=CompletableDeferred<Unit>(); val finish=CompletableDeferred<Unit>()
        val released=AtomicInteger(0); val plays=AtomicInteger(0)
        val worker=QueuedSoundEffects(load={ entered.complete(Unit); finish.await() },playNow={ _,_->plays.incrementAndGet() },
            stopNow={},releaseNow={ released.incrementAndGet() })
        worker.prepare(); withTimeout(2000) { entered.await() }
        worker.close(); worker.close(); finish.complete(Unit)
        withTimeout(2000) { while(released.get()==0) delay(5) }
        assertEquals(AudioStatus.Closed,worker.status.value)
        worker.play(SoundId("late"),1f); assertEquals(0,plays.get()); assertEquals(1,released.get())
    }

    @Test fun engineStateSurvivesPreloadAndResumesOnceAfterBackgrounding() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val starts = AtomicInteger(0)
        val stops = AtomicInteger(0)
        val worker = QueuedSoundEffects(load = {}, playNow = { _, _ -> },
            stopNow = { stops.incrementAndGet() }, engineNow = { if (it) starts.incrementAndGet() }, scope = scope)
        suspend fun until(condition: () -> Boolean) { withTimeout(2_000) { while (!condition()) delay(5) } }
        try {
            worker.setEngine(true)
            worker.prepare()
            until { starts.get() == 1 }
            repeat(1_000) { worker.setEngine(true) }
            delay(30)
            assertEquals(1, starts.get(), "steady state must not call the native engine again")
            worker.setForeground(false)
            until { stops.get() == 1 }
            worker.setForeground(true)
            until { starts.get() == 2 }
            worker.stop()
            until { stops.get() == 2 }
            worker.setForeground(false); worker.setForeground(true)
            delay(30)
            assertEquals(2, starts.get(), "mute/stop must not restart on foreground")
        } finally { scope.cancel() }
    }

    @Test fun aBlockedMixerDoesNotBlockTheCallerAndStopInvalidatesBackloggedCues() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val plays = AtomicInteger(0)
        val worker = QueuedSoundEffects(load = {}, playNow = { _, _ ->
            plays.incrementAndGet()
            entered.countDown()
            release.await(2, TimeUnit.SECONDS)
        }, stopNow = { stopped.countDown() }, scope = scope)
        try {
            worker.prepare()
            withTimeout(2_000) {
                while (entered.count != 0L) { worker.play(SoundId("first"), .3f); delay(5) }
            }
            // These calls must return while the native mixer is still blocked.
            repeat(100) { worker.play(SoundId("second"), .4f) }
            worker.stop()
            release.countDown()
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertEquals(1, plays.get(), "obsolete hits must not burst after unmute/resume")
        } finally { release.countDown(); scope.cancel() }
    }
}
