package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import java.io.File

internal class ReaderCacheFiles(private val root: File) {
    fun imageBytes() = MediaCacheFiles.imageBytes(root)
    fun emoticonBytes() = MediaCacheFiles.emoticonBytes(root)
    fun clear() = MediaCacheFiles.clearImages(root)
    fun trimImages(limitBytes: Long) = MediaCacheFiles.trimImages(root, limitBytes)
    fun clearTemporary() = MediaCacheFiles.clearTemporary(root)
}
