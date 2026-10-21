package app.sitara.music.core.data.imports

import app.sitara.music.core.model.SourceSong
import app.sitara.music.core.model.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** A score from here up is taken as the song without asking. */
const val CONFIDENT = 0.8f

/** A score from here up, below [CONFIDENT], is offered for review; lower is not found. */
const val PLAUSIBLE = 0.45f

private const val TITLE_WEIGHT = 0.6
private const val ARTIST_WEIGHT = 0.3
private const val DURATION_WEIGHT = 0.1

/**
 * How sure the importer is that [candidate] is the song [source] names, from 0 to 1: the title counts most, then the
 * artist and the length, each only when the source gives it. Case, accents, punctuation, bracketed notes, featured
 * artists and "- Remastered" style suffixes are ignored, and a video title that leads with the artist is read without it.
 */
fun matchScore(source: SourceSong, candidate: Track): Float {
    val parts = buildList {
        add(TITLE_WEIGHT to overlap(words(cleanTitle(source.title)), words(cleanTitle(withoutArtistPrefix(candidate.title, source.artist)))))
        if (source.artist.isNotBlank()) add(ARTIST_WEIGHT to artistSimilarity(source.artist, candidate.artist))
        val durationMs = source.durationMs
        if (durationMs != null && candidate.durationMs > 0) add(DURATION_WEIGHT to durationSimilarity(durationMs, candidate.durationMs))
    }
    return (parts.sumOf { (weight, score) -> weight * score } / parts.sumOf { it.first }).toFloat()
}

private val LATIN_ACCENTS = Regex("(?<=\\p{IsLatin})\\p{Mn}+")
private val BRACKETED = Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\{[^}]*\\}")
private val FEATURING = Regex("\\s(feat|ft|featuring)\\.?\\s.*$", RegexOption.IGNORE_CASE)
private val VERSION_SUFFIX = Regex("\\s[-–—]\\s.*\\b(remaster(ed)?|mono|stereo|radio edit|single version)\\b.*$", RegexOption.IGNORE_CASE)
private val NON_WORD = Regex("[^\\p{L}\\p{N}\\p{M}]+")
private val ARTIST_SEPARATORS = Regex("\\s*(,|&|;|/|\\sx\\s|\\sand\\s|\\sfeat\\.?\\s|\\sft\\.?\\s|\\swith\\s)\\s*", RegexOption.IGNORE_CASE)
private val CHANNEL_SUFFIX = Regex("(\\s-\\stopic|vevo|\\sofficial)$", RegexOption.IGNORE_CASE)

/** Lower-case words with Latin accents dropped; other scripts are kept as written. */
internal fun words(text: String): Set<String> =
    LATIN_ACCENTS.replace(Normalizer.normalize(text, Normalizer.Form.NFKD), "").lowercase(Locale.ROOT).split(NON_WORD).filter(String::isNotEmpty).toSet()

private fun cleanTitle(title: String): String = BRACKETED.replace(VERSION_SUFFIX.replace(FEATURING.replace(title, ""), ""), " ")

/** "Artist - Title" video names lose the artist part when it names the source's artist. */
private fun withoutArtistPrefix(title: String, artist: String): String {
    val at = title.indexOf(" - ")
    if (at < 0 || artist.isBlank()) return title
    val prefix = artistNames(title.substring(0, at))
    return if (prefix.any { it in artistNames(artist) }) title.substring(at + 3) else title
}

/** Sørensen–Dice overlap of two word sets. */
private fun overlap(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else 2.0 * a.intersect(b).size / (a.size + b.size)

/** Each credited artist as one key (words run together, a leading "the" and channel suffixes dropped). */
private fun artistNames(credit: String): Set<String> = credit.split(ARTIST_SEPARATORS)
    .map { name -> words(CHANNEL_SUFFIX.replace(name.trim(), "")).filter { it != "the" }.joinToString("") }
    .filter(String::isNotEmpty)
    .toSet()

/** 1 when the two credits share an artist, else the overlap of their words. */
private fun artistSimilarity(source: String, candidate: String): Double =
    if (artistNames(source).any { it in artistNames(candidate) }) 1.0 else overlap(words(source), words(candidate))

/** 1 within 3 s, falling to 0 at 30 s apart. */
private fun durationSimilarity(aMs: Long, bMs: Long): Double = (1.0 - (abs(aMs - bMs) / 1_000.0 - 3.0) / 27.0).coerceIn(0.0, 1.0)
