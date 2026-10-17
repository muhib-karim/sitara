package app.sitara.music

import android.app.Application
import app.sitara.music.core.data.SettingsRepository
import app.sitara.music.core.player.PlayerController
import app.sitara.music.source.fake.FakeMusicSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Phase A wires dependencies by hand (see ADR 0001); Hilt arrives with the real source. */
class SitaraApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val source by lazy { FakeMusicSource() }
    val settings by lazy { SettingsRepository(this) }
    val player by lazy { PlayerController(this, source, appScope) }
}
