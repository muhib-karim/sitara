package app.sitara.music.core.data.scrobble

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.sitara.music.core.data.library.SitaraDatabase
import app.sitara.music.core.model.Scrobble
import app.sitara.music.core.model.ScrobbleService
import app.sitara.music.core.model.Track
import app.sitara.music.source.api.ScrobbleClient
import app.sitara.music.source.api.SendOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plays waiting to be scrobbled: each kept once per service, sent in batches of 50, and kept until a service took them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrobbleOutboxTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), SitaraDatabase::class.java)
        .allowMainThreadQueries().build()
    private var now = 1_800_000_000_000L
    private val outbox = ScrobbleOutbox(db.scrobbles()) { now }

    /** Answers each submit with the next outcome (then SENT) and keeps every batch it was given. */
    private class FakeClient(vararg outcomes: SendOutcome) : ScrobbleClient {
        val batches = mutableListOf<List<Scrobble>>()
        private val outcomes = ArrayDeque(outcomes.toList())

        override suspend fun nowPlaying(track: Track) = SendOutcome.SENT

        override suspend fun submit(scrobbles: List<Scrobble>): SendOutcome {
            batches += scrobbles
            return outcomes.removeFirstOrNull() ?: SendOutcome.SENT
        }
    }

    private fun play(n: Int, startedAtMs: Long = 1_000_000L + n * 300_000L) = Scrobble("t$n", "Song $n", "Artist", "Album", 200_000, startedAtMs)

    private val lb = ScrobbleService.LISTENBRAINZ
    private val lastFm = ScrobbleService.LASTFM

    @After
    fun close() = db.close()

    @Test
    fun `the same play reported again is kept once`() = runTest {
        outbox.add(play(1), setOf(lb))
        outbox.add(play(1), setOf(lb))
        assertEquals(1, outbox.pendingCount(lb).first())
    }

    @Test
    fun `a song started over is a second play`() = runTest {
        outbox.add(play(1, startedAtMs = 1_000), setOf(lb))
        outbox.add(play(1, startedAtMs = 201_000), setOf(lb))
        assertEquals(2, outbox.pendingCount(lb).first())
    }

    @Test
    fun `each service keeps its own copy`() = runTest {
        outbox.add(play(1), setOf(lb, lastFm))
        val client = FakeClient()
        outbox.send(lb, client)
        assertEquals(0, outbox.pendingCount(lb).first())
        assertEquals("Last.fm has not been sent it", 1, outbox.pendingCount(lastFm).first())
    }

    @Test
    fun `plays go out oldest first in batches of 50`() = runTest {
        (1..120).forEach { outbox.add(play(it), setOf(lb)) }
        val client = FakeClient()

        assertEquals(SendOutcome.SENT, outbox.send(lb, client))

        assertEquals(listOf(50, 50, 20), client.batches.map { it.size })
        assertEquals((1..120).map { "t$it" }, client.batches.flatten().map { it.trackId })
        assertEquals(0, outbox.pendingCount(lb).first())
        assertEquals(now, outbox.lastSentAt(lb).first())
    }

    @Test
    fun `a play sent is never sent again, even when its play reports more time heard`() = runTest {
        outbox.add(play(1), setOf(lb))
        val client = FakeClient()
        outbox.send(lb, client)

        outbox.add(play(1), setOf(lb))
        outbox.send(lb, client)

        assertEquals(1, client.batches.size)
    }

    @Test
    fun `a service that cannot take plays now keeps them for the next try`() = runTest {
        (1..60).forEach { outbox.add(play(it), setOf(lb)) }
        val client = FakeClient(SendOutcome.SENT, SendOutcome.TRY_LATER)

        assertEquals(SendOutcome.TRY_LATER, outbox.send(lb, client))
        assertEquals("the first batch went", 10, outbox.pendingCount(lb).first())

        assertEquals(SendOutcome.SENT, outbox.send(lb, client))
        assertEquals((51..60).map { "t$it" }, client.batches.last().map { it.trackId })
    }

    @Test
    fun `a session that ended keeps every play for after signing in again`() = runTest {
        outbox.add(play(1), setOf(lastFm))
        assertEquals(SendOutcome.SIGN_IN_AGAIN, outbox.send(lastFm, FakeClient(SendOutcome.SIGN_IN_AGAIN)))
        assertEquals(1, outbox.pendingCount(lastFm).first())
        assertNull(outbox.lastSentAt(lastFm).first())
    }

    @Test
    fun `a refused batch is tried one play at a time, so one bad play does not lose the rest`() = runTest {
        (1..3).forEach { outbox.add(play(it), setOf(lb)) }
        // The batch is refused, then t1 goes, t2 is refused on its own, t3 goes.
        val client = FakeClient(SendOutcome.REFUSED, SendOutcome.SENT, SendOutcome.REFUSED, SendOutcome.SENT)

        assertEquals(SendOutcome.SENT, outbox.send(lb, client))

        assertEquals(listOf(3, 1, 1, 1), client.batches.map { it.size })
        assertEquals(0, outbox.pendingCount(lb).first())
    }

    @Test
    fun `plays sent a month ago are let go as new ones arrive`() = runTest {
        outbox.add(play(1, startedAtMs = now), setOf(lb))
        outbox.send(lb, FakeClient())
        now += 31L * 24 * 60 * 60 * 1_000

        outbox.add(play(2, startedAtMs = now), setOf(lb))

        val rows = db.query("SELECT track_id FROM scrobble_outbox", null).use { c -> List(c.count) { c.moveToNext(); c.getString(0) } }
        assertEquals(listOf("t2"), rows)
    }

    @Test
    fun `disconnecting a service forgets its waiting plays only`() = runTest {
        outbox.add(play(1), setOf(lb, lastFm))
        outbox.forget(lb)
        assertEquals(0, outbox.pendingCount(lb).first())
        assertEquals(1, outbox.pendingCount(lastFm).first())
    }
}
