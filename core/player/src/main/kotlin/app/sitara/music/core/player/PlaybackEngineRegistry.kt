package app.sitara.music.core.player

import app.sitara.music.source.api.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the running service's engine to in-process UI. The engine only exists while
 * [PlaybackService] is alive; screens come and go without affecting it.
 */
object PlaybackEngineRegistry {
    private val _engine = MutableStateFlow<PlaybackEngine?>(null)
    val engine: StateFlow<PlaybackEngine?> = _engine.asStateFlow()

    internal fun attach(engine: PlaybackEngine) {
        _engine.value = engine
    }

    internal fun detach(engine: PlaybackEngine) {
        _engine.compareAndSet(engine, null)
    }
}

/** Implemented by the Application so the service can reach app-level dependencies without a DI framework. */
interface PlaybackDependencies {
    val streamResolver: StreamResolver
}
