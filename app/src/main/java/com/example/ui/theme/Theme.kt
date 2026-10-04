package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccentLight,
    onPrimary = BrandNavyDark,
    primaryContainer = CyanAccentDark,
    onPrimaryContainer = Color.White,
    secondary = RingOrange,
    onSecondary = Color.White,
    secondaryContainer = RingOrangeDark,
    onSecondaryContainer = Color.White,
    tertiary = SuccessGreenLight,
    onTertiary = BrandNavyDark,
    background = BrandNavyDark,
    onBackground = Color(0xFFF1F5F9),
    surface = BrandSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = BrandCardDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = StopRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanAccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = RingOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = RingOrangeDark,
    tertiary = SuccessGreenDark,
    onTertiary = Color.White,
    background = NeutralLightBackground,
    onBackground = NeutralDarkText,
    surface = NeutralLightSurface,
    onSurface = NeutralDarkText,
    surfaceVariant = NeutralLightCard,
    onSurfaceVariant = NeutralMutedText,
    error = StopRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep BharatTag's distinctive brand palette consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
