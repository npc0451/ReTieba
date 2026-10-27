package dev.infinityf4p.tiebapure.core.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.toBitmap
import coil3.request.ImageRequest
import coil3.request.allowHardware
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import dev.infinityf4p.tiebapure.core.model.ImageContent
import dev.infinityf4p.tiebapure.core.model.VideoContent
import java.io.File

@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val safeUrl = url?.takeIf { MediaUrlPolicy.isAllowed(it) || OfflineMediaPolicy.resolve(it) != null }
    val context = LocalContext.current.applicationContext
    val loader = remember { OriginalImageLoader(context) }
    val scope = rememberCoroutineScope()
    val wantsColor = dev.infinityf4p.tiebapure.core.designsystem.LocalCommunityColors.current && contentDescription?.endsWith("的头像") == true
    var model by remember(safeUrl) { mutableStateOf<File?>(null) }
    var fileLease by remember(safeUrl) { mutableStateOf<MediaFileReadLease?>(null) }
    androidx.compose.runtime.DisposableEffect(safeUrl) { onDispose { fileLease?.close() } }
    var loading by remember(safeUrl) { mutableStateOf(safeUrl != null) }
    LaunchedEffect(safeUrl) {
        try {
            fileLease = safeUrl?.let { loader.acquire(it) }
            model = fileLease?.file
        }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: Exception) { model = null }
        finally { loading = false }
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
            else if (contentDescription?.endsWith("的头像") == true) Text(
                text = contentDescription?.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            ) else Icon(Icons.Outlined.BrokenImage, "图片暂时无法显示", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val imageModel = remember(model, wantsColor) {
                if (wantsColor) ImageRequest.Builder(context).data(model).size(64, 64).allowHardware(false).build() else model
            }
            AsyncImage(
                model = imageModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onSuccess = { success ->
                    if (safeUrl != null && wantsColor && safeUrl !in avatarColors) scope.launch {
                        val color = withContext(Dispatchers.Default) {
                            runCatching {
                                val bitmap = success.result.image.toBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
                                val pixels = IntArray(1024)
                                bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                                dominantAvatarColor(pixels)
                            }.getOrNull()
                        }
                        if (color != null) {
                            if (avatarColors.size >= 128) avatarColors.keys.firstOrNull()?.let(avatarColors::remove)
                            avatarColors[safeUrl] = color
                        }
                    }
                },
            )
        }
    }
}

val LocalImageSaveRequest = staticCompositionLocalOf<((ImageContent) -> Unit)?> { null }

@Composable
fun AvatarImage(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
) {
    RemoteImage(
        url = url,
        contentDescription = "$name 的头像",
        modifier = modifier.clip(CircleShape),
    )
}

@Composable
fun MediaGrid(
    images: List<ImageContent>,
    onImageClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveImage = LocalImageSaveRequest.current
    var pendingSave by remember { mutableStateOf<ImageContent?>(null) }
    pendingSave?.let { image ->
        AlertDialog(onDismissRequest = { pendingSave = null }, title = { Text("图片") },
            confirmButton = { TextButton(onClick = { pendingSave = null; saveImage?.invoke(image) }) { Text("保存图片") } },
            dismissButton = { TextButton(onClick = { pendingSave = null }) { Text("取消") } })
    }
    if (images.isEmpty()) return

    if (images.size == 1) {
        val image = images.first()
        RemoteImage(
            url = image.thumbnailUrl ?: image.originalUrl,
            contentDescription = "帖子图片 1",
            modifier = modifier
                .widthIn(max = 320.dp)
                .fillMaxWidth(if (image.aspectRatio >= 1.25) 1f else 0.72f)
                .aspectRatio(image.aspectRatio.toFloat().coerceIn(0.65f, 1.8f))
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = { onImageClick(0) }, onLongClick = if (saveImage == null) null else ({ pendingSave = image })),
        )
        return
    }

    val columns = mediaGridColumnCount(images.size)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        images.chunked(columns).forEachIndexed { rowIndex, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowImages.forEachIndexed { columnIndex, image ->
                    val index = rowIndex * columns + columnIndex
                    RemoteImage(
                        url = image.thumbnailUrl ?: image.originalUrl,
                        contentDescription = "帖子图片 ${index + 1}",
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = { onImageClick(index) }, onLongClick = if (saveImage == null) null else ({ pendingSave = image })),
                    )
                }
                repeat(columns - rowImages.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun mediaGridColumnCount(imageCount: Int): Int = when (imageCount) {
    2, 4 -> 2
    else -> 3
}

val LocalMediaFileSaveRequest = staticCompositionLocalOf<((dev.infinityf4p.tiebapure.core.model.ContentBlock) -> Unit)?> { null }

@Composable
fun VideoPreview(
    video: VideoContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saveFile = LocalMediaFileSaveRequest.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(video.aspectRatio.toFloat().coerceIn(0.7f, 1.8f))
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = saveFile?.let { save -> { save(dev.infinityf4p.tiebapure.core.model.ContentBlock.Video(video)) } })
            .semantics { contentDescription = "播放视频" },
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            url = video.coverUrl,
            contentDescription = "视频封面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        if (video.durationSeconds > 0) {
            Text(
                text = formatDuration(video.durationSeconds),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.64f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun OriginalImageBar(
    image: ImageContent,
    state: OriginalImageState,
    onRequestOriginal: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is OriginalImageState.Loading -> {
                val percent = (state.progress * 100).toInt().coerceIn(0, 100)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Spacer(
                        Modifier
                            .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                            .height(30.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    )
                    Text(
                        text = "$percent%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            OriginalImageState.Ready -> Text(
                text = "已查看原图",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            is OriginalImageState.Failed,
            OriginalImageState.Preview,
            -> Surface(
                onClick = onRequestOriginal,
                modifier = Modifier.weight(1f).height(36.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.White.copy(alpha = 0.14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = buildOriginalLabel(image, state is OriginalImageState.Failed),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Download, contentDescription = "下载图片", tint = Color.White)
        }
    }
}

internal fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

internal fun formatByteCount(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let {
    when {
        it >= 1024L * 1024L -> "%.1fMB".format(it.toDouble() / (1024 * 1024))
        it >= 1024L -> "%.1fKB".format(it.toDouble() / 1024)
        else -> "${it}B"
    }
}

private fun buildOriginalLabel(image: ImageContent, retry: Boolean): String {
    val prefix = if (retry) "重试原图" else "查看原图"
    return formatByteCount(image.originalSizeBytes)?.let { "$prefix $it" } ?: prefix
}
