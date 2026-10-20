package app.sitara.music.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Sitara's own geometric icon set, drawn on a 24-unit grid. */
object SitaraIcons {
    private fun icon(name: String, filled: Boolean = true, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            if (filled) {
                path(fill = SolidColor(Color.Black), pathBuilder = block)
            } else {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = block,
                )
            }
        }.build()

    val Play = icon("play") { moveTo(7f, 4.5f); lineTo(19.5f, 12f); lineTo(7f, 19.5f); close() }
    val Pause = icon("pause") {
        moveTo(6f, 4.5f); lineTo(10f, 4.5f); lineTo(10f, 19.5f); lineTo(6f, 19.5f); close()
        moveTo(14f, 4.5f); lineTo(18f, 4.5f); lineTo(18f, 19.5f); lineTo(14f, 19.5f); close()
    }
    val Next = icon("next") {
        moveTo(4f, 5f); lineTo(13f, 12f); lineTo(4f, 19f); close()
        moveTo(12f, 5f); lineTo(21f, 12f); lineTo(12f, 19f); close()
    }
    val Previous = icon("previous") {
        moveTo(20f, 5f); lineTo(11f, 12f); lineTo(20f, 19f); close()
        moveTo(12f, 5f); lineTo(3f, 12f); lineTo(12f, 19f); close()
    }
    val Home = icon("home", filled = false) {
        moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f); lineTo(20f, 20f); lineTo(4f, 20f); close()
        moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f)
    }
    val Search = icon("search", filled = false) {
        moveTo(17f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 4f, 10.5f)
        arcTo(6.5f, 6.5f, 0f, true, true, 17f, 10.5f)
        moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f)
    }
    val Library = icon("library", filled = false) {
        moveTo(5f, 4f); lineTo(5f, 20f)
        moveTo(9.5f, 4f); lineTo(9.5f, 20f)
        moveTo(14f, 5f); lineTo(19.5f, 19f)
    }
    val Shuffle = icon("shuffle", filled = false) {
        moveTo(3f, 7f); lineTo(7f, 7f); curveTo(12f, 7f, 12f, 17f, 17f, 17f); lineTo(21f, 17f)
        moveTo(3f, 17f); lineTo(7f, 17f); curveTo(12f, 17f, 12f, 7f, 17f, 7f); lineTo(21f, 7f)
        moveTo(18.5f, 4.5f); lineTo(21f, 7f); lineTo(18.5f, 9.5f)
        moveTo(18.5f, 14.5f); lineTo(21f, 17f); lineTo(18.5f, 19.5f)
    }
    val Repeat = icon("repeat", filled = false) {
        moveTo(4f, 11f); lineTo(4f, 9f); curveTo(4f, 7.5f, 5f, 6.5f, 6.5f, 6.5f); lineTo(19f, 6.5f)
        moveTo(16.5f, 4f); lineTo(19f, 6.5f); lineTo(16.5f, 9f)
        moveTo(20f, 13f); lineTo(20f, 15f); curveTo(20f, 16.5f, 19f, 17.5f, 17.5f, 17.5f); lineTo(5f, 17.5f)
        moveTo(7.5f, 15f); lineTo(5f, 17.5f); lineTo(7.5f, 20f)
    }
    val Lyrics = icon("lyrics", filled = false) {
        moveTo(4f, 6f); lineTo(20f, 6f)
        moveTo(4f, 11f); lineTo(16f, 11f)
        moveTo(4f, 16f); lineTo(12f, 16f)
        moveTo(18f, 14f); lineTo(18f, 19f)
    }
    val Queue = icon("queue", filled = false) {
        moveTo(4f, 6f); lineTo(14f, 6f)
        moveTo(4f, 11f); lineTo(14f, 11f)
        moveTo(4f, 16f); lineTo(10f, 16f)
        moveTo(16f, 12f); lineTo(21f, 15.5f); lineTo(16f, 19f); close()
    }
    val Settings = icon("settings", filled = false) {
        moveTo(15f, 12f)
        arcTo(3f, 3f, 0f, true, true, 9f, 12f)
        arcTo(3f, 3f, 0f, true, true, 15f, 12f)
        moveTo(12f, 3f); lineTo(12f, 5.5f)
        moveTo(12f, 18.5f); lineTo(12f, 21f)
        moveTo(3f, 12f); lineTo(5.5f, 12f)
        moveTo(18.5f, 12f); lineTo(21f, 12f)
        moveTo(5.6f, 5.6f); lineTo(7.4f, 7.4f)
        moveTo(16.6f, 16.6f); lineTo(18.4f, 18.4f)
        moveTo(5.6f, 18.4f); lineTo(7.4f, 16.6f)
        moveTo(16.6f, 7.4f); lineTo(18.4f, 5.6f)
    }
    val ChevronDown = icon("chevron_down", filled = false) { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) }
    val Back = icon("back", filled = false) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
    val Close = icon("close", filled = false) {
        moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
    }
    val List = icon("list", filled = false) {
        for (y in listOf(6f, 12f, 18f)) {
            moveTo(4f, y); lineTo(5f, y); moveTo(10f, y); lineTo(20f, y)
        }
    }
    val Grid = icon("grid", filled = false) {
        for (x in listOf(4f, 14f)) {
            for (y in listOf(4f, 14f)) {
                moveTo(x, y); lineTo(x + 6f, y); lineTo(x + 6f, y + 6f); lineTo(x, y + 6f); close()
            }
        }
    }
    private val heart: PathBuilder.() -> Unit = {
        moveTo(12f, 20f); curveTo(8f, 17f, 3f, 13f, 3f, 8f)
        curveTo(3f, 3f, 9f, 2f, 12f, 7f); curveTo(15f, 2f, 21f, 3f, 21f, 8f)
        curveTo(21f, 13f, 16f, 17f, 12f, 20f); close()
    }
    val Heart = icon("heart", filled = false, block = heart)
    val HeartFilled = icon("heart_filled", block = heart)
    val More = icon("more") {
        for (y in listOf(5f, 12f, 19f)) {
            moveTo(12f, y - 1.75f); arcTo(1.75f, 1.75f, 0f, true, true, 12f, y + 1.75f); arcTo(1.75f, 1.75f, 0f, true, true, 12f, y - 1.75f); close()
        }
    }
    val DragHandle = icon("drag_handle", filled = false) {
        for (y in listOf(9f, 15f)) {
            moveTo(5f, y); lineTo(19f, y)
        }
    }
    val Add = icon("add", filled = false) { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    val Minus = icon("minus", filled = false) { moveTo(5f, 12f); lineTo(19f, 12f) }
    val Timer = icon("timer", filled = false) {
        moveTo(19f, 13f)
        arcTo(7f, 7f, 0f, true, true, 5f, 13f)
        arcTo(7f, 7f, 0f, true, true, 19f, 13f)
        moveTo(12f, 13f); lineTo(12f, 9f)
        moveTo(10f, 3f); lineTo(14f, 3f)
    }
    val Retry = icon("retry", filled = false) {
        // Clockwise loop, open at the top right, with its head at twelve o'clock.
        moveTo(12f, 5f)
        arcTo(7f, 7f, 0f, true, false, 16.95f, 7.05f)
        moveTo(9.5f, 2.5f); lineTo(12f, 5f); lineTo(9.5f, 7.5f)
    }
    val Download = icon("download", filled = false) {
        moveTo(12f, 4f); lineTo(12f, 15f)
        moveTo(7f, 10.5f); lineTo(12f, 15.5f); lineTo(17f, 10.5f)
        moveTo(5f, 20f); lineTo(19f, 20f)
    }
    val Downloaded = icon("downloaded", filled = false) {
        moveTo(20f, 12f)
        arcTo(8f, 8f, 0f, true, true, 4f, 12f)
        arcTo(8f, 8f, 0f, true, true, 20f, 12f)
        moveTo(8.5f, 12.5f); lineTo(11f, 15f); lineTo(15.5f, 9.5f)
    }
    val Delete = icon("delete", filled = false) {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(10f, 4f); lineTo(14f, 4f)
        moveTo(6.5f, 7f); lineTo(7.5f, 20f); lineTo(16.5f, 20f); lineTo(17.5f, 7f)
    }
    val Star = icon("star") {
        moveTo(12f, 2f); curveTo(12.8f, 8f, 16f, 11.2f, 22f, 12f)
        curveTo(16f, 12.8f, 12.8f, 16f, 12f, 22f)
        curveTo(11.2f, 16f, 8f, 12.8f, 2f, 12f)
        curveTo(8f, 11.2f, 11.2f, 8f, 12f, 2f); close()
    }
}
