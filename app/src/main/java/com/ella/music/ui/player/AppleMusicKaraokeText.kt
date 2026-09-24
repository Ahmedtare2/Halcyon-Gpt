package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricWord
import com.ella.music.data.parser.isKanjiOrHangul
import com.ella.music.data.parser.isRtlText
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

internal fun isInlineRubyPronunciation(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty()) return false
    if (compact.any { it.isAppleMusicLatinLetter() }) return false
    return compact.any { it.isAppleMusicKana() || it.isKanjiOrHangul() }
}

private fun Char.isAppleMusicKana(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
}

internal fun appleMusicKaraokeLiftPx(
    wordLiftEnabled: Boolean,
    textSizePx: Float,
    elapsedSinceWordStartMs: Long,
    wordLiftScale: Float = 1f,
    popDurationMs: Long = 220L
): Float = if (wordLiftEnabled && elapsedSinceWordStartMs in 0..popDurationMs) {
    // Was tied to the word's own fill progress (0..1 across its *entire* singing duration), so a
    // 150ms word's whole rise-and-fall happened in 150ms — too fast to read as a deliberate lift
    // at all — while a multi-second held note stretched the same arch out gracefully, which is
    // why only sustained/letter-split words looked like they were lifting. Real Apple Music's pop
    // is a fixed-duration snap timed to a word's *onset*, independent of how long that word takes
    // to sing, so every word gets the same snappy, clearly visible bounce right as it starts.
    val t = elapsedSinceWordStartMs.toFloat() / popDurationMs.toFloat()
    val bounce = kotlin.math.sin((t * kotlin.math.PI).toFloat())
    maxOf(textSizePx * 0.06f, 5f) * bounce * wordLiftScale.coerceIn(0f, 1f)
} else {
    0f
}

@Composable
internal fun TimedLyricText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    active: Boolean,
    style: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    wordLiftScale: Float = 1f,
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
    sustainGlowScale: Float = 1f,
    activeGlowProgress: State<Float>? = null,
    ttmlMotionEnabled: Boolean = false,
    singleLine: Boolean = false,
    statusBarMarquee: Boolean = false,
    followWordFocus: Boolean = false,
    pronunciation: String = "",
    pronunciationWords: List<LyricWord> = emptyList(),
    rubyBelow: Boolean = false,
    splitRubyByCharacter: Boolean = false,
    rubyStyle: TextStyle? = null,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // TTML may encode the blank before a word as part of that word. Move it to the prior
    // karaoke unit before wrapping so every v1 line, including wrapped continuations, starts
    // at the same left edge. Right-aligned v2 rows are visually tolerant of this, but v1 is not.
    // wordLiftEnabled controls per-word vertical lift only; timed karaoke fill still renders.
    val sourceWords = remember(text, words, splitRubyByCharacter) {
        words.moveLeadingSpacesToPreviousWord().let { source ->
            if (splitRubyByCharacter) source.splitForAppleMusicRuby() else source
        }
    }
    val timedWords = remember(text, sourceWords, sustainThresholdMs) {
        sourceWords.toAppleMusicRenderWords(text, sustainThresholdMs)
    }
    val rubies = remember(timedWords, pronunciation, pronunciationWords) {
        rubiesForTimedWords(
            words = timedWords.map { it.word },
            pronunciationWords = pronunciationWords,
            pronunciation = pronunciation
        )
    }
    if (timedWords.isEmpty()) {
        val fallbackGlowProgress = activeGlowProgress?.value?.coerceIn(0f, 1f) ?: 0f
        val fallbackStyle = if (fallbackGlowProgress > 0f) {
            style.copy(
                shadow = Shadow(
                    color = contentColor.copy(
                        alpha = (APPLE_ACTIVE_GLOW_BASE_ALPHA * 0.90f * fallbackGlowProgress * style.color.alpha)
                            .coerceIn(0f, 1f)
                    ),
                    offset = Offset.Zero,
                    blurRadius = APPLE_ACTIVE_GLOW_BASE_BLUR_PX * APPLE_ACTIVE_GLOW_MULTIPLIER * fallbackGlowProgress
                )
            )
        } else {
            style
        }
        BasicText(
            text = text,
            style = fallbackStyle,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            softWrap = !singleLine,
            overflow = TextOverflow.Clip,
            modifier = modifier.then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
        )
        return
    }
    // Keep the timed units as individual layout children. This is the same important distinction
    // as the smooth renderer: a long timed line breaks between singable units, not at arbitrary
    // glyphs, so highlighted and dim lines retain identical visual rows.
    val horizontalArrangement = when (style.textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    // The clock ticks every frame. Hand the words a State they read in the draw/layer phase
    // instead of a value they take as a parameter, so a frame no longer recomposes the whole
    // line's worth of word subtrees just to move one feathered edge.
    val positionState = rememberUpdatedState(positionMs)
    val content: @Composable () -> Unit = {
        var wordIndex = 0
        while (wordIndex < timedWords.size) {
            val renderWord = timedWords[wordIndex]
            val groupKey = renderWord.characterGroupKey
            if (groupKey == null) {
                AppleMusicKaraokeWord(
                    renderWord = renderWord,
                    positionMs = positionState,
                    active = active,
                    baseStyle = style,
                    contentColor = contentColor,
                    wordLiftEnabled = wordLiftEnabled,
                    wordLiftScale = wordLiftScale,
                    sustainGlowScale = sustainGlowScale,
                    activeGlowProgress = activeGlowProgress,
                    ruby = rubies.getOrNull(wordIndex).orEmpty(),
                    rubyStyle = rubyStyle,
                    rubyBelow = rubyBelow,
                    onWordClick = onWordClick,
                    onLongPress = onLongPress
                )
                wordIndex++
            } else {
                // Every consecutive entry sharing this key came from splitting one original word
                // into characters (see toAppleMusicRenderWords). Rendering them inside a single
                // non-wrapping Row means the row-wrapping layout above only ever sees one item
                // for the whole word, so it can never break apart across a line wrap the way
                // handing each character to it separately would risk.
                val groupStart = wordIndex
                var groupEnd = wordIndex
                while (groupEnd < timedWords.size && timedWords[groupEnd].characterGroupKey == groupKey) {
                    groupEnd++
                }
                val groupWord = timedWords[groupStart]
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom,
                    modifier = if (ttmlMotionEnabled) {
                        Modifier.graphicsLayer {
                            translationY = -appleMusicTtmlCharacterGroupLiftPx(
                                textSizePx = style.fontSize.toPx(),
                                groupStartMs = groupWord.characterGroupStartMs ?: groupWord.word.startMs,
                                groupEndMs = groupWord.characterGroupEndMs ?: groupWord.word.endMs,
                                positionMs = positionState.value,
                                enabled = wordLiftEnabled
                            )
                            transformOrigin = TransformOrigin(0.5f, if (rubyBelow) 0f else 1f)
                        }
                    } else {
                        Modifier
                    }
                ) {
                    for (charIndex in groupStart until groupEnd) {
                        AppleMusicKaraokeWord(
                            renderWord = timedWords[charIndex],
                            positionMs = positionState,
                            active = active,
                            baseStyle = style,
                            contentColor = contentColor,
                            wordLiftEnabled = wordLiftEnabled,
                            wordLiftScale = wordLiftScale,
                            sustainGlowScale = sustainGlowScale,
                            activeGlowProgress = activeGlowProgress,
                            useIndividualLift = !ttmlMotionEnabled,
                            ruby = rubies.getOrNull(charIndex).orEmpty(),
                            rubyStyle = rubyStyle,
                            rubyBelow = rubyBelow,
                            onWordClick = onWordClick,
                            onLongPress = onLongPress
                        )
                    }
                }
                wordIndex = groupEnd
            }
        }
    }
    if (singleLine) {
        if (followWordFocus) {
            AppleMusicFocusedTimedRow(
                timedWords = timedWords,
                positionMs = positionMs,
                active = active,
                horizontalArrangement = horizontalArrangement,
                rubyBelow = rubyBelow,
                modifier = modifier,
                content = content
            )
        } else {
            Row(
                modifier = modifier.then(if (statusBarMarquee) Modifier.basicMarquee() else Modifier),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
            ) {
                content()
            }
        }
    } else {
        // FlowRow measures each visual row independently. With centered/right-aligned lyrics it
        // therefore lets wrapped rows acquire a different origin than the first row (especially
        // visible for a translation below a long English line). Lay rows out ourselves against
        // the full line width so every row shares the exact same alignment anchor.
        AppleMusicTimedWordRows(
            textAlign = style.textAlign,
            rubyBelow = rubyBelow,
            modifier = modifier
        ) {
            content()
        }
    }
}

