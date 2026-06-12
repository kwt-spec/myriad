package com.cauldron.myriad.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * AMOLED true-black ember palette (MASTER_PLAN §6): pure #000 background for
 * the B2 Ultra's panel, warm parchment text, ember-orange accents.
 */
private val EmberColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF1A0E08),
    primaryContainer = Color(0xFF2A1208),
    onPrimaryContainer = Color(0xFFFFB68F),
    secondary = Color(0xFFD9A05B),
    onSecondary = Color(0xFF1A1208),
    secondaryContainer = Color(0xFF1E140C),
    onSecondaryContainer = Color(0xFFEFC9A4),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE8DDD0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE8DDD0),
    surfaceVariant = Color(0xFF120E0A),
    onSurfaceVariant = Color(0xFFB0A091),
    outline = Color(0xFF4A3A2E),
    outlineVariant = Color(0xFF2A2018),
)

private val MyriadTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = 10.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    // The reading face: 17sp serif at 1.5 line height, ~65ch measure on the B2 Ultra.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 17.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun MyriadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EmberColors,
        typography = MyriadTypography,
        content = content,
    )
}
