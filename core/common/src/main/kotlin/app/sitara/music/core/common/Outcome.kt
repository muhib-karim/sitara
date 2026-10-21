package app.sitara.music.core.common

/** A minimal success/failure type for calls that cross the source boundary. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>

    data class Failure(val error: Throwable) : Outcome<Nothing>
}

inline fun <T> outcomeOf(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Outcome.Failure(e)
    }

/** Formats a millisecond duration as m:ss (or h:mm:ss). */
fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Formats time spent listening in whole minutes: "42 min", "1 h 5 min", "12 h". */
fun formatListening(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** Formats a byte count the way Android's storage settings do (1 kB = 1000 bytes): "820 kB", "4.2 MB", "1.3 GB". */
fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0)
    return when {
        b < 999_500 -> "${(b + 500) / 1000} kB"
        b < 999_950_000 -> "%.1f MB".format(b / 1e6)
        else -> "%.1f GB".format(b / 1e9)
    }
}
