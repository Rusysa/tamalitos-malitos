package com.tamalitos.malitos

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(primary = Color(0xFF28745A), onPrimary = Color.White,
    primaryContainer = Color(0xFFC5EBD7), onPrimaryContainer = Color(0xFF123C2E),
    secondary = Color(0xFF964026), secondaryContainer = Color(0xFFFFDACC),
    background = Color(0xFFFFF9EE), surface = Color(0xFFFFF9EE), surfaceContainer = Color(0xFFF2EEDF))
private val DarkColors = darkColorScheme(primary = Color(0xFF95D4B2), primaryContainer = Color(0xFF174C3C),
    secondary = Color(0xFFF6B59B), background = Color(0xFF121B16), surface = Color(0xFF121B16), surfaceContainer = Color(0xFF202B24))
@Composable internal fun TamalitosTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
