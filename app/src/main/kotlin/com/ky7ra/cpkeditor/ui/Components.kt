@file:OptIn(ExperimentalMaterial3Api::class)

package com.ky7ra.cpkeditor.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ky7ra.cpkeditor.R
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Bottom navigation destinations. */
enum class AppTab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
    About(R.string.nav_about, Icons.Filled.Info),
}

/** A four-point throwing star, the app's shinobi motif. [hole] is painted in the middle. */
@Composable
fun ShurikenMark(modifier: Modifier = Modifier, color: Color, hole: Color) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val path = Path()
        for (i in 0 until 8) {
            val radius = if (i % 2 == 0) r else r * 0.3f
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val x = c.x + (radius * cos(angle)).toFloat()
            val y = c.y + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
        drawCircle(hole, radius = r * 0.14f, center = c)
    }
}

/** Round selection indicator used by list rows and option cards. */
@Composable
fun CheckDot(selected: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(cs.primary)
                } else {
                    Modifier.border(2.dp, cs.outline, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

enum class PillStyle { Primary, Dark, Outline, Glass }

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Primary,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val ex = LocalExtras.current
    val (container, content) = when (style) {
        PillStyle.Primary -> cs.primary to Color.White
        PillStyle.Dark -> ex.tile to ex.onTile
        PillStyle.Outline -> Color.Transparent to cs.onSurface
        PillStyle.Glass -> Color.White.copy(alpha = 0.14f) to Color.White
    }
    val border = when (style) {
        PillStyle.Outline -> BorderStroke(1.5.dp, cs.onSurface.copy(alpha = 0.25f))
        PillStyle.Dark -> BorderStroke(1.dp, ex.cardBorder)
        else -> null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else 0.4f),
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Box(
            Modifier.padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 8.dp else 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
fun StatusBadge(text: String, container: Color, content: Color) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Column(
            Modifier.padding(16.dp).heightIn(min = 84.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = content.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = modifier,
    )
}

/** Floating dark pill with the three destinations; the active one sits in a round highlight. */
@Composable
fun FloatingNavBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier) {
    val ex = LocalExtras.current
    Surface(modifier = modifier, shape = CircleShape, color = ex.navBar, shadowElevation = 14.dp) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { tab ->
                val active = tab == selected
                val desc = stringResource(tab.label)
                Box(
                    Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(if (active) ex.navActive else Color.Transparent)
                        .clickable(role = Role.Tab, onClickLabel = desc) { onSelect(tab) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = desc,
                        tint = if (active) ex.navActiveIcon else ex.navInactiveIcon,
                    )
                }
            }
        }
    }
}

/** Space a screen should leave at the bottom so the floating nav bar never covers its content. */
val NavBarSpace: Dp = 96.dp

/**
 * "Slide to continue" control: drag the round thumb to the end to trigger [onComplete].
 * Screen readers get a plain click action instead of the drag.
 */
@Composable
fun SlideToAct(label: String, enabled: Boolean, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thumb = 52.dp
    val inset = 4.dp

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumb + inset * 2)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(cs.surfaceVariant)
            .semantics {
                onClick(label = label) {
                    if (enabled) onComplete()
                    enabled
                }
            },
    ) {
        val maxPx = with(density) { (maxWidth - thumb - inset * 2).toPx() }.coerceAtLeast(1f)
        val dragState = rememberDraggableState { delta ->
            scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxPx)) }
        }

        // orange trail that follows the thumb
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .height(thumb + inset * 2)
                .width(with(density) { offset.value.toDp() } + thumb + inset * 2)
                .clip(CircleShape)
                .background(cs.primary),
        )

        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = thumb / 2)
                .graphicsLayer { alpha = 1f - (offset.value / maxPx).coerceIn(0f, 1f) },
        )
        Text(
            "»",
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
        )

        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(inset)
                .size(thumb)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStopped = {
                        if (offset.value > maxPx * 0.82f) onComplete()
                        offset.animateTo(0f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF0F0F10))
        }
    }
}
