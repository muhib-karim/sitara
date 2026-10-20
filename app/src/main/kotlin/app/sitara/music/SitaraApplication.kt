package app.sitara.music

import android.app.Application
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.player.PlaybackDependencies
import app.sitara.music.core.player.PlayerController
import app.sitara.music.source.api.FallbackStreamResolver
import app.sitara.music.source.api.StreamResolver
import app.sitara.music.source.fake.FakeMusicSource
import app.sitara.music.source.youtube.YouTubeMusicSource
import app.sitara.music.source.youtube.YouTubeStreamResolver
import app.sitara.music.source.youtube.installNewPipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Dependencies are wired by hand (see ADR 0001). Search, detail pages, radio and streams are live; home, library and lyrics are samples. */
class SitaraApplication :
    Application(),
    PlaybackDependencies {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sampleSource by lazy { FakeMusicSource() }
    val youtube by lazy { YouTubeMusicSource() }
    val settings by lazy { SettingsRepository(this) }

    // Samples first: that check is local and instant, so only YouTube tracks reach the network.
    override val streamResolver: StreamResolver by lazy { FallbackStreamResolver(listOf(sampleSource, YouTubeStreamResolver())) }
    val player by lazy { PlayerController(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        installNewPipe()
    }
}
