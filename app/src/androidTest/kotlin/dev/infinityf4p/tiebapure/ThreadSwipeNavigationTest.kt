package dev.infinityf4p.tiebapure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.navigation.NavHostController
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.*
import androidx.navigation.compose.composable as plainComposable
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import org.junit.Rule
import org.junit.Test

class ThreadSwipeNavigationTest {
    @get:Rule val compose = createComposeRule()
    @SdkSuppress(minSdkVersion = 26)
    @Test fun forwardPreviewSurvivesMemoryTrimUntilGestureEnds() {
        lateinit var nav: NavHostController
        val home = Color(0xFFB93229)
        val detail = Color(0xFF154DB2)
        compose.setContent {
            TiebaPureTheme {
                nav = rememberNavController()
                ThreadSwipeNavigation(nav, "home", Modifier.testTag("swipe-host")) {
                    NavHost(nav, "home", enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                        readerComposable("home") {
                            Box(Modifier.fillMaxSize().background(home)) {
                                Button({ nav.navigate("detail") }) { Text("打开纯色详情") }
                            }
                        }
                        readerComposable("detail") {
                            val close = LocalThreadSwipeBack.current
                            Box(Modifier.fillMaxSize().background(detail)) {
                                Button({ close?.invoke() }) { Text("关闭纯色详情") }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("打开纯色详情").performClick()
        compose.onNodeWithText("关闭纯色详情").performClick()
        compose.onNodeWithText("打开纯色详情").assertIsDisplayed()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(width - 8f, height * .6f)); moveTo(Offset(width * .45f, height * .6f), 300)
        }
        compose.runOnIdle { readerMemoryTrimRequests.tryEmit(Unit) }
        val pixels = compose.onNodeWithTag("swipe-host").captureToImage().toPixelMap()
        assertEquals(home, pixels[pixels.width / 4, pixels.height * 3 / 4])
        assertEquals(detail, pixels[pixels.width * 4 / 5, pixels.height * 3 / 4])
        compose.onNodeWithTag("swipe-host").performTouchInput { up() }
        compose.onNodeWithText("关闭纯色详情").assertIsDisplayed()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height * .6f)); moveTo(Offset(width * .7f, height * .6f), 300); up()
        }
        compose.onNodeWithText("关闭纯色详情").assertIsDisplayed()
        compose.onNodeWithText("关闭纯色详情").performClick()
        compose.onNodeWithText("打开纯色详情").assertIsDisplayed()
        compose.onNodeWithTag("swipe-host").performTouchInput { down(Offset(width - 8f, height * .6f)) }
        compose.runOnIdle { readerMemoryTrimRequests.tryEmit(Unit) }
        compose.onNodeWithTag("swipe-host").performTouchInput {
            moveTo(Offset(width * .25f, height * .6f), 300); up()
        }
        compose.onNodeWithText("打开纯色详情").assertIsDisplayed()
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test fun rootSwitchDuringDragClearsOffsetAndPreview() {
        lateinit var nav: NavHostController
        var root by mutableStateOf("home")
        val target = Color(0xFF187A35)
        compose.setContent {
            TiebaPureTheme {
                nav = rememberNavController()
                ThreadSwipeNavigation(nav, root, Modifier.testTag("swipe-host")) {
                    NavHost(nav, "home", enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                        readerComposable("home") {
                            Box(Modifier.fillMaxSize().background(Color.Red)) {
                                Button({ nav.navigate("detail") }) { Text("进入详情") }
                            }
                        }
                        readerComposable("detail") { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                        readerComposable(RootDestination.Forums.route) { Box(Modifier.fillMaxSize().background(target)) }
                    }
                }
            }
        }
        compose.onNodeWithText("进入详情").performClick()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height * .6f)); moveTo(Offset(width * .25f, height * .6f), 200)
        }
        compose.runOnIdle { root = RootDestination.Forums.route; nav.navigate(root) }
        val pixels = compose.onNodeWithTag("swipe-host").captureToImage().toPixelMap()
        assertEquals(target, pixels[pixels.width / 4, pixels.height / 2])
        assertEquals(target, pixels[pixels.width * 3 / 4, pixels.height / 2])
        compose.onNodeWithTag("swipe-host").performTouchInput { up() }
        compose.onNodeWithTag("swipe-handoff").assertDoesNotExist()
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test fun missingParentPreviewNeverExposesABlankDrag() {
        val detail = Color(0xFF154DB2)
        compose.setContent {
            TiebaPureTheme {
                val nav = rememberNavController()
                ThreadSwipeNavigation(nav, "home", Modifier.testTag("swipe-host")) {
                    NavHost(nav, "home", enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                        plainComposable("home") {
                            Button({ nav.navigate("detail") }) { Text("无父预览进入") }
                        }
                        readerComposable("detail") {
                            val back = LocalThreadSwipeBack.current
                            Box(Modifier.fillMaxSize().background(detail)) {
                                Button({ back?.invoke() }) { Text("正常返回仍可用") }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("无父预览进入").performClick()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height * .6f)); moveTo(Offset(width * .75f, height * .6f), 300)
        }
        val pixels = compose.onNodeWithTag("swipe-host").captureToImage().toPixelMap()
        assertEquals(detail, pixels[pixels.width / 4, pixels.height * 3 / 4])
        compose.onNodeWithTag("swipe-host").performTouchInput { up() }
        compose.onNodeWithText("正常返回仍可用").performClick()
        compose.onNodeWithText("无父预览进入").assertIsDisplayed()
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test fun bottomBarHeightChangesKeepBackAndForwardPreviews() {
        val home = Color(0xFFB93229)
        val detail = Color(0xFF154DB2)
        compose.setContent {
            TiebaPureTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                Scaffold(bottomBar = {
                    if (entry?.destination?.route == "home") Box(Modifier.fillMaxWidth().height(80.dp)
                        .background(Color.Green).testTag("test-root-bar"))
                }) { padding ->
                    ThreadSwipeNavigation(nav, "home", Modifier.padding(padding).testTag("swipe-host")) {
                        NavHost(nav, "home", enterTransition = { androidx.compose.animation.EnterTransition.None },
                            exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                            readerComposable("home") {
                                Box(Modifier.fillMaxSize().background(home)) {
                                    Button({ nav.navigate("detail") }) { Text("底栏进入帖子") }
                                }
                            }
                            readerComposable("detail") {
                                Box(Modifier.fillMaxSize().background(detail)) { Text("无底栏帖子") }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("底栏进入帖子").performClick()
        compose.onNodeWithTag("test-root-bar").assertDoesNotExist()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height * .6f)); moveTo(Offset(width * .7f, height * .6f), 300)
        }
        var pixels = compose.onNodeWithTag("swipe-host").captureToImage().toPixelMap()
        assertEquals(home, pixels[pixels.width / 4, pixels.height / 2])
        compose.onNodeWithTag("swipe-host").performTouchInput { up() }
        compose.onNodeWithTag("test-root-bar").assertIsDisplayed()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(width - 8f, height * .6f)); moveTo(Offset(width * .3f, height * .6f), 300)
        }
        pixels = compose.onNodeWithTag("swipe-host").captureToImage().toPixelMap()
        assertEquals(detail, pixels[pixels.width * 4 / 5, pixels.height / 2])
        compose.onNodeWithTag("swipe-host").performTouchInput { up() }
        compose.onNodeWithText("无底栏帖子").assertIsDisplayed()
        compose.onNodeWithTag("test-root-bar").assertDoesNotExist()
    }

    @Test fun shortDragReboundsAndCommittedBackCanGoForward() {
        compose.setContent {
            TiebaPureTheme {
                val nav = rememberNavController()
                ThreadSwipeNavigation(nav, "home", androidx.compose.ui.Modifier.testTag("swipe-host")) {
                    NavHost(nav, "home",
                        enterTransition = { androidx.compose.animation.EnterTransition.None },
                        exitTransition = { androidx.compose.animation.ExitTransition.None },
                        popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                        popExitTransition = { androidx.compose.animation.ExitTransition.None }) {
                        readerComposable("home") { Button(onClick = { nav.navigate(buildThreadRoute(1)) }) { Text("打开测试帖子") } }
                        readerComposable("thread/{threadId}?postId={postId}&initialDestination={initialDestination}") {
                            LazyColumn(androidx.compose.ui.Modifier.testTag("test-thread-list")) {
                                item { Text("测试帖子正文") }
                                items(30) { Text("楼层$it", androidx.compose.ui.Modifier.height(60.dp)) }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("打开测试帖子").performClick()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height / 2f)); moveTo(Offset(width * 0.12f, height / 2f), 200); up()
        }
        compose.waitForIdle()
        compose.onNodeWithText("测试帖子正文").assertIsDisplayed()
        compose.onNodeWithTag("test-thread-list").performScrollToIndex(18)
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(8f, height / 2f)); moveTo(Offset(width * 0.85f, height / 2f), 400); up()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("打开测试帖子").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("swipe-handoff").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("打开测试帖子").assertIsDisplayed()
        compose.onNodeWithTag("swipe-host").performTouchInput {
            down(Offset(width - 8f, height / 2f)); moveTo(Offset(width * 0.1f, height / 2f), 400); up()
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("楼层17").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("swipe-handoff").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("楼层17").assertIsDisplayed()
    }
}
