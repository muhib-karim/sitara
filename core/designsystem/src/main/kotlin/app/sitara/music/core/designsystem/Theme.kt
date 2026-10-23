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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.sitara.music.core.model.AccentColor
import app.sitara.music.core.model.Artwork
import app.sitara.music.core.model.DarkMode
import app.sitara.music.core.model.SitaraSettings
import app.sitara.music.core.model.SurfaceTone

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

/** [accent]'s tone for dark or light chrome, tuned to read at AA on every surface; null for [AccentColor.ARTWORK], which follows the cover. */
fun accentTone(accent: AccentColor, dark: Boolean): Color? = when (accent) {
    AccentColor.SAFFRON -> if (dark) SitaraColors.Saffron else SitaraColors.SaffronInk
    AccentColor.ROSE -> Color(if (dark) 0xFFFF7A9C else 0xFFB0204E)
    AccentColor.VIOLET -> Color(if (dark) 0xFFB79CFF else 0xFF6B3FD4)
    AccentColor.BLUE -> Color(if (dark) 0xFF6EB4FF else 0xFF1F5FBF)
    AccentColor.TEAL -> Color(if (dark) 0xFF3FD6C2 else 0xFF006B60)
    AccentColor.GREEN -> Color(if (dark) 0xFF7FD66B else 0xFF2A6E1F)
    AccentColor.ARTWORK -> null
}

/** The colour [tone] casts over the surfaces, or null for neutral; quiet enough that the tinting keeps text at AA. */
fun surfaceTint(tone: SurfaceTone): Color? = when (tone) {
    SurfaceTone.NEUTRAL -> null
    SurfaceTone.WARM -> Color(0xFF8A5A2B)
    SurfaceTone.COOL -> Color(0xFF2B5F8A)
    SurfaceTone.INK -> Color(0xFF3D2B8A)
    SurfaceTone.GREEN -> Color(0xFF2B6B3E)
}

/**
 * The app's colours: Sitara's neutral chrome, tinted with the playing cover's main colour when there is a [tint], black
 * in place of dark grey when [pureBlack], and [accent] as primary once it reads at AA on every surface.
 */
fun sitaraColorScheme(dark: Boolean, pureBlack: Boolean = false, accent: Color? = null, tint: Color? = null): ColorScheme {
    var scheme = if (dark) SitaraDark else SitaraLight
    if (tint != null) scheme = scheme.tinted(tint, dark)
    if (dark && pureBlack) scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color.Black)
    if (accent != null) {
        val primary = readableAccent(accent.toArgb(), scheme, dark)
        scheme = scheme.copy(primary = Color(primary), onPrimary = Color(ArtworkPalette.inkOn(primary)), tertiary = Color(primary), onTertiary = Color(ArtworkPalette.inkOn(primary)))
    }
    return scheme
}

/** [accent], made lighter on dark chrome or darker on light chrome until it reads at AA on all of [scheme]'s surfaces. */
private fun readableAccent(accent: Int, scheme: ColorScheme, dark: Boolean): Int {
    val surfaces = scheme.run { listOf(background, surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest) }.map { it.toArgb() }
    val (h, s, start) = ArtworkPalette.toHsl(accent)
    var l = start
    var tone = accent
    // White on the darkest and black on the lightest surfaces always pass, so the walk ends.
    while (surfaces.any { ArtworkPalette.contrast(tone, it) < 4.5 } && l in 0f..1f) {
        l += if (dark) 0.02f else -0.02f
        tone = ArtworkPalette.fromHsl(h, s, l.coerceIn(0f, 1f))
    }
    return tone
}

/** Surfaces in [tint]'s hue at a capped saturation; their lightness steps keep the neutral text colours at AA. */
private fun ColorScheme.tinted(tint: Color, dark: Boolean): ColorScheme {
    val (h, rawS, _) = ArtworkPalette.toHsl(tint.toArgb())
    val s = rawS.coerceAtMost(if (dark) 0.35f else 0.40f)
    fun tone(l: Float) = Color(ArtworkPalette.fromHsl(h, s, l))
    return if (dark) {
        copy(
            background = tone(0.03f), surface = tone(0.07f), surfaceVariant = tone(0.13f), surfaceDim = tone(0.03f), surfaceBright = tone(0.17f),
            surfaceContainerLowest = tone(0.03f), surfaceContainerLow = tone(0.07f), surfaceContainer = tone(0.09f),
            surfaceContainerHigh = tone(0.13f), surfaceContainerHighest = tone(0.17f),
        )
    } else {
        copy(
            background = tone(0.985f), surface = tone(0.965f), surfaceVariant = tone(0.93f), surfaceDim = tone(0.905f), surfaceBright = tone(0.985f),
            surfaceContainerLowest = tone(0.985f), surfaceContainerLow = tone(0.975f), surfaceContainer = tone(0.955f),
            surfaceContainerHigh = tone(0.93f), surfaceContainerHighest = tone(0.905f),
        )
    }
}

val LocalArtworkWash = staticCompositionLocalOf { true }

/**
 * Sitara's theme from the listener's Appearance [settings]; [nowPlaying] is the cover the artwork accent and tint follow.
 * [dark] defaults to the listener's light or dark choice.
 */
@Composable
fun SitaraTheme(
    settings: SitaraSettings = SitaraSettings(),
    nowPlaying: Artwork? = null,
    dark: Boolean = when (settings.darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    },
    content: @Composable () -> Unit,
) {
    val cover = nowPlaying?.takeIf { settings.accent == AccentColor.ARTWORK || settings.artworkColorsAcrossApp }?.let { rememberArtworkTheme(it, dark) }
    val wallpaper = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
    } else {
        null
    }
    val accent = wallpaper ?: if (settings.accent == AccentColor.ARTWORK) cover?.let { Color(it.accent) } else accentTone(settings.accent, dark)
    // The playing cover's tint, when the listener asked for it, outranks the palette's tone.
    val tint = cover?.takeIf { settings.artworkColorsAcrossApp }?.let { Color(it.backdropTop) } ?: surfaceTint(settings.surfaceTone)
    CompositionLocalProvider(
        LocalReducedMotion provides settings.reducedMotion,
        LocalArtworkWash provides !settings.pureBlack,
    ) {
        MaterialTheme(
            colorScheme = sitaraColorScheme(dark, settings.pureBlack, accent, tint),
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
