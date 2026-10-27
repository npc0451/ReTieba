package dev.infinityf4p.tiebapure

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal fun swipeCommits(distance: Float, width: Float, minimum: Float, velocity: Float = 0f): Boolean =
    width > 0 && (abs(distance) >= maxOf(minimum, width * 0.32f) ||
        (abs(distance) >= minimum && distance * velocity > 0 && abs(velocity) >= width * 1.5f))

internal val LocalThreadSwipeBack = staticCompositionLocalOf<(() -> Unit)?> { null }
private val LocalSwipeRenderer = staticCompositionLocalOf<SwipeRenderer?> { null }

private class RenderedPage(val layer: GraphicsLayer) {
    var size = IntSize.Zero
    val estimatedBytes: Long get() = size.width.toLong() * size.height * 4
}

private class SwipeRenderer(private val graphics: GraphicsContext) {
    private val pages = linkedMapOf<String, RenderedPage>()
    var awaitingDraw: Pair<String, CompletableDeferred<Unit>>? = null
    fun page(id: String): RenderedPage = pages.getOrPut(id) { RenderedPage(graphics.createGraphicsLayer()) }
    fun existing(id: String?): RenderedPage? = pages[id]?.takeIf { it.size != IntSize.Zero }
    fun drawn(id: String) { awaitingDraw?.takeIf { it.first == id }?.second?.complete(Unit) }
    fun retain(ids: Set<RenderedPage>) {
        pages.entries.removeAll { (_, page) ->
            (page !in ids).also { remove -> if (remove) graphics.releaseGraphicsLayer(page.layer) }
        }
    }
    fun release() { pages.values.forEach { graphics.releaseGraphicsLayer(it.layer) }; pages.clear() }
}

/** Record the destination itself, so an outgoing host frame cannot signal readiness for a new page. */
internal fun NavGraphBuilder.readerComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = arguments) { entry ->
        val renderer = LocalSwipeRenderer.current
        val page = remember(renderer, entry.id) { renderer?.page(entry.id) }
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).drawWithContent {
            if (page == null) drawContent() else {
                page.layer.record { this@drawWithContent.drawContent() }
                page.size = IntSize(size.width.toInt(), size.height.toInt())
                drawLayer(page.layer)
                renderer?.drawn(entry.id)
            }
        }) { content(entry) }
    }
}

private class ThreadSwipeState {
    var parents by mutableStateOf<Map<String, RenderedPage>>(emptyMap())
    fun rememberParent(id: String?, page: RenderedPage?) {
        if (id != null && page != null) {
            val candidates = parents - id + (id to page)
            val keys = retainedPreviewKeys(candidates.map { it.key to it.value.estimatedBytes })
            parents = candidates.filterKeys { it in keys }
        }
    }
    var threadPage by mutableStateOf<RenderedPage?>(null)
    var parentEntryId by mutableStateOf<String?>(null)
    var threadRoute by mutableStateOf<String?>(null)
    var closed by mutableStateOf(false)
    var restoring = false
    var closing = false
}

