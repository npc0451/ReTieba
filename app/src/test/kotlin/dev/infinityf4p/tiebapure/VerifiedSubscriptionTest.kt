package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.data.ForumRepository
import dev.infinityf4p.tiebapure.core.model.Account
import dev.infinityf4p.tiebapure.core.model.Forum
import dev.infinityf4p.tiebapure.core.model.ForumInfo
import dev.infinityf4p.tiebapure.core.model.ForumPage
import dev.infinityf4p.tiebapure.core.model.ForumThreadCategory
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VerifiedSubscriptionTest {
    private fun repository(resolve: suspend (String) -> Long) = object : ForumRepository {
        override suspend fun threads(forumName: String, page: Int, category: ForumThreadCategory, account: Account?): ForumPage = error("unused")
        override suspend fun info(forumName: String) = ForumInfo(resolve(forumName), 0, 0, 0, "")
    }

    @Test fun verifiesNormalizedNameEvenWhenCallerSuppliesAnId() = runTest {
        val forum = verifiedSubscriptionForum(Forum(999, " 安卓吧 ", "安卓吧"), repository {
            assertEquals("安卓", it); 123
        })
        assertEquals(123L, forum.id)
        assertEquals("安卓", forum.name)
    }

    @Test fun nonexistentForumIsRejected() = runTest {
        try {
            verifiedSubscriptionForum(Forum(0, "不存在", ""), repository { 0 })
            fail("Invalid forum was accepted")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("未找到"))
        }
    }

    @Test fun networkFailureDoesNotClaimForumDoesNotExist() = runTest {
        try {
            verifiedSubscriptionForum(Forum(0, "安卓", ""), repository { throw IOException("断网") })
            fail("Unverified forum was accepted")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("暂时无法确认"))
            assertFalse(error.message!!.contains("未找到"))
        }
    }

    @Test fun cancellationIsNotTurnedIntoValidationFailure() = runTest {
        try {
            verifiedSubscriptionForum(Forum(0, "安卓", ""), repository { throw CancellationException() })
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }
}
