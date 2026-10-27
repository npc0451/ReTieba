package dev.infinityf4p.tiebapure.core.designsystem

import dev.infinityf4p.tiebapure.core.model.ThreadSummary
import dev.infinityf4p.tiebapure.core.model.UserSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadIdentityTest {
    private val thread = ThreadSummary(id = 1, title = "测试", author = UserSummary(2, "author", "author", "author-avatar"), replyCount = 0, viewCount = 0, blocks = emptyList())
    @Test fun communityIdentityUsesOnlyCommunityAvatar() {
        assertEquals("forum-avatar", threadIdentityAvatarUrl(thread.copy(forumName = "测试", forumAvatarUrl = "forum-avatar")))
        assertNull(threadIdentityAvatarUrl(thread.copy(forumName = "测试")))
        assertEquals("author-avatar", threadIdentityAvatarUrl(thread.copy(forumName = "  ")))
    }
}
