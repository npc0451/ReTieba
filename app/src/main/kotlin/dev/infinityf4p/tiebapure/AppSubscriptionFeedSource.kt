package dev.infinityf4p.tiebapure

import androidx.room.withTransaction
import dev.infinityf4p.tiebapure.core.data.*
import dev.infinityf4p.tiebapure.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

internal class AppSubscriptionFeedSource(
    private val database: TiebaPureDatabase,
    private val forums: ForumRepository,
    private val users: UserRepository,
    private val settings: AppSettingsStore,
) : SubscriptionFeedSource {
    private val dao = database.localLibraryDao()
    private val userDao = database.localUserFollowsDao()
    private val revision = MutableStateFlow(0L)
    override val cacheRevision = revision.asStateFlow()
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.clearFeedCache(); userDao.clearCache()
            revision.value++
        }
    }
    override val preferences = settings.subscriptionFeed
    override suspend fun setPreferences(value: SubscriptionFeedPreferences) = settings.setSubscriptionFeed(value)
    override val subscriptions = combine(dao.observeSubscriptions(), userDao.observeAll()) { forums, people ->
        forums.map { FeedSubscription(it.normalizedName, it.forumId, it.name, it.displayName, it.avatarUrl, it.subscribedAtMilliseconds) } +
            people.map { FeedSubscription(it.userId.toString(), it.userId, it.name, it.displayName, it.portrait, it.followedAtMilliseconds, true) }
    }
    override suspend fun cache(entry: FeedSubscription) = withContext(Dispatchers.IO) {
        val row = if (entry.isUser) userDao.cache(entry.forumId)?.let { it.summaryBlob to it.updatedAtMilliseconds }
            else dao.feedCache(entry.normalizedName)?.let { it.summaryBlob to it.updatedAtMilliseconds }
        row?.let { runCatching {
            val items = retainFeedItems(SubscriptionCacheCodec.decode(it.first, it.second), System.currentTimeMillis())
            CachedForumFeed(items.map { it.thread }, it.second, items)
        }.getOrNull() }
    }
    override suspend fun load(entry: FeedSubscription, page: Int) = load(entry, page, FeedOrder.Publish)
    override suspend fun load(entry: FeedSubscription, page: Int, order: FeedOrder): SubscriptionPage {
        if (entry.isUser) {
            val result = users.threads(entry.forumId, page, account = null)
            if (result.visibility == UserContentVisibility.Private) throw java.io.IOException("该用户未公开帖子。")
            return SubscriptionPage(result.threads, result.currentPage, result.hasMore)
        }
        val result = forums.threads(entry.name, page,
            if (order == FeedOrder.Reply) ForumThreadCategory.ReplyTime else ForumThreadCategory.PublishTime, account = null)
        return SubscriptionPage(result.threads.map { it.copy(forumId = entry.forumId.takeIf { id -> id > 0 },
            forumName = entry.name, forumAvatarUrl = entry.avatarUrl) }, result.currentPage, result.hasMore)
    }
    override suspend fun save(entry: FeedSubscription, cache: CachedForumFeed): Boolean {
        val expectedRevision = revision.value
        return withContext(Dispatchers.IO) {
            val blob = SubscriptionCacheCodec.encode(cache.items)
            database.withTransaction {
                if (revision.value != expectedRevision) return@withTransaction false
                if (entry.isUser) {
                    if (userDao.find(entry.forumId)?.followedAtMilliseconds != entry.subscribedAtMilliseconds) false
                    else { userDao.putCache(UserFeedCacheEntity(entry.forumId, cache.updatedAt, blob)); true }
                } else if (dao.subscription(entry.normalizedName)?.subscribedAtMilliseconds != entry.subscribedAtMilliseconds) false
                else { dao.putFeedCache(ForumFeedCacheEntity(entry.normalizedName, cache.updatedAt, blob)); true }
            }
        }
    }
    override suspend fun maintain() = withContext(Dispatchers.IO) {
        database.withTransaction {
            val rows = dao.allFeedCaches().map { CacheRow("forum:" + it.normalizedName, it.updatedAtMilliseconds, it.summaryBlob) } +
                userDao.allCaches().map { CacheRow("user:" + it.userId, it.updatedAtMilliseconds, it.summaryBlob) }
            val now = System.currentTimeMillis()
            val retained = rows.associate { row -> row.key to runCatching {
                retainFeedItems(SubscriptionCacheCodec.decode(row.blob, row.at), now)
            }.getOrDefault(emptyList()) }.toMutableMap()
            val budget = retainFeedCacheBudget(retained)
            rows.forEach { row ->
                val kept = budget[row.key].orEmpty()
                if (kept.isEmpty()) {
                    if (row.key.startsWith("user:")) userDao.deleteCache(row.key.removePrefix("user:").toLong())
                    else dao.deleteFeedCache(row.key.removePrefix("forum:"))
                } else {
                    val blob = SubscriptionCacheCodec.encode(kept)
                    if (!blob.contentEquals(row.blob)) {
                        if (row.key.startsWith("user:")) userDao.putCache(UserFeedCacheEntity(row.key.removePrefix("user:").toLong(), row.at, blob))
                        else dao.putFeedCache(ForumFeedCacheEntity(row.key.removePrefix("forum:"), row.at, blob))
                    }
                }
            }
        }
    }
    private data class CacheRow(val key: String, val at: Long, val blob: ByteArray)
}
