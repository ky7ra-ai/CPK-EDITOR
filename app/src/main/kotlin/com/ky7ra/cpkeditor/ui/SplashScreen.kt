package com.ky7ra.cpkeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ky7ra.cpkeditor.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One thrown shuriken: where it comes from, where it sticks, and how it spins on the way. */
private class Throw(
    val fromX: Float,       // start direction, in screen widths from the centre
    val fromY: Float,       // same, in screen heights
    val landX: Float,       // landing offset from the cluster centre, in shuriken sizes
    val landY: Float,
    val sizeScale: Float,
    val landAngle: Float,   // final rotation in degrees
    val spinTurns: Float,   // full turns while flying (sign = direction)
    val bend: Float,        // sideways bow of the flight path
    val delayMs: Long,
)

private val Throws = listOf(
    Throw(-1.0f, 0.65f, -0.36f, -0.22f, 1.00f, 18f, 3f, 0.10f, 150),
    Throw(1.0f, -0.35f, 0.38f, -0.08f, 0.92f, -27f, -3f, -0.10f, 470),
    Throw(0.15f, -1.0f, 0.02f, 0.38f, 1.06f, 41f, 3f, 0.08f, 790),
)

private const val FLIGHT_MS = 400
private val Ink = Brand.Ink

/**
 * Opening animation, played on every launch: three shurikens are thrown in from different sides
 * and stick in the middle of the screen, then the app name appears and the splash fades out.
 * Tap or press Back to skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val finished = remember { booleanArrayOf(false) }
    val finish = {
        if (!finished[0]) { finished[0] = true; onFinished() }
    }
    BackHandler(onBack = finish)

    val flight = remember { List(Throws.size) { Animatable(0f) } }
    val impact = remember { List(Throws.size) { Animatable(0f) } }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            Throws.forEachIndexed { i, t ->
                launch {
                    delay(t.delayMs)
                    // Constant speed, then an abrupt stop: that is what makes it read as a throw.
                    flight[i].animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
                    impact[i].animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
                }
            }
        }
        title.animateTo(1f, tween(380))
        delay(420)
        exit.animateTo(1f, tween(320))
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        val density = LocalDensity.current
        val base: Dp = (minOf(maxWidth, maxHeight) * 0.34f).coerceIn(90.dp, 150.dp)
        val basePx = with(density) { base.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val centerY = hPx * 0.44f
        val shakeDp = with(density) { 5.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // A small jolt on every hit.
                    var dx = 0f
                    var dy = 0f
                    for (a in impact) {
                        val v = a.value
                        if (v > 0f && v < 1f) {
                            val k = (1f - v) * (1f - v)
                            dx += sin(v * 46f) * shakeDp * k
                            dy += cos(v * 38f) * shakeDp * 0.6f * k
                        }
                    }
                    translationX = dx
                    translationY = dy
                },
        ) {
            val center = Offset(wPx / 2f, centerY)
            // Warm glow that grows with each hit.
            val glow = (impact.sumOf { it.value.toDouble().coerceIn(0.0, 1.0) }.toFloat() / Throws.size)
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Brand.Orange.copy(alpha = 0.22f * glow), Color.Transparent),
                        center = center,
                        radius = basePx * 2.2f,
                    ),
                    radius = basePx * 2.2f,
                    center = center,
                )
            }
            Throws.forEachIndexed { i, t ->
                drawThrow(t, flight[i].value, impact[i].value, center, basePx, wPx, hPx, i)
            }
        }

        Text(
            stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = with(density) { (centerY - hPx / 2f + basePx * 1.35f).toDp() } + (1f - title.value) * 14.dp)
                .alpha(title.value),
        )
    }
}

private fun DrawScope.drawThrow(
    t: Throw,
    flight: Float,
    impact: Float,
    center: Offset,
    basePx: Float,
    w: Float,
    h: Float,
    index: Int,
) {
    if (flight <= 0f) return
    val land = Offset(center.x + t.landX * basePx, center.y + t.landY * basePx)
    val start = Offset(center.x + t.fromX * (w * 0.75f + basePx), center.y + t.fromY * (h * 0.75f + basePx))
    val dist = hypot(land.x - start.x, land.y - start.y)
    // Unit vector perpendicular to the flight direction, for the slight bow.
    val dirX = (land.x - start.x) / dist
    val dirY = (land.y - start.y) / dist
    val perpX = -dirY
    val perpY = dirX

    fun posAt(f: Float): Offset {
        val bow = sin(PI.toFloat() * f) * t.bend * dist
        return Offset(
            start.x + (land.x - start.x) * f + perpX * bow,
            start.y + (land.y - start.y) * f + perpY * bow,
        )
    }

    fun rotAt(f: Float): Float = t.landAngle - 360f * t.spinTurns * (1f - f)

    val size = basePx * t.sizeScale
    val flying = flight < 1f
    if (flying) {
        // Motion trail: fading ghosts a little behind the shuriken.
        for (j in 4 downTo 1) {
            val f = flight - 0.05f * j
            if (f <= 0f) continue
            val depth = 0.55f + 0.45f * f
            drawShuriken(posAt(f), size * depth, rotAt(f), Ink, alpha = 0.20f / j * 1.6f)
        }
    }
    val depth = 0.55f + 0.45f * flight
    // On landing the star "pops" a little, then quivers as it settles in the wood.
    val pop = 1f + 0.13f * (1f - impact) * cos(impact * 14f)
    val wobble = if (flying) 0f else sin(impact * 38f) * (1f - impact) * 7f
    val finalSize = size * depth * (if (flying) 1f else pop)
    val at = posAt(flight)

    // Soft contact shadow.
    drawCircle(Color.Black.copy(alpha = 0.35f * depth), radius = finalSize * 0.46f, center = at + Offset(finalSize * 0.04f, finalSize * 0.07f))
    drawShuriken(at, finalSize, rotAt(flight) + wobble, Ink)

    if (!flying && impact > 0f && impact < 1f) {
        val fade = 1f - impact
        // Shockwave ring.
        drawCircle(
            Brand.Orange.copy(alpha = 0.7f * fade),
            radius = size * (0.35f + 0.95f * impact),
            center = land,
            style = Stroke(width = size * 0.035f * fade + 1.5f),
        )
        // Sparks flying out of the hit.
        for (n in 0 until 8) {
            val ang = (n * 45f + index * 17f) * (PI.toFloat() / 180f)
            val inner = size * (0.34f + 0.42f * impact)
            val outer = inner + size * 0.16f * fade
            drawLine(
                Color(0xFFFFC27A).copy(alpha = fade),
                Offset(land.x + cos(ang) * inner, land.y + sin(ang) * inner),
                Offset(land.x + cos(ang) * outer, land.y + sin(ang) * outer),
                strokeWidth = 3f,
                cap = StrokeCap.Round,
            )
        }
    }
}
