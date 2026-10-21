package app.sitara.music.core.data.scrobble

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.model.ScrobbleAccount
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.ScrobbleSetup
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrobbleAccountsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "scrobbling-test.preferences_pb").also { it.delete() }

    /** Stands in for the Keystore: reversible, and plainly not the secret. */
    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String) = "sealed:" + plain.reversed()

        override fun decrypt(sealed: String) = sealed.removePrefix("sealed:").reversed().takeIf { sealed.startsWith("sealed:") }
    }

    private fun TestScope.accounts() = ScrobbleAccounts(PreferenceDataStoreFactory.create(scope = backgroundScope) { file }, cipher)

    private val lb = ScrobbleService.LISTENBRAINZ

    @Test
    fun `nothing is connected at first`() = runTest {
        val accounts = accounts()
        assertEquals(ScrobbleSetup(paused = false, accounts = emptyList()), accounts.setup.first())
        assertNull(accounts.secret(lb))
    }

    @Test
    fun `a connected service shows its user and keeps its token sealed`() = runTest {
        val accounts = accounts()
        accounts.connect(lb, "orchid", "token-1")

        assertEquals(listOf(ScrobbleAccount(lb, "orchid")), accounts.setup.first().accounts)
        assertEquals("token-1", accounts.secret(lb))
        assertFalse("the token is never written as it is", file.readBytes().decodeToString().contains("token-1"))
    }

    @Test
    fun `an ended session is flagged until the listener signs in again`() = runTest {
        val accounts = accounts()
        accounts.connect(lb, "orchid", "token-1")
        accounts.signInNeeded(lb)
        assertEquals(true, accounts.setup.first().accounts.single().signInNeeded)

        accounts.connect(lb, "orchid", "token-2")
        assertEquals(false, accounts.setup.first().accounts.single().signInNeeded)
        assertEquals("token-2", accounts.secret(lb))
    }

    @Test
    fun `library only and pause are kept`() = runTest {
        val accounts = accounts()
        accounts.connect(lb, "orchid", "token-1")
        accounts.setLibraryOnly(lb, true)
        accounts.setPaused(true)

        assertEquals(ScrobbleSetup(paused = true, accounts = listOf(ScrobbleAccount(lb, "orchid", libraryOnly = true))), accounts.setup.first())
    }

    @Test
    fun `disconnecting forgets the user and the token`() = runTest {
        val accounts = accounts()
        accounts.connect(lb, "orchid", "token-1")
        accounts.setLibraryOnly(lb, true)
        accounts.disconnect(lb)

        assertEquals(emptyList<ScrobbleAccount>(), accounts.setup.first().accounts)
        assertNull(accounts.secret(lb))
    }
}
