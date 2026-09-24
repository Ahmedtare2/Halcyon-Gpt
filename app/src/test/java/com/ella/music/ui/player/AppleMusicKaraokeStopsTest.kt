package com.ella.music.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The karaoke sweep and its sheen are now issued straight to the draw phase as gradient brushes.
 * Color stops handed to a linear gradient must never step backwards, or the feathered edge
 * inverts partway along the word.
 */
class AppleMusicKaraokeStopsTest {

    private fun Array<Pair<Float, Color>>.assertAscending(label: String) {
        assertTrue("$label: stops outside 0..1 -> ${map { it.first }}", all { it.first in 0f..1f })
        toList().zipWithNext().forEach { (a, b) ->
            assertTrue(
                "$label: ${a.first} > ${b.first} in ${map { it.first }}",
                a.first <= b.first
            )
        }
    }

    @Test
    fun fillStopsStayOrdered() {
        var progress = 0f
        while (progress <= 1f) {
            karaokeFillStops(progress, Color.White, isRtl = false).assertAscending("ltr@$progress")
            karaokeFillStops(progress, Color.White, isRtl = true).assertAscending("rtl@$progress")
            progress += 0.01f
        }
    }

    @Test
    fun sheenStopsStayOrdered() {
        var progress = 0f
        while (progress <= 1f) {
            listOf(0.06f, 0.5f, 1f).forEach { glow ->
                karaokeSheenStops(progress, Color.White, glow, 1f, isRtl = false)
                    .assertAscending("ltr@$progress/$glow")
                karaokeSheenStops(progress, Color.White, glow, 1f, isRtl = true)
                    .assertAscending("rtl@$progress/$glow")
            }
            progress += 0.01f
        }
    }
}
