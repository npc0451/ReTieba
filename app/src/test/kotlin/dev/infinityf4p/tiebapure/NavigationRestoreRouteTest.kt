package dev.infinityf4p.tiebapure

import org.junit.Assert.*
import org.junit.Test

class NavigationRestoreRouteTest {
    @Test fun searchPreservesTypedBooleanForBothEntryModes() {
        listOf(false, true).forEach { forumFirst ->
            assertEquals("search?query=%E5%8F%98&forumFirst=$forumFirst",
                navigationRestoreRoute("search?query={query}&forumFirst={forumFirst}", mapOf("query" to "变", "forumFirst" to forumFirst)))
        }
    }
    @Test fun nullableQueriesAreOmittedAndSpecialCharactersAreEncodedOnce() {
        assertEquals("thread/42?postId=9", navigationRestoreRoute("thread/{threadId}?postId={postId}&initialDestination={initialDestination}",
            mapOf("threadId" to "42", "postId" to "9")))
        assertEquals("forum/a%2Fb%20%26%3F%25", navigationRestoreRoute("forum/{name}", mapOf("name" to "a/b &?%")))
        assertEquals("settings/storage", navigationRestoreRoute("settings/storage", emptyMap()))
        assertNull(navigationRestoreRoute("thread/{id}", emptyMap()))
    }
}
