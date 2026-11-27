package com.ky7ra.cpkeditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector

/** Tray with a downward arrow: "extract". */
val ExtractIcon: ImageVector by lazy {
    materialIcon(name = "Extract") {
        materialPath {
            moveTo(11f, 3f)
            horizontalLineTo(13f)
            verticalLineTo(10f)
            horizontalLineTo(16.5f)
            lineTo(12f, 14.5f)
            lineTo(7.5f, 10f)
            horizontalLineTo(11f)
            close()
            moveTo(5f, 16f)
            horizontalLineTo(7f)
            verticalLineTo(18f)
            horizontalLineTo(17f)
            verticalLineTo(16f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
            close()
        }
    }
}

/** Arrow leaving a square: "open externally". */
val ExternalIcon: ImageVector by lazy {
    materialIcon(name = "External") {
        materialPath {
            moveTo(14f, 3f)
            horizontalLineTo(21f)
            verticalLineTo(10f)
            horizontalLineTo(19f)
            verticalLineTo(6.4f)
            lineTo(10.7f, 14.7f)
            lineTo(9.3f, 13.3f)
            lineTo(17.6f, 5f)
            horizontalLineTo(14f)
            close()
            moveTo(5f, 5f)
            horizontalLineTo(11f)
            verticalLineTo(7f)
            horizontalLineTo(7f)
            verticalLineTo(17f)
            horizontalLineTo(17f)
            verticalLineTo(13f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
        }
    }
}

@Composable
fun FolderGlyph(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(w * 0.12f)
        drawRoundRect(color, topLeft = Offset(0f, h * 0.14f), size = Size(w * 0.46f, h * 0.34f), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(0f, h * 0.26f), size = Size(w, h * 0.62f), cornerRadius = r)
    }
}

@Composable
fun DocGlyph(modifier: Modifier = Modifier, color: Color, lines: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.16f, h * 0.04f),
            size = Size(w * 0.68f, h * 0.92f),
            cornerRadius = CornerRadius(w * 0.14f),
        )
        for (y in floatArrayOf(0.4f, 0.55f, 0.7f)) {
            drawLine(
                lines,
                start = Offset(w * 0.32f, h * y),
                end = Offset(w * 0.68f, h * y),
                strokeWidth = h * 0.07f,
                cap = StrokeCap.Round,
            )
        }
    }
}