/**
 * Scroll a long single-line lyric to the currently sung word. Unlike basicMarquee this has a
 * stable end position: the first word stays at the leading edge until focus reaches it, and the
 * last word stays visible after the line finishes instead of restarting from the beginning.
 */
@Composable
private fun AppleMusicFocusedTimedRow(
    timedWords: List<AppleMusicRenderWord>,
    positionMs: Long,
    active: Boolean,
    horizontalArrangement: Arrangement.Horizontal,
    rubyBelow: Boolean = false,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var contentWidthPx by remember(timedWords) { mutableIntStateOf(0) }
        val focusIndex = if (!active || timedWords.isEmpty()) {
            0
        } else {
            timedWords.indexOfLast { positionMs >= it.word.startMs }
                .coerceIn(0, timedWords.lastIndex)
        }
        val focusFraction = if (timedWords.size <= 1) {
            0f
        } else {
            focusIndex.toFloat() / (timedWords.lastIndex).coerceAtLeast(1)
        }
        val targetOffset = ((contentWidthPx - viewportWidthPx).coerceAtLeast(0f) * focusFraction)
        val animatedOffset = remember { Animatable(0f) }
        LaunchedEffect(targetOffset) {
            animatedOffset.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 90)
            )
        }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .wrapContentWidth(unbounded = true)
                .onSizeChanged { contentWidthPx = it.width }
                .graphicsLayer { translationX = if (isRtl) animatedOffset.value else -animatedOffset.value },
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
        ) {
            content()
        }
    }
}

