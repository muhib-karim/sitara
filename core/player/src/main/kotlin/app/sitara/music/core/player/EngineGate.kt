package app.sitara.music.core.player

/**
 * Holds UI commands until the service's [PlaybackEngine] exists, then runs them once, in order.
 * Attaching never touches the player, so reconnecting a screen cannot restart playback.
 */
class EngineGate {
    private var engine: PlaybackEngine? = null
    private val pending = ArrayDeque<(PlaybackEngine) -> Unit>()

    fun run(action: (PlaybackEngine) -> Unit) {
        val e = engine
        if (e != null) action(e) else pending.addLast(action)
    }

    fun attach(engine: PlaybackEngine?) {
        this.engine = engine
        if (engine == null) return
        while (pending.isNotEmpty()) pending.removeFirst()(engine)
    }
}
