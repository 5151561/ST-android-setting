package io.github.sanitised.st.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

@Immutable
data class STSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val section: Dp = 48.dp
)

@Immutable
data class STRadius(
    val sm: Dp = 4.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 24.dp,
    val pill: Dp = 9999.dp
)

@Immutable
data class STTypography(
    val displayLarge: TextStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp),
    val displayMedium: TextStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    val headlineLarge: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    val headlineMedium: TextStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val bodyLarge: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    val bodyMedium: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    val labelMedium: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp),
    val mono: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
)

val LocalSTSpacing = staticCompositionLocalOf { STSpacing() }
val LocalSTRadius = staticCompositionLocalOf { STRadius() }
val LocalSTTypography = staticCompositionLocalOf { STTypography() }

object STTheme {
    val colors: STColors
        @Composable @ReadOnlyComposable
        get() = LocalSTColors.current

    val spacing: STSpacing
        @Composable @ReadOnlyComposable
        get() = LocalSTSpacing.current

    val radius: STRadius
        @Composable @ReadOnlyComposable
        get() = LocalSTRadius.current

    val typography: STTypography
        @Composable @ReadOnlyComposable
        get() = LocalSTTypography.current
}

private val LightMaterialColorScheme = lightColorScheme(
    primary = STLightColors.accent,
    onPrimary = STLightColors.accentOn,
    primaryContainer = STLightColors.surfaceWarm,
    onPrimaryContainer = STLightColors.fg,
    surface = STLightColors.surface,
    onSurface = STLightColors.fg,
    surfaceVariant = STLightColors.bg,
    onSurfaceVariant = STLightColors.muted,
    background = STLightColors.bg,
    onBackground = STLightColors.fg,
    outline = STLightColors.border,
    outlineVariant = STLightColors.borderSoft,
    error = STLightColors.danger,
    onError = STLightColors.accentOn,
    errorContainer = STLightColors.danger.copy(alpha = 0.12f),
    onErrorContainer = STLightColors.danger
)

private val DarkMaterialColorScheme = darkColorScheme(
    primary = STDarkColors.accent,
    onPrimary = STDarkColors.accentOn,
    primaryContainer = STDarkColors.surfaceWarm,
    onPrimaryContainer = STDarkColors.fg,
    surface = STDarkColors.surface,
    onSurface = STDarkColors.fg,
    surfaceVariant = STDarkColors.bg,
    onSurfaceVariant = STDarkColors.muted,
    background = STDarkColors.bg,
    onBackground = STDarkColors.fg,
    outline = STDarkColors.border,
    outlineVariant = STDarkColors.borderSoft,
    error = STDarkColors.danger,
    onError = STDarkColors.accentOn,
    errorContainer = STDarkColors.danger.copy(alpha = 0.12f),
    onErrorContainer = STDarkColors.danger
)

@Composable
fun STAppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val stColors = if (useDarkTheme) STDarkColors else STLightColors
    val materialColorScheme = if (useDarkTheme) DarkMaterialColorScheme else LightMaterialColorScheme

    CompositionLocalProvider(
        LocalSTColors provides stColors,
        LocalSTSpacing provides STSpacing(),
        LocalSTRadius provides STRadius(),
        LocalSTTypography provides STTypography()
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
