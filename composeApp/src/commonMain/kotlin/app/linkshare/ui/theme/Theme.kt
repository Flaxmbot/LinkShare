package app.linkshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LinkBlue,
    onPrimary = TextPrimary,
    primaryContainer = LinkBlueDark,
    secondary = AccentGreen,
    onSecondary = TextPrimary,
    tertiary = AccentAmber,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    outlineVariant = BorderSubtle,
    error = AccentRed,
    onError = TextPrimary
)

@Composable
fun LinkShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
