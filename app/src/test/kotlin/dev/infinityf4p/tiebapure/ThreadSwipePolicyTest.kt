package dev.infinityf4p.tiebapure

import org.junit.Assert.*
import org.junit.Test

class ThreadSwipePolicyTest {
    @Test fun velocityCanFinishACommittedDirectionButNeverATinyAccidentalDrag() {
        assertTrue(swipeCommits(80f, 400f, 72f, 900f))
        assertTrue(swipeCommits(-80f, 400f, 72f, -900f))
        assertFalse(swipeCommits(20f, 400f, 72f, 2000f))
        assertFalse(swipeCommits(80f, 400f, 72f, -900f))
    }
    @Test fun requiresMeaningfulTravelInEitherDirection() {
        assertFalse(swipeCommits(30f, 400f, 72f))
        assertFalse(swipeCommits(-127f, 400f, 72f))
        assertTrue(swipeCommits(128f, 400f, 72f))
        assertTrue(swipeCommits(-150f, 400f, 72f))
        assertFalse(swipeCommits(100f, 0f, 72f))
        assertFalse(swipeCommits(65f, 180f, 72f))
    }
}
