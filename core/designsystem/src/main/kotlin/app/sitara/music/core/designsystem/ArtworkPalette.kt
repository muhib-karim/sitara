package app.sitara.music.core.designsystem

import app.sitara.music.core.model.Artwork
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colours the player derives from a cover. Pure ARGB maths (no Android types)
 * so it is unit-tested on the JVM and cheap enough to run on every track change.
 */
data class ArtworkTheme(
    val accent: Int,
    val onAccent: Int,
    val backdropTop: Int,
    val backdropBottom: Int,
    val content: Int,
)

/**
 * What a loaded cover tells the stage: its [swatches], the most common first and then the vibrant one (or the muted one
 * when nothing in the cover is vibrant), and the [brightest] colour its blur can show.
 */
data class CoverColors(val swatches: List<Int>, val brightest: Int)

object ArtworkPalette {
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val NEAR_BLACK = 0xFF080808.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** [colors]: the cover's most common colour first, then the one to accent with. */
    fun themeFrom(colors: List<Int>, dark: Boolean): ArtworkTheme {
        require(colors.isNotEmpty())
        // A grey dominant can borrow a chromatic secondary.
        val dominant = colors.first()
        val seed = if (chroma(dominant) >= 0.08f) dominant else colors.maxBy { chroma(it) }
        val (h, rawS, _) = toHsl(seed)
        // Near-greys stay neutral instead of picking up an arbitrary hue.
        val s = if (chroma(seed) < 0.08f) 0f else rawS.coerceIn(0.25f, 0.60f)
        // The wash keeps the cover's main colour; the accent takes the vibrant one when the cover has one.
        val accentSeed = colors.getOrNull(1)?.takeIf { chroma(it) >= 0.08f }
        val accentH = if (accentSeed == null) h else toHsl(accentSeed).first
        val accentS = when {
            accentSeed != null -> toHsl(accentSeed).second.coerceIn(0.45f, 0.9f)
            s == 0f -> 0f
            else -> s.coerceIn(0.45f, 0.9f)
        }
        val top = if (dark) fromHsl(h, (s * 0.7f).coerceAtMost(0.6f), 0.26f) else fromHsl(h, (s * 0.6f).coerceAtMost(0.55f), 0.86f)
        val bottom = if (dark) fromHsl(h, (s * 0.5f).coerceAtMost(0.4f), 0.07f) else fromHsl(h, (s * 0.3f).coerceAtMost(0.3f), 0.96f)
        var accentL = if (dark) 0.68f else 0.42f
        var accent = fromHsl(accentH, accentS, accentL)
        var onAccent = bestOn(accent)
        // The accent is drawn as text on the backdrop and under text of its own. Mid-luminance hues (blues, greys)
        // miss AA on one or the other, so walk lightness away from the backdrop until both pass; the far end always does.
        while ((contrast(accent, onAccent) < 4.5 || contrast(accent, top) < 4.5) && accentL in 0.05f..0.95f) {
            accentL += if (dark) 0.03f else -0.03f
            accent = fromHsl(accentH, accentS, accentL)
            onAccent = bestOn(accent)
        }
        val content = if (dark) WHITE else NEAR_BLACK
        return ArtworkTheme(accent, onAccent, top, bottom, content)
    }

    /** [artwork]'s theme: from its [cover] once loaded, else (no cover, offline, failed) from the artwork's own colours. */
    fun coverTheme(artwork: Artwork, cover: CoverColors?, dark: Boolean): ArtworkTheme =
        themeFrom(cover?.swatches?.takeIf { it.isNotEmpty() } ?: artwork.colors, dark)

    /**
     * The least black scrim the stage needs so [text] reads at AA wherever the blurred cover shows its [brightest]
     * colour: the theme's wash is under the scrim and the accent glow over it, as the backdrop draws them. Never below
     * [ArtworkScrim.Floor], so a dark cover's own colour still carries the stage.
     */
    fun stageScrim(brightest: Int, theme: ArtworkTheme, text: Int): Float {
        var scrim = ArtworkScrim.Floor
        while (scrim < 1f && contrast(text, stageBrightest(brightest, theme, scrim)) < 4.5) scrim += 0.02f
        return scrim.coerceAtMost(1f)
    }

    /** The brightest colour the stage shows over a cover whose blur peaks at [brightest], under a [scrim] of black. */
    fun stageBrightest(brightest: Int, theme: ArtworkTheme, scrim: Float): Int =
        over(theme.accent, ArtworkScrim.Glow, over(BLACK, scrim, over(theme.backdropTop, ArtworkScrim.Wash, brightest)))

    /** [top] at [alpha] over the opaque [bottom], blended in sRGB as the canvas does. */
    private fun over(top: Int, alpha: Float, bottom: Int): Int {
        fun channel(shift: Int) = ((((top shr shift) and 0xFF) * alpha + ((bottom shr shift) and 0xFF) * (1f - alpha)) + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun chroma(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (max(r, max(g, b)) - min(r, min(g, b))) / 255f
    }

    private fun bestOn(color: Int): Int = if (contrast(color, WHITE) >= contrast(color, NEAR_BLACK)) WHITE else NEAR_BLACK

    /** Text colour for a solid [block]: pure black or white, whichever contrasts more; one of the two always reaches 4.58:1. */
    fun inkOn(block: Int): Int = if (contrast(block, WHITE) >= contrast(block, BLACK)) WHITE else BLACK

    fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    /** WCAG contrast ratio between two opaque colours (1..21). */
    fun contrast(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun saturation(argb: Int): Float = toHsl(argb).second

    fun lightness(argb: Int): Float = toHsl(argb).third

    fun toHsl(argb: Int): Triple<Float, Float, Float> {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return Triple(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return Triple(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2 * l - 1f)) * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1 -> Triple(c, x, 0f)
            hp < 2 -> Triple(x, c, 0f)
            hp < 3 -> Triple(0f, c, x)
            hp < 4 -> Triple(0f, x, c)
            hp < 5 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}
