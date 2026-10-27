package dev.infinityf4p.tiebapure.feature.thread

import dev.infinityf4p.tiebapure.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentSearchTest {
    private val author = UserSummary(1, "作者", "作者", "")
    private val parent = Post(2u, 1, 2, author, null, 1, listOf(ContentBlock.Text("桥下有风")), 1, 2, previewSubposts = emptyList())
    private val child = Subpost(3u, 2, author, null, listOf(ContentBlock.Text("<img> Riverside")), 1, 1)
    @Test fun searchesLoadedParentAndChildTextAndDeduplicatesStableIds() {
        val a = CommentSearchEntry(parent)
        val b = CommentSearchEntry(parent, child)
        assertEquals(listOf(a), matchLoadedComments(listOf(a,a,b), "桥下"))
        assertEquals(listOf(b), matchLoadedComments(listOf(a,b), " riverside "))
        assertEquals(listOf(b), matchLoadedComments(listOf(b), "<img>"))
        assertTrue(matchLoadedComments(listOf(a,b), " ").isEmpty())
    }
}
