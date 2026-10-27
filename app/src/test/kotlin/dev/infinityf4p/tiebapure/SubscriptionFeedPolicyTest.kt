package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SubscriptionFeedPolicyTest {
    private val now = 2_000_000_000_000L
    private fun thread(id: Long, created: Long? = now / 1000, replied: Long? = null, replies: Int = 1) =
        ThreadSummary(id, title = "$id", author = UserSummary(1, "a", "a", ""), replyCount = replies,
            viewCount = 0, blocks = emptyList(), createdAtEpochSeconds = created, lastReplyAtEpochSeconds = replied)
    @Test fun windowsAreInclusiveAndMissingTimesAreExcluded() {
        FeedWindow.entries.filter { it.days != null }.forEach { window ->
            val prefs = SubscriptionFeedPreferences(window = window)
            val boundary = now / 1000 - window.days!! * 86400L
            assertTrue(prefs.matches(thread(1, boundary), now))
            assertFalse(prefs.matches(thread(1, boundary - 1), now))
            assertFalse(prefs.matches(thread(1, null), now))
        }
        assertTrue(SubscriptionFeedPreferences(window = FeedWindow.All).matches(thread(1, null), now))
    }
    @Test fun replyOrderIncludesRecentlyActiveOldThreads() {
        val old = thread(1, now / 1000 - 60 * 86400L, now / 1000)
        assertFalse(SubscriptionFeedPreferences().matches(old, now))
        assertTrue(SubscriptionFeedPreferences(sort = FeedSort.Replied).matches(old, now))
        assertFalse(SubscriptionFeedPreferences(sort = FeedSort.Replied).matches(thread(2), now))
    }
    @Test fun rankingUsesRealCountersAndStableIds() {
        val items = listOf(thread(1, replies = 2), thread(2, replies = 20), thread(3, replies = 20))
        assertEquals(listOf(3L, 2L, 1L), SubscriptionFeedPreferences(sort = FeedSort.Replies).sorted(items).map { it.id })
    }
    @Test fun sourceSelectionDistinguishesAllEmptyAndTypes() {
        val forum = FeedSubscription("a", 1, "a", "a", null, 0)
        val user = forum.copy(isUser = true)
        assertTrue(SubscriptionFeedPreferences().includes(user))
        assertFalse(SubscriptionFeedPreferences(sourceKeys = emptySet()).includes(forum))
        assertFalse(SubscriptionFeedPreferences(kind = FeedSourceKind.User, sourceKeys = setOf(forum.key)).includes(user))
        assertTrue(SubscriptionFeedPreferences(sourceKeys = setOf(user.key)).includes(user))
    }
    @Test fun cacheRoundTripsMetadataAndReadsLegacyWithoutChangingOfflineCodec() {
        val item = FeedCachedItem(thread(1), now, FeedOrder.Reply)
        assertEquals(listOf(item), SubscriptionCacheCodec.decode(SubscriptionCacheCodec.encode(listOf(item)), 0))
        val legacy = SubscriptionCacheCodec.decode(SavedThreadBlobCodec.encodeSummaries(listOf(item.thread)), now)
        assertEquals(FeedOrder.Publish, legacy.single().order)
        assertEquals(now, legacy.single().fetchedAt)
    }
    @Test fun sourceCacheCapsFiftyAndExpiresByFetchTime() {
        val items = (1L..70L).map { FeedCachedItem(thread(it), now - it, FeedOrder.Publish) } +
            FeedCachedItem(thread(100), now - FEED_EXPIRY_MS - 1, FeedOrder.Publish)
        val kept = retainFeedItems(items, now)
        assertEquals(50, kept.size)
        assertEquals(1L, kept.first().thread.id)
        assertFalse(kept.any { it.thread.id == 100L })
    }
    @Test fun globalBudgetCountsSourceDuplicatesAndEncodedBytes() {
        val a = FeedCachedItem(thread(1), now, FeedOrder.Publish)
        val b = FeedCachedItem(thread(2), now - 1, FeedOrder.Publish)
        val rows = mapOf("a" to listOf(a,b), "b" to listOf(a))
        val count = retainFeedCacheBudget(rows, maxCount = 2)
        assertEquals(2, count.values.sumOf { it.size })
        assertTrue(count.values.flatten().all { it.thread.id == 1L })
        val bytes = SubscriptionCacheCodec.encode(listOf(a)).size
        val budget = retainFeedCacheBudget(rows, maxBytes = bytes)
        assertEquals(1, budget.values.sumOf { it.size })
        assertTrue(budget.values.sumOf { SubscriptionCacheCodec.encode(it).size } <= bytes)
    }
    @Test fun corruptedCacheDoesNotDecode() {
        val bytes = SubscriptionCacheCodec.encode(listOf(FeedCachedItem(thread(1), now, FeedOrder.Publish)))
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        assertTrue(runCatching { SubscriptionCacheCodec.decode(bytes, now) }.isFailure)
    }
}
