package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokePositionInterpolatorTest {
    @Test
    fun playingKeepsFrameClockInsteadOfSnappingToASlightlyLateSample() {
        val display = 10_000L
        val lateSample = 9_950L
        val next = nextSmoothLyricPositionMs(
            displayMs = display,
            sampledMs = lateSample,
            frameDeltaMs = 16L,
            playing = true
        )
        assertEquals(10_016L, next)
    }

    @Test
    fun pausedFollowsTheSampledPosition() {
        assertEquals(
            4_200L,
            nextSmoothLyricPositionMs(
                displayMs = 4_000L,
                sampledMs = 4_200L,
                frameDeltaMs = 16L,
                playing = false
            )
        )
    }

    @Test
    fun largeSeekSnapsToTheSample() {
        assertEquals(
            80_000L,
            nextSmoothLyricPositionMs(
                displayMs = 1_000L,
                sampledMs = 80_000L,
                frameDeltaMs = 16L,
                playing = true
            )
        )
    }

    @Test
    fun aStaleSampleDoesNotRewindTheKaraokeFill() {
        var display = 1_000L
        repeat(8) {
            display = nextSmoothLyricPositionMs(
                displayMs = display,
                sampledMs = 1_000L,
                frameDeltaMs = 16L,
                playing = true
            )
        }
        assertTrue(display >= 1_100L)
    }

    @Test
    fun backwardDriftBeyondToleranceIsGentlyCorrectedNotIgnored() {
        // 700ms behind is past the 600ms backward-drift tolerance but well under the 1500ms
        // seek threshold. Before this fix, everything short of a full seek-snap fell through to
        // returning the raw frame-clock prediction unchanged, so a persistent small clock bias
        // (nothing more exotic than frameDeltaMs measurement jitter) could drift the highlight
        // further and further ahead of the real vocal for an entire song with zero correction,
        // then finally hard-snap backward once the drift crossed 1500ms.
        val next = nextSmoothLyricPositionMs(
            displayMs = 10_700L,
            sampledMs = 10_000L,
            frameDeltaMs = 16L,
            playing = true
        )
        assertEquals(10_537L, next)
        assertTrue("should ease back toward the sample, not sit at the naive prediction", next < 10_716L)
    }
}
