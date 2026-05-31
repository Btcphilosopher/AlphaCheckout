package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val NaturalTonesColorScheme = lightColorScheme(
    primary = SteelPrimary,
    secondary = SteelSecondary,
    tertiary = SteelTertiary,
    background = SpaceDarkBG,
    surface = SpaceSurface,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1B20), // Charcoal text
    onSurface = Color(0xFF1D1B20),     // Charcoal text
    outline = SpaceOutline,
    error = SignalFailure,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Use clean light Natural Tones by default
    dynamicColor: Boolean = false, // Enforce our custom Natural Tones theme
    content: @Composable () -> Unit,
) {
    val colorScheme = NaturalTonesColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
