package dev.infinityf4p.tiebapure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.infinityf4p.tiebapure.core.model.*
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.infinityf4p.tiebapure.core.data.BlocklistEntity
import dev.infinityf4p.tiebapure.core.model.ReaderMediaLoadingPolicy
import dev.infinityf4p.tiebapure.core.model.TiebaContentFilterPolicy
import dev.infinityf4p.tiebapure.feature.home.HomeCallbacks
import dev.infinityf4p.tiebapure.feature.home.HomeThreadRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SubscriptionFeedScreen(
    viewModel: SubscriptionFeedViewModel,
    callbacks: HomeCallbacks,
    mediaLoadingPolicy: ReaderMediaLoadingPolicy,
    blockedEntries: List<BlocklistEntity>,
    onBrowseForums: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var sourcePicker by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEnter() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onLeave() }
    LaunchedEffect(listState, viewModel) {
        snapshotFlow { listState.isScrollInProgress && listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 3 } == true }
            .distinctUntilChanged().collect { if (it) viewModel.loadMore() }
    }
    if (sourcePicker) FeedSourcePicker(state.sources, state.preferences.sourceKeys,
        onDismiss = { sourcePicker = false }, onApply = { keys ->
            viewModel.updatePreferences(state.preferences.copy(sourceKeys = keys)); sourcePicker = false
        })
    val blocklist = blockedEntries.toBlocklistSnapshot()
    val threads = state.threads.filter { TiebaContentFilterPolicy.shouldKeep(it, blocklist) }
    Column(Modifier.fillMaxSize()) {
        dev.infinityf4p.tiebapure.core.designsystem.ReaderPageHeader("订阅") {
            IconButton(onClick = onBrowseForums) { Icon(Icons.Outlined.GridView, "进吧与管理订阅") }
            IconButton(onClick = viewModel::refresh, enabled = !state.isInitializing) {
                Icon(Icons.Outlined.Refresh, "刷新订阅帖子")
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FeedChoice(state.preferences.kind, FeedSourceKind.entries, { when(it) { FeedSourceKind.All -> "全部"; FeedSourceKind.Forum -> "贴吧"; FeedSourceKind.User -> "用户" } }) {
                viewModel.updatePreferences(state.preferences.copy(kind = it))
            }
            TextButton(onClick = { sourcePicker = true }) { Text("选择来源") }
            FeedChoice(state.preferences.window, FeedWindow.entries, { when(it) { FeedWindow.Day -> "24小时"; FeedWindow.Week -> "近7天"; FeedWindow.Month -> "近30天"; FeedWindow.All -> "不限时间" } }) {
                viewModel.updatePreferences(state.preferences.copy(window = it))
            }
            FeedChoice(state.preferences.sort, FeedSort.entries, { when(it) { FeedSort.Published -> "最新发布"; FeedSort.Replied -> "最新回复"; FeedSort.Replies -> "热度（回复数）" } }) {
                viewModel.updatePreferences(state.preferences.copy(sort = it))
            }
        }
        if (state.pendingCount > 0) TextButton(onClick = viewModel::applyPending, modifier = Modifier.fillMaxWidth()) { Text("有 ${state.pendingCount} 条新内容 · 点击显示") }
        PullToRefreshBox(isRefreshing = state.isBusy || state.isInitializing, onRefresh = viewModel::refresh) {
            LazyColumn(Modifier.fillMaxSize().testTag("subscription-feed"), state = listState,
                contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (state.sources.isEmpty() && !state.isInitializing) item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.GridView, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("让订阅里出现你想看的帖子", style = MaterialTheme.typography.titleMedium)
                        Text("订阅贴吧，或在用户主页本地关注。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onBrowseForums) { Text("去进吧添加订阅") }
                    }
                }
                if (state.subscriptionCount > 0) item {
                    val updated = state.oldestCacheAt?.let {
                        SimpleDateFormat("MM-dd HH:mm", Locale.ROOT).format(Date(it))
                    }
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("已检查 ${state.updatedCount}/${state.subscriptionCount} 个入选来源",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text((updated?.let { "最早缓存 $it · " } ?: "") + when (state.preferences.sort) { FeedSort.Published -> "按已加载发布时间排序"; FeedSort.Replied -> "按已加载最后回复时间排序"; FeedSort.Replies -> "按已加载回复数排序，并非官方热榜" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.warning != null || state.missingTimeCount > 0) item {
                    Text(listOfNotNull(state.warning, if (state.missingTimeCount > 0) "${state.missingTimeCount} 条缺少所需时间，未计入当前范围。" else null).joinToString("\n"), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (state.sources.isNotEmpty() && state.subscriptionCount == 0) item { Text("未选择有效来源，请调整来源或类型。", Modifier.padding(24.dp)) }
                if (state.failedForums.isNotEmpty()) item {
                    Text(state.failedForums.joinToString("\n"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = viewModel::retryFailures, enabled = !state.isBusy) { Text("重试失败的来源") }
                }
                if (state.limitReached) item { Text("已达到本次阅读2000条上限，请缩小来源或时间范围，或重新刷新。", Modifier.padding(16.dp)) }
                items(state.pausedSources, key = { "paused:" + it.key }) { entry ->
                    TextButton(onClick = { viewModel.continueSource(entry.key) }, enabled = !state.isBusy) { Text("${entry.displayName}：连续两页无新匹配 · 继续查找") }
                }
                items(threads, key = { it.id }) { thread ->
                    HomeThreadRow(thread, callbacks, mediaLoadingPolicy)
                }
                if (state.subscriptionCount > 0 && !state.isInitializing) item {
                    if (threads.isEmpty() && !state.isBusy) Text(if (state.failedForums.isNotEmpty()) "更新失败，当前范围暂无可显示缓存。" else if (state.attemptedCount < state.subscriptionCount) "当前范围暂无已加载帖子，仍有来源尚未检查。" else "当前范围暂无已加载帖子，可扩大时间范围或继续汇总。", Modifier.padding(16.dp))
                    if (state.hasMore) TextButton(onClick = viewModel::loadMore, enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isBusy) "正在更新…" else if (state.attemptedCount < state.subscriptionCount)
                            "继续更新下一批来源" else "加载更多帖子")
                    } else if (!state.isBusy && state.failedForums.isEmpty()) {
                        Text("当前没有更多自动加载候选，可调整范围。", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> FeedChoice(value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(label(value)) }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun FeedSourcePicker(sources: List<FeedSubscription>, selected: Set<String>?, onDismiss: () -> Unit, onApply: (Set<String>?) -> Unit) {
    var all by remember { mutableStateOf(selected == null) }
    var draft by remember { mutableStateOf(selected ?: sources.map { it.key }.toSet()) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("展示来源") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("搜索已订阅贴吧、用户") }, singleLine = true)
            Row {
                TextButton(onClick = { all = true; draft = sources.map { it.key }.toSet() }) { Text("全选") }
                TextButton(onClick = { all = false; draft = emptySet() }) { Text("清空") }
            }
            LazyColumn(Modifier.heightIn(max = 350.dp)) {
                listOf(false, true).forEach { isUser ->
                    item { Text(if (isUser) "关注用户" else "订阅贴吧", style = MaterialTheme.typography.labelLarge) }
                    items(sources.filter { it.isUser == isUser && (it.displayName.contains(query, true) || it.name.contains(query, true)) }, key = { it.key }) { entry ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(entry.key in draft, { checked -> all = false; draft = if (checked) draft + entry.key else draft - entry.key })
                            Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Text("仅改变展示，不取消订阅或关注。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { onApply(if (all) null else draft) }) { Text("应用") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
