package app.sitara.music.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sitara.music.core.model.SitaraSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "sitara_settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<SitaraSettings> = store.data.map { prefs ->
        SettingsCodec.decode(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
    }

    suspend fun update(transform: (SitaraSettings) -> SitaraSettings) {
        store.edit { prefs ->
            val current = SettingsCodec.decode(prefs.asMap().entries.associate { (k, v) -> k.name to v.toString() })
            SettingsCodec.encode(transform(current)).forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
        }
    }
}
