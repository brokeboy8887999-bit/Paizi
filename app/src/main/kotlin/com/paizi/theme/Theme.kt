package com.paizi.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = PaiziDarkBackground,
    surface = PaiziDarkSurface,
    surfaceVariant = PaiziDarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = PaiziPrimary,
    onPrimary = PaiziOnPrimary,
    primaryContainer = PaiziPrimaryContainer,
    onPrimaryContainer = PaiziOnPrimaryContainer,
    secondary = PaiziSecondary,
    onSecondary = PaiziOnSecondary,
    secondaryContainer = PaiziSecondaryContainer,
    onSecondaryContainer = PaiziOnSecondaryContainer,
    tertiary = PaiziTertiary,
    onTertiary = PaiziOnTertiary,
    tertiaryContainer = PaiziTertiaryContainer,
    onTertiaryContainer = PaiziOnTertiaryContainer,
    background = PaiziBackground,
    onBackground = PaiziOnBackground,
    surface = PaiziSurface,
    onSurface = PaiziOnSurface,
    surfaceVariant = PaiziSurfaceVariant,
    onSurfaceVariant = PaiziOnSurfaceVariant
)

@Composable
fun PAIZITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
