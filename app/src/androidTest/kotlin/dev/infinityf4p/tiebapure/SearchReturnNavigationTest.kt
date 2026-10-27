package dev.infinityf4p.tiebapure

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import org.junit.Rule
import org.junit.Test

class SearchReturnNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typedSearchArgumentsNeverTrapToolbarReturn() {
        var dispatcher: OnBackPressedDispatcher? = null
        compose.setContent {
            val owner = LocalOnBackPressedDispatcherOwner.current
            SideEffect { dispatcher = owner?.onBackPressedDispatcher }
            val controller = rememberNavController()
            TiebaPureTheme {
                ThreadSwipeNavigation(controller, "home") {
                    NavHost(controller, "home") {
                        readerComposable("home") {
                            Column {
                                Text("首页")
                                TextButton(onClick = { controller.navigate("search?query=变&forumFirst=true") }) { Text("搜吧") }
                                TextButton(onClick = { controller.navigate("search?query=变&forumFirst=false") }) { Text("全站搜索") }
                            }
                        }
                        readerComposable("search?query={query}&forumFirst={forumFirst}", arguments = listOf(
                            navArgument("query") { type = NavType.StringType; nullable = true; defaultValue = null },
                            navArgument("forumFirst") { type = NavType.BoolType; defaultValue = false },
                        )) {
                            val back = LocalThreadSwipeBack.current
                            TextButton(onClick = { back?.invoke() }) { Text("返回搜索来源") }
                        }
                    }
                }
            }
        }
        listOf("搜吧", "全站搜索").forEach { entry ->
            compose.onNodeWithText(entry).performClick()
            compose.onNodeWithText("返回搜索来源").performClick()
            compose.onNodeWithText("首页").assertIsDisplayed()
        }
        compose.onNodeWithText("搜吧").performClick()
        compose.runOnIdle { requireNotNull(dispatcher).onBackPressed() }
        compose.onNodeWithText("首页").assertIsDisplayed()
    }
}
