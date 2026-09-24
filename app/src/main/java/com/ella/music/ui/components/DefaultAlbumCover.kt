package com.ella.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Music

@Composable
fun DefaultAlbumCover(
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White.copy(alpha = 0.92f)
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF5E9BFF),
                    Color(0xFF3E63D8),
                    Color(0xFF202A68)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.34f
            val center = Offset(size.width * 0.52f, size.height * 0.50f)
            drawCircle(Color.White.copy(alpha = 0.16f), radius, center)
            drawCircle(
                color = Color.White.copy(alpha = 0.26f),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = size.minDimension * 0.035f)
            )
            drawCircle(Color.Black.copy(alpha = 0.14f), radius * 0.18f, center)
        }
        Icon(
            imageVector = MiuixIcons.Regular.Music,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(40.dp)
        )
    }
}
