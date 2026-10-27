package dev.infinityf4p.tiebapure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReaderNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowBarKeepsFiveAccessibleTabsAtDoubleFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                TiebaPureTheme(darkTheme = true) {
                    var selected by remember { mutableStateOf(RootDestination.Home) }
                    Box(Modifier.width(320.dp)) {
                        RootNavigationBar(selected) { selected = it }
                    }
                }
            }
        }
        val tabs = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        tabs.assertCountEquals(5)
        tabs.fetchSemanticsNodes().forEach {
            assertTrue(it.boundsInRoot.width >= 48f)
            assertTrue(it.boundsInRoot.height >= 48f)
        }
        compose.onNodeWithText("推荐").assertIsSelected()
        compose.onNodeWithText("设置").performClick().assertIsSelected()
        compose.onNodeWithText("推荐").assertIsNotSelected()
    }
}
