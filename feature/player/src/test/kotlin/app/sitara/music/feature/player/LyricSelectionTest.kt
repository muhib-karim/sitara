package app.sitara.music.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lines picked for a share card stay one run of one to five lines; a tap moves its nearer end. */
class LyricSelectionTest {
    @Test fun aTapBelowReachesDownToTheTappedLine() = assertEquals(3..6, extendSelection(3..4, 6))

    @Test fun aTapAboveReachesUpToTheTappedLine() = assertEquals(1..4, extendSelection(3..4, 1))

    @Test fun aFarTapKeepsFiveLinesEndingAtTheTappedOne() {
        assertEquals(6..10, extendSelection(3..4, 10))
        assertEquals(1..5, extendSelection(8..9, 1))
    }

    @Test fun aTapOnAnEndLetsThatLineGo() {
        assertEquals(4..6, extendSelection(3..6, 3))
        assertEquals(3..5, extendSelection(3..6, 6))
    }

    @Test fun aTapInsideEndsTheRunThere() = assertEquals(3..4, extendSelection(3..6, 4))

    @Test fun theLastLineLeftStaysPicked() = assertEquals(5..5, extendSelection(5..5, 5))
}
