package dev.infinityf4p.tiebapure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import java.io.File
import dev.infinityf4p.tiebapure.core.designsystem.ReaderNavigationRow
import dev.infinityf4p.tiebapure.core.designsystem.ReaderSectionHeader
import dev.infinityf4p.tiebapure.core.media.ReaderCachePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun StorageSettingsScreen(container: AppContainer, onOpenOffline: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val files = remember(context) { ReaderCacheFiles(context.cacheDir) }
    val offline by container.savedThreads.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    var usage by remember { mutableStateOf(ReaderStorageSnapshot()) }
    var measured by remember { mutableStateOf(false) }
    var summaries by remember { mutableLongStateOf(0) }
    var limit by remember { mutableIntStateOf(ReaderCachePreferences.imageLimitMb(context)) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun measure() {
        val result = withContext(Dispatchers.IO) {
            val info = context.applicationInfo
            val summaries = container.database.localLibraryDao().feedCacheBytes() + container.database.localUserFollowsDao().cacheBytes()
            measureReaderStorage(File(info.dataDir), (listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()).map(::File)) to summaries
        }
        usage = result.first
        summaries = result.second
        measured = true
    }
    fun perform(action: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { action() }; measure(); message = result }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                message = error.message ?: "操作失败，请重试。"
                try { measure() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
            }
            finally { limit = ReaderCachePreferences.imageLimitMb(context); busy = false }
        }
    }
    LaunchedEffect(Unit) {
        try { measure() } catch (error: CancellationException) { throw error }
        catch (error: Exception) { message = error.message ?: "无法统计缓存。" }
        finally { busy = false }
    }
    fun usageLabel(bytes: Long) = if (measured) cacheSizeLabel(bytes) else "统计中…"
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ReaderSectionHeader("文件占用") }
        item { ListItem(headlineContent = { Text("安装包文件") }, trailingContent = { Text(if (measured) usageLabel(usage.apk) else "统计中…") }) }
        item { ListItem(headlineContent = { Text("应用私有文件合计") }, trailingContent = { Text(if (measured) usageLabel(usage.privateFiles) else "统计中…") }) }
        item { Text("以下为可读取文件的大小快照；不含系统共享的编译文件和已导出到相册的内容，与系统设置的总占用可能不同。",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ReaderSectionHeader("私有文件分项") }
        item { ListItem(headlineContent = { Text("图片缓存") }, supportingContent = { Text("头像、缩略图及已查看的大图；按下载文件字节统计，不等同于系统缓存总量") }, trailingContent = { Text(usageLabel(usage.images)) }) }
        item { ListItem(headlineContent = { Text("表情缓存") }, trailingContent = { Text(usageLabel(usage.emoticons)) }) }
        item { ListItem(headlineContent = { Text("下载与播放临时文件") }, supportingContent = { Text("正在使用 ${usageLabel(usage.activeTemporary)}") }, trailingContent = { Text(usageLabel(usage.temporary)) }) }
        item { ListItem(headlineContent = { Text("数据库及日志") }, supportingContent = { Text("摘要内容 ${cacheSizeLabel(summaries)}，已包含在本项内") }, trailingContent = { Text(usageLabel(usage.database)) }) }
        item { ListItem(headlineContent = { Text("离线媒体文件") }, trailingContent = { Text(usageLabel(usage.offlineMedia)) }) }
        item { ListItem(headlineContent = { Text("其他本地文件") }, supportingContent = { Text("包含阅读字体、偏好和其他私有缓存") }, trailingContent = { Text(usageLabel(usage.other)) }) }
        item { TextButton(enabled = !busy, onClick = { perform { "已重新统计文件大小。" } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("重新统计") } }
        item { Text("清理缓存会保留正在使用的媒体，以及订阅、关注、收藏、历史、阅读位置和离线保存。",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { ReaderSectionHeader("图片缓存目标上限") }
        item { Text("图片按最近使用淘汰；自动超限整理至目标的85%。使用结束或返回前台后安排空闲整理，空闲整理最多每分钟一次；下载超限时会即时淘汰。正在使用的媒体可能令占用暂时超限。", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(ReaderCachePreferences.imageLimitsMb) { value ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                RadioButton(selected = limit == value, enabled = !busy, onClick = {
                    perform {
                        ReaderCachePreferences.setImageLimitMb(context, value)
                        val released = files.trimImages(value.toLong() * 1_024 * 1_024)
                        "已设置为 $value MiB，清理 ${cacheSizeLabel(released)} 图片缓存。"
                    }
                    limit = value
                })
                Text("$value MiB", Modifier.padding(vertical = 12.dp))
            }
        }
        item { TextButton(enabled = !busy, onClick = { perform { "已清理 ${cacheSizeLabel(files.clear())} 图片与表情缓存；正在使用或无法删除的文件会保留。" } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清理图片与表情缓存") } }
        item { TextButton(enabled = !busy, onClick = { perform { "已清理 ${cacheSizeLabel(files.clearTemporary())} 临时文件；正在使用或无法删除的文件会保留。" } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清理闲置临时文件") } }
        item { TextButton(enabled = !busy, onClick = { perform {
            container.subscriptionFeed.clearCache()
            "已清理摘要内容，数据库文件中的空闲空间会复用。"
        } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("清理订阅摘要") } }
        item { Text("存储不足时优先清理闲置临时文件，再整理旧图片与表情。清理不会删除离线保存；文件删除后实际空闲容量以系统为准。",
            Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        item { ReaderSectionHeader("离线保存") }
        item { ReaderNavigationRow("管理离线保存", trailing = cacheSizeLabel(offline.sumOf { it.mediaBytes + it.snapshotBytes }), onClick = onOpenOffline) }
        item { Text("离线保存为主动下载，不随缓存上限清理。订阅和书签也独立保留。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        if (busy) item { CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp)) }
        message?.let { item { Text(it, Modifier.padding(16.dp)) } }
    }
}

internal fun cacheSizeLabel(bytes: Long): String {
    val size = bytes.coerceAtLeast(0)
    return when {
        size < 1_024 -> "$size B"
        size < 1_048_576 -> String.format(Locale.ROOT, "%.1f KiB", size / 1024.0)
        size < 1_073_741_824 -> String.format(Locale.ROOT, "%.1f MiB", size / 1048576.0)
        else -> String.format(Locale.ROOT, "%.1f GiB", size / 1073741824.0)
    }
}
