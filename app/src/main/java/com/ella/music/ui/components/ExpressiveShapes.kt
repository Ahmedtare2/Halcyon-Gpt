package com.ella.music.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

/**
 * Wraps an [androidx.graphics.shapes.RoundedPolygon] (built centered at the origin with radius 1,
 * i.e. spanning [-1, 1]) as a Compose [Shape], scaling it to fill the drawn size.
 */
private class RoundedPolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath().asComposePath()
        val matrix = Matrix()
        // Order applies right-to-left: shift [-1,1] -> [0,2], then scale to [0, size].
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/** Material 3 Expressive-style "cookie": a softly scalloped circle. */
val CookieShape: Shape = RoundedPolygonShape(
    RoundedPolygon.star(
        numVerticesPerRadius = 8,
        innerRadius = 0.86f,
        rounding = CornerRounding(0.5f),
        innerRounding = CornerRounding(0.5f),
    )
)

/** Material 3 Expressive-style 4-petal clover / flower. */
val CloverShape: Shape = RoundedPolygonShape(
    RoundedPolygon.star(
        numVerticesPerRadius = 4,
        innerRadius = 0.52f,
        rounding = CornerRounding(0.34f),
        innerRounding = CornerRounding(0.34f),
    )
)

/** Material 3 Expressive-style rounded heptagon "pebble". */
val PebbleShape: Shape = RoundedPolygonShape(
    RoundedPolygon(numVertices = 7, rounding = CornerRounding(0.5f))
)
