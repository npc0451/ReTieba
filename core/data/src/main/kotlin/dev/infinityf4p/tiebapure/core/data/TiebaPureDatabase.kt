package dev.infinityf4p.tiebapure.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BlocklistEntity::class,
        BrowsingHistoryEntity::class,
        ContentDraftEntity::class,
        ReadingPositionEntity::class,
        RecentForumEntity::class,
        SavedThreadEntity::class,
        SearchHistoryEntity::class,
        LocalSubscriptionEntity::class,
        LocalBookmarkEntity::class,
        ForumFeedCacheEntity::class,
        LocalUserFollowEntity::class,
        UserFeedCacheEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class TiebaPureDatabase : RoomDatabase() {
    abstract fun blocklistDao(): BlocklistDao
    abstract fun browsingHistoryDao(): BrowsingHistoryDao
    abstract fun contentDraftDao(): ContentDraftDao
    abstract fun readingPositionDao(): ReadingPositionDao
    abstract fun recentForumDao(): RecentForumDao
    abstract fun savedThreadDao(): SavedThreadDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun localUserFollowsDao(): LocalUserFollowsDao

    companion object {
        @Volatile
        private var instance: TiebaPureDatabase? = null

        fun getInstance(context: Context): TiebaPureDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TiebaPureDatabase::class.java,
                "tiebapure.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_subscriptions` (`normalized_name` TEXT NOT NULL, `forum_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `display_name` TEXT NOT NULL, `avatar_url` TEXT, `subscribed_at_ms` INTEGER NOT NULL, PRIMARY KEY(`normalized_name`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_bookmarks` (`thread_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `author_name` TEXT NOT NULL, `forum_name` TEXT, `bookmarked_at_ms` INTEGER NOT NULL, PRIMARY KEY(`thread_id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `forum_feed_cache` (`normalized_name` TEXT NOT NULL, `updated_at_ms` INTEGER NOT NULL, `summary_blob` BLOB NOT NULL, PRIMARY KEY(`normalized_name`), FOREIGN KEY(`normalized_name`) REFERENCES `local_subscriptions`(`normalized_name`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `local_user_follows` (`user_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `display_name` TEXT NOT NULL, `portrait` TEXT NOT NULL, `followed_at_ms` INTEGER NOT NULL, PRIMARY KEY(`user_id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_feed_cache` (`user_id` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, `summary_blob` BLOB NOT NULL, PRIMARY KEY(`user_id`), FOREIGN KEY(`user_id`) REFERENCES `local_user_follows`(`user_id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `content_drafts`")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `content_drafts` (`account_id` TEXT NOT NULL, `target_key` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `target_metadata` BLOB NOT NULL, `attachment_file_name` TEXT NOT NULL, `attachment_byte_count` INTEGER NOT NULL, `attachment_sha256` TEXT NOT NULL, `image_count` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, PRIMARY KEY(`account_id`, `target_key`))""",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_content_drafts_account_id` ON `content_drafts` (`account_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_content_drafts_updated_at_ms` ON `content_drafts` (`updated_at_ms`)",
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `saved_threads` (`thread_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `author_name` TEXT NOT NULL, `forum_name` TEXT NOT NULL, `saved_at_ms` INTEGER NOT NULL, `snapshot_blob` BLOB NOT NULL, PRIMARY KEY(`thread_id`))""",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_threads_saved_at_ms` ON `saved_threads` (`saved_at_ms`)",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `saved_threads` ADD COLUMN `media_mode` TEXT NOT NULL DEFAULT 'TextOnly'")
                db.execSQL("ALTER TABLE `saved_threads` ADD COLUMN `media_byte_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_threads` ADD COLUMN `new_reply_count` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `saved_threads` ADD COLUMN `last_checked_at_ms` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `saved_threads` ADD COLUMN `metadata_version` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
