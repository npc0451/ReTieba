package dev.infinityf4p.tiebapure

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.infinityf4p.tiebapure.core.media.SecureVideoDownloadClient
import dev.infinityf4p.tiebapure.core.media.SecureVoiceAudioDownloadClient
import dev.infinityf4p.tiebapure.core.model.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberMediaFileExport(onMessage: (String) -> Unit): (ContentBlock) -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onMessage)
    var pending by remember { mutableStateOf<ContentBlock?>(null) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val block = pending
        pending = null
        if (uri != null && block != null) scope.launch {
            busy = true
            try {
                report("正在保存媒体文件…")
                withContext(Dispatchers.IO) {
                    val lease: AutoCloseable
                    val file: java.io.File
                    when (block) {
                        is ContentBlock.Video -> {
                            val video = SecureVideoDownloadClient(context).download(block.value.videoUrl ?: error("视频地址不可用。"))
                            lease = video; file = video.file
                        }
                        is ContentBlock.Voice -> {
                            val voice = SecureVoiceAudioDownloadClient(context).download(block.value)
                            lease = voice; file = voice.file
                        }
                        else -> error("不支持的媒体文件。")
                    }
                    lease.use {
                        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                            file.inputStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                }
                            }
                        } ?: error("无法写入所选文件。")
                    }
                }
                report("媒体文件已保存。")
            } catch (error: Exception) {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }
                }
                if (error is CancellationException) throw error
                report(error.message ?: "媒体保存失败，请重试。")
            } finally { busy = false }
        }
    }
    return { block ->
        if (busy || pending != null) report("请先完成当前保存。")
        else {
            pending = block
            // shortcut: audio encoding varies; keep a neutral suffix until the downloader exposes its detected format.
            val extension = if (block is ContentBlock.Video) "mp4" else "audio"
            try { launcher.launch("TiebaReader-${System.currentTimeMillis()}.$extension") }
            catch (error: Exception) { pending = null; report(error.message ?: "无法打开文件保存窗口。") }
        }
    }
}
