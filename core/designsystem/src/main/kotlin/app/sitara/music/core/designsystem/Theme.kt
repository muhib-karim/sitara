package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.ThemeMode

object SitaraColors {
    val Saffron = Color(0xFFFF9F1C)
    val SaffronInk = Color(0xFF9A4D00)
    val Stage = Color(0xFF080808)
    val StageContent = Color.White
    val StageSecondary = Color(0xFFBDBDBD)
}

private val SitaraDark = darkColorScheme(
    primary = SitaraColors.Saffron,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF392308),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Color(0xFFD8D8D2),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF30302C),
    onSecondaryContainer = Color.White,
    tertiary = SitaraColors.Saffron,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF392308),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF101010),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF202020),
    onSurfaceVariant = Color(0xFFA6A6A6),
    surfaceTint = Color.Transparent,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF282828),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF202020),
    surfaceContainerHighest = Color(0xFF282828),
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFF383838),
    inverseSurface = Color(0xFFF6F6F4),
    inverseOnSurface = Color(0xFF161614),
    inversePrimary = SitaraColors.SaffronInk,
)

private val SitaraLight = lightColorScheme(
    primary = SitaraColors.SaffronInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1B8),
    onPrimaryContainer = Color(0xFF462300),
    secondary = Color(0xFF454540),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4DF),
    onSecondaryContainer = Color(0xFF161614),
    tertiary = SitaraColors.SaffronInk,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE1B8),
    onTertiaryContainer = Color(0xFF462300),
    background = Color.White,
    onBackground = Color(0xFF161614),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF161614),
    surfaceVariant = Color(0xFFECECE8),
    onSurfaceVariant = Color(0xFF62625D),
    surfaceTint = Color.Transparent,
    surfaceDim = Color(0xFFE4E4DF),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFAF8),
    surfaceContainer = Color(0xFFF6F6F4),
    surfaceContainerHigh = Color(0xFFECECE8),
    surfaceContainerHighest = Color(0xFFE4E4DF),
    outline = Color(0xFF777770),
    outlineVariant = Color(0xFFC8C8C2),
    inverseSurface = Color(0xFF202020),
    inverseOnSurface = Color.White,
    inversePrimary = SitaraColors.Saffron,
)

/** Artwork belongs to the listening stage; chrome stays recognisably Sitara. */
fun sitaraColorScheme(mode: ThemeMode, dark: Boolean, dynamic: ColorScheme? = null): ColorScheme {
    val base = if (dark || mode == ThemeMode.AMOLED) SitaraDark else SitaraLight
    return when (mode) {
        ThemeMode.DYNAMIC -> if (dynamic == null) base else base.copy(primary = dynamic.primary, onPrimary = dynamic.onPrimary)
        ThemeMode.AMOLED -> base.copy(surface = Color.Black, surfaceContainer = Color.Black)
        ThemeMode.ARTWORK -> base
    }
}

val LocalArtworkWash = staticCompositionLocalOf { true }

@Composable
fun SitaraTheme(
    themeMode: ThemeMode = ThemeMode.ARTWORK,
    darkMode: DarkMode = DarkMode.SYSTEM,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = themeMode == ThemeMode.AMOLED || when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val dynamic = if (themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    CompositionLocalProvider(
        LocalReducedMotion provides reducedMotion,
        LocalArtworkWash provides (themeMode != ThemeMode.AMOLED),
    ) {
        MaterialTheme(
            colorScheme = sitaraColorScheme(themeMode, dark, dynamic),
            typography = SitaraTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}
