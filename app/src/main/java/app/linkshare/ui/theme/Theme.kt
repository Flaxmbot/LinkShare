package app.linkshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NougatColorScheme = darkColorScheme(
    primary = NougatTeal,
    onPrimary = NougatTextPrimary,
    primaryContainer = NougatTealDark,
    onPrimaryContainer = NougatTextPrimary,
    secondary = NougatTealLight,
    onSecondary = NougatTextPrimary,
    tertiary = NougatPurple,
    background = NougatBackground,
    onBackground = NougatTextPrimary,
    surface = NougatSurface,
    onSurface = NougatTextPrimary,
    surfaceVariant = NougatSurfaceLight,
    onSurfaceVariant = NougatTextSecondary,
    error = NougatRed,
    onError = NougatTextPrimary,
    outline = NougatCardBorder,
    outlineVariant = NougatDivider
)

private val NougatTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

@Composable
fun LinkShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NougatColorScheme,
        typography = NougatTypography,
        content = content
    )
}
