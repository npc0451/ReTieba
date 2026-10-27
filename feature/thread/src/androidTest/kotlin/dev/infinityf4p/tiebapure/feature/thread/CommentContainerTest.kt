package dev.infinityf4p.tiebapure.feature.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CommentContainerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewAndExpandedRepliesAlignAtLargeFontOnNarrowScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                TiebaPureTheme(darkTheme = true) {
                    Column(Modifier.width(320.dp)) {
                        CommentContainer { Text("主评论", Modifier.testTag("parent")) }
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            CommentContainer(inset = 0.dp) {
                                Text("楼中楼预览", Modifier.testTag("preview"))
                            }
                        }
                        CommentContainer {
                            Text("展开后的楼中楼回复内容", Modifier.testTag("expanded"))
                        }
                    }
                }
            }
        }
        val parent = compose.onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
        val preview = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
        val expanded = compose.onNodeWithTag("expanded").fetchSemanticsNode().boundsInRoot
        assertEquals(preview.left, expanded.left, 0.5f)
        assertEquals(parent.left, expanded.left, 0.5f)
        assertTrue(expanded.right <= 304.5f)
        compose.onNodeWithTag("expanded").assertHasNoClickAction()
    }
}
