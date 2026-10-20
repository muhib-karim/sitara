package app.sitara.music.core.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/** A modal sheet whose [content] gets a `close` that runs an action, then slides the sheet away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitaraSheet(onDismiss: () -> Unit, content: @Composable (close: (action: () -> Unit) -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        content { action ->
            action()
            scope.launch { state.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
}
