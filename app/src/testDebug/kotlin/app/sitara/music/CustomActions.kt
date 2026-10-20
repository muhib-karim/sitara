package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString

/** Runs the node's accessibility action named [label], as TalkBack would. */
internal fun SemanticsNodeInteraction.performCustomAction(label: String) {
    fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
}

/**
 * Sets a text field's text without focusing it. A focused field's blinking cursor keeps Robolectric's frame clock
 * busy until the idling timeout, and every frame leaks native text layouts until the test JVM runs out of memory.
 */
internal fun SemanticsNodeInteraction.setTextUnfocused(text: String) {
    performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(text)) }
}
