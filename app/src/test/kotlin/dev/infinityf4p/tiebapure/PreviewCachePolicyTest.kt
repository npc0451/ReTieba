package dev.infinityf4p.tiebapure

import org.junit.Assert.*
import org.junit.Test

class PreviewCachePolicyTest {
    @Test fun byteBudgetRetainsRecentFramesWithoutClearingNavigationState() {
        val mib = 1_048_576L
        assertEquals(setOf("second", "third"), retainedPreviewKeys(listOf("first" to 5 * mib, "second" to 5 * mib, "third" to 5 * mib)))
        assertEquals(setOf("b", "c", "d", "e"), retainedPreviewKeys(listOf("a", "b", "c", "d", "e").map { it to mib }))
        assertEquals(setOf("valid"), retainedPreviewKeys(listOf("large" to PARENT_PREVIEW_BYTES + 1, "valid" to mib)))
    }

}
