package com.ella.music.ui.player

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Kotlin port of the motion ideas used by the uploaded am-lyrics engine.
 *
 * The web engine uses a spring-like onset for lyric motion and a shared visual unit for
 * character-timed words. This implementation keeps those ideas but follows the requested
 * Android behavior exactly:
 *
 * 1. A normal active word rises once and then stays at the lifted position for the entire
 *    lifetime of the active lyric line.
 * 2. A character-timed word shares the same base lift as a normal word.
 * 3. During the character timing interval, the whole word receives one smooth arch-shaped
 *    additional lift. The letters still highlight independently, but they never move vertically
 *    as separate objects.
 * 4. At the end of the character interval the extra lift is exactly zero, so the word returns to
 *    the normal word height and remains there until the line changes.
 * 5. There is no bounce, overshoot, or repeated up/down cycle.
 */
internal object AmLyricsMotionEngine {

    /**
     * Smooth monotonic word lift. It rises with a critically-damped spring curve and then holds.
     */
    fun wordLiftPx(
        textSizePx: Float,
        elapsedSinceWordStartMs: Long,
        enabled: Boolean,
        riseDurationMs: Long = 260L,
        scale: Float = 1f
    ): Float {
        if (!enabled || elapsedSinceWordStartMs < 0L) return 0f

        val baseLift = maxOf(textSizePx * 0.06f, 5f)
        val normalizedScale = scale.coerceIn(0f, 1.5f)
        val maxLift = baseLift * normalizedScale
        if (maxLift <= 0f) return 0f

        val duration = riseDurationMs.coerceAtLeast(1L)
        val progress = (elapsedSinceWordStartMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

        // Same family of smooth response used by the uploaded engine's springProgress(), but
        // clamped to one monotonic rise. There is deliberately no falling half of a sine wave.
        val phase = 2f * PI.toFloat() * progress
        val spring = 1f - (1f + phase) * kotlin.math.exp(-phase)
        return (maxLift * spring).coerceIn(0f, maxLift)
    }

    /**
     * Total vertical lift for one TTML character-timed word.
     *
     * The base word lift rises once and holds. On top of it, the character-timed portion traces
     * one arch: 0 -> peak -> 0. The arch is shared by the entire character group, so the letters
     * can highlight one-by-one without bobbing independently.
     */
    fun characterTimedWordLiftPx(
        textSizePx: Float,
        groupStartMs: Long,
        groupEndMs: Long,
        positionMs: Long,
        enabled: Boolean,
        baseRiseDurationMs: Long = 260L
    ): Float {
        if (!enabled || groupEndMs <= groupStartMs) return 0f

        val baseLift = wordLiftPx(
            textSizePx = textSizePx,
            elapsedSinceWordStartMs = positionMs - groupStartMs,
            enabled = true,
            riseDurationMs = baseRiseDurationMs,
            scale = 1f
        )

        val durationMs = (groupEndMs - groupStartMs).coerceAtLeast(1L)
        val elapsedMs = positionMs - groupStartMs
        if (elapsedMs <= 0L) return 0f

        // The extra character arc is proportional to the duration, with a modest cap so long
        // sustained words remain visually attached to the lyric line.
        val durationFactor = ((durationMs - 300L).toFloat() / 2700f).coerceIn(0f, 1f)
        val durationScale = 0.90f + durationFactor * 0.45f
        val arcPeak = (maxOf(textSizePx * 0.11f, 6f) * durationScale)
            .coerceAtMost(maxOf(textSizePx * 0.24f, 18f))

        if (elapsedMs >= durationMs) return baseLift

        val progress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

        // Smooth arch: starts at normal word height, reaches one peak, then returns exactly to
        // normal word height at the final character timestamp.
        val arch = sin((progress * PI).toFloat())
        val easedArch = arch * arch * (3f - 2f * arch)
        return baseLift + arcPeak * easedArch
    }
}
