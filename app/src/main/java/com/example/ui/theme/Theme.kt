package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AmberWarm,
    onPrimary = SlateDark,
    primaryContainer = SlateBorder,
    onPrimaryContainer = AmberGlow,
    secondary = SteelGrey,
    onSecondary = SlateDark,
    secondaryContainer = SlateCard,
    onSecondaryContainer = PaperCream,
    tertiary = CopperAccent,
    onTertiary = Color.White,
    background = SlateDark,
    onBackground = PaperCream,
    surface = SlateCard,
    onSurface = PaperCream,
    surfaceVariant = SlateBorder,
    onSurfaceVariant = SteelGrey,
    outline = SlateBorder,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AmberWarm,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFFE65100),
    secondary = Color(0xFF455A64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFD8DC),
    onSecondaryContainer = Color(0xFF263238),
    tertiary = CopperAccent,
    onTertiary = Color.White,
    background = Color(0xFFECEFF1),
    onBackground = Color(0xFF212121),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFCFD8DC),
    onSurfaceVariant = Color(0xFF37474F),
    outline = Color(0xFFB0BEC5),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce Joe joe's custom identity!
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
