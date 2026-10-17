package app.sitara.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.ThemeMode

object SitaraColors {
    val Night = Color(0xFF0B0B14)
    val NightRaised = Color(0xFF15152A)
    val Gold = Color(0xFFF5C451)
    val Violet = Color(0xFF8B7CFF)
    val Rose = Color(0xFFFF6B9A)
    val Mist = Color(0xFFF6F5FB)
}

private val SitaraDark = darkColorScheme(
    primary = SitaraColors.Gold,
    onPrimary = SitaraColors.Night,
    secondary = SitaraColors.Violet,
    onSecondary = Color.White,
    tertiary = SitaraColors.Rose,
    background = SitaraColors.Night,
    onBackground = Color(0xFFECEAF6),
    surface = SitaraColors.Night,
    onSurface = Color(0xFFECEAF6),
    surfaceVariant = SitaraColors.NightRaised,
    onSurfaceVariant = Color(0xFFB4B1C8),
    surfaceContainer = Color(0xFF13131F),
    surfaceContainerHigh = Color(0xFF1B1B2B),
)

private val SitaraLight = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A00),
    tertiary = Color(0xFFC2185B),
    background = SitaraColors.Mist,
    onBackground = Color(0xFF15131F),
    surface = SitaraColors.Mist,
    onSurface = Color(0xFF15131F),
    surfaceVariant = Color(0xFFE7E5F1),
    onSurfaceVariant = Color(0xFF4A475C),
    surfaceContainer = Color(0xFFEEEDF6),
    surfaceContainerHigh = Color(0xFFE6E4F0),
)

val SitaraTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/** Builds the colour scheme for a theme mode; artwork colours tint the brand scheme. */
fun sitaraColorScheme(mode: ThemeMode, dark: Boolean, artwork: List<Int>?, dynamic: ColorScheme? = null): ColorScheme {
    val base = if (dark) SitaraDark else SitaraLight
    return when (mode) {
        ThemeMode.DYNAMIC -> dynamic ?: base
        ThemeMode.AMOLED -> SitaraDark.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF0A0A0A))
        ThemeMode.ARTWORK -> {
            if (artwork.isNullOrEmpty()) return base
            val t = ArtworkPalette.themeFrom(artwork, dark)
            base.copy(primary = Color(t.accent), onPrimary = Color(t.onAccent))
        }
    }
}

@Composable
fun SitaraTheme(
    themeMode: ThemeMode = ThemeMode.ARTWORK,
    darkMode: DarkMode = DarkMode.SYSTEM,
    artwork: List<Int>? = null,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme() || themeMode == ThemeMode.AMOLED
        DarkMode.LIGHT -> themeMode == ThemeMode.AMOLED
        DarkMode.DARK -> true
    }
    val dynamic = if (themeMode == ThemeMode.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        null
    }
    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = sitaraColorScheme(themeMode, dark, artwork, dynamic),
            typography = SitaraTypography,
            content = content,
        )
    }
}
