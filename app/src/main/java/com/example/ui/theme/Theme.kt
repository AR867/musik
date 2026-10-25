package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

private val MusicFormDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBlack,
    primaryContainer = GraphiteCard,
    onPrimaryContainer = Color(0xFFE2F9FF),
    secondary = NeonViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF281E4A),
    onSecondaryContainer = Color(0xFFEDE7F6),
    tertiary = ElectricBlue,
    background = ObsidianBlack,
    onBackground = Color(0xFFF3F6FF),
    surface = GraphiteSurface,
    onSurface = Color(0xFFF3F6FF),
    surfaceVariant = GraphiteElevated,
    onSurfaceVariant = Color(0xFFA9B4CC),
    outline = Color(0xFF333D58),
    error = Color(0xFFFF5252)
)

private val MusicFormLightColorScheme = lightColorScheme(
    primary = Color(0xFF006876),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB4EBFF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = DeepPurpleAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DDFF),
    onSecondaryContainer = Color(0xFF21005D),
    tertiary = ElectricBlue,
    background = LightBackground,
    onBackground = DeepInk,
    surface = LightSurface,
    onSurface = DeepInk,
    surfaceVariant = LightElevated,
    onSurfaceVariant = Color(0xFF49536B),
    outline = Color(0xFFC5CEE0),
    error = Color(0xFFD32F2F)
)

val MusicFormShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MusicFormTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (isDark) MusicFormDarkColorScheme else MusicFormLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MusicFormShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MusicFormTheme(
        themeMode = if (darkTheme) AppThemeMode.DARK else AppThemeMode.LIGHT,
        content = content
    )
}
