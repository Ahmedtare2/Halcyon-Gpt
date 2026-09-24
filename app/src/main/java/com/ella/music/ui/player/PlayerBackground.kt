package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
internal fun ImmersiveCoverBackground(
    palette: PlayerPalette,
    flowEffectMode: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.top.copy(alpha = 0.64f),
                            palette.middle.copy(alpha = 0.58f),
                            palette.bottom.copy(alpha = 0.72f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = 0.20f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.18f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
    }
}

/**
 * The dynamic-cover counterpart to [ImmersiveCoverBackground]: instead of one palette extracted
 * once from a static bitmap, [livePalette] is expected to be updated every couple hundred ms from
 * the actual playing video frame (see DynamicCoverVideo's onLivePaletteFrame), so the background
 * visibly tracks what's on screen right now rather than staying fixed to a single first-frame
 * snapshot for the whole song. Two things make it read as "more aggressive" than the static
 * version rather than just "the same thing but updating": every color transition is short and
 * snappy (~380ms, versus most ambient-background implementations - including this app's own
 * AppleCoverFlowBackground - deliberately drifting over several seconds so it reads as calm
 * background wallpaper, not something reacting to you), and every layer's alpha is pushed
 * noticeably higher so a real color swing in the video is unmistakable instead of a subtle tint
 * shift.
 */
@Composable
internal fun DynamicCoverReactiveBackground(
    livePalette: PlayerPalette,
    modifier: Modifier = Modifier
) {
    val reactiveSpec = androidx.compose.animation.core.tween<Color>(
        durationMillis = 380,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )
    val top by androidx.compose.animation.animateColorAsState(livePalette.top, reactiveSpec, label = "reactiveTop")
    val middle by androidx.compose.animation.animateColorAsState(livePalette.middle, reactiveSpec, label = "reactiveMiddle")
    val bottom by androidx.compose.animation.animateColorAsState(livePalette.bottom, reactiveSpec, label = "reactiveBottom")
    val accent by androidx.compose.animation.animateColorAsState(livePalette.accent, reactiveSpec, label = "reactiveAccent")

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            top.copy(alpha = 0.86f),
                            middle.copy(alpha = 0.80f),
                            bottom.copy(alpha = 0.90f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.38f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
    }
}

@Composable
internal fun SharedPlayerPageBackground(
    song: com.ella.music.data.model.Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    palette: PlayerPalette,
    currentPositionMs: Long,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    dynamicFlowEnabled: Boolean = false,
    useBlurBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val useCustomPlayerBackground = playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && !useBlurBackground
    when {
        useCustomPlayerBackground -> PlayerCustomBackground(
            uri = playerBackgroundUri,
            imageAlpha = playerBackgroundOpacity,
            dimAlpha = playerBackgroundDim,
            modifier = modifier
        )
        beautifulLyricsBackground -> BeautifulLyricsDynamicBackground(
            palette = palette,
            coverBitmap = embeddedCover ?: paletteBitmap,
            positionMs = currentPositionMs,
            isPlaying = isPlaying,
            modifier = modifier
        )
        useBlurBackground -> PlayerBlurBackground(
            song = song,
            embeddedCover = embeddedCover,
            palette = palette,
            motion = 0.42f,
            isPlaying = isPlaying,
            modifier = modifier
        )
        (embeddedCover ?: paletteBitmap) != null -> AppleCoverFlowBackground(
            coverBitmap = embeddedCover ?: paletteBitmap,
            backgroundColor = palette.middle,
            isDark = !palette.isLight,
            isPlaying = isPlaying,
            animate = dynamicFlowEnabled,
            modifier = modifier
        )
        else -> Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.top,
                        palette.middle,
                        palette.bottom
                    )
                )
            )
        )
    }
}
