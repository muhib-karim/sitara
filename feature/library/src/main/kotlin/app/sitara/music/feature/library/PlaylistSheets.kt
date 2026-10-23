package app.sitara.music.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.PillButton
import app.sitara.music.core.designsystem.SitaraSheet
import app.sitara.music.core.designsystem.SitaraSpacing

/**
 * Asks for a playlist name; the confirm button appears once the name has something in it. Confirming, from the button,
 * the keyboard's Done key or a hardware Enter, closes the keyboard itself, so none of it stays open over the dock.
 */
@Composable
fun PlaylistNameSheet(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    SitaraSheet(onDismiss) { close ->
        val confirm = {
            keyboard?.hide()
            focus.clearFocus()
            if (name.isNotBlank()) close { onConfirm(name.trim()) }
        }
        SheetForm(title) {
            OutlinedTextField(
                name, { name = it.take(MAX_NAME_LENGTH) },
                Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                    val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (enter && event.type == KeyEventType.KeyUp) confirm()
                    enter
                },
                singleLine = true, label = { Text(stringResource(R.string.library_playlist_name)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (name.isNotBlank()) PillButton(confirmLabel, confirm)
                PillButton(stringResource(R.string.library_cancel), { close {} }, primary = false)
            }
        }
    }
}

@Composable
internal fun DeletePlaylistSheet(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SitaraSheet(onDismiss) { close ->
        SheetForm(stringResource(R.string.library_delete_playlist_title, name)) {
            Text(stringResource(R.string.library_delete_playlist_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(stringResource(R.string.library_delete_playlist), { close(onConfirm) })
                PillButton(stringResource(R.string.library_cancel), { close {} }, primary = false)
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
