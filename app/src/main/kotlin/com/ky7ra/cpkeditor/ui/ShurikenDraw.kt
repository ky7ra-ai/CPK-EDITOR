package com.ky7ra.cpkeditor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/** The shuriken artwork parsed once; the strings come from [ShurikenArt] (same shapes as the launcher icon). */
private object ShurikenPaths {
    // A malformed string must never take the whole app down at launch; draw nothing instead.
    private fun parse(d: String): Path = runCatching { PathParser().parsePathString(d).toPath() }.getOrElse { Path() }
    val silhouette: Path by lazy { parse(ShurikenArt.SILHOUETTE) }
    val lit: Path by lazy { parse(ShurikenArt.LIT_FACETS) }
    val hub: Path by lazy { parse(ShurikenArt.HUB) }
    val hole: Path by lazy { parse(ShurikenArt.HOLE) }
    val silhouetteWithHole: Path by lazy {
        parse(ShurikenArt.SILHOUETTE + " " + ShurikenArt.HOLE).apply { fillType = PathFillType.EvenOdd }
    }
}

/** Steel and hub colors of the full-color shuriken (as in the launcher icon). */
object ShurikenColors {
    val Light = Color(0xFFF4F4F4)
    val Dark = Color(0xFFB4B9C2)
    val Hub = Brand.Orange
}

/**
 * Draws the full-color shuriken centred on [center]. [size] is the tip-to-tip diameter in pixels;
 * [holeColor] fills the little hole in the middle (pass the background color).
 */
fun DrawScope.drawShuriken(
    center: Offset,
    size: Float,
    rotationDeg: Float,
    holeColor: Color,
    alpha: Float = 1f,
) {
    if (alpha <= 0f) return
    val s = size / (2f * ShurikenArt.TIP_RADIUS)
    val mid = ShurikenArt.VIEWPORT / 2f
    withTransform({
        translate(center.x, center.y)
        rotate(rotationDeg, Offset.Zero)
        scale(s, s, Offset.Zero)
        translate(-mid, -mid)
    }) {
        drawPath(ShurikenPaths.silhouette, ShurikenColors.Dark, alpha)
        drawPath(ShurikenPaths.lit, ShurikenColors.Light, alpha)
        drawPath(ShurikenPaths.hub, ShurikenColors.Hub, alpha)
        drawPath(ShurikenPaths.hole, holeColor, alpha)
    }
}

/** Single-color shuriken with a see-through-looking hole, used as the small in-app motif. */
fun DrawScope.drawShurikenMark(color: Color, hole: Color) {
    val s = size.minDimension / (2f * ShurikenArt.TIP_RADIUS)
    val mid = ShurikenArt.VIEWPORT / 2f
    withTransform({
        translate(this@drawShurikenMark.size.width / 2f, this@drawShurikenMark.size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-mid, -mid)
    }) {
        drawPath(ShurikenPaths.silhouette, color)
        drawPath(ShurikenPaths.hole, hole)
    }
}
