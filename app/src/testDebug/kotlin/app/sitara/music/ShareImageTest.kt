package app.sitara.music

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.IntentCompat
import app.sitara.music.core.designsystem.shareImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A card (stats, lyrics) leaves the app as a PNG in the cache, offered through the share sheet with read access only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareImageTest {
    @Test fun anImageIsSharedFromTheCacheThroughTheAppsFileProvider() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val image = Bitmap.createBitmap(90, 160, Bitmap.Config.ARGB_8888)

        runBlocking { shareImage(activity, image, "sitara-lyrics.png", "Share lyrics") }

        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share lyrics", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE))
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("image/png", send.type)
        assertTrue("the receiving app may read it", send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${activity.packageName}.files", uri.authority)
        val file = File(activity.cacheDir, "shared/sitara-lyrics.png")
        assertTrue("written to the cache, no storage permission needed", file.length() > 0)
    }
}
