package com.trackure.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TrackureDarkColorScheme = darkColorScheme(
    primary = SportyBluePrimary,
    secondary = SportyGreenPrimary,
    tertiary = StravaOrangeHighlight,
    background = BackgroundCanvas,
    surface = SurfaceCard,
    onPrimary = BackgroundCanvas,
    onSecondary = BackgroundCanvas,
    onTertiary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun TrackureTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TrackureDarkColorScheme,
        typography = Typography,
        content = content
    )
}