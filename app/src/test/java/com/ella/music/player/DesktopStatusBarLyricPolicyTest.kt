package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopStatusBarLyricPolicyTest {
    @Test
    fun mergedSecondaryUsesExactlyOneSpace() {
        assertEquals(
            "Original Translation",
            mergeDesktopStatusBarLyric("Original ", " Translation", mergeSecondary = true)
        )
    }

    @Test
    fun disabledMergeLeavesMainTextUntouched() {
        assertEquals(
            "Original ",
            mergeDesktopStatusBarLyric("Original ", " Translation", mergeSecondary = false)
        )
    }

    @Test
    fun multiFragmentSecondaryIsFlattenedBeforeMerging() {
        assertEquals(
            "Main translation one pronunciation two",
            mergeDesktopStatusBarLyric(
                mainText = "Main",
                secondaryText = " translation one\n  pronunciation\ttwo ",
                mergeSecondary = true
            )
        )
    }

    @Test
    fun secondaryWhitespaceNormalizationKeepsOneVisualRun() {
        assertEquals(
            "wa ta shi ni ai sare tai",
            "  wa   ta shi\nni\t ai sare tai  ".normalizeDesktopStatusBarSecondaryText()
        )
    }
}
