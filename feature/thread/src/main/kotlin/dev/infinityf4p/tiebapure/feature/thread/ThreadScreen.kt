package dev.infinityf4p.tiebapure.feature.thread

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.infinityf4p.tiebapure.core.designsystem.LocalReadingPreferences
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.infinityf4p.tiebapure.core.designsystem.ReaderCard
import dev.infinityf4p.tiebapure.core.designsystem.ReaderSectionBand
import dev.infinityf4p.tiebapure.core.designsystem.readerFontFamily
import dev.infinityf4p.tiebapure.core.media.AvatarImage
import dev.infinityf4p.tiebapure.core.media.ImageGallery
import dev.infinityf4p.tiebapure.core.media.ImageSaveAction
import dev.infinityf4p.tiebapure.core.media.VideoPlayer
import dev.infinityf4p.tiebapure.core.media.VoicePlaybackCoordinator
import dev.infinityf4p.tiebapure.core.model.ImageContent
import dev.infinityf4p.tiebapure.core.model.Forum
import dev.infinityf4p.tiebapure.core.model.Post
import dev.infinityf4p.tiebapure.core.model.ReadingPreferences
import dev.infinityf4p.tiebapure.core.model.Subpost
import dev.infinityf4p.tiebapure.core.model.ThreadPage
import dev.infinityf4p.tiebapure.core.model.ThreadReplySort
import dev.infinityf4p.tiebapure.core.model.VideoContent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

sealed interface ThreadReplyTarget {
    data object Thread : ThreadReplyTarget
    data class Floor(val post: Post) : ThreadReplyTarget
    data class Nested(val parent: Post, val subpost: Subpost) : ThreadReplyTarget
}

enum class ThreadInitialDestination { Replies }

data class ThreadCapabilities(
    val canReply: Boolean = true,
    val canLike: Boolean = true,
    val canCollect: Boolean = true,
    val canRefresh: Boolean = true,
    val canFilterReplies: Boolean = true,
    val alwaysShowSubpostOpenAction: Boolean = true,
)

private data class GallerySelection(val images: List<ImageContent>, val index: Int)

