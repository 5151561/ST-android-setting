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
    bg = Color(0xFFF8FAFD),
    surface = Color(0xFFFFFFFF),
    surfaceWarm = Color(0xFFE8F0FE),
    fg = Color(0xFF202124),
    fg2 = Color(0xFF3C4043),
    muted = Color(0xFF5F6368),
    border = Color(0xFFDADCE0),
    borderSoft = Color(0xFFEDF0F2),
    accent = Color(0xFF1A73E8),
    accentOn = Color(0xFFFFFFFF),
    success = Color(0xFF188038),
    warn = Color(0xFFF9AB00),
    danger = Color(0xFFD93025)
)

val STDarkColors = STColors(
    bg = Color(0xFF1A1C1E),
    surface = Color(0xFF2D2F31),
    surfaceWarm = Color(0xFF1E2A3A),
    fg = Color(0xFFE3E3E3),
    fg2 = Color(0xFFC4C7C5),
    muted = Color(0xFF9AA0A6),
    border = Color(0xFF444746),
    borderSoft = Color(0xFF3C3E40),
    accent = Color(0xFF8AB4F8),
    accentOn = Color(0xFF1A1C1E),
    success = Color(0xFF81C995),
    warn = Color(0xFFFDD663),
    danger = Color(0xFFF28B82)
)

val LocalSTColors = staticCompositionLocalOf { STLightColors }
