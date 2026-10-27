package dev.infinityf4p.tiebapure.core.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import dev.infinityf4p.tiebapure.core.model.Forum
import dev.infinityf4p.tiebapure.core.model.ThreadSummary
import java.util.Locale
import kotlinx.coroutines.flow.Flow

fun localForumKey(value: String): String? = value.trim().removeSuffix("吧").trim()
    .takeIf { it.isNotEmpty() && it.none { character -> character.isISOControl() || character == '/' || character == '\\' } }
    ?.lowercase(Locale.ROOT)

@Entity(tableName = "local_subscriptions")
data class LocalSubscriptionEntity(
    @PrimaryKey @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "forum_id") val forumId: Long,
    val name: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "subscribed_at_ms") val subscribedAtMilliseconds: Long,
) {
    fun toForum() = Forum(forumId, name, displayName, avatarUrl)
}

@Entity(tableName = "local_bookmarks")
data class LocalBookmarkEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: Long,
    val title: String,
    @ColumnInfo(name = "author_name") val authorName: String,
    @ColumnInfo(name = "forum_name") val forumName: String?,
    @ColumnInfo(name = "bookmarked_at_ms") val bookmarkedAtMilliseconds: Long,
)

@Entity(
    tableName = "forum_feed_cache",
    foreignKeys = [ForeignKey(
        entity = LocalSubscriptionEntity::class,
        parentColumns = ["normalized_name"], childColumns = ["normalized_name"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ForumFeedCacheEntity(
    @PrimaryKey @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMilliseconds: Long,
    @ColumnInfo(name = "summary_blob", typeAffinity = ColumnInfo.BLOB) val summaryBlob: ByteArray,
)

@Dao
interface LocalLibraryDao {
    @Query("SELECT * FROM local_subscriptions ORDER BY subscribed_at_ms DESC, normalized_name ASC")
    fun observeSubscriptions(): Flow<List<LocalSubscriptionEntity>>

    @Query("SELECT * FROM local_subscriptions WHERE normalized_name = :key LIMIT 1")
    suspend fun subscription(key: String): LocalSubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubscription(entity: LocalSubscriptionEntity)

    @Query("UPDATE local_subscriptions SET forum_id = CASE WHEN :forumId > 0 THEN :forumId ELSE forum_id END, avatar_url = COALESCE(:avatarUrl, avatar_url) WHERE normalized_name = :key")
    suspend fun enrichSubscription(key: String, forumId: Long, avatarUrl: String?)

    @Query("DELETE FROM local_subscriptions WHERE normalized_name = :key")
    suspend fun removeSubscription(key: String)

    @Query("SELECT * FROM local_bookmarks ORDER BY bookmarked_at_ms DESC, thread_id DESC")
    fun observeBookmarks(): Flow<List<LocalBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookmark(entity: LocalBookmarkEntity)

    @Query("DELETE FROM local_bookmarks WHERE thread_id = :threadId")
    suspend fun removeBookmark(threadId: Long)

    @Query("SELECT * FROM forum_feed_cache WHERE normalized_name = :key LIMIT 1")
    suspend fun feedCache(key: String): ForumFeedCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putFeedCache(entity: ForumFeedCacheEntity)

    @Query("SELECT * FROM forum_feed_cache")
    suspend fun allFeedCaches(): List<ForumFeedCacheEntity>

    @Query("DELETE FROM forum_feed_cache WHERE normalized_name = :key")
    suspend fun deleteFeedCache(key: String)

    @Query("SELECT COALESCE(SUM(length(summary_blob)), 0) FROM forum_feed_cache")
    suspend fun feedCacheBytes(): Long

    @Query("DELETE FROM forum_feed_cache")
    suspend fun clearFeedCache()
}

class LocalLibraryRepository(
    private val database: TiebaPureDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.localLibraryDao()
    val subscriptions = dao.observeSubscriptions()
    val bookmarks = dao.observeBookmarks()

    suspend fun subscribe(forum: Forum) {
        val key = requireNotNull(localForumKey(forum.name)) { "贴吧名称无效。" }
        val name = forum.name.trim().removeSuffix("吧").trim()
        database.withTransaction {
            dao.insertSubscription(LocalSubscriptionEntity(
                key, forum.id.coerceAtLeast(0), name, "${name}吧",
                forum.avatarUrl?.takeIf(String::isNotBlank), now(),
            ))
            dao.enrichSubscription(key, forum.id, forum.avatarUrl?.takeIf(String::isNotBlank))
        }
    }

    suspend fun unsubscribe(forumName: String) {
        dao.removeSubscription(requireNotNull(localForumKey(forumName)) { "贴吧名称无效。" })
    }

    suspend fun bookmark(thread: ThreadSummary) {
        require(thread.id > 0) { "帖子 ID 无效。" }
        dao.insertBookmark(LocalBookmarkEntity(
            thread.id, thread.title, thread.author.resolvedDisplayName,
            thread.forumName, now(),
        ))
    }

    suspend fun removeBookmark(threadId: Long) {
        require(threadId > 0) { "帖子 ID 无效。" }
        dao.removeBookmark(threadId)
    }
}
