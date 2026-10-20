package app.sitara.music.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Asks for a playlist name; confirming is only offered once the name has something in it. */
@Composable
fun PlaylistNameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(MAX_NAME_LENGTH) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton({ onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun DeletePlaylistDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “$name”?") },
        text = { Text("The playlist goes; its songs stay wherever else you saved them.") },
        confirmButton = { TextButton(onConfirm) { Text("Delete") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

internal const val MAX_NAME_LENGTH = 100
