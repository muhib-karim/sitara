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
