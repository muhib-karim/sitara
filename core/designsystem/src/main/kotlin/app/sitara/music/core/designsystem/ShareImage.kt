package app.sitara.music.core.designsystem

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Offers [image] to the share sheet titled [title] as a PNG named [fileName]: written to the cache, so no storage
 * permission is needed, and handed out through the app's FileProvider ("<package>.files", declared in the app's manifest)
 * with read access for the chosen app only.
 */
suspend fun shareImage(context: Context, image: Bitmap, fileName: String, title: String) {
    val file = File(context.cacheDir, "shared/$fileName")
    withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The clip lets the share sheet show the image in its preview.
    send.clipData = ClipData.newRawUri(null, uri)
    context.startActivity(Intent.createChooser(send, title))
}
