package com.mobdev.catgram.ui.theme

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
    primary = Color(0xFFE4BFAE),
    onPrimary = Color(0xFF402014),
    primaryContainer = Color(0xFF573527),
    onPrimaryContainer = Color(0xFFFFDBCB),
    inversePrimary = Color(0xFF714B3A),
    secondary = Color(0xFFE5BAD2),
    onSecondary = Color(0xFF44263B),
    secondaryContainer = Color(0xFF5C3C53),
    onSecondaryContainer = Color(0xFFFFD7ED),
    tertiary = Color(0xFF9DD3CB),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF14534E),
    onTertiaryContainer = Color(0xFFB5EFE7),
    background = Color(0xFF181210),
    onBackground = Color(0xFFEDDDD7),
    surface = Color(0xFF181210),
    onSurface = Color(0xFFEDDDD7),
    surfaceVariant = Color(0xFF50443F),
    onSurfaceVariant = Color(0xFFD4C2BA),
    surfaceTint = Color(0xFFE4BFAE),
    inverseSurface = Color(0xFFEDDDD7),
    inverseOnSurface = Color(0xFF372F2B),
    outline = Color(0xFF9D8D86),
    outlineVariant = Color(0xFF50443F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF403835),
    surfaceDim = Color(0xFF181210),
    surfaceContainerLowest = Color(0xFF120D0B),
    surfaceContainerLow = Color(0xFF211A17),
    surfaceContainer = Color(0xFF251E1B),
    surfaceContainerHigh = Color(0xFF302825),
    surfaceContainerHighest = Color(0xFF3B332F),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF714B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF2A150D),
    inversePrimary = Color(0xFFE4BFAE),
    secondary = Color(0xFF76536B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD7ED),
    onSecondaryContainer = Color(0xFF2D1126),
    tertiary = Color(0xFF316B65),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB5EFE7),
    onTertiaryContainer = Color(0xFF00201D),
    background = Color(0xFFFFF8F3),
    onBackground = Color(0xFF211A17),
    surface = Color(0xFFFFF8F3),
    onSurface = Color(0xFF211A17),
    surfaceVariant = Color(0xFFF1DED6),
    onSurfaceVariant = Color(0xFF50443F),
    surfaceTint = Color(0xFF714B3A),
    inverseSurface = Color(0xFF372F2B),
    inverseOnSurface = Color(0xFFFCEEE8),
    outline = Color(0xFF82736C),
    outlineVariant = Color(0xFFD4C2BA),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFF8F3),
    surfaceDim = Color(0xFFE5D8D2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFF9ECE6),
    surfaceContainerHigh = Color(0xFFF3E6E0),
    surfaceContainerHighest = Color(0xFFEDDED8),
)

@Composable
fun CatgramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
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
