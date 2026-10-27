package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

internal const val FEED_REFRESH_MS = 15 * 60_000L
internal const val FEED_EXPIRY_MS = 30 * 86_400_000L
internal const val FEED_MAX_ITEMS = 2000
internal const val FEED_MAX_BYTES = 10 * 1024 * 1024
internal const val FEED_SOURCE_MAX_ITEMS = 50

internal enum class FeedOrder { Publish, Reply }
internal val SubscriptionFeedPreferences.order get() = if (sort == FeedSort.Replied) FeedOrder.Reply else FeedOrder.Publish
internal fun SubscriptionFeedPreferences.includes(entry: FeedSubscription) =
    (sourceKeys == null || entry.key in sourceKeys.orEmpty()) && when (kind) {
        FeedSourceKind.All -> true
        FeedSourceKind.Forum -> !entry.isUser
        FeedSourceKind.User -> entry.isUser
    }

internal fun SubscriptionFeedPreferences.timestamp(thread: ThreadSummary): Long? =
    (if (sort == FeedSort.Replied) thread.lastReplyAtEpochSeconds else thread.createdAtEpochSeconds)?.takeIf { it > 0 }

internal fun SubscriptionFeedPreferences.matches(thread: ThreadSummary, now: Long): Boolean =
    window.containsEpochSeconds(timestamp(thread), now / 1000)

internal fun SubscriptionFeedPreferences.sorted(threads: List<ThreadSummary>): List<ThreadSummary> =
    threads.sortedWith(if (sort == FeedSort.Replies) threadReplyCountOrder
    else compareByDescending<ThreadSummary> { timestamp(it) ?: Long.MIN_VALUE }.thenByDescending { it.id })

internal data class FeedCachedItem(val thread: ThreadSummary, val fetchedAt: Long, val order: FeedOrder)

internal fun retainFeedCacheBudget(
    rows: Map<String, List<FeedCachedItem>>,
    maxCount: Int = FEED_MAX_ITEMS,
    maxBytes: Int = FEED_MAX_BYTES,
): Map<String, List<FeedCachedItem>> {
    val result = linkedMapOf<String, MutableList<FeedCachedItem>>()
    var bytes = 0
    var count = 0
    rows.flatMap { (key, items) -> items.map { key to it } }
        .sortedWith(compareByDescending<Pair<String, FeedCachedItem>> { it.second.fetchedAt }.thenBy { it.first }.thenByDescending { it.second.thread.id })
        .forEach { (key, item) ->
            val size = SubscriptionCacheCodec.encode(listOf(item)).size - 8
            val overhead = if (key in result) 0 else 8
            if (count < maxCount && bytes + size + overhead <= maxBytes) {
                result.getOrPut(key) { mutableListOf() }.add(item)
                bytes += size + overhead; count++
            }
        }
    return result
}

internal fun retainFeedItems(items: List<FeedCachedItem>, now: Long): List<FeedCachedItem> =
    items.filter { it.fetchedAt in (now - FEED_EXPIRY_MS)..now }
        .sortedByDescending { it.fetchedAt }.distinctBy { it.thread.id }.take(FEED_SOURCE_MAX_ITEMS)

/** Only subscription cache uses this envelope; offline snapshots keep their original format. */
internal object SubscriptionCacheCodec {
    private const val MAGIC = 0x54504632
    fun encode(items: List<FeedCachedItem>): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(items.size)
            items.forEach {
                val blob = SavedThreadBlobCodec.encodeSummaries(listOf(it.thread))
                out.writeLong(it.fetchedAt); out.writeInt(it.order.ordinal); out.writeInt(blob.size); out.write(blob)
            }
        }
        bytes.toByteArray()
    }
    fun decode(blob: ByteArray, legacyUpdatedAt: Long): List<FeedCachedItem> {
        require(blob.size in 4..FEED_MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(blob)).use { input ->
            if (input.readInt() != MAGIC) return SavedThreadBlobCodec.decodeSummaries(blob)
                .take(FEED_SOURCE_MAX_ITEMS).map { FeedCachedItem(it, legacyUpdatedAt, FeedOrder.Publish) }
            val count = input.readInt(); require(count in 0..FEED_SOURCE_MAX_ITEMS)
            val items = List(count) {
                val at = input.readLong()
                val order = FeedOrder.entries.getOrNull(input.readInt()) ?: error("无效缓存顺序")
                val size = input.readInt(); require(size in 1..input.available())
                val bytes = ByteArray(size); input.readFully(bytes)
                FeedCachedItem(SavedThreadBlobCodec.decodeSummaries(bytes).single(), at, order)
            }
            require(input.available() == 0)
            items
        }
    }
}
