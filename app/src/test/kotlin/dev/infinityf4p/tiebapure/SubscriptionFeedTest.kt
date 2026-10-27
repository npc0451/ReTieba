package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.model.*

import androidx.lifecycle.ViewModelStore
import dev.infinityf4p.tiebapure.core.model.ForumPage
import dev.infinityf4p.tiebapure.core.model.ThreadSummary
import dev.infinityf4p.tiebapure.core.model.UserSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SubscriptionFeedTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun vm(source: Source, now: () -> Long = { 1_000_000 }) = SubscriptionFeedViewModel(source, now).also { store.put("feed", it) }
    private fun entry(id: Int, time: Long = 1) = FeedSubscription("bar$id", id.toLong(), "bar$id", "bar${id}吧", null, time)
    private fun thread(id: Long, time: Long? = id) = ThreadSummary(id = id, title = "$id",
        author = UserSummary(1, "author", "author", ""), replyCount = 0, viewCount = 0,
        createdAtEpochSeconds = time, blocks = emptyList())
    private class Source(entries: List<FeedSubscription>) : SubscriptionFeedSource {
        override val subscriptions = MutableStateFlow(entries)
        override val preferences = MutableStateFlow(dev.infinityf4p.tiebapure.core.model.SubscriptionFeedPreferences(window = dev.infinityf4p.tiebapure.core.model.FeedWindow.All))
        override val cacheRevision = MutableStateFlow(0L)
        var preferenceSaveSucceeds = true
        override suspend fun setPreferences(value: dev.infinityf4p.tiebapure.core.model.SubscriptionFeedPreferences) {
            if (!preferenceSaveSucceeds) throw java.io.IOException("磁盘不可写")
            preferences.value = value
        }
        val caches = mutableMapOf<String, CachedForumFeed>()
        val requests = mutableListOf<Pair<String, Int>>()
        val orderedRequests = mutableListOf<Triple<String, Int, FeedOrder>>()
        var saveSucceeds = true
        override suspend fun load(entry: FeedSubscription, page: Int, order: FeedOrder): SubscriptionPage {
            orderedRequests += Triple(entry.key, page, order)
            return load(entry, page)
        }
        var active = 0
        var maximumActive = 0
        var response: suspend (FeedSubscription, Int) -> SubscriptionPage = { entry, page ->
            SubscriptionPage( emptyList(), page, false)
        }
        override suspend fun cache(entry: FeedSubscription) = caches[entry.normalizedName]
        override suspend fun load(entry: FeedSubscription, page: Int): SubscriptionPage {
            requests += entry.normalizedName to page
            active++; maximumActive = maxOf(maximumActive, active)
            try { return response(entry, page) } finally { active-- }
        }
        override suspend fun save(entry: FeedSubscription, cache: CachedForumFeed): Boolean {
            if (!saveSucceeds || entry !in subscriptions.value) return false
            caches[entry.normalizedName] = cache
            return true
        }
    }

    @Test fun forumAndUserWithSameKeyHaveIndependentPagingAndUnfollow() = runTest(dispatcher) {
        val forum = entry(1)
        val person = forum.copy(isUser = true)
        val source = Source(listOf(forum, person))
        source.response = { entry, page -> SubscriptionPage(listOf(thread(if (entry.isUser) 20 else 10)), page, page == 1) }
        val vm = vm(source)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.subscriptionCount)
        assertEquals(listOf(20L, 10L), vm.uiState.value.threads.map { it.id })
        vm.loadMore(); advanceUntilIdle()
        assertEquals(2, source.requests.count { it.second == 2 })
        source.subscriptions.value = listOf(forum)
        advanceUntilIdle()
        assertEquals(listOf(10L), vm.uiState.value.threads.map { it.id })
    }

    @Test fun hundredSubscriptionsAreBatchedAndConcurrencyIsBounded() = runTest(dispatcher) {
        val source = Source((1..100).map { entry(it) })
        source.response = { entry, page -> delay(10); SubscriptionPage( listOf(thread(entry.forumId)), page, false) }
        val vm = vm(source)
        advanceUntilIdle()
        assertEquals(8, source.requests.size)
        assertEquals(4, source.maximumActive)
        repeat(12) { vm.loadMore(); advanceUntilIdle() }
        assertEquals(100, source.requests.size)
        assertEquals(100, vm.uiState.value.updatedCount)
        assertEquals((100L downTo 1L).toList(), vm.uiState.value.threads.map { it.id })
        assertFalse(vm.uiState.value.hasMore)
    }

    @Test fun cacheIsVisibleBeforeNetworkAndMissingTimesSortLast() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.caches["bar1"] = CachedForumFeed(listOf(thread(1, null), thread(2, 9), thread(3, 9)), 40)
        source.response = { entry, page -> delay(100); SubscriptionPage( listOf(thread(4)), page, false) }
        val vm = vm(source)
        runCurrent()
        assertEquals(listOf(3L, 2L, 1L), vm.uiState.value.threads.map { it.id })
        assertEquals(0, vm.uiState.value.updatedCount)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.pendingCount)
        vm.applyPending()
        assertEquals(listOf(3L, 2L, 4L, 1L), vm.uiState.value.threads.map { it.id })
    }

    @Test fun partialFailureKeepsCacheAndRetryDoesNotAdvanceFailedCursor() = runTest(dispatcher) {
        val source = Source(listOf(entry(1), entry(2)))
        source.caches["bar1"] = CachedForumFeed(listOf(thread(10)), 4)
        var fail = true
        source.response = { entry, page ->
            if (entry.forumId == 1L && fail) throw java.io.IOException("离线")
            SubscriptionPage( listOf(thread(entry.forumId)), page, false)
        }
        val vm = vm(source)
        advanceUntilIdle()
        vm.applyPending()
        assertEquals(listOf(10L, 2L), vm.uiState.value.threads.map { it.id })
        assertEquals(1, vm.uiState.value.updatedCount)
        assertEquals(1, vm.uiState.value.failedForums.size)
        fail = false; vm.retryFailures(); advanceUntilIdle()
        assertEquals(listOf("bar2" to 1, "bar1" to 1, "bar1" to 1), source.requests)
        assertEquals(2, vm.uiState.value.updatedCount)
        assertTrue(vm.uiState.value.failedForums.isEmpty())
    }

    @Test fun pagingDeduplicatesAndPersistsRecentLoadedSummaries() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { entry, page -> SubscriptionPage(
            if (page == 1) listOf(thread(3), thread(2)) else listOf(thread(2), thread(1)), page, page == 1) }
        val vm = vm(source); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(listOf(3L, 2L, 1L), vm.uiState.value.threads.map { it.id })
        assertEquals(setOf(3L, 2L, 1L), source.caches["bar1"]!!.items.map { it.thread.id }.toSet())
        vm.refresh(); advanceUntilIdle()
        assertEquals(1, source.requests.last().second)
    }

    @Test fun timeoutIsLocalAndOtherForumsStillLoad() = runTest(dispatcher) {
        val source = Source(listOf(entry(1), entry(2)))
        source.response = { entry, page ->
            if (entry.forumId == 1L) delay(20_000)
            SubscriptionPage( listOf(thread(entry.forumId)), page, false)
        }
        val vm = vm(source); advanceUntilIdle()
        assertEquals(listOf(2L), vm.uiState.value.threads.map { it.id })
        assertTrue(vm.uiState.value.failedForums.single().contains("超时"))
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test fun unsubscribeDropsLateResultsAndCache() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { entry, page ->
            withContext(NonCancellable) { delay(100) }
            SubscriptionPage( listOf(thread(1)), page, false)
        }
        val vm = vm(source); runCurrent()
        source.subscriptions.value = emptyList()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.threads.isEmpty())
        assertEquals(0, vm.uiState.value.subscriptionCount)
        assertTrue(source.caches.isEmpty())
    }

    @Test fun firstBatchPrefersMissingAndOldCachesAndDeduplicatesAcrossForums() = runTest(dispatcher) {
        val source = Source((1..10).map { entry(it) })
        (1..9).forEach { source.caches["bar$it"] = CachedForumFeed(emptyList(), it.toLong()) }
        source.response = { entry, page -> SubscriptionPage( listOf(thread(1)), page, false) }
        val vm = vm(source); advanceUntilIdle()
        assertEquals("bar10", source.requests.first().first)
        assertEquals((1..7).map { "bar$it" }, source.requests.drop(1).map { it.first })
        assertEquals(1, vm.uiState.value.threads.size)
        assertTrue(vm.uiState.value.hasMore)
    }
    @Test fun emptySelectionDoesNotRequestOrMixRecommendations() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.preferences.value = SubscriptionFeedPreferences(sourceKeys = emptySet(), window = FeedWindow.All)
        val vm = vm(source); advanceUntilIdle()
        assertTrue(source.requests.isEmpty()); assertEquals(0, vm.uiState.value.subscriptionCount)
        vm.updatePreferences(source.preferences.value.copy(sourceKeys = null)); advanceUntilIdle()
        assertEquals(1, source.requests.size)
    }
    @Test fun freshCacheIsShownWithoutAutomaticRequestAndExpiresAtFifteenMinutes() = runTest(dispatcher) {
        var clock = 1_000_000L
        val source = Source(listOf(entry(1)))
        source.caches["bar1"] = CachedForumFeed(listOf(thread(1)), clock)
        val vm = vm(source) { clock }; advanceUntilIdle()
        assertTrue(source.requests.isEmpty()); assertEquals(listOf(1L), vm.uiState.value.threads.map { it.id })
        clock += FEED_REFRESH_MS - 1; vm.onEnter(); advanceUntilIdle(); assertTrue(source.requests.isEmpty())
        clock++; vm.onEnter(); advanceUntilIdle(); assertEquals(1, source.requests.size)
        vm.onEnter(); advanceUntilIdle(); assertEquals(1, source.requests.size)
    }
    @Test fun changingRequestOrderStartsIndependentCursor() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { _, page -> SubscriptionPage(listOf(thread(page.toLong())), page, true) }
        val vm = vm(source); advanceUntilIdle(); vm.loadMore(); advanceUntilIdle()
        vm.updatePreferences(source.preferences.value.copy(sort = FeedSort.Replied)); advanceUntilIdle()
        assertEquals(Triple("forum:bar1", 1, FeedOrder.Reply), source.orderedRequests.last())
        vm.updatePreferences(source.preferences.value.copy(sort = FeedSort.Published)); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(Triple("forum:bar1", 3, FeedOrder.Publish), source.orderedRequests.last())
    }
    @Test fun twoPagesWithoutNewMatchesPauseOnlyThatSourceAndCanContinue() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { _, page -> SubscriptionPage(listOf(thread(1)), page, true) }
        val vm = vm(source); advanceUntilIdle()
        repeat(2) { vm.loadMore(); advanceUntilIdle() }
        assertFalse(vm.uiState.value.hasMore); assertEquals(1, vm.uiState.value.pausedSources.size)
        vm.continueSource(entry(1).key); advanceUntilIdle()
        assertEquals(4, source.requests.last().second)
    }
    @Test fun largerWindowResumesPausedCursorWithoutRefetchingFirstPage() = runTest(dispatcher) {
        val clock = 2_000_000_000_000L
        val source = Source(listOf(entry(1)))
        source.preferences.value = SubscriptionFeedPreferences(window = FeedWindow.Day)
        source.response = { _, page -> SubscriptionPage(listOf(thread(page.toLong(), clock/1000 - 2*86400)), page, true) }
        val vm = vm(source) { clock }; advanceUntilIdle(); vm.loadMore(); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.pausedSources.size)
        vm.updatePreferences(source.preferences.value.copy(window = FeedWindow.Week)); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle(); assertEquals(3, source.requests.last().second)
        assertEquals(3, vm.uiState.value.threads.size)
    }
    @Test fun cacheFailureDoesNotDiscardSuccessfulNetworkResponse() = runTest(dispatcher) {
        val source = Source(listOf(entry(1))).apply { saveSucceeds = false }
        source.response = { _, page -> SubscriptionPage(listOf(thread(1)), page, false) }
        val vm = vm(source); advanceUntilIdle()
        assertEquals(listOf(1L), vm.uiState.value.threads.map { it.id }); assertNotNull(vm.uiState.value.warning)
        assertTrue(vm.uiState.value.failedForums.isEmpty())
    }
    @Test fun sessionLimitStopsAppendingWithoutEvictingVisiblePosts() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { _, page -> SubscriptionPage((1L..2100L).map { thread(it) }, page, true) }
        val vm = vm(source); advanceUntilIdle()
        assertEquals(2000, vm.uiState.value.threads.size); assertTrue(vm.uiState.value.limitReached)
        vm.loadMore(); advanceUntilIdle(); assertEquals(1, source.requests.size)
        assertEquals(50, source.caches["bar1"]!!.items.size)
    }
    @Test fun samePostRemainsWhenFilteringItsOtherSource() = runTest(dispatcher) {
        val forum = entry(1); val user = entry(2).copy(isUser = true)
        val source = Source(listOf(forum,user))
        source.response = { _, page -> SubscriptionPage(listOf(thread(5)), page, false) }
        val vm = vm(source); advanceUntilIdle()
        assertEquals(1, vm.uiState.value.threads.size)
        vm.updatePreferences(source.preferences.value.copy(kind = FeedSourceKind.User, sourceKeys = setOf(user.key))); advanceUntilIdle()
        assertEquals(listOf(5L), vm.uiState.value.threads.map { it.id })
    }

    @Test fun explicitRefreshAfterSessionLimitStartsANewBoundedReadingSet() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        var first = true
        source.response = { _, page -> SubscriptionPage(if (first) (1L..2100L).map { thread(it) } else listOf(thread(9999)), page, false) }
        val vm = vm(source); advanceUntilIdle(); assertTrue(vm.uiState.value.limitReached)
        first = false; vm.refresh(); advanceUntilIdle()
        assertFalse(vm.uiState.value.limitReached); assertEquals(listOf(9999L), vm.uiState.value.threads.map { it.id })
    }
    @Test fun backgroundSourceChangesDoNotStartRequestsUntilReentry() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        val vm = vm(source); advanceUntilIdle(); vm.onLeave()
        source.subscriptions.value += entry(2); advanceUntilIdle(); assertEquals(1, source.requests.size)
        vm.onEnter(); advanceUntilIdle(); assertEquals(2, source.requests.size)
    }

    @Test fun preferenceFailureKeepsTemporaryChoiceAndReportsUnsavedState() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        val vm = vm(source); advanceUntilIdle(); source.preferenceSaveSucceeds = false
        vm.updatePreferences(source.preferences.value.copy(window = FeedWindow.Day)); advanceUntilIdle()
        assertEquals(FeedWindow.Day, vm.uiState.value.preferences.window)
        assertEquals(FeedWindow.All, source.preferences.value.window)
        assertTrue(vm.uiState.value.warning!!.contains("未保存"))
    }
    @Test fun clearingCacheInvalidatesMemoryWithoutRemovingSubscriptions() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { _, page -> SubscriptionPage(listOf(thread(1)), page, false) }
        val vm = vm(source); advanceUntilIdle(); vm.onLeave()
        source.caches.clear(); source.cacheRevision.value++; advanceUntilIdle()
        assertTrue(vm.uiState.value.threads.isEmpty()); assertEquals(1, vm.uiState.value.sources.size)
        assertEquals(1, source.requests.size)
        vm.onEnter(); advanceUntilIdle(); assertEquals(2, source.requests.size)
    }
    @Test fun changingSelectionRejectsLateUncancellablePage() = runTest(dispatcher) {
        val source = Source(listOf(entry(1)))
        source.response = { _, page ->
            withContext(NonCancellable) { delay(100) }
            SubscriptionPage(listOf(thread(1)), page, false)
        }
        val vm = vm(source); runCurrent()
        vm.updatePreferences(source.preferences.value.copy(sourceKeys = emptySet())); advanceUntilIdle()
        assertTrue(vm.uiState.value.threads.isEmpty()); assertEquals(0, vm.uiState.value.subscriptionCount)
        assertTrue(source.caches.isEmpty())
    }

}
