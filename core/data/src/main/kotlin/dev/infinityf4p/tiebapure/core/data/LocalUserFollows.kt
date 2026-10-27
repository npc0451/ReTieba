package dev.infinityf4p.tiebapure.core.data

import androidx.room.*
import dev.infinityf4p.tiebapure.core.model.UserSummary
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_user_follows")
data class LocalUserFollowEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Long,
    val name: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val portrait: String,
    @ColumnInfo(name = "followed_at_ms") val followedAtMilliseconds: Long,
) {
    fun toUser() = UserSummary(userId, name, displayName, portrait)
}

@Entity(tableName = "user_feed_cache", foreignKeys = [ForeignKey(
    entity = LocalUserFollowEntity::class, parentColumns = ["user_id"], childColumns = ["user_id"], onDelete = ForeignKey.CASCADE,
)])
data class UserFeedCacheEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMilliseconds: Long,
    @ColumnInfo(name = "summary_blob", typeAffinity = ColumnInfo.BLOB) val summaryBlob: ByteArray,
)

@Dao
interface LocalUserFollowsDao {
    @Query("SELECT * FROM local_user_follows ORDER BY followed_at_ms DESC, user_id DESC")
    fun observeAll(): Flow<List<LocalUserFollowEntity>>
    @Query("SELECT * FROM local_user_follows WHERE user_id = :id")
    suspend fun find(id: Long): LocalUserFollowEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LocalUserFollowEntity)
    @Query("DELETE FROM local_user_follows WHERE user_id = :id")
    suspend fun remove(id: Long)
    @Query("SELECT * FROM user_feed_cache WHERE user_id = :id")
    suspend fun cache(id: Long): UserFeedCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(entry: UserFeedCacheEntity)
    @Query("SELECT * FROM user_feed_cache")
    suspend fun allCaches(): List<UserFeedCacheEntity>
    @Query("DELETE FROM user_feed_cache WHERE user_id = :id")
    suspend fun deleteCache(id: Long)
    @Query("SELECT COALESCE(SUM(length(summary_blob)), 0) FROM user_feed_cache")
    suspend fun cacheBytes(): Long
    @Query("DELETE FROM user_feed_cache")
    suspend fun clearCache()
}

class LocalUserFollowsRepository(private val database: TiebaPureDatabase) {
    private val dao = database.localUserFollowsDao()
    val users = dao.observeAll()
    suspend fun follow(user: UserSummary) {
        require(user.id > 0) { "无法识别该用户，请刷新个人主页后重试。" }
        dao.insert(LocalUserFollowEntity(user.id, user.name, user.resolvedDisplayName, user.portrait, System.currentTimeMillis()))
    }
    suspend fun unfollow(id: Long) { require(id > 0); dao.remove(id) }
}
