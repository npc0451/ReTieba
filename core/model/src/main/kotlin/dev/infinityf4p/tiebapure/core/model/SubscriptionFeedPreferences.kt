package dev.infinityf4p.tiebapure.core.model

enum class FeedSourceKind { All, Forum, User }
enum class FeedSort { Published, Replied, Replies }
enum class FeedWindow(val days: Int?) { Day(1), Week(7), Month(30), All(null) }

data class SubscriptionFeedPreferences(
    val kind: FeedSourceKind = FeedSourceKind.All,
    val sourceKeys: Set<String>? = null,
    val sort: FeedSort = FeedSort.Published,
    val window: FeedWindow = FeedWindow.Week,
)

/** Bounded windows exclude unknown and future timestamps; All retains unknown timestamps. */
fun FeedWindow.containsEpochSeconds(timestamp: Long?, nowEpochSeconds: Long): Boolean {
    val days = days ?: return true
    val value = timestamp?.takeIf { it > 0 } ?: return false
    return value in (nowEpochSeconds - days * 86_400L)..nowEpochSeconds
}

val threadReplyCountOrder: Comparator<ThreadSummary> = compareByDescending<ThreadSummary> { it.replyCount }
    .thenByDescending { it.likeCount }
    .thenByDescending { it.createdAtEpochSeconds ?: Long.MIN_VALUE }
    .thenByDescending { it.id }
