package app.sitara.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraSpacing

/** Asks for a playlist name; the confirm button appears once the name has something in it. */
@Composable
fun PlaylistNameSheet(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    SitaraSheet(onDismiss) { close ->
        SheetForm(title) {
            OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name.isNotBlank()) PillButton(confirmLabel, { close { onConfirm(name.trim()) } })
                PillButton("Cancel", { close {} }, primary = false)
            }
        }
    }
}

@Composable
internal fun DeletePlaylistSheet(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        SheetForm("Delete “$name”?") {
            Text("The playlist goes; its songs stay wherever else you saved them.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Delete playlist", { close(onConfirm) })
                PillButton("Cancel", { close {} }, primary = false)
            }
        }
    }
}

@Composable
private fun SheetForm(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        content()
    }
}

internal const val MAX_NAME_LENGTH = 100
