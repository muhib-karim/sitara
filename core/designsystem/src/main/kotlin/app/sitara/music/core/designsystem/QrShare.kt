package app.sitara.music.core.designsystem

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sitara.music.core.common.QrCode
import app.sitara.music.core.model.ShareTarget

/**
 * A QR code for [text], dark on white with the four-module quiet zone scanners need, whatever the theme: a camera
 * pointed at the screen opens the link.
 */
@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val code = remember(text) { QrCode.encode(text) }
    val description = stringResource(R.string.ds_qr_code_for, text)
    Canvas(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(SitaraRadius.Cover)).background(Color.White).testTag("qr-code")
            .semantics { contentDescription = description },
    ) {
        val cell = size.minDimension / (code.size + 2 * QUIET_ZONE)
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (code[x, y]) drawRect(Color.Black, Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** Share: the link as text, its QR code to scan from the screen, or the code as an image. */
@Composable
fun ShareSheetContent(target: ShareTarget, onSendLink: () -> Unit, onShareImage: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(target.artwork, Modifier.size(48.dp), cornerRadius = SitaraRadius.Cover)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(target.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (target.subtitle.isNotBlank()) {
                    Text(target.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        QrCodeImage(target.link, Modifier.padding(vertical = 16.dp).widthIn(max = 280.dp).fillMaxWidth())
        SelectionContainer {
            Text(target.link, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(stringResource(R.string.ds_send_link), onSendLink)
            PillButton(stringResource(R.string.ds_share_qr_image), onShareImage, primary = false)
        }
    }
}

/** Offers [text] to the share sheet titled [title]. */
fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, title))
}

/** A 1080 × 1280 card with [target]'s QR code and name, offered to the share sheet as a PNG. */
suspend fun shareQrCard(context: Context, target: ShareTarget) {
    shareImage(context, qrCard(target), "sitara-qr.png", context.getString(R.string.ds_share_qr_code))
}

/** The shared card: white, the code with its quiet zone, then the title and subtitle under it. */
fun qrCard(target: ShareTarget, width: Int = 1080, height: Int = 1280): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    val code = QrCode.encode(target.link)
    val side = width * 0.8f
    val cell = side / (code.size + 2 * QUIET_ZONE)
    val left = (width - side) / 2
    val top = width * 0.08f
    val dark = Paint().apply { color = AndroidColor.BLACK }
    for (y in 0 until code.size) {
        for (x in 0 until code.size) {
            if (code[x, y]) {
                val l = left + (x + QUIET_ZONE) * cell
                val t = top + (y + QUIET_ZONE) * cell
                canvas.drawRect(l, t, l + cell + 0.5f, t + cell + 0.5f, dark)
            }
        }
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        textSize = width * 0.05f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val subtitlePaint = Paint(titlePaint).apply {
        color = AndroidColor.DKGRAY
        textSize = width * 0.038f
        typeface = Typeface.DEFAULT
    }
    val textWidth = width * 0.86f
    val baseline = top + side + width * 0.08f
    canvas.drawText(TextUtils.ellipsize(target.title, android.text.TextPaint(titlePaint), textWidth, TextUtils.TruncateAt.END).toString(), width / 2f, baseline, titlePaint)
    if (target.subtitle.isNotBlank()) {
        val line = TextUtils.ellipsize(target.subtitle, android.text.TextPaint(subtitlePaint), textWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(line, width / 2f, baseline + width * 0.06f, subtitlePaint)
    }
    return bitmap
}

private const val QUIET_ZONE = 4
