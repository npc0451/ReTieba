package dev.infinityf4p.tiebapure.core.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.infinityf4p.tiebapure.core.model.Forum
import dev.infinityf4p.tiebapure.core.model.ThreadSummary
import dev.infinityf4p.tiebapure.core.model.UserSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalLibraryDatabaseTest {
    @Test
    fun cacheRowEvictionKeepsOtherSourcesAndRelationships() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TiebaPureDatabase::class.java).build()
        try {
            val library = LocalLibraryRepository(db)
            library.subscribe(Forum(1, "one", "one吧")); library.subscribe(Forum(2, "two", "two吧"))
            val people = LocalUserFollowsRepository(db)
            people.follow(UserSummary(1, "one", "one", "")); people.follow(UserSummary(2, "two", "two", ""))
            val dao = db.localLibraryDao(); val userDao = db.localUserFollowsDao()
            dao.putFeedCache(ForumFeedCacheEntity("one", 1, byteArrayOf(1)))
            dao.putFeedCache(ForumFeedCacheEntity("two", 2, byteArrayOf(2)))
            userDao.putCache(UserFeedCacheEntity(1, 1, byteArrayOf(1)))
            userDao.putCache(UserFeedCacheEntity(2, 2, byteArrayOf(2)))
            assertEquals(2, dao.allFeedCaches().size); assertEquals(2, userDao.allCaches().size)
            dao.deleteFeedCache("one"); userDao.deleteCache(1)
            assertEquals(listOf("two"), dao.allFeedCaches().map { it.normalizedName })
            assertEquals(listOf(2L), userDao.allCaches().map { it.userId })
            assertEquals(2, library.subscriptions.first().size); assertEquals(2, people.users.first().size)
        } finally { db.close() }
    }
    @Test
    fun followingIsIdempotentAndUnfollowOnlyDeletesItsCache() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TiebaPureDatabase::class.java).build()
        try {
            val follows = LocalUserFollowsRepository(db)
            val user = UserSummary(42, "name", "用户", "portrait")
            follows.follow(user)
            follows.follow(user)
            assertEquals(1, follows.users.first().size)
            db.localUserFollowsDao().putCache(UserFeedCacheEntity(42, 1, byteArrayOf(1)))
            LocalLibraryRepository(db).subscribe(Forum(1, "android", "android吧"))
            follows.unfollow(42)
            assertEquals(0, follows.users.first().size)
            assertNull(db.localUserFollowsDao().cache(42))
            assertEquals(1, db.localLibraryDao().observeSubscriptions().first().size)
        } finally { db.close() }
    }

    @Test
    fun clearingSummaryCacheKeepsSubscriptionsAndBookmarks() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TiebaPureDatabase::class.java).build()
        try {
            val library = LocalLibraryRepository(db)
            library.subscribe(Forum(1, "android", "android吧"))
            db.localLibraryDao().insertBookmark(LocalBookmarkEntity(10, "帖子", "作者", "android", 1))
            db.localLibraryDao().putFeedCache(ForumFeedCacheEntity("android", 1, byteArrayOf(1, 2)))
            assertEquals(2L, db.localLibraryDao().feedCacheBytes())
            db.localLibraryDao().clearFeedCache()
            assertEquals(0L, db.localLibraryDao().feedCacheBytes())
            assertEquals(1, library.subscriptions.first().size)
            assertEquals(1, library.bookmarks.first().size)
        } finally { db.close() }
    }
    @Test
    fun duplicateSubscriptionsPreserveMetadataAndRemovalClearsOnlyTheirCache() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TiebaPureDatabase::class.java).build()
        try {
            var clock = 10L
            val library = LocalLibraryRepository(db) { clock++ }
            library.subscribe(Forum(0, " Android吧 ", "Android吧"))
            library.subscribe(Forum(42, "android", "android吧", "https://example.com/avatar"))
            db.localLibraryDao().putFeedCache(ForumFeedCacheEntity("android", 10, byteArrayOf(1)))
            library.subscribe(Forum(0, "Android吧", "Android吧"))
            val subscriptions = library.subscriptions.first()
            assertEquals(1, subscriptions.size)
            assertEquals(42L, subscriptions.single().forumId)
            assertEquals("https://example.com/avatar", subscriptions.single().avatarUrl)
            assertEquals(10L, subscriptions.single().subscribedAtMilliseconds)
            assertNotNull(db.localLibraryDao().feedCache("android"))
            library.unsubscribe("ANDROID吧")
            assertEquals(0, library.subscriptions.first().size)
            assertNull(db.localLibraryDao().feedCache("android"))
        } finally {
            db.close()
        }
    }

    @Test
    fun bookmarksAreIdempotentAndDoNotDeleteOfflineSnapshots() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            TiebaPureDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.execSQL("INSERT INTO saved_threads (thread_id,title,author_name,forum_name,saved_at_ms,snapshot_blob) VALUES (9,'saved','author','forum',11,X'0102')")
            val library = LocalLibraryRepository(db) { 20L }
            val thread = ThreadSummary(9, title = "saved", author = UserSummary(1,"a","作者",""),
                replyCount = 0, viewCount = 0, blocks = emptyList())
            library.bookmark(thread)
            library.bookmark(thread)
            assertEquals(1, library.bookmarks.first().size)
            library.removeBookmark(9)
            assertEquals(0, library.bookmarks.first().size)
            assertEquals(listOf(9L), db.savedThreadDao().threadIds())
            library.bookmark(thread)
            db.savedThreadDao().remove(9)
            assertEquals(listOf(9L), library.bookmarks.first().map { it.threadId })
        } finally {
            db.close()
        }
    }
}
