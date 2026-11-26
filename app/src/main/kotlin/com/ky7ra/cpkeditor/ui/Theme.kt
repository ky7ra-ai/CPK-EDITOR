package com.ky7ra.cpkeditor.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand palette: shinobi orange, ink black and clean white. */
object Brand {
    val Orange = Color(0xFFFF5A36)
    val OrangeDark = Color(0xFFFF6A45)
    val Ink = Color(0xFF0F0F10)
}

/** Colors that Material's scheme has no slot for. */
@Immutable
class AppExtras(
    val navBar: Color,
    val navActive: Color,
    val navActiveIcon: Color,
    val navInactiveIcon: Color,
    /** The "black tile" color: dark cards, selection bar, nav pill. */
    val tile: Color,
    val onTile: Color,
    val cardBorder: Color,
)

val LocalExtras = staticCompositionLocalOf<AppExtras> { error("AppExtras not provided") }

private val LightColors = lightColorScheme(
    primary = Brand.Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD1),
    onPrimaryContainer = Color(0xFF3A0E04),
    secondary = Brand.Ink,
    onSecondary = Color.White,
    tertiary = Color(0xFF2B2B2E),
    onTertiary = Color.White,
    background = Color(0xFFECECEE),
    onBackground = Brand.Ink,
    surface = Color(0xFFF8F8F9),
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE2E2E5),
    onSurfaceVariant = Color(0xFF66666C),
    surfaceTint = Color(0xFFF8F8F9),
    inverseSurface = Brand.Ink,
    inverseOnSurface = Color.White,
    outline = Color(0xFFD0D0D5),
    outlineVariant = Color(0xFFDCDCE0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F3F5),
    surfaceContainer = Color(0xFFEFEFF1),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE9E9EC),
)

private val DarkColors = darkColorScheme(
    primary = Brand.OrangeDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2012),
    onPrimaryContainer = Color(0xFFFFDAD1),
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color(0xFFE8E8EA),
    onTertiary = Brand.Ink,
    background = Color(0xFF0C0C0D),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF171719),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF242427),
    onSurfaceVariant = Color(0xFF9D9DA3),
    surfaceTint = Color(0xFF171719),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Brand.Ink,
    outline = Color(0xFF3A3A3E),
    outlineVariant = Color(0xFF2C2C30),
    surfaceContainerLowest = Color(0xFF0C0C0D),
    surfaceContainerLow = Color(0xFF131315),
    surfaceContainer = Color(0xFF171719),
    surfaceContainerHigh = Color(0xFF202023),
    surfaceContainerHighest = Color(0xFF2A2A2E),
)

private val LightExtras = AppExtras(
    navBar = Brand.Ink,
    navActive = Color.White,
    navActiveIcon = Brand.Ink,
    navInactiveIcon = Color(0xFF9A9AA0),
    tile = Brand.Ink,
    onTile = Color.White,
    cardBorder = Color(0xFFE0E0E4),
)

private val DarkExtras = AppExtras(
    navBar = Color(0xFF26262A),
    navActive = Brand.OrangeDark,
    navActiveIcon = Color.White,
    navInactiveIcon = Color(0xFF9D9DA3),
    tile = Color.Black,
    onTile = Color.White,
    cardBorder = Color(0xFF2C2C30),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val BaseType = Typography()

private val AppTypography = Typography(
    headlineLarge = BaseType.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = BaseType.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = BaseType.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    titleLarge = BaseType.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleMedium = BaseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseType.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun CpkEditorTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtras provides (if (darkTheme) DarkExtras else LightExtras)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
