package dev.infinityf4p.tiebapure.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFC93600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2D6),
    onPrimaryContainer = Color(0xFF491300),
    inversePrimary = Color(0xFFFF8B60),
    secondary = Color(0xFF57636C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1E4E8),
    onSecondaryContainer = Color(0xFF1A1A1B),
    tertiary = Color(0xFF248A3D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F7E0),
    onTertiaryContainer = Color(0xFF0A3215),
    background = Color.White,
    onBackground = Color(0xFF1A1A1B),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1B),
    surfaceTint = Color(0xFFC93600),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF57636C),
    inverseSurface = Color(0xFF343536),
    inverseOnSurface = Color(0xFFF5F5F5),
    surfaceDim = Color(0xFFE1E4E8),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFF0F1F2),
    surfaceContainerHighest = Color(0xFFE1E4E8),
    outline = Color(0xFF81878C),
    outlineVariant = Color(0xFFE1E4E8),
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF680008),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8B60),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF6B260D),
    onPrimaryContainer = Color(0xFFFFE2D6),
    inversePrimary = Color(0xFFC93600),
    secondary = Color(0xFFA7B0B7),
    onSecondary = Color(0xFF252526),
    secondaryContainer = Color(0xFF343536),
    onSecondaryContainer = Color(0xFFE8EDF0),
    tertiary = Color(0xFF30D158),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF164823),
    onTertiaryContainer = Color(0xFFD9F7E0),
    background = Color(0xFF030303),
    onBackground = Color(0xFFE8EDF0),
    surface = Color(0xFF1A1A1B),
    onSurface = Color(0xFFE8EDF0),
    surfaceTint = Color(0xFFFF8B60),
    surfaceVariant = Color(0xFF252526),
    onSurfaceVariant = Color(0xFFA7B0B7),
    inverseSurface = Color(0xFFE1E4E8),
    inverseOnSurface = Color(0xFF1A1A1B),
    surfaceDim = Color(0xFF030303),
    surfaceBright = Color(0xFF252526),
    surfaceContainerLowest = Color(0xFF030303),
    surfaceContainerLow = Color(0xFF121213),
    surfaceContainer = Color(0xFF1A1A1B),
    surfaceContainerHigh = Color(0xFF252526),
    surfaceContainerHighest = Color(0xFF252526),
    outline = Color(0xFF81878C),
    outlineVariant = Color(0xFF343536),
    error = Color(0xFFFF453A),
    onError = Color.Black,
    errorContainer = Color(0xFF680008),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

val BrandFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
)

internal fun readerTypography(family: FontFamily): Typography {
    val defaults = Typography()
    return Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = family),
        displayMedium = defaults.displayMedium.copy(fontFamily = family),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = family),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = family),
        displaySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 34.sp,
            lineHeight = 41.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
        ),
    )
}

private val TiebaPureShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

@Immutable
data class TiebaPureDimensions(
    val spacingXxs: Dp = 4.dp,
    val spacingXs: Dp = 8.dp,
    val spacingSm: Dp = 12.dp,
    val spacingMd: Dp = 16.dp,
    val spacingLg: Dp = 24.dp,
    val chipRadius: Dp = 6.dp,
    val mediaRadius: Dp = 8.dp,
    val cardRadius: Dp = 4.dp,
    val avatarSmall: Dp = 32.dp,
    val avatarMedium: Dp = 40.dp,
    val avatarLarge: Dp = 48.dp,
    val avatarProfile: Dp = 56.dp,
    val iconInline: Dp = 17.dp,
    val iconToolbar: Dp = 22.dp,
    val iconPlay: Dp = 48.dp,
    val minimumTouchTarget: Dp = 48.dp,
    val readableContentMaxWidth: Dp = 680.dp,
)

val LocalTiebaPureDimensions = staticCompositionLocalOf { TiebaPureDimensions() }

object TiebaPureColors {
    val BrandAccent = Color(0xFFFF4500)
    val VideoAccent = Color(0xFFF59E0B)
}

@Composable
fun TiebaPureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily = BrandFontFamily,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = remember(fontFamily) { readerTypography(fontFamily) },
        shapes = TiebaPureShapes,
        content = content,
    )
}
