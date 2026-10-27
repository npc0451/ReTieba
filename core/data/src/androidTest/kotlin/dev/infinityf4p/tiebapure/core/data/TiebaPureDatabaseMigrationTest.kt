package dev.infinityf4p.tiebapure.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TiebaPureDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TiebaPureDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrationFromOneDropsOnlyPreReleaseDrafts() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                "INSERT INTO content_drafts (account_id,target_key,title,body,images_blob,updated_at_ms) VALUES ('a','t','title','body',X'01',1)",
            )
            execSQL(
                "INSERT INTO browsing_history (thread_id,title,author_name,forum_name,visited_at_ms) VALUES (7,'thread','author','forum',9)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            TiebaPureDatabase.MIGRATION_1_2,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM content_drafts").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT title FROM browsing_history WHERE thread_id = 7").use { cursor ->
                cursor.moveToFirst()
                assertEquals("thread", cursor.getString(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrationFromTwoAddsSavedThreadsWithoutChangingExistingData() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                "INSERT INTO browsing_history (thread_id,title,author_name,forum_name,visited_at_ms) VALUES (8,'kept','author','forum',10)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            TiebaPureDatabase.MIGRATION_2_3,
        ).use { database ->
            database.query("SELECT title FROM browsing_history WHERE thread_id = 8").use { cursor ->
                cursor.moveToFirst()
                assertEquals("kept", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM saved_threads").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrationFromThreePreservesSnapshotsAndMarksMetadataForRepair() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                "INSERT INTO saved_threads (thread_id,title,author_name,forum_name,saved_at_ms,snapshot_blob) VALUES (9,'saved','author','forum',11,X'0102')",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            4,
            true,
            TiebaPureDatabase.MIGRATION_3_4,
        ).use { database ->
            database.query(
                "SELECT snapshot_blob,media_mode,media_byte_count,new_reply_count,last_checked_at_ms,metadata_version FROM saved_threads WHERE thread_id = 9",
            ).use { cursor ->
                cursor.moveToFirst()
                assertArrayEquals(byteArrayOf(1, 2), cursor.getBlob(0))
                assertEquals("TextOnly", cursor.getString(1))
                assertEquals(0L, cursor.getLong(2))
                assertEquals(0, cursor.getInt(3))
                assertTrue(cursor.isNull(4))
                assertEquals(0, cursor.getInt(5))
            }
        }
    }

    @Test
    fun migrationFromFourPreservesReadingAndOfflineDataAndAddsLocalLibrary() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL("INSERT INTO browsing_history (thread_id,title,author_name,forum_name,visited_at_ms) VALUES (8,'kept','author','forum',10)")
            execSQL("INSERT INTO reading_positions (thread_id,post_id,floor,updated_at_ms) VALUES (8,'123',7,10)")
            execSQL("INSERT INTO saved_threads (thread_id,title,author_name,forum_name,saved_at_ms,snapshot_blob) VALUES (9,'saved','author','forum',11,X'0102')")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, TiebaPureDatabase.MIGRATION_4_5).use { database ->
            database.query("SELECT title FROM browsing_history WHERE thread_id = 8").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kept", cursor.getString(0))
            }
            database.query("SELECT post_id,floor FROM reading_positions WHERE thread_id = 8").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("123", cursor.getString(0))
                assertEquals(7, cursor.getInt(1))
            }
            database.query("SELECT snapshot_blob FROM saved_threads WHERE thread_id = 9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertArrayEquals(byteArrayOf(1, 2), cursor.getBlob(0))
            }
            listOf("local_subscriptions", "local_bookmarks", "forum_feed_cache").forEach { table ->
                database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
            }
        }
    }

    @Test
    fun migrationFromFiveKeepsLocalLibraryAndAddsFollowing() {
        helper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL("INSERT INTO local_subscriptions VALUES ('android',1,'android','android吧',NULL,1)")
            execSQL("INSERT INTO local_bookmarks VALUES (9,'kept','author','android',1)")
            close()
        }
        helper.runMigrationsAndValidate(DATABASE_NAME, 6, true, TiebaPureDatabase.MIGRATION_5_6).use { db ->
            listOf("local_subscriptions", "local_bookmarks").forEach { table ->
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0))
                }
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO local_user_follows VALUES (42,'name','user','portrait',1)")
            db.execSQL("INSERT INTO user_feed_cache VALUES (42,1,X'0102')")
            db.execSQL("DELETE FROM local_user_follows WHERE user_id=42")
            db.query("SELECT COUNT(*) FROM user_feed_cache").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "draft-migration-test"
    }
}