@Composable
private fun AppleMusicTimedWordRows(
    textAlign: TextAlign,
    rubyBelow: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val availableWidth = constraints.maxWidth
            .takeUnless { it == androidx.compose.ui.unit.Constraints.Infinity }
            ?: measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = availableWidth)
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(childConstraints)
            val rowIndex = rows.lastIndex
            val currentWidth = rowWidths.getOrElse(rowIndex) { 0 }
            val shouldWrap = rowIndex >= 0 && currentWidth > 0 && currentWidth + placeable.width > availableWidth
            if (shouldWrap) {
                rows += mutableListOf(placeable)
                rowWidths += placeable.width
                rowHeights += placeable.height
            } else if (rowIndex >= 0) {
                rows[rowIndex] += placeable
                rowWidths[rowIndex] = currentWidth + placeable.width
                rowHeights[rowIndex] = maxOf(rowHeights[rowIndex], placeable.height)
            } else {
                rows += mutableListOf(placeable)
                rowWidths += placeable.width
                rowHeights += placeable.height
            }
        }

        val layoutWidth = availableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = rowHeights.sum().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            var y = 0
            rows.indices.forEach { rowIndex ->
                val rowWidth = rowWidths[rowIndex]
                var x = when (textAlign) {
                    TextAlign.End -> (layoutWidth - rowWidth).coerceAtLeast(0)
                    TextAlign.Center -> ((layoutWidth - rowWidth) / 2).coerceAtLeast(0)
                    else -> 0
                }
                rows[rowIndex].forEach { placeable ->
                    // Bottom-align when ruby is above so furigana grows upward without dropping the kanji.
                    // Top-align when ruby is below so furigana grows downward without raising the kanji.
                    val placeableY = if (rubyBelow) y else y + rowHeights[rowIndex] - placeable.height
                    placeable.placeRelative(x, placeableY)
                    x += placeable.width
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

@Composable
private fun AppleMusicKaraokeWord(
    renderWord: AppleMusicRenderWord,
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    wordLiftScale: Float = 1f,
    sustainGlowScale: Float = 1f,
    activeGlowProgress: State<Float>? = null,
    useIndividualLift: Boolean = true,
    ruby: String = "",
    rubyStyle: TextStyle? = null,
    rubyBelow: Boolean = false,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val word = renderWord.word
    val rubyContent: @Composable () -> Unit = {
        if (ruby.isNotBlank() && rubyStyle != null) {
            val tracking = when {
                ruby.length >= 5 -> (-0.55).sp
                ruby.length >= 4 -> (-0.30).sp
                else -> (-0.10).sp
            }
            val scaledRubyStyle = if (ruby.length >= 5) {
                rubyStyle.copy(
                    fontSize = rubyStyle.fontSize * 0.88f,
                    letterSpacing = tracking
                )
            } else {
                rubyStyle.copy(letterSpacing = tracking)
            }
            BasicText(
                text = ruby,
                style = scaledRubyStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(
                if (onWordClick != null) {
                    Modifier.pointerInput(word.startMs, onWordClick, onLongPress) {
                        detectTapGestures(
                            onTap = { onWordClick(word.startMs) },
                            onLongPress = onLongPress?.let { press -> { press() } }
                        )
                    }
                } else Modifier
            )
            // The reference renderer moves each word independently by 6% of the text size (at
            // least 5 px), then adds only a 3% bottom-anchored scale during the held-note phase.
            // Keeping the transform on the word rather than the whole line is what creates the
            // floating vocal feel. Reading the clock inside the layer block keeps the lift on the
            // layer phase, so a 20-word line no longer recomposes 20 subtrees per frame.
            .graphicsLayer {
                translationY = if (useIndividualLift) {
                    -appleMusicKaraokeLiftPx(
                        wordLiftEnabled = wordLiftEnabled,
                        textSizePx = baseStyle.fontSize.toPx(),
                        elapsedSinceWordStartMs = (positionMs.value - renderWord.word.startMs).coerceAtLeast(0L),
                        wordLiftScale = wordLiftScale
                    )
                } else {
                    0f
                }
                transformOrigin = TransformOrigin(0.5f, if (rubyBelow) 0f else 1f)
            }
    ) {
        if (!rubyBelow) rubyContent()
        AppleMusicKaraokeGlyphs(
            renderWord = renderWord,
            positionMs = positionMs,
            active = active,
            baseStyle = baseStyle,
            contentColor = contentColor,
            sustainGlowScale = sustainGlowScale,
            activeGlowProgress = activeGlowProgress
        )
        if (rubyBelow) rubyContent()
    }
}

/** Holds the last measured paragraph so the draw phase can reuse it without re-laying out text. */
private class KaraokeGlyphLayout {
    var value: TextLayoutResult? = null
}

/**
 * One karaoke unit, measured once and repainted from the playback clock.
 *
 * The previous renderer stacked up to four [BasicText] children per word and rebuilt their
 * [TextStyle]s from `positionMs` on every frame, so an active line re-entered composition — and
 * the sweeping word re-entered text layout — 120 times a second. Every visual pass is kept here
 * (dim base, feathered sweep, sustain halo and its travelling sheen, held-note glow); they are
 * simply issued as `drawText` calls against one cached [TextLayoutResult], which is the one thing
 * that must not be recomputed per frame.
 */
@Composable
private fun AppleMusicKaraokeGlyphs(
    renderWord: AppleMusicRenderWord,
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    sustainGlowScale: Float,
    activeGlowProgress: State<Float>?
) {
    val word = renderWord.word
    val measurer = rememberTextMeasurer()
    val cache = remember { KaraokeGlyphLayout() }
    val baseAlpha = baseStyle.color.alpha
    val bright = contentColor.copy(alpha = baseAlpha)
    val dim = contentColor.copy(alpha = baseAlpha * 0.36f)
    val sustainDurationMs = renderWord.sustainDurationMs
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl || word.text.isRtlText()

    Layout(
        modifier = Modifier.drawBehind {
            val layout = cache.value ?: return@drawBehind
            val clock = positionMs.value
            val progress = renderWord.karaokeProgress(clock, active)
            val glow = renderWord.sustainGlowAlpha(clock, active) * sustainGlowScale.coerceIn(0f, 1f)
            val activeGlow = activeGlowProgress?.value?.coerceIn(0f, 1f) ?: 0f

            if (activeGlow > 0f) {
                // Active-line glow is intentionally separate from the held-note halo. The
                // baseline values are deliberately restrained; the 4x multiplier produces the
                // visible Apple-Music-style lift without changing font, fill color, or typography.
                drawText(
                    textLayoutResult = layout,
                    color = contentColor.copy(
                        alpha = (APPLE_ACTIVE_GLOW_BASE_ALPHA * APPLE_ACTIVE_GLOW_MULTIPLIER * activeGlow * baseAlpha)
                            .coerceIn(0f, 1f)
                    ),
                    shadow = Shadow(
                        color = contentColor.copy(
                            alpha = (APPLE_ACTIVE_GLOW_BASE_ALPHA * 0.90f * activeGlow * baseAlpha)
                                .coerceIn(0f, 1f)
                        ),
                        offset = Offset.Zero,
                        blurRadius = APPLE_ACTIVE_GLOW_BASE_BLUR_PX * APPLE_ACTIVE_GLOW_MULTIPLIER * activeGlow
                    )
                )
            }

            if (glow > 0f) {
                // A long held note grows a soft halo around the glyph before the fill reaches it.
                // Blur radius has no ceiling, so it carries most of the ~4x boost — a wider soft
                // spread reads as "more glowing" far better than pushing alpha toward fully
                // opaque, which would just clip into a flat blob instead of staying luminous.
                val durationScale = ((sustainDurationMs - 600L).coerceAtLeast(0L) / 2_400f)
                    .coerceIn(0f, 1f)
                drawText(
                    textLayoutResult = layout,
                    color = contentColor.copy(
                        alpha = ((0.26f + durationScale * 0.40f) * glow * baseAlpha).coerceIn(0f, 1f)
                    ),
                    shadow = Shadow(
                        color = contentColor.copy(
                            alpha = ((0.65f + durationScale * 0.30f) * glow * baseAlpha).coerceIn(0f, 1f)
                        ),
                        offset = Offset.Zero,
                        blurRadius = (44f + durationScale * 32f) * glow
                    )
                )
            }
            val glowShadow = glow.takeIf { it > 0.05f }?.let { glowAlpha ->
                Shadow(
                    color = contentColor.copy(alpha = 0.55f * baseAlpha * glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = 32f * glowAlpha
                )
            }
            val wordWidth = layout.size.width.toFloat().coerceAtLeast(1f)
            when {
                progress <= 0f -> drawText(textLayoutResult = layout, color = dim)
                progress >= 1f -> drawText(
                    textLayoutResult = layout,
                    color = bright,
                    shadow = glowShadow
                )
                else -> {
                    drawText(textLayoutResult = layout, color = dim)
                    // A real alpha gradient rather than a hard clip: the sweep keeps its soft
                    // feathered edge while the dim glyph stays legible underneath it.
                    drawText(
                        textLayoutResult = layout,
                        brush = Brush.horizontalGradient(
                            colorStops = karaokeFillStops(progress, bright, isRtl),
                            startX = 0f,
                            endX = wordWidth
                        ),
                        shadow = glowShadow
                    )
                    // A narrow material sheen follows the karaoke edge. Reserve it for an actual
                    // held note: on every short syllable a second moving highlight reads as two
                    // independent progress bars.
                    if (glow > 0.05f) {
                        drawText(
                            textLayoutResult = layout,
                            brush = Brush.horizontalGradient(
                                colorStops = karaokeSheenStops(
                                    progress = progress,
                                    contentColor = contentColor,
                                    glow = glow,
                                    baseAlpha = baseAlpha,
                                    isRtl = isRtl
                                ),
                                startX = 0f,
                                endX = wordWidth
                            )
                        )
                    }
                }
            }
        }
    ) { _, constraints ->
        val cached = cache.value
        val layout = if (
            cached != null &&
            cached.layoutInput.text.text == word.text &&
            cached.layoutInput.style == baseStyle &&
            cached.layoutInput.constraints == constraints &&
            cached.layoutInput.layoutDirection == layoutDirection &&
            cached.layoutInput.density.density == density &&
            cached.layoutInput.density.fontScale == fontScale
        ) {
            cached
        } else {
            measurer.measure(
                text = word.text,
                style = baseStyle,
                overflow = TextOverflow.Clip,
                constraints = constraints,
                layoutDirection = layoutDirection,
                density = this
            ).also { cache.value = it }
        }
        layout(layout.size.width, layout.size.height) {}
    }
}

private fun AppleMusicRenderWord.karaokeProgress(positionMs: Long, active: Boolean): Float =
    if (active) {
        ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun karaokeFillStops(
    progress: Float,
    bright: Color,
    isRtl: Boolean
): Array<Pair<Float, Color>> {
    // A brief near-white overshoot right at the wipe boundary, on every word — unlike the sheen
    // below (held notes only, a second travelling band), this is baked into the single fill
    // gradient itself, so it can't read as a competing progress indicator. This is what makes the
    // ordinary word-to-word highlight itself look "shining" rather than just a flat color wipe.
    val glint = lerp(bright, Color.White, 0.55f)
    return if (isRtl) {
        arrayOf(
            0f to Color.Transparent,
            (1f - progress).coerceAtLeast(0f) to Color.Transparent,
            (1f - (progress - 0.03f)).coerceIn(0f, 1f) to glint,
            (1f - (progress - 0.15f)).coerceIn(0f, 1f) to bright,
            1f to bright
        )
    } else {
        arrayOf(
            0f to bright,
            (progress - 0.15f).coerceAtLeast(0f) to bright,
            (progress - 0.03f).coerceAtLeast(0f) to glint,
            progress to Color.Transparent,
            1f to Color.Transparent
        )
    }
}

internal fun karaokeSheenStops(
    progress: Float,
    contentColor: Color,
    glow: Float,
    baseAlpha: Float,
    isRtl: Boolean
): Array<Pair<Float, Color>> {
    val sheenColor = lerp(contentColor, Color.White, 0.6f)
    val sheenAlpha = (0.22f + glow * 0.40f) * baseAlpha
    return if (isRtl) {
        arrayOf(
            0f to Color.Transparent,
            (1f - (progress + 0.045f)).coerceAtLeast(0f) to Color.Transparent,
            (1f - (progress - 0.055f)).coerceIn(0f, 1f) to sheenColor.copy(alpha = sheenAlpha),
            (1f - (progress - 0.20f)).coerceAtMost(1f) to Color.Transparent,
            1f to Color.Transparent
        )
    } else {
        val sheenStart = (progress - 0.20f).coerceAtLeast(0f)
        arrayOf(
            0f to Color.Transparent,
            sheenStart to Color.Transparent,
            (progress - 0.055f).coerceIn(sheenStart, progress) to sheenColor.copy(alpha = sheenAlpha),
            (progress + 0.045f).coerceAtMost(1f) to Color.Transparent,
            1f to Color.Transparent
        )
    }
}

internal fun rubiesForTimedWords(
    words: List<LyricWord>,
    pronunciationWords: List<LyricWord>,
    pronunciation: String
): List<String> {
    if (words.isEmpty()) return emptyList()
    val blanks = List(words.size) { "" }
    val rubyWords = pronunciationWords
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotBlank() && it.endMs > it.startMs }
    val rubyText = pronunciation.trim()
    if (rubyWords.isEmpty() && rubyText.isBlank()) return blanks

    if (rubyWords.isNotEmpty()) {
        val spansAllWords = rubyWords.size == 1 &&
            words.size > 1 &&
            words.all { word -> timedRangesOverlap(word, rubyWords.first()) }
        if (spansAllWords) {
            return attachRubyByCorrespondence(words, rubyWords.first().text)
        }
        val assigned = assignRubySpansToWords(words, rubyWords)
        if (assigned.any { it.isNotBlank() }) return assigned
        return attachRubyByCorrespondence(words, rubyWords.joinToString("") { it.text })
    }
    return attachRubyByCorrespondence(words, rubyText)
}

internal fun assignRubySpansToWords(
    words: List<LyricWord>,
    rubyWords: List<LyricWord>
): List<String> {
    if (rubyWords.size == words.size) return rubyWords.map { it.text }
    val result = MutableList(words.size) { "" }
    val usedWords = BooleanArray(words.size)
    rubyWords.forEach { ruby ->
        val match = words.indices
            .filter { index -> !usedWords[index] }
            .maxWithOrNull(
                compareBy<Int> { overlapMs(words[it], ruby) }
                    .thenBy { -kotlin.math.abs(words[it].startMs - ruby.startMs) }
            )
            ?.takeIf { index ->
                overlapMs(words[index], ruby) > 0L ||
                    kotlin.math.abs(words[index].startMs - ruby.startMs) <= 25L
            }
        if (match != null) {
            usedWords[match] = true
            result[match] = ruby.text
        }
    }
    return result
}

private fun attachRubyBySyllables(words: List<LyricWord>, reading: String): List<String> {
    val tokens = reading.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { mutableListOf<String>() }
    var sIdx = 0
    var tIdx = 0

    while (sIdx < surface.length) {
        val character = surface[sIdx]
        if (character.isWhitespace()) {
            sIdx++
            continue
        }

        if (character.isKanjiOrHangul()) {
            var runEnd = sIdx + 1
            while (runEnd < surface.length && surface[runEnd].isKanjiOrHangul()) {
                runEnd++
            }
            val runLength = runEnd - sIdx

            // Look ahead for the next Latin anchor in surface
            var nextLatinMatch: Int? = null
            var restIdx = runEnd
            while (restIdx < surface.length) {
                val restChar = surface[restIdx]
                if (restChar.isAppleMusicLatinLetter()) {
                    var wordEnd = restIdx + 1
                    while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                        wordEnd++
                    }
                    val latinWord = surface.substring(restIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
                    if (latinWord.isNotEmpty()) {
                        for (tokI in tIdx until tokens.size) {
                            val normTok = tokens[tokI].filter { it.isAppleMusicLatinLetter() }.lowercase()
                            if (normTok == latinWord) {
                                nextLatinMatch = tokI
                                break
                            }
                        }
                    }
                    break
                }
                restIdx++
            }

            val consumed = if (nextLatinMatch != null) {
                maxOf(1, nextLatinMatch - tIdx)
            } else {
                val remTokens = tokens.size - tIdx
                val remKanji = (runEnd until surface.length).count { surface[it].isKanjiOrHangul() }
                if (remKanji > 0) {
                    maxOf(1, minOf(runLength, remTokens - remKanji))
                } else {
                    remTokens
                }
            }

            val tokEnd = minOf(tokens.size, tIdx + consumed)
            val assignedTokens = tokens.subList(tIdx, tokEnd)
            tIdx = tokEnd

            if (assignedTokens.size == runLength) {
                assignedTokens.forEachIndexed { i, tok ->
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            } else if (assignedTokens.size == 1) {
                rubyByWord[charToWord[sIdx]].add(assignedTokens[0])
            } else {
                for (i in 0 until minOf(runLength, assignedTokens.size)) {
                    val tok = if (i == runLength - 1 && assignedTokens.size > runLength) {
                        assignedTokens.subList(i, assignedTokens.size).joinToString(" ")
                    } else {
                        assignedTokens[i]
                    }
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            }
            sIdx = runEnd
        } else if (character.isAppleMusicLatinLetter()) {
            var wordEnd = sIdx + 1
            while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                wordEnd++
            }
            val latinWord = surface.substring(sIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
            if (tIdx < tokens.size) {
                val normTok = tokens[tIdx].filter { it.isAppleMusicLatinLetter() }.lowercase()
                if (normTok == latinWord) {
                    tIdx++
                }
            }
            sIdx = wordEnd
        } else {
            if (tIdx < tokens.size && tokens[tIdx] == character.toString()) {
                tIdx++
            }
            sIdx++
        }
    }

    return rubyByWord.map { it.joinToString(" ") }
}

internal fun attachRubyByCorrespondence(words: List<LyricWord>, reading: String): List<String> {
    if (words.isEmpty()) return emptyList()
    val cleanReading = reading.trim()
    if (cleanReading.isBlank()) return List(words.size) { "" }

    if (cleanReading.contains(' ') || cleanReading.any { it.isAppleMusicLatinLetter() }) {
        val syllableAligned = attachRubyBySyllables(words, cleanReading)
        if (syllableAligned.any { it.isNotBlank() }) {
            return syllableAligned
        }
    }

    val readingChars = cleanReading.filterNot { it.isWhitespace() }.toList()
    if (readingChars.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { StringBuilder() }
    var surfaceIndex = 0
    var readingIndex = 0
    while (surfaceIndex < surface.length) {
        val character = surface[surfaceIndex]
        when {
            character.isWhitespace() -> surfaceIndex++
            character.isAppleMusicCjkIdeograph() -> {
                var runEnd = surfaceIndex + 1
                while (runEnd < surface.length && surface[runEnd].isAppleMusicCjkIdeograph()) {
                    runEnd++
                }
                val consumed = readingConsumedForKanjiRun(
                    rest = surface.substring(runEnd),
                    reading = readingChars,
                    readingIndex = readingIndex
                )
                val rubyEnd = (readingIndex + consumed).coerceAtMost(readingChars.size)
                val runLength = runEnd - surfaceIndex
                val pieces = splitReadingAcrossKanji(
                    reading = readingChars.subList(readingIndex, rubyEnd),
                    kanjiCount = runLength
                )
                pieces.forEachIndexed { offset, piece ->
                    if (piece.isNotEmpty()) {
                        rubyByWord[charToWord[surfaceIndex + offset]].append(piece)
                    }
                }
                readingIndex = rubyEnd
                surfaceIndex = runEnd
            }
            character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit() -> {
                if (readingIndex < readingChars.size && kanaEquals(character, readingChars[readingIndex])) {
                    readingIndex++
                }
                surfaceIndex++
            }
            else -> {
                if (readingIndex < readingChars.size && character == readingChars[readingIndex]) {
                    readingIndex++
                }
                surfaceIndex++
            }
        }
    }
    if (readingIndex < readingChars.size) {
        val leftover = readingChars.subList(readingIndex, readingChars.size).joinToString("")
        val target = rubyByWord.indices.lastOrNull { rubyByWord[it].isNotEmpty() }
            ?: words.indexOfLast { word -> word.text.any { it.isAppleMusicCjkIdeograph() } }
                .takeIf { it >= 0 }
            ?: 0
        rubyByWord[target].append(leftover)
    }
    return rubyByWord.map { it.toString() }
}

private fun readingConsumedForKanjiRun(
    rest: String,
    reading: List<Char>,
    readingIndex: Int
): Int {
    val remaining = reading.size - readingIndex
    if (remaining <= 0) return 0
    val laterKanjiRuns = countIdeographRuns(rest)
    val nextAnchor = rest.firstOrNull { character ->
        character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit()
    }
    if (nextAnchor == null) {
        return if (laterKanjiRuns > 0) {
            (remaining - laterKanjiRuns).coerceAtLeast(1).coerceAtMost(remaining)
        } else {
            remaining
        }
    }
    val anchorAt = reading.subList(readingIndex, reading.size).indexOfFirst { kanaEquals(it, nextAnchor) }
    if (anchorAt >= 0) return anchorAt
    if (laterKanjiRuns <= 0) return remaining
    val keep = laterKanjiRuns.coerceAtMost(remaining - 1)
    return (remaining - keep).coerceAtLeast(1)
}

private fun countIdeographRuns(text: String): Int {
    var count = 0
    var inRun = false
    text.forEach { character ->
        val ideograph = character.isAppleMusicCjkIdeograph()
        if (ideograph && !inRun) count++
        inRun = ideograph
    }
    return count
}

private fun splitReadingAcrossKanji(reading: List<Char>, kanjiCount: Int): List<String> {
    if (kanjiCount <= 0) return emptyList()
    if (kanjiCount == 1) return listOf(reading.joinToString(""))
    if (reading.isEmpty()) return List(kanjiCount) { "" }
    val pieces = MutableList(kanjiCount) { StringBuilder() }
    val guaranteed = minOf(kanjiCount, reading.size)
    repeat(guaranteed) { index -> pieces[index].append(reading[index]) }
    if (reading.size > guaranteed) {
        pieces[kanjiCount - 1].append(reading.subList(guaranteed, reading.size).joinToString(""))
    }
    return pieces.map { it.toString() }
}

private fun kanaEquals(first: Char, second: Char): Boolean {
    fun fold(character: Char): Char {
        if (character in '\u30A1'..'\u30F6') return (character.code - 0x60).toChar()
        return character
    }
    return fold(first) == fold(second)
}

private fun timedRangesOverlap(first: LyricWord, second: LyricWord): Boolean =
    minOf(first.endMs, second.endMs) > maxOf(first.startMs, second.startMs)

private fun overlapMs(first: LyricWord, second: LyricWord): Long =
    (minOf(first.endMs, second.endMs) - maxOf(first.startMs, second.startMs)).coerceAtLeast(0L)

private fun Char.isAppleMusicCjkIdeograph(): Boolean = isKanjiOrHangul()

private const val APPLE_ACTIVE_GLOW_MULTIPLIER = 4f
private const val APPLE_ACTIVE_GLOW_BASE_ALPHA = 0.10f
private const val APPLE_ACTIVE_GLOW_BASE_BLUR_PX = 16f

private fun appleMusicTtmlCharacterGroupLiftPx(
    textSizePx: Float,
    groupStartMs: Long,
    groupEndMs: Long,
    positionMs: Long,
    enabled: Boolean
): Float {
    if (!enabled || groupEndMs <= groupStartMs) return 0f
    val durationMs = (groupEndMs - groupStartMs).coerceAtLeast(1L)
    val progress = ((positionMs - groupStartMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    if (progress <= 0f || progress >= 1f) return 0f

    // Longer character-by-character spans get a slightly larger arch, but are capped so a long
    // held note never pulls the word out of the lyric line. All characters share this same curve.
    val durationFactor = ((durationMs - 300L).toFloat() / 2_700f).coerceIn(0f, 1f)
    val durationScale = 0.85f + durationFactor * 0.40f
    // Keep the character-timed word above the ordinary TTML line lift even at the shortest
    // supported duration. The duration term then makes longer spelling animations rise farther.
    val baseLift = maxOf(textSizePx * 0.13f, 7f)
    val maxLift = (baseLift * durationScale).coerceAtMost(maxOf(textSizePx * 0.22f, 16f))
    val arch = kotlin.math.sin((progress * kotlin.math.PI).toFloat())
    return maxLift * arch
}

private fun AppleMusicRenderWord.sustainGlowAlpha(positionMs: Long, active: Boolean): Float {
    val sustainEndMs = sustainEndMs ?: return 0f
    if (!active || positionMs !in word.startMs until sustainEndMs) return 0f
    val duration = sustainEndMs - word.startMs
    val elapsed = positionMs - word.startMs
    // ConePlayer starts the held-note envelope at the beginning of the marked word; it does not
    // wait for a separate attack delay. This is why its halo is already visible around the first
    // sung glyph in a long "Oh" rather than appearing halfway through the word.
    val progress = (elapsed.toFloat() / duration.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    return if (progress < 0.7f) {
        sin((progress / 0.7f) * (PI.toFloat() / 2f))
    } else {
        cos(((progress - 0.7f) / 0.3f) * (PI.toFloat() / 2f))
    }.coerceIn(0f, 1f)
}

private data class AppleMusicRenderWord(
    val word: LyricWord,
    val sustainEndMs: Long? = null,
    // Shared by every character produced from the same original word (see
    // toAppleMusicRenderWords), null for anything that wasn't character-split. The renderer
    // groups consecutive entries with the same key into one non-wrapping unit.
    val characterGroupKey: Int? = null,
    val characterGroupStartMs: Long? = null,
    val characterGroupEndMs: Long? = null
) {
    val sustainDurationMs: Long get() = (sustainEndMs ?: word.endMs) - word.startMs
}

private fun List<LyricWord>.toAppleMusicRenderWords(
    lineText: String,
    sustainThresholdMs: Int
): List<AppleMusicRenderWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<AppleMusicRenderWord>()
    var cursor = 0
    forEachIndexed { index, word ->
        if (word.text.isBlank() || word.endMs <= word.startMs) return@forEachIndexed
        val start = lineText.indexOf(word.text, cursor)
        if (start < 0) return emptyList()
        val end = start + word.text.length
        val nextStart = getOrNull(index + 1)?.text?.let { next -> lineText.indexOf(next, end) } ?: -1
        val suffix = when {
            nextStart > end -> lineText.substring(end, nextStart)
            index == lastIndex && end < lineText.length -> lineText.substring(end)
            else -> ""
        }
        val duration = word.endMs - word.startMs
        val splitForCharacters = word.shouldSplitForAppleMusicCharacters(sustainThresholdMs)
        if (splitForCharacters) {
            val chars = word.text.toCharArray()
            val segmentDuration = duration / chars.size
            chars.forEachIndexed { charIndex, char ->
                val segmentStart = word.startMs + segmentDuration * charIndex
                val segmentEnd = if (charIndex == chars.lastIndex) {
                    word.endMs
                } else {
                    segmentStart + segmentDuration
                }
                result += AppleMusicRenderWord(
                    word = LyricWord(
                        text = char.toString() + if (charIndex == chars.lastIndex) suffix else "",
                        startMs = segmentStart,
                        endMs = segmentEnd
                    ),
                    sustainEndMs = word.endMs,
                    characterGroupKey = index,
                    characterGroupStartMs = word.startMs,
                    characterGroupEndMs = word.endMs
                )
            }
        } else {
            // TTML providers sometimes put a short English phrase in a single timed span.
            // Split it at word boundaries so each word gets its own progressive feather.
            result += AppleMusicRenderWord(
                word = word.copy(text = word.text + suffix),
                sustainEndMs = word.endMs.takeIf { duration >= sustainThresholdMs.coerceAtLeast(0) }
            )
                .splitEnglishPhraseForAppleMusic()
        }
        cursor = end + suffix.length
    }
    return result.withAppleMusicCharacterTimingGroups(lineText)
}

/**
 * Group real character-by-character TTML timings into one visual motion unit. The highlight can
 * still advance character by character, but the vertical transform is shared by the entire run.
 */
private fun List<AppleMusicRenderWord>.withAppleMusicCharacterTimingGroups(
    lineText: String
): List<AppleMusicRenderWord> {
    if (size < 2 || lineText.isBlank()) return this

    data class PositionedWord(
        val index: Int,
        val start: Int,
        val end: Int,
        val coreText: String
    )

    val positioned = mutableListOf<PositionedWord>()
    var cursor = 0
    forEachIndexed { index, renderWord ->
        val core = renderWord.word.text.trim()
        if (core.isBlank()) return@forEachIndexed
        val start = lineText.indexOf(core, cursor)
        if (start < 0) return this
        val end = start + core.length
        positioned += PositionedWord(index, start, end, core)
        cursor = end
    }
    if (positioned.size != size) return this

    val result = toMutableList()
    var runStart = 0
    while (runStart < positioned.size) {
        val first = positioned[runStart]
        if (!isAppleMusicCharacterTimingCandidate(first.coreText)) {
            runStart++
            continue
        }

        var runEnd = runStart + 1
        while (runEnd < positioned.size) {
            val previous = positioned[runEnd - 1]
            val current = positioned[runEnd]
            if (previous.end != current.start ||
                !isAppleMusicCharacterTimingCandidate(current.coreText)
            ) break
            runEnd++
        }

        if (runEnd - runStart >= 2) {
            val groupKey = -(runStart + 1)
            val groupStartMs = this[first.index].word.startMs
            val groupEndMs = this[positioned[runEnd - 1].index].word.endMs
            for (index in runStart until runEnd) {
                val originalIndex = positioned[index].index
                result[originalIndex] = result[originalIndex].copy(
                    characterGroupKey = groupKey,
                    characterGroupStartMs = groupStartMs,
                    characterGroupEndMs = groupEndMs
                )
            }
        }
        runStart = runEnd
    }
    return result
}

private fun isAppleMusicCharacterTimingCandidate(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length != 1) return false
    val character = trimmed.first()
    return character.isAppleMusicLatinLetter() || character.isAppleMusicCjkCharacter() || character.isDigit()
}

/**
 * A TTML/LRC provider may put a whole long CJK phrase in one timed span. If that span wraps in
 * the player, a single BasicText child gives every visual row the same progress. Split long
 * timed phrases into character-sized children so wrapped rows can complete from top to bottom.
 */
internal fun LyricWord.shouldSplitForAppleMusicCharacters(
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
): Boolean {
    if (endMs - startMs < sustainThresholdMs.coerceAtLeast(0).toLong() || text.length <= 1) return false
    // Used to exclude Latin script entirely: a word split into one FlowRow-style item per
    // character could break apart across a line wrap ("stranger" -> "stra" / "nger"). Now that
    // the renderer groups every character sharing a characterGroupKey into one non-wrapping unit
    // before handing it to the row layout (see groupIntoWrapAtomicUnits), the whole exploded word
    // wraps as a single piece the same way it did unsplit, so this is safe for Latin too.
    return text.any { it.isAppleMusicLatinLetter() || it.isAppleMusicCjkCharacter() }
}

private fun Char.isAppleMusicLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isAppleMusicCjkCharacter(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}

private fun AppleMusicRenderWord.splitEnglishPhraseForAppleMusic(): List<AppleMusicRenderWord> {
    val sourceText = word.text
    if (!sourceText.any { it in 'a'..'z' || it in 'A'..'Z' } || !sourceText.any(Char::isWhitespace)) {
        return listOf(this)
    }
    val segments = Regex("\\S+\\s*").findAll(sourceText).map { it.value }.toList()
    if (segments.size < 2) return listOf(this)

    val totalWeight = segments.sumOf { segment ->
        segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    }.coerceAtLeast(1)
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
    var elapsed = 0L
    return segments.mapIndexed { index, segment ->
        val weight = segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        val startMs = word.startMs + elapsed
        val endMs = if (index == segments.lastIndex) {
            word.endMs
        } else {
            (word.startMs + (duration * (elapsed + weight) / totalWeight)).coerceAtLeast(startMs + 1L)
        }
        elapsed += weight
        AppleMusicRenderWord(
            word = LyricWord(text = segment, startMs = startMs, endMs = endMs),
            // A sustained source span is represented by a glow on the final sung word; this
            // avoids every word in a phrase receiving the same permanent halo.
            sustainEndMs = sustainEndMs?.takeIf { index == segments.lastIndex }
        )
    }
}

/** Keep inter-word whitespace on the previous unit so a wrapped row starts at the shared edge. */
internal fun List<LyricWord>.moveLeadingSpacesToPreviousWord(): List<LyricWord> {
    val result = mutableListOf<LyricWord>()
    forEach { word ->
        val leadingWhitespace = word.text.takeWhile(Char::isWhitespace)
        if (leadingWhitespace.isNotEmpty() && result.isNotEmpty()) {
            val previous = result.removeAt(result.lastIndex)
            result += previous.copy(text = previous.text + leadingWhitespace)
        }
        val visibleText = word.text.drop(leadingWhitespace.length)
        if (visibleText.isNotEmpty()) result += word.copy(text = visibleText)
    }
    return result
}

/**
 * TTML ruby often arrives as one timed span for a whole Japanese phrase. Split that span into
 * character-sized timed units before attaching ruby text so the reading can sit below its own
 * character instead of becoming one second line under the whole phrase.
 */
internal fun List<LyricWord>.splitForAppleMusicRuby(): List<LyricWord> {
    if (isEmpty()) return this
    return flatMap { word ->
        val pieces = mutableListOf<StringBuilder>()
        word.text.forEach { character ->
            if (character.isWhitespace() && pieces.isNotEmpty()) {
                pieces.last().append(character)
            } else {
                pieces += StringBuilder().append(character)
            }
        }
        val hasMultipleCjkCharacters = pieces.size > 1 &&
            pieces.any { piece -> piece.any { it.isAppleMusicCjkCharacter() } }
        val duration = word.endMs - word.startMs
        if (!hasMultipleCjkCharacters || duration < pieces.size.toLong()) {
            listOf(word)
        } else {
            pieces.mapIndexed { index, piece ->
                val start = word.startMs + duration * index / pieces.size
                val end = if (index == pieces.lastIndex) {
                    word.endMs
                } else {
                    word.startMs + duration * (index + 1) / pieces.size
                }
                LyricWord(
                    text = piece.toString(),
                    startMs = start,
                    endMs = end.coerceAtLeast(start + 1L)
                )
            }
        }
    }
}
