package dev.infinityf4p.tiebapure

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

class ReaderNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun allRootsAndSettingsSubpagesHaveWorkingReturnPaths() {
        listOf("推荐", "订阅", "进吧", "收藏", "设置").forEach { title ->
            compose.onNode(hasText(title) and isSelectable()).performClick().assertIsSelected()
        }
        listOf("阅读设置", "屏蔽设置", "存储与缓存", "关于与开源许可").forEach { title ->
            compose.onNodeWithText(title).performScrollTo().performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNode(hasText("设置") and isSelectable()).assertIsSelected()
        }
        compose.onNodeWithText("离线保存的帖子").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("设置") and isSelectable()).assertIsSelected()
    }

    @Test
    fun rootsKeepTheirOwnScrollWithoutCrossRootReturnButton() {
        val library = (compose.activity.application as TiebaPureApplication).container.localLibrary
        val prefix = "恢复${System.nanoTime()}"
        val forums = (0..15).map { dev.infinityf4p.tiebapure.core.model.Forum(100L + it, "$prefix-$it", "$prefix-${it}吧") }
        kotlinx.coroutines.runBlocking { forums.forEach { library.subscribe(it) } }
        try {
            compose.onNodeWithText("进吧").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithText("已订阅贴吧").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("local-forum-list").performScrollToNode(hasText(forums.first().displayName))
            compose.onNodeWithText(forums.first().displayName).assertIsDisplayed()
            compose.onNode(hasText("设置") and isSelectable()).performClick()
            compose.onNodeWithContentDescription("返回上个栏目").assertDoesNotExist()
            compose.onNode(hasText("进吧") and isSelectable()).performClick()
            compose.onNode(hasText("进吧") and isSelectable()).assertIsSelected()
            compose.onNodeWithText(forums.first().displayName).assertIsDisplayed()
        } finally {
            kotlinx.coroutines.runBlocking { forums.forEach { library.unsubscribe(it.name) } }
        }
    }

    @Test
    fun localLibraryIsReachableWithoutAnAccount() {
        compose.onNodeWithText("收藏").performClick()
        compose.onNodeWithText("离线保存").assertIsDisplayed()
        compose.onNodeWithContentDescription("浏览历史").performClick()
        compose.onNodeWithText("浏览历史").assertIsDisplayed()
        compose.onNodeWithText("登录").assertDoesNotExist()
    }

    @Test
    fun localSubscriptionSurvivesActivityRecreationAndCanBeRemoved() {
        val name = "ReaderLocalTest${System.nanoTime()}"
        val library = (compose.activity.application as TiebaPureApplication).container.localLibrary
        // Seed a verified fixture locally; this navigation test must not depend on live Tieba.
        kotlinx.coroutines.runBlocking {
            library.subscribe(dev.infinityf4p.tiebapure.core.model.Forum(123, name, "${name}吧"))
        }
        compose.onNodeWithText("进吧").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("${name}吧").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("${name}吧").assertIsDisplayed()
        compose.onNodeWithText("管理").performClick()
        val key = "local-subscription-${name.lowercase()}"
        compose.onNode(hasText("取消订阅") and hasAnyAncestor(hasTestTag(key))).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("${name}吧").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun subscriptionFeedAndForumDirectoryAreSeparateRoots() {
        compose.onNodeWithText("订阅").performClick()
        compose.onNodeWithContentDescription("进吧与管理订阅").assertIsDisplayed().performClick()
        compose.onNodeWithTag("forum-search-entry").assertIsDisplayed().performClick()
        compose.onNodeWithTag("search-input").assertIsDisplayed()
        compose.onNodeWithText("进入贴吧").assertDoesNotExist()
        compose.onNodeWithText("添加订阅").assertDoesNotExist()
    }

    @Test
    fun settingsExposeReadingPreferencesAndPersistDarkAppearance() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("允许发帖").assertDoesNotExist()
        compose.onNodeWithText("允许回帖").assertDoesNotExist()
        compose.onNodeWithText("允许点赞").assertDoesNotExist()
        compose.onNode(hasText("深色") and isSelectable()).performClick()
        compose.onNode(hasText("深色") and isSelectable()).assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("深色") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("阅读设置").performClick()
        compose.onNodeWithText("字体").performScrollTo().assertIsDisplayed()
    }
}
