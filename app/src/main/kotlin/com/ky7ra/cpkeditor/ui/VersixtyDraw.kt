package com.ky7ra.cpkeditor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser

/** The three pieces of the logo, parsed once from [VersixtyArt] (same shapes as the launcher icon). */
object VersixtyPaths {
    // A malformed string must never take the whole app down at launch; draw nothing instead.
    private fun parse(d: String): Path = runCatching { PathParser().parsePathString(d).toPath() }.getOrElse { Path() }

    /** The "V". */
    val v: Path by lazy { parse(VersixtyArt.V) }

    /** The slanted stem of the "6". */
    val stem: Path by lazy { parse(VersixtyArt.STEM) }

    /** The bowl of the "6", with its hole. */
    val ring: Path by lazy { parse(VersixtyArt.RING) }
}

/** Logo colors: white V, orange 6. */
object VersixtyColors {
    val V = Color(0xFFF7F7F7)
    val Six = Brand.Orange
}

/** Draws the whole logo fitted and centred in this scope's bounds. */
fun DrawScope.drawVersixtyMark(color: Color, accent: Color = color) {
    val s = minOf(size.width / VersixtyArt.WIDTH, size.height / VersixtyArt.HEIGHT)
    withTransform({
        translate(size.width / 2f, size.height / 2f)
        scale(s, s, Offset.Zero)
        translate(-VersixtyArt.CX, -VersixtyArt.CY)
    }) {
        drawPath(VersixtyPaths.v, color)
        drawPath(VersixtyPaths.stem, accent)
        drawPath(VersixtyPaths.ring, accent)
    }
}