@Composable
fun ThreadRoute(
    threadId: Long,
    initialPostId: ULong? = null,
    initialDestination: ThreadInitialDestination? = null,
    viewModelSessionKey: String = "guest",
    repository: ThreadRepository,
    onBack: () -> Unit,
    onReply: (ThreadReplyTarget) -> Unit,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onShare: (String) -> Unit,
    onSave: ((ThreadPage, List<Post>) -> Unit)? = null,
    onBookmark: ((ThreadPage) -> Unit)? = null,
    isBookmarked: Boolean = false,
    isBookmarkUpdating: Boolean = false,
    isSaving: Boolean = false,
    isSaved: Boolean = false,
    onDownloadImage: (ImageContent) -> Unit,
    onSaveImage: ImageSaveAction? = null,
    readingPreferences: ReadingPreferences = ReadingPreferences(),
    capabilities: ThreadCapabilities = ThreadCapabilities(),
    onCollectionChanged: (Boolean) -> Unit = {},
    onForumClick: ((Forum) -> Unit)? = null,
    isLocallySubscribed: (Forum) -> Boolean = { false },
    onToggleLocalSubscription: ((Forum) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: ThreadViewModel = viewModel(
        key = "thread-$threadId-${initialPostId ?: 0uL}-${initialDestination?.name ?: "default"}-$viewModelSessionKey",
        factory = ThreadViewModel.factory(
            threadId,
            repository,
            readingPreferences.defaultReplySort,
            initialPostId,
            restoreReadingPosition = initialDestination == null,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val voicePlayback = remember(context) { VoicePlaybackCoordinator.shared(context) }
    val currentOnCollectionChanged by rememberUpdatedState(onCollectionChanged)

    LaunchedEffect(viewModel) {
        viewModel.confirmedCollectionChanges.collect { collected ->
            currentOnCollectionChanged(collected)
        }
    }

    DisposableEffect(viewModel, voicePlayback) {
        onDispose {
            viewModel.flushReadingPosition()
            voicePlayback.stop()
        }
    }

    CompositionLocalProvider(LocalReadingPreferences provides readingPreferences) {
        ThreadScreen(
            state = state,
            initialDestination = initialDestination,
            capabilities = capabilities,
            onBack = onBack,
            onForumClick = onForumClick,
            isLocallySubscribed = isLocallySubscribed,
            onToggleLocalSubscription = onToggleLocalSubscription,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetry = viewModel::retry,
            onSort = viewModel::selectSort,
            onOnlyAuthor = viewModel::setOnlyThreadAuthor,
            onReply = onReply,
            onUserClick = onUserClick,
            onLinkClick = onLinkClick,
            onShare = { onShare(buildThreadShareUrl(threadId)) },
            onSave = state.page?.let { page ->
                onSave?.let { save -> { save(page, state.posts) } }
            },
            onBookmark = state.page?.let { page -> onBookmark?.let { bookmark -> { bookmark(page) } } },
            isBookmarked = isBookmarked,
            isBookmarkUpdating = isBookmarkUpdating,
            isSaving = isSaving,
            isSaved = isSaved,
            onOpenSubposts = viewModel::openSubposts,
            onCloseSubposts = viewModel::closeSubposts,
            onLoadMoreSubposts = viewModel::loadMoreSubposts,
            onRetrySubposts = viewModel::retrySubposts,
            onToggleThreadLike = viewModel::toggleThreadLike,
            onTogglePostLike = viewModel::togglePostLike,
            onToggleSubpostLike = viewModel::toggleSubpostLike,
            onToggleCollection = viewModel::toggleCollection,
            onReadingPositionChanged = viewModel::visibleReadingPositionChanged,
            onReadingPositionRestored = viewModel::readingPositionRestored,
            onActionErrorShown = viewModel::clearActionError,
            onDownloadImage = onDownloadImage,
            onSaveImage = onSaveImage,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: ThreadUiState,
    initialDestination: ThreadInitialDestination? = null,
    capabilities: ThreadCapabilities = ThreadCapabilities(),
    onBack: () -> Unit,
    onForumClick: ((Forum) -> Unit)? = null,
    isLocallySubscribed: (Forum) -> Boolean = { false },
    onToggleLocalSubscription: ((Forum) -> Unit)? = null,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onSort: (ThreadReplySort) -> Unit,
    onOnlyAuthor: (Boolean) -> Unit,
    onReply: (ThreadReplyTarget) -> Unit,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onShare: () -> Unit,
    onSave: (() -> Unit)? = null,
    onShowSavedInfo: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    isBookmarked: Boolean = false,
    isBookmarkUpdating: Boolean = false,
    isSaving: Boolean = false,
    isSaved: Boolean = false,
    onOpenSubposts: (Post) -> Unit,
    onCloseSubposts: () -> Unit,
    onLoadMoreSubposts: () -> Unit,
    onRetrySubposts: () -> Unit,
    onToggleThreadLike: () -> Unit,
    onTogglePostLike: (Post) -> Unit,
    onToggleSubpostLike: (Subpost) -> Unit,
    onToggleCollection: () -> Unit,
    onReadingPositionChanged: (ThreadReadingPosition) -> Unit,
    onReadingPositionRestored: () -> Unit,
    onActionErrorShown: () -> Unit,
    onDownloadImage: (ImageContent) -> Unit,
    onSaveImage: ImageSaveAction? = null,
    modifier: Modifier = Modifier,
) {
    var gallery by remember { mutableStateOf<GallerySelection?>(null) }
    var video by remember { mutableStateOf<VideoContent?>(null) }
    var showsActions by remember { mutableStateOf(false) }
    val actionVisibility = capabilities.actionVisibility(hasPage = state.page != null)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showsCommentSearch by remember { mutableStateOf(false) }
    var searchTargetPost by remember { mutableStateOf<ULong?>(null) }
    var searchTargetSubpost by remember { mutableStateOf<ULong?>(null) }
    if (showsCommentSearch) {
        val searchable = remember(state.posts, state.page?.mainPost, state.subposts) {
            val parents = (listOfNotNull(state.page?.mainPost) + state.posts).distinctBy { it.id }
            parents.flatMap { parent -> listOf(CommentSearchEntry(parent)) + parent.previewSubposts.map { CommentSearchEntry(parent, it) } } +
                state.subposts?.let { nested -> nested.items.map { CommentSearchEntry(nested.parent, it) } }.orEmpty()
        }
        CommentSearchDialog(searchable, { showsCommentSearch = false }) { result ->
            showsCommentSearch = false; searchTargetPost = result.parent.id; searchTargetSubpost = result.subpost?.id
            if (result.subpost != null) {
                if (state.subposts?.parent?.id != result.parent.id) onOpenSubposts(result.parent)
            } else {
                if (state.subposts != null) onCloseSubposts()
                scope.launch {
                    val index = state.posts.indexOfFirst { it.id == result.parent.id }
                    listState.animateScrollToItem(if (index >= 0) index + THREAD_REPLY_ITEM_OFFSET else 0)
                }
            }
        }
    }
    val densityForContext = LocalDensity.current.density
    val showForumContext by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset >= 64 * densityForContext } }

    val avatarTone = dev.infinityf4p.tiebapure.core.media.avatarColor(state.page?.forum?.avatarUrl)
    var selectedTone by remember(state.page?.thread?.id) { mutableStateOf<Int?>(null) }
    val transitionActive = dev.infinityf4p.tiebapure.core.designsystem.LocalReaderTransitionActive.current
    LaunchedEffect(avatarTone, transitionActive) { if (!transitionActive && selectedTone == null) selectedTone = avatarTone }
    val hasCommunityTone = dev.infinityf4p.tiebapure.core.designsystem.LocalCommunityColors.current && selectedTone != null
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val barColor = if (hasCommunityTone) dev.infinityf4p.tiebapure.core.media.readableAvatarBar(selectedTone!!, dark) else MaterialTheme.colorScheme.surface
    val barText = if (hasCommunityTone) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    val setPostBarColor = dev.infinityf4p.tiebapure.core.designsystem.LocalPostBarColor.current
    val compactWindow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600
    DisposableEffect(barColor, compactWindow, setPostBarColor) {
        if (compactWindow) setPostBarColor(barColor)
        onDispose { if (compactWindow) setPostBarColor(null) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var appliedInitialDestination by rememberSaveable(initialDestination) { mutableStateOf(false) }
    var handledReplyChangeVersion by rememberSaveable { mutableLongStateOf(state.replyChangeVersion) }
    LaunchedEffect(state.replyChangeVersion) {
        if (handledReplyChangeVersion == state.replyChangeVersion) return@LaunchedEffect
        handledReplyChangeVersion = state.replyChangeVersion
        if (state.replyChangeVersion > 0 && listState.firstVisibleItemIndex >= THREAD_REPLY_FILTER_ITEM_INDEX) {
            listState.scrollToItem(THREAD_REPLY_FILTER_ITEM_INDEX)
        }
    }

    LaunchedEffect(state.actionErrorMessage) {
        state.actionErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onActionErrorShown()
        }
    }

    LaunchedEffect(state.errorMessage) {
        if (state.page != null && !state.hasPendingReplyFilters) {
            state.errorMessage?.let { snackbarHostState.showSnackbar("加载失败，已保留当前内容。$it") }
        }
    }

    LaunchedEffect(state.readingPositionToRestore, state.posts.map(Post::id)) {
        val position = state.readingPositionToRestore ?: return@LaunchedEffect
        val postIndex = state.posts.indexOfFirst {
            it.id == position.postId || (position.floor > 0 && it.floor == position.floor)
        }
        when {
            state.page?.mainPost?.id == position.postId -> {
                listState.scrollToItem(0)
                onReadingPositionRestored()
            }
            postIndex >= 0 -> {
                listState.scrollToItem(postIndex + THREAD_REPLY_ITEM_OFFSET)
                onReadingPositionRestored()
            }
        }
    }

    LaunchedEffect(initialDestination, state.page, appliedInitialDestination) {
        if (initialDestination == ThreadInitialDestination.Replies && state.page != null && !appliedInitialDestination) {
            listState.scrollToItem(THREAD_REPLY_FILTER_ITEM_INDEX)
            appliedInitialDestination = true
        }
    }

    LaunchedEffect(listState, state.posts.map(Post::id), state.readingPositionToRestore, state.isChangingReplies, state.hasPendingReplyFilters) {
        if (state.readingPositionToRestore != null || state.isChangingReplies || state.hasPendingReplyFilters) return@LaunchedEffect
        snapshotFlow {
            if (listState.isScrollInProgress) return@snapshotFlow null
            if (listState.firstVisibleItemIndex < THREAD_REPLY_ITEM_OFFSET) return@snapshotFlow null
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { item -> (item.key as? String)?.postIdFromListKey() }
                .lastOrNull()
        }
            .distinctUntilChanged()
            .collect { postId ->
                val post = state.posts.firstOrNull { it.id == postId } ?: return@collect
                onReadingPositionChanged(ThreadReadingPosition(post.id, post.floor))
            }
    }

    LaunchedEffect(listState, state.posts.size, state.page?.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (state.page?.hasMore == true && lastVisible >= listState.layoutInfo.totalItemsCount - 3) {
                    onLoadMore()
                }
            }
    }

    val detailDensity = LocalDensity.current
    var detailWidth by remember { mutableStateOf(0.dp) }
    val useReplySidebar = detailWidth >= 920.dp
    val nestedListState = rememberLazyListState()
    val nestedContent: @Composable (SubpostUiState) -> Unit = { nested ->
        SubpostSheet(
                state = nested,
                listState = nestedListState,
                capabilities = capabilities,
                threadAuthorId = state.page?.thread?.author?.id,
                onClose = onCloseSubposts,
                targetSubpost = searchTargetSubpost,
                onReplyParent = if (capabilities.canReply) {
                    { onReply(ThreadReplyTarget.Floor(it)) }
                } else {
                    null
                },
                onReply = if (capabilities.canReply) {
                    { onReply(ThreadReplyTarget.Nested(nested.parent, it)) }
                } else {
                    null
                },
                onUserClick = onUserClick,
                onLinkClick = onLinkClick,
                onImagesClick = { images, index -> gallery = GallerySelection(images, index) },
                onVideoClick = { video = it },
                onLoadMore = onLoadMoreSubposts,
                onRetry = onRetrySubposts,
                updatingLikeTargets = state.updatingLikeTargets,
                unknownLikeTargets = state.unknownLikeTargets,
                onTogglePostLike = onTogglePostLike,
                onToggleSubpostLike = onToggleSubpostLike,
            )
    }
    Row(modifier.fillMaxSize().onSizeChanged { detailWidth = with(detailDensity) { it.width.toDp() } }) {
    CompositionLocalProvider(dev.infinityf4p.tiebapure.core.media.LocalImageSaveRequest provides onDownloadImage) {
    Scaffold(
        modifier = Modifier.weight(1f).testTag("thread-screen"),
        // The app container already owns safeDrawing. Consuming it again leaves a navigation-bar-height gap.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (showForumContext) ThreadForumTitle(state.page?.forum, onForumClick)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, contentDescription = "关闭帖子") }
                },
                actions = {
                    IconButton(onClick = { showsCommentSearch = true }, enabled = state.page != null) { Icon(Icons.Outlined.Search, "搜索本帖已加载评论") }
                    Box {
                        IconButton(onClick = { showsActions = true }, modifier = Modifier.testTag("thread-more-actions")) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多帖子操作")
                        }
                        DropdownMenu(expanded = showsActions, onDismissRequest = { showsActions = false }) {
                            onShowSavedInfo?.let { show -> DropdownMenuItem(
                                text = { Text("查看保存信息") },
                                onClick = { showsActions = false; show() }) }
                            onSave?.let { save -> DropdownMenuItem(text = { Text(if (isSaved) "更新离线保存" else "离线保存") }, enabled = state.page != null && !isSaving,
                                modifier = Modifier.testTag("thread-save-action"), onClick = { showsActions = false; save() }) }
                            DropdownMenuItem(text = { Text("分享帖子") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                modifier = Modifier.testTag("thread-share-action"),
                                onClick = { showsActions = false; onShare() })
                            if (capabilities.canRefresh) DropdownMenuItem(text = { Text("刷新") },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                enabled = !state.isRefreshing && !state.isChangingReplies && !state.isInitialLoading,
                                onClick = { showsActions = false; onRefresh() })
                        }
                    }
                },
                expandedHeight = 56.dp,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, titleContentColor = barText, navigationIconContentColor = barText, actionIconContentColor = barText),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (actionVisibility.showReplyActions) {
                Surface(
                    modifier = Modifier.testTag("thread-bottom-bar"),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Surface(
                            onClick = { onReply(ThreadReplyTarget.Thread) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("thread-reply-bar")
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .heightIn(min = 48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("回复帖子", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag("thread-content"),
        ) {
            when {
                state.isInitialLoading && state.page == null -> LoadingState()
                state.page == null -> ErrorState(state.errorMessage, onRetry)
                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val bodyInset = commentBodyInsetDp(maxWidth.value,
                        LocalDensity.current.fontScale * LocalReadingPreferences.current.fontSize.scale).dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("thread-reply-list")
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        item(key = "main") {
                            MainPost(
                                state = state,
                                onReply = onReply,
                                onUserClick = onUserClick,
                                onLinkClick = onLinkClick,
                                onImagesClick = { images, index -> gallery = GallerySelection(images, index) },
                                onVideoClick = { video = it },
                                onOpenSubposts = onOpenSubposts,
                                alwaysShowSubpostOpenAction = capabilities.alwaysShowSubpostOpenAction,
                                onToggleLike = onToggleThreadLike,
                                capabilities = capabilities,
                                onForumClick = onForumClick,
                                isLocallySubscribed = isLocallySubscribed,
                                onToggleLocalSubscription = onToggleLocalSubscription,
                                onBookmark = onBookmark, isBookmarked = isBookmarked, onShare = onShare,
                                onShowComments = { scope.launch { listState.animateScrollToItem(THREAD_REPLY_FILTER_ITEM_INDEX) } },
                            )
                            dev.infinityf4p.tiebapure.core.designsystem.ReaderContentDivider(
                                Modifier.testTag("thread-main-comments-divider"))
                        }
                        if (capabilities.canFilterReplies) {
                            item(key = "filters") {
                                Column {
                                    val showApplied = state.hasPendingReplyFilters && !state.isChangingReplies
                                    ReplyFilters(if (showApplied) state.appliedSort else state.sort,
                                        if (showApplied) state.appliedOnlyThreadAuthor else state.onlyThreadAuthor, onSort, onOnlyAuthor)
                                    if (state.isChangingReplies || state.hasPendingReplyFilters) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                            .testTag("thread-reply-transition"), horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            if (state.isChangingReplies) CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                                            else TextButton(onClick = onRetry) { Text("加载失败，重试") }
                                        }
                                    }
                                }
                            }
                        }
                        items(items = state.posts, key = { "post-${it.id}" }) { post ->
                            var collapsed by rememberSaveable(post.id.toString()) { mutableStateOf(false) }
                            LaunchedEffect(searchTargetPost) { if (searchTargetPost == post.id) collapsed = false }
                            CommentContainer {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                AuthorLine(
                                    author = post.author,
                                    floor = post.floor,
                                    likeCount = post.likeCount,
                                    isLiked = post.isLiked,
                                    isLikeUpdating = ThreadLikeTarget(post.id, post.likeObjectType) in state.updatingLikeTargets,
                                    onToggleLike = if (!capabilities.canLike ||
                                        ThreadLikeTarget(post.id, post.likeObjectType) in state.unknownLikeTargets
                                    ) null else ({ onTogglePostLike(post) }),
                                    onUserClick = onUserClick,
                                    isThreadAuthor = post.author.id > 0L && post.author.id == state.page.thread.author.id,
                                    isCollapsed = collapsed,
                                    onToggleCollapsed = { collapsed = !collapsed },
                                )
                                AnimatedVisibility(visible = !collapsed, enter = expandVertically(tween(180)) + fadeIn(tween(120)),
                                    exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
                                PostBody(
                                    post = post,
                                    onReply = if (capabilities.canReply) {
                                        { onReply(ThreadReplyTarget.Floor(it)) }
                                    } else {
                                        null
                                    },
                                    onUserClick = onUserClick,
                                    onLinkClick = onLinkClick,
                                    onImagesClick = { images, index -> gallery = GallerySelection(images, index) },
                                    onVideoClick = { video = it },
                                    onOpenSubposts = onOpenSubposts,
                                    showSubpostOpenAction = capabilities.alwaysShowSubpostOpenAction,
                                    modifier = Modifier.fillMaxWidth(),
                                    bodyInset = bodyInset,
                                    threadAuthorId = state.page.thread.author.id,
                                )
                                }
                                }
                            }
                            dev.infinityf4p.tiebapure.core.designsystem.ReaderContentDivider()
                        }
                        item(key = "footer") {
                            ThreadFooter(state, onLoadMore, onRetry)
                        }
                    }
                }
            }
        }
    }

    if (useReplySidebar) state.subposts?.let { nested ->
        androidx.compose.material3.VerticalDivider()
        Surface(Modifier.width(320.dp).fillMaxHeight()) { nestedContent(nested) }
    }
    }
    if (!useReplySidebar) state.subposts?.let { nested ->
        ModalBottomSheet(
            onDismissRequest = onCloseSubposts,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            nestedContent(nested)
        }
    }

    gallery?.let { selection ->
        ImageGallery(
            images = selection.images,
            initialPage = selection.index,
            onDismiss = { gallery = null },
            onDownload = onDownloadImage,
            saveAction = onSaveImage,
        )
    }
    video?.let { selected -> VideoPlayer(selected, onDismiss = { video = null }) }
    }
}

internal fun buildThreadShareUrl(threadId: Long): String {
    require(threadId > 0) { "threadId must be positive" }
    return "https://tieba.baidu.com/p/$threadId"
}

@Composable
private fun MainPost(
    state: ThreadUiState,
    onReply: (ThreadReplyTarget) -> Unit,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onImagesClick: (List<ImageContent>, Int) -> Unit,
    onVideoClick: (VideoContent) -> Unit,
    onOpenSubposts: (Post) -> Unit,
    alwaysShowSubpostOpenAction: Boolean,
    onToggleLike: () -> Unit,
    capabilities: ThreadCapabilities,
    onForumClick: ((Forum) -> Unit)?,
    isLocallySubscribed: (Forum) -> Boolean,
    onToggleLocalSubscription: ((Forum) -> Unit)?,
    onBookmark: (() -> Unit)?, isBookmarked: Boolean, onShare: () -> Unit,
    onShowComments: () -> Unit,
) {
    val page = state.page ?: return
    ReaderCard(cornerRadius = 0.dp) {
      Column(Modifier.fillMaxWidth()) {
        dev.infinityf4p.tiebapure.core.designsystem.ReaderCommunityRow(
            name = page.forum.displayName, time = null,
            onOpen = { onForumClick?.invoke(page.forum) },
            avatar = { AvatarImage(page.forum.avatarUrl, page.forum.displayName, Modifier.size(24.dp)) },
            subscribed = isLocallySubscribed(page.forum),
            onSubscribe = onToggleLocalSubscription?.let { action -> { action(page.forum) } },
        )
        val mainPostId = page.mainPost?.id ?: page.thread.firstPostId
        val mainAuthor = page.mainPost?.author ?: page.thread.author
        AuthorLine(
            author = mainAuthor,
            floor = 1,
            likeCount = page.thread.likeCount,
            isLiked = page.thread.isLiked,
            isLikeUpdating = mainPostId != null && ThreadLikeTarget(
                mainPostId,
                dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Thread,
            ) in state.updatingLikeTargets,
            onToggleLike = if (
                !capabilities.canLike || mainPostId == null || ThreadLikeTarget(
                    mainPostId,
                    dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Thread,
                ) in state.unknownLikeTargets
            ) null else onToggleLike,
            onUserClick = onUserClick,
            isThreadAuthor = mainAuthor.id > 0L && mainAuthor.id == page.thread.author.id,
            isMainPost = true,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            page.thread.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = readerFontFamily(LocalReadingPreferences.current.fontFamily),
                fontSize = (18 * LocalReadingPreferences.current.fontSize.scale).sp,
                lineHeight = (26 * LocalReadingPreferences.current.fontSize.scale).sp,
            ),
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        if (page.mainPostIsSummaryFallback) {
            ReaderSectionBand(Modifier.padding(bottom = 8.dp)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "主楼完整内容暂不可用，以下为来源页摘要。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        page.mainPost?.let { mainPost ->
            PostBody(
                post = mainPost,
                onReply = if (capabilities.canReply) {
                    { onReply(ThreadReplyTarget.Floor(it)) }
                } else {
                    null
                },
                onUserClick = onUserClick,
                onLinkClick = onLinkClick,
                onImagesClick = onImagesClick,
                onVideoClick = onVideoClick,
                onOpenSubposts = onOpenSubposts,
                showSubpostOpenAction = alwaysShowSubpostOpenAction,
                isMainPost = true,
                threadAuthorId = page.thread.author.id,
            )
        } ?: RichContent(
            blocks = page.thread.blocks,
            onLinkClick = onLinkClick,
            onUserClick = onUserClick,
            onImagesClick = onImagesClick,
            onVideoClick = onVideoClick,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            dev.infinityf4p.tiebapure.core.designsystem.ReaderInteractionStats(page.thread.replyCount, page.thread.likeCount,
                modifier = Modifier.weight(1f), onCommentsClick = onShowComments,
                )
            onBookmark?.let { action -> IconButton(onClick = action, modifier = Modifier.testTag("thread-local-bookmark")) {
                Icon(if (isBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, "本地收藏")
            } }
            dev.infinityf4p.tiebapure.core.designsystem.ReaderShareAction(onShare)
        }
      }
    }
}

@Composable
private fun ReplyFilters(sort: ThreadReplySort, onlyAuthor: Boolean, onSort: (ThreadReplySort) -> Unit, onOnlyAuthor: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box {
            TextButton(onClick = { expanded = true }) { Text("${sort.displayName} ▾") }
            DropdownMenu(expanded, { expanded = false }) {
                ThreadReplySort.entries.forEach { value -> DropdownMenuItem(text = { Text(value.displayName) }, onClick = { expanded = false; onSort(value) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(onlyAuthor, onOnlyAuthor)
            Text("只看楼主", style = MaterialTheme.typography.labelMedium)
        }
    }
}


@Composable
private fun ThreadFooter(
    state: ThreadUiState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state.isChangingReplies || state.hasPendingReplyFilters) return
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        when (
            threadFooterContent(
                hasPage = state.page != null,
                isLoadingMore = state.isLoadingMore,
                hasMore = state.page?.hasMore == true,
                hasError = state.errorMessage != null,
            )
        ) {
            ThreadFooterContent.Loading -> CircularProgressIndicator()
            ThreadFooterContent.Error -> TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
            ThreadFooterContent.LoadMore -> TextButton(onClick = onLoadMore) { Text("加载更多回复") }
            ThreadFooterContent.End -> Text(
                "没有更多回复了",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            null -> Unit
        }
    }
}

@Composable
private fun ThreadForumTitle(
    forum: Forum?,
    onForumClick: ((Forum) -> Unit)?,
) {
    if (forum == null) {
        Text("帖子", maxLines = 1)
        return
    }

    val forumName = forum.displayName.ifBlank { forum.name }.ifBlank { "贴吧" }
    val navigableForum = normalizedThreadForumRoute(forum)
    val forumClick = onForumClick?.let { callback ->
        navigableForum?.let { target -> { callback(target) } }
    }
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("thread-forum-title")
            .then(
                if (forumClick == null) Modifier else Modifier.clickable(onClick = forumClick),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Text(
            forumName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (forumClick != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubpostSheet(
    state: SubpostUiState,
    listState: LazyListState,
    capabilities: ThreadCapabilities,
    threadAuthorId: Long?,
    onClose: () -> Unit,
    targetSubpost: ULong? = null,
    onReplyParent: ((Post) -> Unit)?,
    onReply: ((Subpost) -> Unit)?,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onImagesClick: (List<ImageContent>, Int) -> Unit,
    onVideoClick: (VideoContent) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    updatingLikeTargets: Set<ThreadLikeTarget>,
    unknownLikeTargets: Set<ThreadLikeTarget>,
    onTogglePostLike: (Post) -> Unit,
    onToggleSubpostLike: (Subpost) -> Unit,
) {
    LaunchedEffect(targetSubpost, state.items) {
        val index = state.items.indexOfFirst { it.id == targetSubpost }
        if (index >= 0) listState.scrollToItem(index + 2)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bodyInset = commentBodyInsetDp(maxWidth.value,
            LocalDensity.current.fontScale * LocalReadingPreferences.current.fontSize.scale).dp
        LazyColumn(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), state = listState,
        ) {
            item(key = "title") {
                CommentContainer(verticalPadding = 0.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            floorReplyTitle(state.parent.floor, maxOf(state.parent.subpostCount, state.items.size).takeIf { it > 0 || !state.isLoading }),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭楼中楼")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item(key = "parent") {
                CommentContainer {
                  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AuthorLine(
                        author = state.parent.author,
                        floor = state.parent.floor,
                        likeCount = state.parent.likeCount,
                        isLiked = state.parent.isLiked,
                        isLikeUpdating = ThreadLikeTarget(
                            state.parent.id,
                            state.parent.likeObjectType,
                        ) in updatingLikeTargets,
                        onToggleLike = if (
                            !capabilities.canLike ||
                            ThreadLikeTarget(state.parent.id, state.parent.likeObjectType) in unknownLikeTargets
                        ) null else ({ onTogglePostLike(state.parent) }),
                        onUserClick = onUserClick,
                        isThreadAuthor = threadAuthorId != null && threadAuthorId > 0L && state.parent.author.id == threadAuthorId,
                    )
                    PostBody(
                        post = state.parent,
                        onReply = onReplyParent,
                        onUserClick = onUserClick,
                        onLinkClick = onLinkClick,
                        onImagesClick = onImagesClick,
                        onVideoClick = onVideoClick,
                        onOpenSubposts = {},
                        threadAuthorId = threadAuthorId,
                        showSubpostPreview = false,
                        bodyInset = bodyInset,
                    )
                  }
                }
                dev.infinityf4p.tiebapure.core.designsystem.ReaderContentDivider()
            }
            items(state.items, key = { "subpost-${it.id}" }) { item ->
                SubpostRow(
                    subpost = item,
                    onReply = onReply,
                    onUserClick = onUserClick,
                    onLinkClick = onLinkClick,
                    onImagesClick = onImagesClick,
                    onVideoClick = onVideoClick,
                    isLikeUpdating = ThreadLikeTarget(
                        item.id,
                        dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Subpost,
                    ) in updatingLikeTargets,
                    onToggleLike = if (
                        !capabilities.canLike || ThreadLikeTarget(
                            item.id,
                            dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Subpost,
                        ) in unknownLikeTargets
                    ) null else ({ onToggleSubpostLike(item) }),
                    threadAuthorId = threadAuthorId,
                    bodyInset = bodyInset,
                )
                dev.infinityf4p.tiebapure.core.designsystem.ReaderContentDivider(Modifier.padding(start = 28.dp, end = 16.dp), nested = true)
            }
            item(key = "nested-footer") {
                Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.isLoading -> CircularProgressIndicator()
                        state.errorMessage != null -> TextButton(onClick = onRetry) { Text("加载失败，点击重试") }
                        state.hasMore -> TextButton(onClick = onLoadMore) { Text("加载更多回复") }
                        state.items.isEmpty() -> Text("暂无楼中楼回复", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize().testTag("thread-initial-loading"), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
    }
}

@Composable
private fun ErrorState(message: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message ?: "帖子加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private val ThreadReplySort.displayName: String
    get() = when (this) {
        ThreadReplySort.Hot -> "热门"
        ThreadReplySort.Ascending -> "正序"
        ThreadReplySort.Descending -> "倒序"
    }

private const val THREAD_REPLY_ITEM_OFFSET = 2
private const val THREAD_REPLY_FILTER_ITEM_INDEX = 1

private val Post.likeObjectType: dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType
    get() = if (floor == 1) {
        dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Thread
    } else {
        dev.infinityf4p.tiebapure.core.model.TiebaLikeObjectType.Post
    }

private fun String.postIdFromListKey(): ULong? =
    removePrefix("post-").takeIf { it.length != length }?.toULongOrNull()
