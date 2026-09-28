package io.github.rehaancubess.joyframe

import io.github.rehaancubess.joyframe.render.GameViewOptions
import kotlin.test.*

class SmoothnessTest {
    @Test fun renderSizeAppliesTheScaleAndTheGamesPhoneCap() {
        // An iPhone 15 Plus drawable, as measured in the source game: 2796x1290 -> 1920x886.
        val (w, h) = GameViewOptions(renderScale = 1f).renderSize(2796, 1290)
        assertEquals(1920, w); assertTrue(h in 884..887)
        // A 1080x2400 Android panel at the default 0.8 scale stays under the pixel cap.
        val (aw, ah) = GameViewOptions(renderScale = .8f).renderSize(1080, 2400)
        assertEquals(864, aw); assertEquals(1920, ah)
        assertEquals(800 to 600, GameViewOptions.Full.renderSize(800, 600))
        assertEquals(1 to 1, GameViewOptions().renderSize(0, 600))
        assertFailsWith<IllegalArgumentException> { GameViewOptions(renderScale = 2f) }
    }

    @Test fun framePacingCountsLateFramesAndPercentilesOverTheWindow() {
        val pacing = FramePacing(windowSeconds = 10f)
        var now = 0L
        pacing.record(now)
        repeat(95) { now += 16_666_667; pacing.record(now) }
        repeat(5) { now += 50_000_000; pacing.record(now) }
        val report = pacing.report
        assertEquals(100, report.frames)
        assertEquals(5f, report.latePercent, .01f)
        assertEquals(50f, report.worstMs, .01f)
        assertEquals(16.67f, report.p95Ms, .01f)
        assertEquals(50f, report.p99Ms, .01f)
        assertTrue(report.fps in 50f..56f)
        now += 5_000_000_000; pacing.record(now)
        assertEquals(0, pacing.report.frames, "a long pause starts a fresh window, not a 5 s dropped frame")
    }

    @Test fun framePacingKeepsOnlyTheRecentWindow() {
        val pacing = FramePacing(windowSeconds = 1f)
        var now = 0L
        pacing.record(now)
        repeat(600) { now += 16_666_667; pacing.record(now) }
        assertTrue(pacing.report.frames in 59..61)
        assertEquals(0f, pacing.report.latePercent)
    }
}
