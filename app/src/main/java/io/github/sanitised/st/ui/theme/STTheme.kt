package io.github.sanitised.st.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Typography as MaterialTypography
import io.github.sanitised.st.ThemeColorSource

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
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val pill: Dp = 9999.dp
)

@Immutable
data class STTypography(
    val displayLarge: TextStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Normal, lineHeight = 56.sp),
    val displayMedium: TextStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
    val headlineLarge: TextStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp),
    val headlineMedium: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
    val bodyLarge: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    val bodyMedium: TextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    val bodySmall: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    val labelMedium: TextStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    val mono: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
)

val LocalSTSpacing = staticCompositionLocalOf { STSpacing() }
val LocalSTRadius = staticCompositionLocalOf { STRadius() }
val LocalSTTypography = staticCompositionLocalOf { STTypography() }

// 聊天正文字号缩放系数(基准 14sp -> scale 1f)与消息冒泡风格开关,由 STAppTheme 提供、
// 聊天渲染层(ChatRichTextLine / ChatBubbleSurface)消费。用 compositionLocalOf(非 static):
// 这两个值会在设置屏被用户修改,变更时需要触发消费者重组;而聊天滚动时它们恒定,
// .current 只是一次读取不引发重组,故对滚动帧率无影响。
val LocalChatFontScale = compositionLocalOf { 1f }
val LocalChatBubbleStyle = compositionLocalOf { true }

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

private val LightBrandColorScheme = lightColorScheme(
    primary = Color(0xFF875213),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCBE),
    onPrimaryContainer = Color(0xFF2D1600),
    secondary = Color(0xFF755A43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCBE),
    onSecondaryContainer = Color(0xFF2B1708),
    tertiary = Color(0xFF5D641F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E7B0),
    onTertiaryContainer = Color(0xFF1B1F00),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF231A12),
    surfaceVariant = Color(0xFFF1DFD0),
    onSurfaceVariant = Color(0xFF51453A),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF231A12),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E6),
    surfaceContainer = Color(0xFFF8EADD),
    surfaceContainerHigh = Color(0xFFF2E4D6),
    surfaceContainerHighest = Color(0xFFEBDDCF),
    outline = Color(0xFF847466),
    outlineVariant = Color(0xFFD5C3B0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkBrandColorScheme = darkColorScheme(
    primary = Color(0xFFFFB871),
    onPrimary = Color(0xFF4A2700),
    primaryContainer = Color(0xFF6B3B05),
    onPrimaryContainer = Color(0xFFFFDCBE),
    secondary = Color(0xFFE5C0A2),
    onSecondary = Color(0xFF422B17),
    secondaryContainer = Color(0xFF5B412B),
    onSecondaryContainer = Color(0xFFFFDCBE),
    tertiary = Color(0xFFC6CB95),
    onTertiary = Color(0xFF2F340D),
    tertiaryContainer = Color(0xFF454B21),
    onTertiaryContainer = Color(0xFFE2E7B0),
    surface = Color(0xFF18130E),
    onSurface = Color(0xFFECE0D3),
    surfaceVariant = Color(0xFF51453A),
    onSurfaceVariant = Color(0xFFD5C3B0),
    background = Color(0xFF18130E),
    onBackground = Color(0xFFECE0D3),
    surfaceContainerLowest = Color(0xFF120E09),
    surfaceContainerLow = Color(0xFF211B14),
    surfaceContainer = Color(0xFF251F17),
    surfaceContainerHigh = Color(0xFF302921),
    surfaceContainerHighest = Color(0xFF3B342B),
    outline = Color(0xFF9D8B7C),
    outlineVariant = Color(0xFF51453A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = MaterialTypography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun STAppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    colorSource: ThemeColorSource = ThemeColorSource.BRAND,
    chatFontSize: Float = 14f,
    chatBubbleStyle: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val materialColorScheme = when {
        colorSource == ThemeColorSource.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useDarkTheme -> DarkBrandColorScheme
        else -> LightBrandColorScheme
    }

    CompositionLocalProvider(
        LocalSTColors provides materialColorScheme.asLegacySTColors(),
        LocalSTSpacing provides STSpacing(),
        LocalSTRadius provides STRadius(),
        LocalSTTypography provides STTypography(),
        LocalChatFontScale provides (chatFontSize / 14f),
        LocalChatBubbleStyle provides chatBubbleStyle
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

private fun ColorScheme.asLegacySTColors(): STColors {
    return STColors(
        bg = background,
        surface = surface,
        surfaceWarm = primaryContainer,
        fg = onBackground,
        fg2 = onSurface,
        muted = onSurfaceVariant,
        border = outline,
        borderSoft = outlineVariant,
        accent = primary,
        accentOn = onPrimary,
        success = tertiary,
        warn = secondary,
        danger = error
    )
}
