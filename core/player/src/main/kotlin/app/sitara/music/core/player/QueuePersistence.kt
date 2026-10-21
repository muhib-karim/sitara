package app.sitara.music.core.player

import app.sitara.music.core.model.SavedQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest

internal const val QUEUE_SAVE_DEBOUNCE_MS = 1_000L
internal const val QUEUE_SAVE_INTERVAL_MS = 15_000L

/** The queue as kept across a restart, [positionMs] into the current entry; null when nothing is queued. */
fun QueueState.toSaved(positionMs: Long): SavedQueue? {
    if (currentEntry == null) return null
    val unshuffled = if (shuffled) originalOrder.map { original -> entries.indexOfFirst { it.uid == original.uid } } else null
    // A finished queue would resume at the very end of its last song; start that song over instead.
    return SavedQueue(items, currentIndex, if (ended) 0 else positionMs.coerceAtLeast(0), repeatMode, unshuffled)
}

/** The queue a [SavedQueue] describes, with fresh entry ids; null when it has no current song. A broken shuffle order is dropped. */
fun SavedQueue.toQueueState(): QueueState? {
    if (currentIndex !in tracks.indices) return null
    val entries = tracks.mapIndexed { i, track -> QueueEntry(i.toLong(), track) }
    val order = unshuffledOrder?.takeIf { it.sorted() == tracks.indices.toList() }
    return QueueState(
        entries = entries,
        currentIndex = currentIndex,
        repeatMode = repeatMode,
        shuffled = order != null,
        originalOrder = order?.map(entries::get).orEmpty(),
        nextUid = entries.size.toLong(),
    )
}

/**
 * Hands [save] the queue once a change settles, whenever playback pauses and every [QUEUE_SAVE_INTERVAL_MS] while
 * playing, so process death loses at most a few seconds of position; the play being heard is checkpointed with it.
 * An empty queue is never saved over the last one.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal suspend fun keepSaving(engine: PlaybackEngine, save: (SavedQueue) -> Unit) {
    val playing = engine.state.map { it.isPlaying }.distinctUntilChanged()
    merge(
        engine.state.map { it.queue }.distinctUntilChanged().debounce(QUEUE_SAVE_DEBOUNCE_MS),
        playing.filter { !it },
        playing.transformLatest { isPlaying ->
            while (isPlaying) {
                delay(QUEUE_SAVE_INTERVAL_MS)
                emit(Unit)
            }
        },
    ).collect {
        engine.checkpointListening()
        engine.savedQueue()?.let(save)
    }
}
