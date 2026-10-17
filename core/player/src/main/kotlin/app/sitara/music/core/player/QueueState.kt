package app.sitara.music.core.player

import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import kotlin.random.Random

/**
 * Immutable play queue. Every operation returns a new state, which keeps the
 * engine trivially testable and safe to expose through a StateFlow.
 *
 * When shuffled, [originalOrder] remembers the unshuffled list so turning
 * shuffle off restores it with the current track still selected.
 */
data class QueueState(
    val items: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val originalOrder: List<Track> = emptyList(),
    val ended: Boolean = false,
) {
    val current: Track? get() = items.getOrNull(currentIndex)
    val upNext: List<Track> get() = if (currentIndex < 0) items else items.drop(currentIndex + 1)
    val hasNext: Boolean get() = repeatMode != RepeatMode.OFF || currentIndex < items.lastIndex

    fun setQueue(tracks: List<Track>, startIndex: Int = 0): QueueState = QueueState(
        items = tracks,
        currentIndex = if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.lastIndex),
        repeatMode = repeatMode,
    )

    fun add(track: Track): QueueState = copy(
        items = items + track,
        currentIndex = if (currentIndex < 0) items.size else currentIndex,
        originalOrder = if (shuffled) originalOrder + track else originalOrder,
        ended = false,
    )

    fun addNext(track: Track): QueueState {
        if (currentIndex < 0) return add(track)
        val list = items.toMutableList().apply { add(currentIndex + 1, track) }
        val original = if (shuffled) {
            val at = originalOrder.indexOf(current) + 1
            originalOrder.toMutableList().apply { add(at, track) }
        } else {
            originalOrder
        }
        return copy(items = list, originalOrder = original, ended = false)
    }

    fun move(from: Int, to: Int): QueueState {
        if (from !in items.indices || to !in items.indices || from == to) return this
        val list = items.toMutableList()
        list.add(to, list.removeAt(from))
        val newIndex = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return copy(items = list, currentIndex = newIndex)
    }

    fun remove(index: Int): QueueState {
        if (index !in items.indices) return this
        val removed = items[index]
        val list = items.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            list.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(list.lastIndex)
            else -> currentIndex
        }
        return copy(items = list, currentIndex = newIndex, originalOrder = originalOrder - removed)
    }

    fun withRepeat(mode: RepeatMode): QueueState = copy(repeatMode = mode)

    fun toggleShuffle(random: Random = Random.Default): QueueState {
        val now = current
        return if (shuffled) {
            val restored = originalOrder
            copy(items = restored, currentIndex = restored.indexOf(now), shuffled = false, originalOrder = emptyList())
        } else {
            val rest = items.filterIndexed { i, _ -> i != currentIndex }.shuffled(random)
            val list = listOfNotNull(now) + rest
            copy(items = list, currentIndex = if (now == null) -1 else 0, shuffled = true, originalOrder = items)
        }
    }

    /** Automatic advance when a track finishes: honours repeat-one. */
    fun next(): QueueState = when {
        items.isEmpty() -> this
        repeatMode == RepeatMode.ONE -> copy(ended = false)
        else -> skipNext()
    }

    /** User-initiated skip: always moves on, wrapping only with repeat-all. */
    fun skipNext(): QueueState = when {
        items.isEmpty() -> this
        currentIndex < items.lastIndex -> copy(currentIndex = currentIndex + 1, ended = false)
        repeatMode == RepeatMode.OFF -> copy(ended = true)
        else -> copy(currentIndex = 0, ended = false)
    }

    fun previous(): QueueState = when {
        items.isEmpty() -> this
        currentIndex > 0 -> copy(currentIndex = currentIndex - 1, ended = false)
        repeatMode == RepeatMode.ALL -> copy(currentIndex = items.lastIndex, ended = false)
        else -> copy(currentIndex = 0, ended = false)
    }

    fun skipTo(index: Int): QueueState = if (index in items.indices) copy(currentIndex = index, ended = false) else this
}
