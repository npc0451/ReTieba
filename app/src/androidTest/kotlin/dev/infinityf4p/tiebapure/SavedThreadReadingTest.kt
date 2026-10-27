package dev.infinityf4p.tiebapure

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import dev.infinityf4p.tiebapure.core.model.*
import org.junit.Rule
import org.junit.Test

class SavedThreadReadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savingNoticeCanHideSurviveRestorationAndReopenFromMenu() {
        val author = UserSummary(id = 1, name = "author", displayName = "作者", portrait = "")
        val main = Post(id = 11uL, threadId = 100, floor = 1, author = author,
            ipAddress = null, createdAtEpochSeconds = null, subpostCount = 0, likeCount = 0, previewSubposts = emptyList(),
            blocks = listOf(ContentBlock.Text("离线正文保留")))
        val snapshot = SavedThreadSnapshot(
            thread = ThreadSummary(id = 100, title = "离线测试", author = author, forumName = "测试",
                replyCount = 0, viewCount = 0, blocks = main.blocks),
            forum = Forum(9, "测试", "测试吧"),
            posts = listOf(SavedThreadPostSnapshot(main, emptyList())), savedAtMilliseconds = 1_700_000_000_000,
            replyCaptureComplete = false,
        )
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TiebaPureTheme {
                SavedThreadDetailScreen(snapshot, ReadingPreferences(), onBack = {}, onForumClick = {},
                    onUserClick = {}, onLinkClick = {}, onShare = {}, onDownloadImage = {}, onSaveImage = null,
                    modifier = Modifier)
            }
        }
        val notice = compose.onNodeWithTag("saved-thread-info").assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
        org.junit.Assert.assertEquals("收起保存信息", notice.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].label)
        notice.performClick()
        compose.onNodeWithTag("saved-thread-info").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("saved-thread-info").assertDoesNotExist()
        compose.onNodeWithText("离线正文保留").assertIsDisplayed()
        compose.onNodeWithTag("thread-more-actions").performClick()
        compose.onNodeWithText("查看保存信息").performClick()
        compose.onNodeWithTag("saved-thread-info").assertIsDisplayed()
        compose.onNodeWithText("部分回复未保存", substring = true).assertIsDisplayed()
    }
}
