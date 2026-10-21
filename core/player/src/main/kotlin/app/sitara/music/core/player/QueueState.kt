package app.sitara.music.core.player

import app.sitara.music.core.model.RepeatMode
import app.sitara.music.core.model.Track
import kotlin.random.Random

/** One slot in the queue. [uid] tells apart the same track queued twice; [fromRadio] marks a song radio added. */
data class QueueEntry(
    val uid: Long,
    val track: Track,
    val fromRadio: Boolean = false,
)

/**
 * Immutable play queue. Every operation returns a new state, which keeps the
 * engine trivially testable and safe to expose through a StateFlow.
 *
 * When shuffled, [originalOrder] remembers the unshuffled entries so turning
 * shuffle off restores it with the current entry (not just an equal track) selected.
 */
data class QueueState(
    val entries: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val originalOrder: List<QueueEntry> = emptyList(),
    val ended: Boolean = false,
    val nextUid: Long = 0,
) {
    val items: List<Track> get() = entries.map { it.track }
    val currentEntry: QueueEntry? get() = entries.getOrNull(currentIndex)
    val current: Track? get() = currentEntry?.track
    val upNext: List<Track> get() = if (currentIndex < 0) items else items.drop(currentIndex + 1)
    val hasNext: Boolean get() = repeatMode != RepeatMode.OFF || currentIndex < entries.lastIndex

    /** The entry the player moves to by itself when the current one ends; null when playback stops there or repeats it. */
    val followingEntry: QueueEntry? get() = when {
        currentIndex < 0 || ended || repeatMode == RepeatMode.ONE -> null
        currentIndex < entries.lastIndex -> entries[currentIndex + 1]
        repeatMode == RepeatMode.ALL && entries.size > 1 -> entries[0]
        else -> null
    }

    /** How many of the last Up next songs radio added; radio songs stay behind the ones the listener queued. */
    val upNextFromRadio: Int get() = entries.drop(currentIndex + 1).takeLastWhile { it.fromRadio }.size

    /** At most one entry left after the current one and nothing repeats: the moment to extend the queue. */
    val runningOut: Boolean get() = currentIndex >= 0 && repeatMode == RepeatMode.OFF && currentIndex >= entries.lastIndex - 1

    fun setQueue(tracks: List<Track>, startIndex: Int = 0): QueueState = QueueState(
        entries = tracks.mapIndexed { i, t -> QueueEntry(nextUid + i, t) },
        currentIndex = if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.lastIndex),
        repeatMode = repeatMode,
        nextUid = nextUid + tracks.size,
    )

    /** Queues [track] at the end, or, unless [fromRadio], ahead of the songs radio added. */
    fun add(track: Track, fromRadio: Boolean = false): QueueState {
        val entry = QueueEntry(nextUid, track, fromRadio)
        val at = if (fromRadio) entries.size else entries.size - upNextFromRadio
        return copy(
            entries = entries.toMutableList().apply { add(at, entry) },
            currentIndex = if (currentIndex < 0) at else currentIndex,
            originalOrder = if (shuffled) originalOrder.toMutableList().apply { add(if (fromRadio) size else size - takeLastWhile { it.fromRadio }.size, entry) } else originalOrder,
            ended = false,
            nextUid = nextUid + 1,
        )
    }

    fun addNext(track: Track): QueueState {
        if (currentIndex < 0) return add(track)
        val entry = QueueEntry(nextUid, track)
        val list = entries.toMutableList().apply { add(currentIndex + 1, entry) }
        val original = if (shuffled) {
            val at = originalOrder.indexOfFirst { it.uid == currentEntry?.uid } + 1
            originalOrder.toMutableList().apply { add(at, entry) }
        } else {
            originalOrder
        }
        return copy(entries = list, originalOrder = original, ended = false, nextUid = nextUid + 1)
    }

    /** Inserts [tracks] right after the current entry, in their order; a finished queue stays finished. */
    fun playNext(tracks: List<Track>): QueueState = tracks.asReversed().fold(this) { q, track -> q.addNext(track) }.copy(ended = ended)

    /** Moves within Up next, where 0 is the entry after the current one. */
    fun moveUpNext(from: Int, to: Int): QueueState =
        if (from < 0 || to < 0 || from >= upNext.size || to >= upNext.size) this else move(currentIndex + 1 + from, currentIndex + 1 + to)

    fun removeUpNext(index: Int): QueueState = if (index !in upNext.indices) this else remove(currentIndex + 1 + index)

    /** Puts back an [entry] removed from Up next at [index], or as near as the queue now allows; once only. */
    fun reinsertUpNext(index: Int, entry: QueueEntry): QueueState {
        if (currentIndex < 0 || entries.any { it.uid == entry.uid }) return this
        val at = (currentIndex + 1 + index).coerceIn(currentIndex + 1, entries.size)
        return copy(
            entries = entries.toMutableList().apply { add(at, entry) },
            originalOrder = if (shuffled) originalOrder + entry else originalOrder,
            ended = false,
        )
    }

    fun move(from: Int, to: Int): QueueState {
        if (from !in entries.indices || to !in entries.indices || from == to) return this
        val list = entries.toMutableList()
        list.add(to, list.removeAt(from))
        val newIndex = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return copy(entries = list, currentIndex = newIndex)
    }

    fun remove(index: Int): QueueState {
        if (index !in entries.indices) return this
        val removed = entries[index]
        val list = entries.toMutableList().apply { removeAt(index) }
        val newIndex = when {
            list.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1
            index == currentIndex -> currentIndex.coerceAtMost(list.lastIndex)
            else -> currentIndex
        }
        return copy(entries = list, currentIndex = newIndex, originalOrder = originalOrder.filter { it.uid != removed.uid })
    }

    fun withRepeat(mode: RepeatMode): QueueState = copy(repeatMode = mode)

    fun toggleShuffle(random: Random = Random.Default): QueueState {
        val now = currentEntry
        return if (shuffled) {
            val restored = originalOrder
            copy(
                entries = restored,
                currentIndex = restored.indexOfFirst { it.uid == now?.uid },
                shuffled = false,
                originalOrder = emptyList(),
            )
        } else {
            // Radio songs are shuffled among themselves, so they still play after the listener's own.
            val (radio, own) = entries.filterIndexed { i, _ -> i != currentIndex }.partition { it.fromRadio }
            val list = listOfNotNull(now) + own.shuffled(random) + radio.shuffled(random)
            copy(entries = list, currentIndex = if (now == null) -1 else 0, shuffled = true, originalOrder = entries)
        }
    }

    /** Automatic advance when a track finishes: honours repeat-one. */
    fun next(): QueueState = when {
        entries.isEmpty() -> this
        repeatMode == RepeatMode.ONE -> copy(ended = false)
        else -> skipNext()
    }

    /** User-initiated skip: always moves on, wrapping only with repeat-all. */
    fun skipNext(): QueueState = when {
        entries.isEmpty() -> this
        currentIndex < entries.lastIndex -> copy(currentIndex = currentIndex + 1, ended = false)
        repeatMode == RepeatMode.OFF -> copy(ended = true)
        else -> copy(currentIndex = 0, ended = false)
    }

    fun previous(): QueueState = when {
        entries.isEmpty() -> this
        currentIndex > 0 -> copy(currentIndex = currentIndex - 1, ended = false)
        repeatMode == RepeatMode.ALL -> copy(currentIndex = entries.lastIndex, ended = false)
        else -> copy(currentIndex = 0, ended = false)
    }

    fun skipTo(index: Int): QueueState = if (index in entries.indices) copy(currentIndex = index, ended = false) else this

    /** Follows the player when it moves to another entry on its own (auto-advance, system Next). */
    fun select(uid: Long): QueueState {
        val index = entries.indexOfFirst { it.uid == uid }
        return if (index < 0) this else copy(currentIndex = index, ended = false)
    }
}
