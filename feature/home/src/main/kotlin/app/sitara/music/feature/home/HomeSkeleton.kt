package app.sitara.music.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import app.sitara.music.core.designsystem.PlaceholderBlock
import app.sitara.music.core.designsystem.SitaraRadius
import app.sitara.music.core.designsystem.SitaraSpacing
import app.sitara.music.core.designsystem.rememberPlaceholderPulse

// Home's placeholders, in its own layout. Only the first group speaks to TalkBack, once, as "Loading Home".

@Composable
internal fun ChipsSkeleton() {
    val pulse = rememberPlaceholderPulse()
    Row(
        Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(76.dp, 92.dp, 84.dp, 100.dp).forEach { width -> PlaceholderBlock(pulse, Modifier.size(width, SitaraSpacing.TouchTarget), CircleShape) }
    }
}

/** Quick picks while they load: a heading and four song rows, announced as [label]. */
@Composable
internal fun RowsSkeleton(modifier: Modifier = Modifier, label: String = stringResource(R.string.home_loading)) {
    val pulse = rememberPlaceholderPulse()
    Column(modifier.clearAndSetSemantics { contentDescription = label }) {
        HeadingBone(pulse)
        Column(Modifier.padding(horizontal = SitaraSpacing.Gutter)) {
            repeat(4) {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlaceholderBlock(pulse, Modifier.size(48.dp), RoundedCornerShape(8.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.6f).height(14.dp))
                        PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.35f).height(12.dp))
                    }
                }
            }
        }
    }
}

/** A carousel while it loads: a heading and the first covers with two lines under each. */
@Composable
internal fun CarouselSkeleton() {
    val pulse = rememberPlaceholderPulse()
    Column(Modifier.padding(top = 12.dp).clearAndSetSemantics {}) {
        HeadingBone(pulse)
        // Unbounded, so the third cover keeps its size and runs off the edge like a real carousel instead of shrinking.
        Row(
            Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start, unbounded = true).padding(horizontal = SitaraSpacing.Gutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            repeat(3) {
                Column(Modifier.width(CardWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaceholderBlock(pulse, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(SitaraRadius.Tile))
                    PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.85f).height(14.dp))
                    PlaceholderBlock(pulse, Modifier.fillMaxWidth(0.5f).height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun HeadingBone(pulse: State<Float>) {
    PlaceholderBlock(pulse, Modifier.padding(horizontal = SitaraSpacing.Gutter, vertical = 12.dp).size(140.dp, 20.dp))
}
