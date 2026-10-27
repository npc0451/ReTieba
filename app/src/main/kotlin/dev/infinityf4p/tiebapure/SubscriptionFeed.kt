package dev.infinityf4p.tiebapure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.infinityf4p.tiebapure.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class FeedSubscription(
    val normalizedName: String, val forumId: Long, val name: String, val displayName: String,
    val avatarUrl: String?, val subscribedAtMilliseconds: Long, val isUser: Boolean = false,
) {
    val key: String get() = (if (isUser) "user:" else "forum:") + normalizedName
    fun toForum() = Forum(forumId, name, displayName, avatarUrl)
}
internal data class SubscriptionPage(val threads: List<ThreadSummary>, val currentPage: Int, val hasMore: Boolean)
internal data class CachedForumFeed(
    val threads: List<ThreadSummary>, val updatedAt: Long,
    val items: List<FeedCachedItem> = threads.map { FeedCachedItem(it, updatedAt, FeedOrder.Publish) },
)
internal interface SubscriptionFeedSource {
    val subscriptions: Flow<List<FeedSubscription>>
    val preferences: Flow<SubscriptionFeedPreferences> get() = flowOf(SubscriptionFeedPreferences())
    val cacheRevision: Flow<Long> get() = flowOf(0L)
    suspend fun setPreferences(value: SubscriptionFeedPreferences) = Unit
    suspend fun cache(entry: FeedSubscription): CachedForumFeed?
    suspend fun load(entry: FeedSubscription, page: Int): SubscriptionPage
    suspend fun load(entry: FeedSubscription, page: Int, order: FeedOrder): SubscriptionPage = load(entry, page)
    suspend fun save(entry: FeedSubscription, cache: CachedForumFeed): Boolean
    suspend fun maintain() = Unit
}
internal data class SubscriptionFeedState(
    val threads: List<ThreadSummary> = emptyList(),
    val sources: List<FeedSubscription> = emptyList(),
    val preferences: SubscriptionFeedPreferences = SubscriptionFeedPreferences(),
    val subscriptionCount: Int = 0, val updatedCount: Int = 0, val attemptedCount: Int = 0,
    val failedForums: List<String> = emptyList(), val oldestCacheAt: Long? = null,
    val isBusy: Boolean = false, val isInitializing: Boolean = true, val hasMore: Boolean = false,
    val missingTimeCount: Int = 0, val pendingCount: Int = 0,
    val pausedSources: List<FeedSubscription> = emptyList(), val limitReached: Boolean = false,
    val warning: String? = null,
)
internal class SubscriptionFeedViewModel(
    private val source: SubscriptionFeedSource,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private class Session(val entry: FeedSubscription, val order: FeedOrder) {
        var nextPage = 1; var checkedAt: Long? = null; var hasMore = true
        var emptyPages = 0; var error: String? = null; var attempted = false
    }
    private val sessions = linkedMapOf<Pair<String, FeedOrder>, Session>()
    private val items = linkedMapOf<String, List<FeedCachedItem>>()
    private val cachedAt = mutableMapOf<String, Long>()
    private var entries = emptyList<FeedSubscription>()
    private var preferences = SubscriptionFeedPreferences()
    private var visibleIds: Set<Long>? = null
    private var pendingIds = emptySet<Long>()
    private var warning: String? = null
    private var limitReached = false
    private val state = MutableStateFlow(SubscriptionFeedState())
    val uiState = state.asStateFlow()
    private var request: Job? = null
    private var generation = 0L
    private var initialized = false
    private var settingsReady = false
    private var active = true
    private var cacheRevision = 0L
    private var refreshSnapshot: List<ThreadSummary>? = null

    init {
        viewModelScope.launch {
            try { source.maintain() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { warning = "摘要缓存暂不可用，将尝试网络加载。" }
            combine(source.subscriptions, source.preferences, source.cacheRevision) { entries, prefs, revision -> Triple(entries, prefs, revision) }
                .distinctUntilChanged().collectLatest { (next, prefs, revision) ->
                    generation++
                    request?.cancelAndJoin()
                    if (revision != cacheRevision) {
                        sessions.clear(); items.clear(); cachedAt.clear()
                        visibleIds = null; pendingIds = emptySet(); limitReached = false
                    }
                    cacheRevision = revision
                    val retained = entries.associateBy { it.key }
                    next.forEach { entry ->
                        if (retained[entry.key]?.subscribedAtMilliseconds != entry.subscribedAtMilliseconds) {
                            sessions.keys.filter { it.first == entry.key }.forEach(sessions::remove)
                            val cached = try { source.cache(entry) }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { warning = "部分摘要缓存暂不可用，将尝试网络加载。"; null }
                            items[entry.key] = cached?.items.orEmpty()
                            cachedAt.remove(entry.key)
                            cached?.updatedAt?.let { cachedAt[entry.key] = it }
                        }
                    }
                    entries = next
                    items.keys.retainAll(next.map { it.key }.toSet())
                    cachedAt.keys.retainAll(next.map { it.key }.toSet())
                    sessions.keys.filter { key -> next.none { it.key == key.first } }.forEach(sessions::remove)
                    val queryChanged = preferences.kind != prefs.kind || preferences.sourceKeys != prefs.sourceKeys || preferences.order != prefs.order
                    val expanded = (prefs.window.days ?: Int.MAX_VALUE) > (preferences.window.days ?: Int.MAX_VALUE)
                    preferences = prefs
                    if (expanded) sessions.values.forEach { it.emptyPages = 0 }
                    if (queryChanged) { visibleIds = null; pendingIds = emptySet() }
                    initialized = true; settingsReady = true
                    publish()
                    if (active) onEnter()
                }
        }
    }
    private fun selected() = entries.filter(preferences::includes)
    private fun session(entry: FeedSubscription): Session {
        val order = if (entry.isUser) FeedOrder.Publish else preferences.order
        return sessions.getOrPut(entry.key to order) { Session(entry, order) }
    }
    private fun candidates(): List<ThreadSummary> = selected().flatMap { items[it.key].orEmpty() }
        .sortedByDescending { it.fetchedAt }.distinctBy { it.thread.id }.map { it.thread }
    fun onEnter() {
        active = true
        if (!initialized || state.value.isBusy || limitReached) return
        val stale = selected().map(::session).filter { it.checkedAt == null || now() - it.checkedAt!! >= FEED_REFRESH_MS }
            .sortedBy { it.checkedAt ?: cachedAt[it.entry.key] ?: Long.MIN_VALUE }
        // A cached snapshot is not a successful check in this session; freshness only suppresses automatic requests.
        val outdated = stale.filter { s -> s.checkedAt != null || items[s.entry.key].orEmpty().none { it.order == s.order && now() - it.fetchedAt in 0 until FEED_REFRESH_MS } }
        if (outdated.isNotEmpty()) start(outdated.take(8), automatic = true, reset = true)
    }
    fun onLeave() {
        active = false
        generation++
        request?.cancel()
        publish()
    }
    fun updatePreferences(value: SubscriptionFeedPreferences) {
        if (!settingsReady || value == preferences) return
        viewModelScope.launch {
            try { source.setPreferences(value) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                generation++; request?.cancelAndJoin(); preferences = value
                visibleIds = null; pendingIds = emptySet(); sessions.values.forEach { it.emptyPages = 0 }
                warning = "筛选已临时生效，但未保存。"; publish(); onEnter()
            }
        }
    }
    fun refresh() {
        if (!initialized) return
        generation++
        val previous = request
        request = viewModelScope.launch {
            previous?.cancelAndJoin()
            selected().map(::session).forEach { it.nextPage = 1; it.checkedAt = null; it.hasMore = true; it.error = null; it.emptyPages = 0; it.attempted = false }
            val fallback = if (limitReached) items.toMap() else null
            if (fallback != null) {
                refreshSnapshot = state.value.threads
                items.clear()
            }
            limitReached = false; visibleIds = null; pendingIds = emptySet()
            try { batch(nextBatch(), false, generation) }
            finally {
                if (fallback != null && candidates().isEmpty()) { items.putAll(fallback); limitReached = true }
                refreshSnapshot = null
                publish()
            }
        }
    }
    fun loadMore() { if (!state.value.isBusy && !limitReached) start(nextBatch()) }
    fun retryFailures() { if (!state.value.isBusy) start(selected().map(::session).filter { it.error != null }.take(8)) }
    fun continueSource(key: String) {
        if (state.value.isBusy || limitReached) return
        selected().firstOrNull { it.key == key }?.let { val s = session(it); s.emptyPages = 0; start(listOf(s)) }
    }
    fun applyPending() { visibleIds = null; pendingIds = emptySet(); publish() }
    private fun nextBatch(): List<Session> {
        val all = selected().map(::session)
        val unchecked = all.filter { !it.attempted }
        return if (unchecked.isNotEmpty()) unchecked.sortedBy { it.checkedAt ?: cachedAt[it.entry.key] ?: Long.MIN_VALUE }.take(8)
        else all.filter { it.error == null && it.hasMore && it.emptyPages < 2 }
            .sortedWith(compareBy<Session> { it.nextPage }.thenBy { it.checkedAt ?: Long.MIN_VALUE }).take(8)
    }
    private fun start(selected: List<Session>, automatic: Boolean = false, reset: Boolean = false) {
        if (selected.isEmpty() || state.value.isBusy) return
        if (reset) selected.forEach { it.nextPage = 1; it.hasMore = true; it.emptyPages = 0 }
        if (automatic && state.value.threads.isNotEmpty()) visibleIds = state.value.threads.map { it.id }.toSet()
        state.value = state.value.copy(isBusy = true)
        val token = generation
        request = viewModelScope.launch { batch(selected, automatic, token) }
    }
    private suspend fun batch(selected: List<Session>, automatic: Boolean, token: Long) {
        publish(true)
        val permits = Semaphore(4)
        try {
            coroutineScope {
                selected.map { session -> async {
                    permits.withPermit {
                        try {
                            val requestedPage = session.nextPage
                            val page = withTimeoutOrNull(12_000) { source.load(session.entry, requestedPage, session.order) }
                                ?: throw java.io.IOException("请求超时")
                            ensureActive()
                            if (token != generation || entries.none { it.key == session.entry.key && it.subscribedAtMilliseconds == session.entry.subscribedAtMilliseconds }) return@withPermit
                            val currentIds = items.values.flatten().map { it.thread.id }.toSet()
                            var room = (FEED_MAX_ITEMS - currentIds.size).coerceAtLeast(0)
                            val accepted = page.threads.distinctBy { it.id }.filter { it.id in currentIds || (room > 0).also { ok -> if (ok) room-- } }
                            if (accepted.size < page.threads.size) limitReached = true
                            val at = now()
                            val old = items[session.entry.key].orEmpty()
                            val incoming = accepted.map { FeedCachedItem(it, at, session.order) }
                            val merged = (incoming + old).distinctBy { it.thread.id }
                            items[session.entry.key] = merged
                            if (automatic && visibleIds != null) pendingIds = candidates().filter { preferences.matches(it, at) && it.id !in visibleIds!! }.map { it.id }.toSet()
                            if (incoming.none { preferences.matches(it.thread, at) && old.none { c -> c.thread.id == it.thread.id } }) session.emptyPages++ else session.emptyPages = 0
                            session.checkedAt = if (requestedPage == 1) at else session.checkedAt
                            session.nextPage = maxOf(requestedPage + 1, page.currentPage + 1)
                            session.hasMore = page.hasMore; session.error = null
                            try {
                                if (!source.save(session.entry, CachedForumFeed(merged.map { it.thread }, at, retainFeedItems(merged, at)))) warning = "部分订阅摘要未保存，当前仍可阅读。"
                            } catch (error: CancellationException) { throw error }
                            catch (_: Exception) { warning = "部分订阅摘要未保存，当前仍可阅读。" }
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { session.error = error.message ?: "加载失败" }
                        session.attempted = true
                        if (token == generation) publish(true)
                    }
                } }.awaitAll()
            }
            try { source.maintain() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { warning = "缓存清理未完成，可在存储设置中重试。" }
        } finally { if (token == generation) publish() }
    }
    private fun publish(busy: Boolean = false) {
        val selected = selected().map(::session)
        val all = candidates()
        val filtered = all.filter { preferences.matches(it, now()) && (visibleIds == null || it.id in visibleIds!!) }
        state.value = SubscriptionFeedState(
            threads = if (busy) refreshSnapshot ?: preferences.sorted(filtered) else preferences.sorted(filtered), sources = entries, preferences = preferences,
            subscriptionCount = selected.size, updatedCount = selected.count { it.checkedAt != null },
            attemptedCount = selected.count { it.attempted }, failedForums = selected.filter { it.error != null }.map { "${it.entry.displayName}：${it.error}" },
            oldestCacheAt = selected.mapNotNull { s -> items[s.entry.key]?.minOfOrNull { it.fetchedAt } }.minOrNull(),
            isBusy = busy, isInitializing = !initialized, hasMore = !limitReached && nextBatch().isNotEmpty(),
            missingTimeCount = if (preferences.window.days == null) 0 else all.count { preferences.timestamp(it) == null },
            pendingCount = pendingIds.size, pausedSources = selected.filter { it.emptyPages >= 2 && it.hasMore }.map { it.entry },
            limitReached = limitReached, warning = warning,
        )
    }
}
