package dev.infinityf4p.tiebapure.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.infinityf4p.tiebapure.core.designsystem.ReaderCard
import dev.infinityf4p.tiebapure.core.designsystem.ReaderInteractionStats
import dev.infinityf4p.tiebapure.core.designsystem.ThreadMediaPreview
import dev.infinityf4p.tiebapure.core.designsystem.TiebaPureTheme
import dev.infinityf4p.tiebapure.core.designsystem.compactInteractionCount
import dev.infinityf4p.tiebapure.core.media.AvatarImage
import dev.infinityf4p.tiebapure.core.media.RemoteImage
import dev.infinityf4p.tiebapure.core.model.ContentBlock
import dev.infinityf4p.tiebapure.core.model.Forum
import dev.infinityf4p.tiebapure.core.model.ReaderMediaLoadingPolicy
import dev.infinityf4p.tiebapure.core.model.ThreadSummary
import dev.infinityf4p.tiebapure.core.model.UserSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    callbacks: HomeCallbacks = HomeCallbacks(),
    programmaticRefreshRequest: Long = 0L,
    mediaLoadingPolicy: ReaderMediaLoadingPolicy = ReaderMediaLoadingPolicy.Automatic,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        callbacks = callbacks,
        onRefresh = { viewModel.refresh() },
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retry,
        programmaticRefreshRequest = programmaticRefreshRequest,
        mediaLoadingPolicy = mediaLoadingPolicy,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    callbacks: HomeCallbacks,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit = onRefresh,
    programmaticRefreshRequest: Long = 0L,
    mediaLoadingPolicy: ReaderMediaLoadingPolicy = ReaderMediaLoadingPolicy.Automatic,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val topState = rememberTopAppBarState()
    val topBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topState,
        canScroll = { uiState.threads.isNotEmpty() && !uiState.isInitialLoading && !uiState.isRefreshing })
    LaunchedEffect(uiState.isRefreshing, uiState.threads.isEmpty()) {
        if (uiState.isRefreshing || uiState.threads.isEmpty()) topState.heightOffset = 0f
    }
    val pullToRefreshState = rememberPullToRefreshState()
    val retainedRefreshOffset = remember { Animatable(0f) }
    var handledRefreshRequest by remember { mutableLongStateOf(programmaticRefreshRequest) }
    var handledRefreshCommit by remember { mutableLongStateOf(uiState.refreshCommitVersion) }
    val gestureRefreshOffset = HomeRefreshMotionPolicy.gestureContentOffsetDp(
        pullToRefreshState.distanceFraction,
    )
    LaunchedEffect(uiState.isRefreshing) {
        if (uiState.isRefreshing) {
            retainedRefreshOffset.snapTo(maxOf(retainedRefreshOffset.value, gestureRefreshOffset))
            retainedRefreshOffset.animateTo(
                HomeRefreshMotionPolicy.heldContentDistanceDp,
                tween(
                    HomeRefreshMotionPolicy.settleDurationMillis,
                    easing = LinearOutSlowInEasing,
                ),
            )
        } else {
            retainedRefreshOffset.animateTo(
                0f,
                tween(
                    HomeRefreshMotionPolicy.reboundDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
    LaunchedEffect(programmaticRefreshRequest) {
        if (programmaticRefreshRequest == handledRefreshRequest) return@LaunchedEffect
        handledRefreshRequest = programmaticRefreshRequest
        if (uiState.isInitialLoading || uiState.isRefreshing) return@LaunchedEffect
        if (listState.layoutInfo.totalItemsCount > 0) {
            if (listState.firstVisibleItemIndex > HomeRefreshMotionPolicy.animatedScrollItemLimit) {
                listState.scrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
        onRefresh()
    }
    LaunchedEffect(uiState.refreshCommitVersion) {
        if (uiState.refreshCommitVersion == handledRefreshCommit) return@LaunchedEffect
        handledRefreshCommit = uiState.refreshCommitVersion
        // A stable item key otherwise keeps the old first post anchored after new rows are prepended.
        listState.requestScrollToItem(0)
    }
    val refreshContentOffset = maxOf(gestureRefreshOffset, retainedRefreshOffset.value)
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(topBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { HomeTopBar(callbacks.onOpenSearch, topBehavior) },
    ) { topPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(topPadding)
                .testTag("home-feed"),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = refreshContentOffset.dp)
                    .testTag("home-refresh-content"),
            ) {
                when {
                    uiState.isInitialLoading && uiState.threads.isEmpty() -> CenteredProgress("正在加载帖子")
                    uiState.showsEmptyPageContinuation -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                    ) {
                        item(key = "empty-page-message") {
                            EmptyPageMessage(
                                title = if (uiState.errorMessage == null) "暂无推荐" else "加载失败",
                                message = uiState.errorMessage ?: "当前页暂无可显示内容。",
                            )
                        }
                        item(key = "empty-page-continuation") {
                            EmptyPageContinuation(
                                isLoading = uiState.isRefreshing || uiState.isLoadingMore,
                                error = uiState.errorMessage,
                                onRetry = onRetry,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                    uiState.errorMessage != null && uiState.threads.isEmpty() -> CenteredMessage(
                        title = "加载失败",
                        message = uiState.errorMessage,
                        action = "重试",
                        onAction = onRetry,
                    )
                    uiState.threads.isEmpty() -> CenteredMessage(
                        title = "暂无推荐",
                        message = "下拉即可刷新推荐帖子。",
                        action = null,
                        onAction = {},
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home-feed-list"),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        itemsIndexed(
                            items = uiState.threads,
                            key = { _, thread -> thread.id },
                        ) { index, thread ->
                            HomeThreadRow(
                                thread = thread,
                                callbacks = callbacks,
                                mediaLoadingPolicy = mediaLoadingPolicy,
                                modifier = Modifier
                                    .animateItem(),
                            )
                            if (index >= uiState.threads.lastIndex - 4 && uiState.hasMore) {
                                LaunchedEffect(thread.id) { onLoadMore() }
                            }
                        }
                        item(key = "footer") {
                            FeedFooter(
                                isLoading = uiState.isLoadingMore,
                                error = uiState.errorMessage,
                                hasMore = uiState.hasMore,
                                onRetry = onRetry,
                                onLoadMore = onLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(onSearch: () -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
    TopAppBar(modifier = Modifier.testTag("home-top-bar"), title = {
        Surface(onClick = onSearch, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("home-search"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("搜索贴吧、帖子", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, expandedHeight = 56.dp, windowInsets = WindowInsets(0,0,0,0), scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface))
}

@Composable
fun HomeThreadRow(
    thread: ThreadSummary,
    callbacks: HomeCallbacks,
    mediaLoadingPolicy: ReaderMediaLoadingPolicy,
    modifier: Modifier = Modifier,
) {
    val forum = thread.forumRoute()
    ReaderCard(modifier = modifier, cornerRadius = 0.dp, onClick = { callbacks.onOpenThread(thread) }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            dev.infinityf4p.tiebapure.core.designsystem.ReaderCommunityRow(
                name = forum?.displayName ?: thread.author.resolvedDisplayName,
                time = compactThreadTime(thread.createdAtEpochSeconds),
                onOpen = { if (forum != null) callbacks.onOpenForum(forum) else callbacks.onOpenUser(thread.author) },
                avatar = { AvatarImage(dev.infinityf4p.tiebapure.core.designsystem.threadIdentityAvatarUrl(thread), forum?.displayName ?: thread.author.resolvedDisplayName, Modifier.size(24.dp)) },
                subscribed = forum?.let(callbacks.isSubscribed) == true,
                onSubscribe = forum?.let { f -> callbacks.onSubscribe?.let { action -> { action(f) } } },
                more = { ThreadMenu(thread, forum, callbacks.onBlockForum, callbacks.onOpenUser) },
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (thread.title.isNotBlank()) {
                    Text(
                        thread.title,
                        style = dev.infinityf4p.tiebapure.core.designsystem.readerContentStyle(16, 22).copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                thread.textPreview.takeIf(String::isNotBlank)?.let { preview ->
                    Text(
                        preview,
                        style = dev.infinityf4p.tiebapure.core.designsystem.readerContentStyle(14, 20),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ThreadBadges(thread)
            ThreadMediaPreview(
                blocks = thread.blocks,
                mediaLoadingPolicy = mediaLoadingPolicy,
                onItemClick = { callbacks.onOpenMedia(thread, it.blockIndex) },
            ) { url, description, modifier ->
                RemoteImage(url = url, contentDescription = description, modifier = modifier)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ReaderInteractionStats(
                comments = thread.replyCount,
                likes = thread.likeCount,
                onCommentsClick = { callbacks.onOpenComments(thread) },
                modifier = Modifier.weight(1f).testTag("home-like-${thread.id}"),
            )
            callbacks.onShare?.let { share -> dev.infinityf4p.tiebapure.core.designsystem.ReaderShareAction { share(thread) } }
            }
        }
    }
    dev.infinityf4p.tiebapure.core.designsystem.ReaderContentDivider()
}


@Composable
private fun ThreadBadges(thread: ThreadSummary) {
    if (!thread.isTop && !thread.isGood) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (thread.isTop) ThreadBadge("置顶")
        if (thread.isGood) ThreadBadge("精品")
    }
}

@Composable
private fun ThreadBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun ThreadMenu(
    @Suppress("UNUSED_PARAMETER") thread: ThreadSummary,
    forum: Forum?,
    onBlockForum: (Forum) -> Unit,
    onOpenUser: (UserSummary) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("查看作者：${thread.author.resolvedDisplayName}") }, enabled = thread.author.id > 0,
                onClick = { expanded = false; onOpenUser(thread.author) })
            if (forum != null) {
                DropdownMenuItem(
                    text = { Text("屏蔽${forum.displayName}") },
                    onClick = {
                        expanded = false
                        onBlockForum(forum)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("贴吧信息不可用") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            }
        }
    }
}

@Composable
private fun FeedFooter(
    isLoading: Boolean,
    error: String?,
    hasMore: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            error != null -> TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
            hasMore -> TextButton(onClick = onLoadMore) { Text("加载更多帖子") }
            !hasMore -> Text("没有更多了", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyPageMessage(title: String, message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().height(132.dp).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EmptyPageContinuation(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("home-empty-page-load-more")
    if (isLoading) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    } else {
        TextButton(onClick = if (error == null) onLoadMore else onRetry, modifier = modifier) {
            Text(if (error == null) "继续加载" else "加载失败，点击重试")
        }
    }
}

@Composable
private fun CenteredProgress(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(label, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

internal fun compactCount(value: Int): String = compactInteractionCount(value)

internal object HomeRefreshMotionPolicy {
    const val heldContentDistanceDp = 56f
    const val maximumContentDistanceDp = 76f
    const val settleDurationMillis = 160
    const val reboundDurationMillis = 260
    const val animatedScrollItemLimit = 4

    fun gestureContentOffsetDp(distanceFraction: Float): Float {
        val progress = distanceFraction.coerceAtLeast(0f)
        if (progress <= 1f) return progress * heldContentDistanceDp
        val overshootRange = maximumContentDistanceDp - heldContentDistanceDp
        val resistedOvershoot = overshootRange * (1f - exp(-1.6f * (progress - 1f)))
        return heldContentDistanceDp + resistedOvershoot
    }
}

internal fun compactThreadTime(
    epochSeconds: Long?,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000,
): String? {
    val timestamp = epochSeconds?.takeIf { it > 0L } ?: return null
    val elapsed = (nowEpochSeconds - timestamp).coerceAtLeast(0L)
    return when {
        elapsed < 60 -> "刚刚"
        elapsed < 3_600 -> "${elapsed / 60}分钟前"
        elapsed < 86_400 -> "${elapsed / 3_600}小时前"
        elapsed < 7 * 86_400 -> "${elapsed / 86_400}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp * 1_000))
    }
}

private val previewThreads = listOf(
    ThreadSummary(
        id = 1,
        forumId = 10,
        title = "TiebaPure Android 版界面预览",
        author = UserSummary(7, "sample", "示例用户", ""),
        forumName = "Android",
        replyCount = 128,
        viewCount = 2_400,
        likeCount = 37,
        blocks = listOf(ContentBlock.Text("首页保持安静、紧凑，帖子信息可以快速浏览。")),
    ),
    ThreadSummary(
        id = 2,
        title = "没有账号也能浏览公开内容",
        author = UserSummary(8, "guest", "公开内容", ""),
        forumName = "数码",
        replyCount = 12_500,
        viewCount = 82_000,
        likeCount = 10_020,
        blocks = listOf(ContentBlock.Text("离线 Preview 不会访问真实账号或网络。")),
    ),
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    TiebaPureTheme {
        HomeScreen(
            uiState = HomeUiState(threads = previewThreads, hasMore = true, nextPage = 2),
            callbacks = HomeCallbacks(),
            onRefresh = {},
            onLoadMore = {},
        )
    }
}
