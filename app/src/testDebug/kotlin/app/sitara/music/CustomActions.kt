package app.sitara.music

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction

/** Runs the node's accessibility action named [label], as TalkBack would. */
internal fun SemanticsNodeInteraction.performCustomAction(label: String) {
    fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
}
