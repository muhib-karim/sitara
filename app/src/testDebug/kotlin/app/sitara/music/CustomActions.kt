package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString

/** Runs the node's accessibility action named [label], as TalkBack would. */
internal fun SemanticsNodeInteraction.performCustomAction(label: String) {
    fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
}

/** Sets a text field's text through its SetText action, as accessibility services do, without focusing it. */
internal fun SemanticsNodeInteraction.setTextUnfocused(text: String) {
    performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
}
