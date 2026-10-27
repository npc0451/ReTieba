package dev.infinityf4p.tiebapure

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.infinityf4p.tiebapure.core.data.LocalLibraryRepository
import dev.infinityf4p.tiebapure.core.data.TiebaPureDatabase
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import dev.infinityf4p.tiebapure.core.model.Forum
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LocalForumsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val database = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, TiebaPureDatabase::class.java,
    ).build()
    private val library = LocalLibraryRepository(database)
    @After fun close() { database.close() }

    @Test fun recentLongPressCanCancelThenRemoveWithoutUnsubscribing() {
        val forum = Forum(42, "安卓", "安卓吧")
        val recent = androidx.compose.runtime.mutableStateOf(listOf(forum))
        var opened = false
        runBlocking {
            library.subscribe(forum)
            database.recentForumDao().upsert(checkNotNull(recentForumEntity(forum, visitedAtMilliseconds = 1)))
        }
        compose.setContent {
            TiebaPureTheme {
                LocalForumsRoute(library, onOpenForum = { opened = true }, onSearch = {},
                    recentForums = recent.value, onRemoveRecent = {
                        database.recentForumDao().remove(checkNotNull(recentForumEntity(it, visitedAtMilliseconds = 0)).normalizedName)
                        recent.value = emptyList()
                    })
            }
        }
        compose.onNodeWithTag("recent-forum-安卓").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertFalse(opened) }
        assertEquals(1, runBlocking { database.recentForumDao().observeAll().first() }.size)
        compose.onNodeWithTag("recent-forum-安卓").performTouchInput { longClick() }
        compose.onNodeWithText("移除记录").performClick()
        compose.waitUntil(5000) { recent.value.isEmpty() }
        assertTrue(runBlocking { database.recentForumDao().observeAll().first() }.isEmpty())
        assertEquals(1, runBlocking { library.subscriptions.first() }.size)
    }

    @Test fun searchEntryOpensSearchWithoutAddingSubscriptions() {
        var searches = 0
        compose.setContent {
            TiebaPureTheme {
                LocalForumsRoute(library, onOpenForum = {}, onSearch = { searches++ })
            }
        }
        compose.onNodeWithText("输入贴吧名称").assertDoesNotExist()
        compose.onNodeWithText("进入贴吧").assertDoesNotExist()
        compose.onNodeWithText("添加订阅").assertDoesNotExist()
        compose.onNodeWithTag("forum-search-entry").performClick()
        compose.runOnIdle { assertEquals(1, searches) }
        assertTrue(runBlocking { library.subscriptions.first() }.isEmpty())
    }

    @Test fun subscribedForumStillOpensDirectly() {
        val forum = Forum(42, "安卓", "安卓吧")
        runBlocking { library.subscribe(forum) }
        var opened: Forum? = null
        compose.setContent {
            TiebaPureTheme { LocalForumsRoute(library, onOpenForum = { opened = it }, onSearch = {}) }
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("local-subscription-安卓").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("local-subscription-安卓").performClick()
        compose.runOnIdle { assertEquals(forum, opened) }
    }
}