/** Root changes reset gesture state without recreating the NavHost or its saved scroll positions. */
@Composable
internal fun ThreadSwipeNavigation(
    controller: NavHostController,
    rootScope: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val history = remember(controller, rootScope) { ThreadSwipeState() }
    val graphics = LocalGraphicsContext.current
    val renderer = remember(graphics, controller, rootScope) { SwipeRenderer(graphics) }
    val scope = rememberCoroutineScope()
    val latestRootScope by rememberUpdatedState(rootScope)
    val entry by controller.currentBackStackEntryAsState()
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val minimum = with(density) { 72.dp.toPx() }
    val windowShape = LocalWindowInfo.current.containerSize
    var previousWindowShape by remember { mutableStateOf(windowShape) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val width = viewportSize.width.toFloat()
    var offset by remember(controller, rootScope) { mutableFloatStateOf(0f) }
    val direction by remember(controller, rootScope) { derivedStateOf { offset.compareTo(0f) } }
    var settling by remember(controller, rootScope) { mutableStateOf(false) }
    var handoffPage by remember(controller, rootScope) { mutableStateOf<RenderedPage?>(null) }
    var gestureJob by remember(controller, rootScope) { mutableStateOf<Job?>(null) }
    fun isDetail(route: String?) = route != null && route != "detail-empty" && RootDestination.entries.none { it.route == route }
    val canBack = isDetail(entry?.destination?.route) && controller.previousBackStackEntry != null
    val canForward = history.closed && history.parentEntryId == entry?.id && history.threadRoute != null

    // Only cached render lists are retained; no periodic GPU-to-bitmap copies run on root pages.
    LaunchedEffect(renderer, entry?.id, history.parents, history.threadPage, handoffPage) {
        withFrameNanos { }
        renderer.retain((history.parents.values + listOfNotNull(history.threadPage, handoffPage,
            controller.currentBackStackEntry?.id?.let(renderer::page))).toSet())
    }

    LaunchedEffect(renderer) {
        readerMemoryTrimRequests.collect {
            snapshotFlow { settling || direction != 0 }.first { active -> !active }
            history.parents = emptyMap()
            history.threadPage = null
            renderer.retain(listOfNotNull(controller.currentBackStackEntry?.id?.let(renderer::page)).toSet())
        }
    }

    suspend fun awaitDestinationDraw() {
        val id = controller.currentBackStackEntry?.id ?: return
        val ready = CompletableDeferred<Unit>()
        renderer.awaitingDraw = id to ready
        try {
            withTimeoutOrNull(1_000) { ready.await(); withFrameNanos { } }
        } finally { renderer.awaitingDraw = null }
    }

    suspend fun closeThread(animateHandoff: Boolean = false) {
        if (history.closing) return
        val current = controller.currentBackStackEntry ?: return
        val previous = controller.previousBackStackEntry ?: return
        settling = true
        history.closing = true
        try {
            if (history.closed) history.threadRoute?.let { controller.clearBackStack(it) }
            history.threadRoute = navigationRestoreRoute(current.destination.route,
                current.arguments?.keySet()?.associateWith { current.arguments?.get(it) }.orEmpty())
            history.parentEntryId = previous.id
            history.threadPage = renderer.existing(current.id)
            handoffPage = if (animateHandoff) history.parents[current.id] else null
            offset = 0f
            history.closed = try {
                history.threadRoute?.let { controller.popBackStack(it, inclusive = true, saveState = true) } == true
            } catch (_: IllegalArgumentException) { false }
            if (!history.closed) {
                history.threadRoute = null
                history.threadPage = null
                controller.popBackStack()
            }
            if (handoffPage != null) awaitDestinationDraw()
        } finally { history.closing = false; handoffPage = null; settling = false }
    }

    DisposableEffect(controller, renderer) {
        var observedId = controller.currentBackStackEntry?.id
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { nav, destination, _ ->
            if (nav.currentBackStackEntry?.id == observedId) return@OnDestinationChangedListener
            observedId = nav.currentBackStackEntry?.id
            if (history.closing) return@OnDestinationChangedListener
            if (!history.restoring) {
                gestureJob?.cancel()
                offset = 0f; handoffPage = null; settling = false
            }
            if (isDetail(destination.route)) {
                if (history.restoring) history.restoring = false else {
                    if (history.closed) history.threadRoute?.let { controller.clearBackStack(it) }
                    history.threadRoute = null
                    history.closed = false
                    history.threadPage = null
                    history.parentEntryId = nav.previousBackStackEntry?.id
                    history.rememberParent(nav.currentBackStackEntry?.id, renderer.existing(nav.previousBackStackEntry?.id))
                }
            }
        }
        controller.addOnDestinationChangedListener(listener)
        onDispose {
            gestureJob?.cancel()
            controller.removeOnDestinationChangedListener(listener)
            renderer.release()
            if (history.closed) history.threadRoute?.let { controller.clearBackStack(it) }
        }
    }

    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.background).onSizeChanged {
        // Bottom navigation changes content height during ordinary navigation, not window geometry.
        if (viewportSize != IntSize.Zero && (viewportSize.width != it.width || previousWindowShape != windowShape)) {
            gestureJob?.cancel()
            offset = 0f; settling = false; handoffPage = null
            history.parents = emptyMap(); history.threadPage = null
        }
        if (!settling) offset = 0f
        previousWindowShape = windowShape
        viewportSize = it
    }.pointerInput(renderer, entry?.id, viewportSize, canBack, canForward, settling) {
        if ((!canBack && !canForward) || settling) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val gestureEntryId = controller.currentBackStackEntry?.id
            val backward = canBack && down.position.x <= edge
            val forward = canForward && down.position.x >= size.width - edge
            val targetPage = if (backward) history.parents[gestureEntryId] else history.threadPage
            // Missing/evicted or differently oriented previews never start a blank-window gesture.
            if ((!backward && !forward) || targetPage == null || targetPage.size.width != viewportSize.width) return@awaitEachGesture
            val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var delta = Offset.Zero
            var accepted = false
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (event.changes.count { it.pressed } > 1 || change.isConsumed) { cancelled = true; break }
                if (!change.pressed) break
                delta += change.position - change.previousPosition
                if (!accepted) {
                    if (abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) { cancelled = true; break }
                    if (abs(delta.x) <= viewConfiguration.touchSlop || abs(delta.x) < abs(delta.y) * 1.4f) continue
                    if ((backward && delta.x < 0) || (forward && delta.x > 0)) { cancelled = true; break }
                    // A trim may have evicted the preview after pointer-down but before touch slop.
                    val available = if (backward) history.parents[gestureEntryId] else history.threadPage
                    if (available !== targetPage) { cancelled = true; break }
                    accepted = true
                }
                change.consume()
                offset = if (backward) delta.x.coerceIn(0f, width) else delta.x.coerceIn(-width, 0f)
            }
            if (accepted) {
                settling = true
                val velocity = velocityTracker.calculateVelocity().x
                gestureJob = scope.launch {
                    try {
                        val commit = !cancelled && swipeCommits(offset, width, minimum, velocity)
                        val target = if (!commit) 0f else if (backward) width else -width
                        animate(offset, target, initialVelocity = velocity,
                            animationSpec = spring(dampingRatio = 1f, stiffness = 350f)) { value, _ -> offset = value }
                        if (latestRootScope != rootScope || controller.currentBackStackEntry?.id != gestureEntryId) return@launch
                        if (commit) {
                            if (backward) closeThread(animateHandoff = true) else {
                                val parent = renderer.existing(gestureEntryId)
                                handoffPage = history.threadPage
                                offset = 0f
                                history.restoring = true
                                history.threadRoute?.let { controller.navigate(it) { restoreState = true; launchSingleTop = true } }
                                history.rememberParent(controller.currentBackStackEntry?.id, parent)
                                history.closed = false
                                awaitDestinationDraw()
                            }
                        }
                    } finally { offset = 0f; handoffPage = null; settling = false }
                }
            }
        }
    }) {
        if (direction > 0) history.parents[entry?.id]?.let { page ->
            Box(Modifier.fillMaxSize().drawWithContent { drawLayer(page.layer) })
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = if (offset >= 0) offset else offset * .15f
            shadowElevation = if (offset > 0) 8.dp.toPx() else 0f
        }) {
            CompositionLocalProvider(LocalSwipeRenderer provides renderer,
                LocalThreadSwipeBack provides if (canBack) ({
                    if (!settling) scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { closeThread() }; Unit
                }) else null,
                dev.infinityf4p.tiebapure.core.designsystem.LocalReaderTransitionActive provides (settling || direction != 0)) {
                content()
            }
        }
        if (direction < 0) history.threadPage?.let { page ->
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = width + offset }
                .drawWithContent { drawLayer(page.layer) })
        }
        if (settling) Box(Modifier.fillMaxSize().testTag("swipe-handoff").pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        }.drawWithContent { handoffPage?.let { drawLayer(it.layer) } })
    }
    BackHandler(enabled = canBack) {
        if (!settling) scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { closeThread() }
    }
}
