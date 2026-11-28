package com.ky7ra.cpkeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/** Fast start, long soft landing: how the logo pieces slide into place. */
private val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Where a logo piece starts, in logo units away from its final spot, and when it is released. */
private class Entry(val fromX: Float, val fromY: Float, val delayMs: Long)

private val VEntry = Entry(-215f, 16f, 0)        // V slides in from the left
private val StemEntry = Entry(120f, -190f, 90)   // stem of the 6 drops in from the upper right
private val RingEntry = Entry(190f, 130f, 180)   // bowl of the 6 sweeps in from the lower right

private const val SLIDE_MS = 640
private const val START_TILT = -20f              // whole logo starts tilted, then snaps upright
private const val BURST_TRIANGLES = 12

/**
 * Opening animation, played on every launch: the three pieces of the Versixty "V6" logo slide in
 * with speed streaks, lock together while the logo snaps upright, a ring of little triangles
 * bursts out, then the name appears. Tap or press Back to skip.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val finished = remember { booleanArrayOf(false) }
    val finish = {
        if (!finished[0]) { finished[0] = true; onFinished() }
    }
    BackHandler(onBack = finish)

    val slide = remember { listOf(Animatable(0f), Animatable(0f), Animatable(0f)) }
    val tilt = remember { Animatable(START_TILT) }
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            listOf(VEntry, StemEntry, RingEntry).forEachIndexed { i, e ->
                launch {
                    delay(e.delayMs)
                    slide[i].animateTo(1f, tween(SLIDE_MS, easing = EaseOutExpo))
                }
            }
            launch {
                delay(330)
                tilt.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 170f))
            }
            launch {
                delay(720)
                pop.snapTo(1.07f)
                pop.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 320f))
            }
            launch {
                delay(720)
                burst.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
            }
            launch {
                delay(900)
                word.animateTo(1f, tween(520, easing = EaseOutExpo))
            }
        }
        delay(650)
        exit.animateTo(1f, tween(300))
        finish()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Brand.Ink)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // Logo units -> pixels: the mark is about half the narrower screen side wide.
        val unit = minOf(wPx, hPx) * 0.50f / VersixtyArt.WIDTH
        val center = Offset(wPx / 2f, hPx * 0.42f)
        val markHalfH = VersixtyArt.HEIGHT * unit / 2f

        Canvas(Modifier.fillMaxSize()) {
            drawLogo(
                center = center,
                unit = unit,
                tilt = tilt.value,
                pop = pop.value,
                slide = slide.map { it.value },
                burst = burst.value,
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { (center.y + markHalfH).toDp() } + 34.dp)
                .graphicsLayer {
                    alpha = word.value
                    translationY = (1f - word.value) * 18.dp.toPx()
                    scaleX = 1.12f - 0.12f * word.value
                    scaleY = scaleX
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("VER") }
                    withStyle(SpanStyle(color = Brand.Orange)) { append("SIXTY") }
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = 5.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                letterSpacing = 3.sp,
            )
        }
    }
}

private fun DrawScope.drawLogo(
    center: Offset,
    unit: Float,
    tilt: Float,
    pop: Float,
    slide: List<Float>,
    burst: Float,
) {
    // Soft orange glow behind the logo, flaring while the burst plays.
    val glow = sin(PI.toFloat() * burst.coerceIn(0f, 1f))
    if (glow > 0f) {
        val r = unit * VersixtyArt.WIDTH * 1.1f
        drawCircle(
            Brush.radialGradient(listOf(Brand.Orange.copy(alpha = 0.26f * glow), Color.Transparent), center, r),
            radius = r,
            center = center,
        )
    }

    // Every piece is drawn in logo units, in one tilted, scaled frame centred on the logo.
    fun frame(block: DrawScope.() -> Unit) = withTransform({
        translate(center.x, center.y)
        rotate(tilt, Offset.Zero)
        scale(unit * pop, unit * pop, Offset.Zero)
        translate(-VersixtyArt.CX, -VersixtyArt.CY)
    }, block)

    val entries = listOf(VEntry, StemEntry, RingEntry)
    val paths = listOf(VersixtyPaths.v, VersixtyPaths.stem, VersixtyPaths.ring)
    val colors = listOf(VersixtyColors.V, VersixtyColors.Six, VersixtyColors.Six)

    for (i in 0..2) {
        val p = slide[i]
        if (p <= 0f) continue
        val ox = entries[i].fromX * (1f - p)
        val oy = entries[i].fromY * (1f - p)
        frame {
            // Speed streaks trailing behind the moving piece.
            val dist = hypot(ox, oy)
            if (dist > 1f) {
                val dx = ox / dist
                val dy = oy / dist
                val fade = Math.pow((1f - p).toDouble(), 0.55).toFloat()
                val len = minOf(dist, 70f) * fade
                for ((k, lat) in floatArrayOf(-15f, -2f, 11f).withIndex()) {
                    val sx = VersixtyArt.CX + ox - dy * lat + dx * (8f + k * 7f)
                    val sy = VersixtyArt.CY + oy + dx * lat + dy * (8f + k * 7f)
                    drawLine(
                        colors[i].copy(alpha = 0.75f * fade),
                        Offset(sx, sy),
                        Offset(sx + dx * len * (1f - 0.22f * k), sy + dy * len * (1f - 0.22f * k)),
                        strokeWidth = 1.5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            // The piece itself.
            withTransform({ translate(ox, oy) }) { drawPath(paths[i], colors[i]) }
        }
    }

    // Little triangles bursting out of the finished logo.
    if (burst > 0f && burst < 1f) {
        val fade = Math.pow((1f - burst).toDouble(), 1.2).toFloat()
        frame {
            for (n in 0 until BURST_TRIANGLES) {
                val ang = (n * (360f / BURST_TRIANGLES) + 12f) * (PI.toFloat() / 180f)
                val reach = 40f + burst * (46f + (n % 3) * 16f)
                val size = if (n % 2 == 0) 3.2f else 4.8f
                val ux = cos(ang)
                val uy = sin(ang)
                val bx = VersixtyArt.CX + ux * reach
                val by = VersixtyArt.CY + uy * reach * 0.8f
                val tri = Path().apply {
                    moveTo(bx + ux * size * 1.4f, by + uy * size * 1.4f)
                    lineTo(bx - uy * size * 0.8f, by + ux * size * 0.8f)
                    lineTo(bx + uy * size * 0.8f, by - ux * size * 0.8f)
                    close()
                }
                val c = if (n % 3 == 0) Brand.Orange else Color.White.copy(alpha = 0.6f)
                drawPath(tri, c.copy(alpha = c.alpha * fade))
            }
        }
    }
}
