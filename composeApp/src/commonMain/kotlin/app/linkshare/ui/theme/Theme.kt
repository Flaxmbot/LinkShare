package app.linkshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NougatTeal,
    onPrimary = NougatTextPrimary,
    primaryContainer = NougatTealDark,
    secondary = NougatGreen,
    onSecondary = NougatTextPrimary,
    tertiary = NougatAmber,
    background = NougatBackground,
    onBackground = NougatTextPrimary,
    surface = NougatSurface,
    onSurface = NougatTextPrimary,
    surfaceVariant = NougatSurfaceLight,
    onSurfaceVariant = NougatTextSecondary,
    outline = NougatCardBorder,
    outlineVariant = NougatDivider,
    error = NougatRed,
    onError = NougatTextPrimary
)

@Composable
fun LinkShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
