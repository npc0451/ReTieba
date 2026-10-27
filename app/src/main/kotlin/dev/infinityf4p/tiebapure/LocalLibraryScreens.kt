package dev.infinityf4p.tiebapure

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.infinityf4p.tiebapure.core.data.LocalLibraryRepository
import dev.infinityf4p.tiebapure.core.data.localForumKey
import dev.infinityf4p.tiebapure.core.designsystem.ReaderCard
import dev.infinityf4p.tiebapure.core.designsystem.ReaderNavigationRow
import dev.infinityf4p.tiebapure.core.designsystem.ReaderSectionHeader
import dev.infinityf4p.tiebapure.core.media.AvatarImage
import dev.infinityf4p.tiebapure.core.model.Forum
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LocalForumsRoute(
    repository: LocalLibraryRepository,
    onOpenForum: (Forum) -> Unit,
    onSearch: () -> Unit,
    recentForums: List<Forum> = emptyList(),
    followedUsers: List<dev.infinityf4p.tiebapure.core.model.UserSummary> = emptyList(),
    onOpenUser: (dev.infinityf4p.tiebapure.core.model.UserSummary) -> Unit = {},
    onUnfollowUser: ((Long) -> Unit)? = null,
    onRemoveRecent: (suspend (Forum) -> Unit)? = null,
) {
    val entries by repository.subscriptions.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var recentToRemove by remember { mutableStateOf<Forum?>(null) }
    var managing by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    fun change(action: suspend () -> Unit, success: String) {
        if (busy) return
        busy = true
        isError = false
        message = null
        scope.launch {
            try {
                action()
                message = success
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                isError = true
                message = error.message ?: "本地订阅操作失败，请重试。"
            } finally {
                busy = false
            }
        }
    }
    recentToRemove?.let { forum ->
        AlertDialog(onDismissRequest = { recentToRemove = null },
            title = { Text("移除最近浏览记录？") },
            text = { Text("仅移除${forum.displayName}的最近浏览记录，保留本地订阅。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                recentToRemove = null
                onRemoveRecent?.let { remove -> change({ remove(forum) }, "已移除最近浏览记录。") }
            }) { Text("移除记录") } },
            dismissButton = { TextButton(onClick = { recentToRemove = null }) { Text("取消") } })
    }
    Column(Modifier.fillMaxSize()) {
        dev.infinityf4p.tiebapure.core.designsystem.ReaderPageHeader("进吧") {
            TextButton(onClick = { managing = !managing }, enabled = entries.isNotEmpty() || followedUsers.isNotEmpty()) { Text(if (managing) "完成" else "管理") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("local-forum-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Surface(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("forum-search-entry"),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("搜索贴吧、帖子", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            message?.let { item {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            } }
            if (entries.isEmpty()) item {
                Text("通过搜索找到感兴趣的贴吧并订阅，就能在这里快速进吧。",
                    modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entries.isNotEmpty()) item { ReaderSectionHeader("已订阅贴吧", trailing = "${entries.size}", contentPadding = PaddingValues(vertical = 8.dp)) }
            items(entries, key = { it.normalizedName }) { entry ->
                ReaderCard(modifier = Modifier.testTag("local-subscription-${entry.normalizedName}"), cornerRadius = 16.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), onClick = { onOpenForum(entry.toForum()) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarImage(entry.avatarUrl, entry.displayName, Modifier.size(32.dp))
                        Text(entry.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (managing) TextButton(onClick = { change({ repository.unsubscribe(entry.name) }, "已取消本地订阅。") },
                            enabled = !busy) { Text("取消订阅") }
                        else Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (followedUsers.isNotEmpty()) item { ReaderSectionHeader("关注的人", trailing = "${followedUsers.size}", contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp)) }
            items(followedUsers, key = { "user-${it.id}" }) { user ->
                ReaderCard(cornerRadius = 16.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), onClick = { onOpenUser(user) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(user.portrait, user.resolvedDisplayName, Modifier.size(32.dp))
                        Text(user.resolvedDisplayName, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!managing) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (managing) onUnfollowUser?.let { remove -> TextButton(onClick = { remove(user.id) }) { Text("取消关注") } }
                    }
                }
            }
            if (recentForums.isNotEmpty()) item { ReaderSectionHeader("最近浏览", contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp)) }
            items(recentForums.distinctBy { localForumKey(it.name) }, key = { "recent-${it.name}" }) { forum ->
                ReaderNavigationRow(forum.displayName,
                    modifier = Modifier.testTag("recent-forum-${localForumKey(forum.name)}"),
                    onLongClick = onRemoveRecent?.let { { if (!busy) recentToRemove = forum } },
                    onClick = { onOpenForum(forum) })
            }
        }
    }
}

@Composable
fun LocalBookmarksRoute(
    repository: LocalLibraryRepository,
    offlineThreadIds: Set<Long>,
    onOpenThread: (Long) -> Unit,
    onOpenOfflineThread: (Long) -> Unit,
    onOpenOfflineLibrary: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val entries by repository.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var busyIds by remember { mutableStateOf(emptySet<Long>()) }
    var message by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        dev.infinityf4p.tiebapure.core.designsystem.ReaderPageHeader("收藏") {
            IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, "浏览历史") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("收藏保存书签，离线保存需单独下载。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ReaderNavigationRow("离线保存", icon = Icons.Outlined.DownloadDone, subtitle = "已下载的正文和媒体", trailing = "${offlineThreadIds.size} 个帖子", onClick = onOpenOfflineLibrary)
            }
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (entries.isEmpty()) item {
                Text("还没有本地收藏。在帖子页点击书签图标即可收藏。",
                    modifier = Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(entries, key = { it.threadId }) { entry ->
                ReaderCard(cornerRadius = 16.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), onClick = { onOpenThread(entry.threadId) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.title, style = dev.infinityf4p.tiebapure.core.designsystem.readerContentStyle(16,22).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 3,
                            overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(entry.forumName, entry.authorName).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (entry.threadId in offlineThreadIds) {
                                TextButton(onClick = { onOpenOfflineThread(entry.threadId) }) { Text("离线阅读") }
                            }
                            TextButton(
                                enabled = entry.threadId !in busyIds,
                                onClick = {
                                    if (entry.threadId !in busyIds) {
                                        busyIds = busyIds + entry.threadId
                                        scope.launch {
                                            try {
                                                repository.removeBookmark(entry.threadId)
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                message = error.message ?: "取消收藏失败，请重试。"
                                            } finally {
                                                busyIds = busyIds - entry.threadId
                                            }
                                        }
                                    }
                                },
                            ) { Text("取消收藏") }
                        }
                    }
                }
            }
        }
    }
}
