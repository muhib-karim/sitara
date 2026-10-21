package app.sitara.music.core.data.scrobble

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.sitara.music.core.model.ScrobbleAccount
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.ScrobbleSetup
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.scrobblingStore: DataStore<Preferences> by preferencesDataStore(name = "sitara_scrobbling")
private val PAUSED = booleanPreferencesKey("paused")

/** Seals and opens a credential; [decrypt] is null for anything it cannot open. */
interface SecretCipher {
    fun encrypt(plain: String): String

    fun decrypt(sealed: String): String?
}

/**
 * The scrobbling services the listener connected: user names and settings in a DataStore of their own, each token or
 * session key sealed by [cipher] (an Android Keystore key in the app) before it is written.
 */
class ScrobbleAccounts(private val store: DataStore<Preferences>, private val cipher: SecretCipher) {
    constructor(context: Context) : this(context.applicationContext.scrobblingStore, KeystoreCipher())

    val setup: Flow<ScrobbleSetup> = store.data.map { prefs ->
        ScrobbleSetup(
            paused = prefs[PAUSED] ?: false,
            accounts = ScrobbleService.entries.mapNotNull { service ->
                val keys = Keys(service)
                prefs[keys.user]?.let { ScrobbleAccount(service, it, prefs[keys.libraryOnly] ?: false, prefs[keys.signInNeeded] ?: false) }
            },
        )
    }

    /** [service]'s token or session key; null when it is not connected or the sealed copy cannot be opened. */
    suspend fun secret(service: ScrobbleService): String? = store.data.first()[Keys(service).secret]?.let(cipher::decrypt)

    /** Connects [service] as [userName], or signs it in again, clearing an ended session. */
    suspend fun connect(service: ScrobbleService, userName: String, secret: String) {
        val sealed = cipher.encrypt(secret)
        val keys = Keys(service)
        store.edit {
            it[keys.user] = userName
            it[keys.secret] = sealed
            it.remove(keys.signInNeeded)
        }
    }

    suspend fun disconnect(service: ScrobbleService) {
        val keys = Keys(service)
        store.edit { prefs -> listOf(keys.user, keys.secret, keys.libraryOnly, keys.signInNeeded).forEach { prefs.remove(it) } }
    }

    /** The service ended the session; plays keep waiting for a new sign-in. */
    suspend fun signInNeeded(service: ScrobbleService) {
        store.edit { it[Keys(service).signInNeeded] = true }
    }

    suspend fun setLibraryOnly(service: ScrobbleService, libraryOnly: Boolean) {
        store.edit { it[Keys(service).libraryOnly] = libraryOnly }
    }

    suspend fun setPaused(paused: Boolean) {
        store.edit { it[PAUSED] = paused }
    }

    private class Keys(service: ScrobbleService) {
        private val prefix = service.name.lowercase()
        val user = stringPreferencesKey("${prefix}_user")
        val secret = stringPreferencesKey("${prefix}_secret")
        val libraryOnly = booleanPreferencesKey("${prefix}_library_only")
        val signInNeeded = booleanPreferencesKey("${prefix}_sign_in_needed")
    }
}

/** AES-GCM with a key that never leaves the Android Keystore; a sealed value is the IV and the ciphertext in Base64. */
internal class KeystoreCipher : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.encodeToByteArray()), Base64.NO_WRAP)
    }

    // A sealed value from another phone (a restored backup) or a cleared Keystore cannot be opened: the listener signs in again.
    override fun decrypt(sealed: String): String? = try {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)) }
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    } catch (e: GeneralSecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "sitara_scrobbling"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
