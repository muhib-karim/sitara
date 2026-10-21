package app.sitara.music.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    @Test
    fun `a drag lands on the nearest whole row inside the list`() {
        assertEquals(2, reorderTarget(from = 2, offset = 20f, rowHeight = 64f, count = 5))
        assertEquals(3, reorderTarget(from = 2, offset = 40f, rowHeight = 64f, count = 5))
        assertEquals(0, reorderTarget(from = 2, offset = -100f, rowHeight = 64f, count = 5))
        assertEquals(4, reorderTarget(from = 2, offset = 900f, rowHeight = 64f, count = 5))
    }

    @Test
    fun `an unmeasured row stays where it is`() {
        assertEquals(2, reorderTarget(from = 2, offset = 300f, rowHeight = 0f, count = 5))
    }

    @Test
    fun `a drag ticks once for each row it passes, and not past the ends`() {
        var ticks = 0
        val state = ReorderState(onStep = { ticks++ }).apply { rowHeight = 64f }
        state.start(index = 1, count = 4)
        state.drag(20f)
        assertEquals("inside its own row", 0, ticks)
        state.drag(30f)
        assertEquals("over the next row", 1, ticks)
        state.drag(64f)
        assertEquals(2, ticks)
        state.drag(500f)
        assertEquals("the last row is as far as it goes", 2, ticks)
        state.drag(-550f)
        assertEquals("back over the row after its own", 3, ticks)
        assertEquals(1 to 2, state.finish())
    }

    @Test
    fun `rows between the start and the target step aside toward the start`() {
        assertEquals(listOf(0, -1, -1, 0, 0), (0..4).map { reorderShift(it, from = 0, target = 2) })
        assertEquals(listOf(0, 1, 1, 0, 0), (0..4).map { reorderShift(it, from = 3, target = 1) })
    }
}
