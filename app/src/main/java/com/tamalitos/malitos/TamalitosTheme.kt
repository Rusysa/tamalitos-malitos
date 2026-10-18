package com.tamalitos.malitos

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF22654B), onPrimary = Color.White,
    primaryContainer = Color(0xFFD4ECDD), onPrimaryContainer = Color(0xFF123D2A),
    secondary = Color(0xFF995134), onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC9), onSecondaryContainer = Color(0xFF54240F),
    tertiary = Color(0xFF526491), tertiaryContainer = Color(0xFFDCE4FF), onTertiaryContainer = Color(0xFF26375E),
    background = Color(0xFFF8F7F2), onBackground = Color(0xFF202B25),
    surface = Color(0xFFF8F7F2), onSurface = Color(0xFF202B25), onSurfaceVariant = Color(0xFF566159),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF3F2EB),
    surfaceContainer = Color(0xFFEEEFE7), surfaceContainerHigh = Color(0xFFE7EAE1),
    surfaceContainerHighest = Color(0xFFE0E5DB), outline = Color(0xFF727D73), outlineVariant = Color(0xFFD5DBD0))
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA0D8B6), onPrimary = Color(0xFF083923),
    primaryContainer = Color(0xFF214D37), onPrimaryContainer = Color(0xFFC7EED3),
    secondary = Color(0xFFF4B79A), onSecondary = Color(0xFF54240F),
    secondaryContainer = Color(0xFF663A25), onSecondaryContainer = Color(0xFFFFDBC9),
    tertiary = Color(0xFFB4C5F1), tertiaryContainer = Color(0xFF34466C), onTertiaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF111713), onBackground = Color(0xFFE1E8DF),
    surface = Color(0xFF111713), onSurface = Color(0xFFE1E8DF), onSurfaceVariant = Color(0xFFB8C5B8),
    surfaceContainerLowest = Color(0xFF0C120F), surfaceContainerLow = Color(0xFF19211B),
    surfaceContainer = Color(0xFF202A23), surfaceContainerHigh = Color(0xFF29342C),
    surfaceContainerHighest = Color(0xFF334037), outline = Color(0xFF879889), outlineVariant = Color(0xFF3D4C40))
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp))

@Composable internal fun TamalitosTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}
