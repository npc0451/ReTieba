package dev.infinityf4p.tiebapure.feature.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.infinityf4p.tiebapure.core.model.Post
import dev.infinityf4p.tiebapure.core.model.Subpost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class CommentSearchEntry(val parent: Post, val subpost: Subpost? = null) {
    val id get() = "${parent.id}:${subpost?.id ?: 0u}"
    val text get() = subpostPreviewText(subpost?.blocks ?: parent.blocks)
    val author get() = (subpost?.author ?: parent.author).resolvedDisplayName
}
internal fun matchLoadedComments(entries: List<CommentSearchEntry>, query: String): List<CommentSearchEntry> {
    val word = query.trim()
    return if (word.isEmpty()) emptyList() else entries.distinctBy { it.id }.filter { it.text.contains(word, ignoreCase = true) }
}

@Composable
internal fun CommentSearchDialog(entries: List<CommentSearchEntry>, onDismiss: () -> Unit, onSelect: (CommentSearchEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results by produceState(emptyList<CommentSearchEntry>(), query, entries) {
        delay(150)
        value = withContext(Dispatchers.Default) { matchLoadedComments(entries, query) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("搜索评论") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("输入评论文字") })
            Text("仅搜索已加载评论，不代表全帖；不会自动扫描未加载页。", style = MaterialTheme.typography.bodySmall)
            Text("${results.size} 条匹配", style = MaterialTheme.typography.labelMedium)
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(results, key = { it.id }) { result ->
                    val text = result.text
                    val start = text.indexOf(query.trim(), ignoreCase = true)
                    val from = (start - 30).coerceAtLeast(0)
                    val to = (start + query.trim().length + 70).coerceAtMost(text.length)
                    val snippet = text.substring(from, to)
                    Column(Modifier.fillMaxWidth().clickable { onSelect(result) }.padding(vertical = 12.dp)) {
                        Text("${if (result.parent.floor > 0) "${result.parent.floor}楼" else "楼层未知"} · ${result.author}", style = MaterialTheme.typography.labelMedium)
                        Text(AnnotatedString(snippet, listOf(AnnotatedString.Range(SpanStyle(fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary), (start - from).coerceAtLeast(0),
                            (start - from + query.trim().length).coerceIn(0, snippet.length)))),
                            style = dev.infinityf4p.tiebapure.core.designsystem.readerContentStyle(16,22))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
