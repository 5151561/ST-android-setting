package io.github.sanitised.st.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class STColors(
    val bg: Color,
    val surface: Color,
    val surfaceWarm: Color,
    val fg: Color,
    val fg2: Color,
    val muted: Color,
    val border: Color,
    val borderSoft: Color,
    val accent: Color,
    val accentOn: Color,
    val success: Color,
    val warn: Color,
    val danger: Color
)

val STLightColors = STColors(
    bg = Color(0xFFFFF8F2),
    surface = Color(0xFFFFF8F2),
    surfaceWarm = Color(0xFFFFDCBE),
    fg = Color(0xFF231A12),
    fg2 = Color(0xFF51453A),
    muted = Color(0xFF7D6B5B),
    border = Color(0xFF847466),
    borderSoft = Color(0xFFE9D8C7),
    accent = Color(0xFF875213),
    accentOn = Color(0xFFFFFFFF),
    success = Color(0xFF5D641F),
    warn = Color(0xFF8A5600),
    danger = Color(0xFFBA1A1A)
)

val STDarkColors = STColors(
    bg = Color(0xFF18130E),
    surface = Color(0xFF18130E),
    surfaceWarm = Color(0xFF6B3B05),
    fg = Color(0xFFECE0D3),
    fg2 = Color(0xFFD5C3B0),
    muted = Color(0xFFBCA996),
    border = Color(0xFF9D8B7C),
    borderSoft = Color(0xFF51453A),
    accent = Color(0xFFFFB871),
    accentOn = Color(0xFF4A2700),
    success = Color(0xFFC6CB95),
    warn = Color(0xFFE5C0A2),
    danger = Color(0xFFFFB4AB)
)

val LocalSTColors = staticCompositionLocalOf { STLightColors }
