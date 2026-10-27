package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import java.io.File

internal data class ReaderStorageSnapshot(
    val apk: Long = 0,
    val database: Long = 0,
    val images: Long = 0,
    val emoticons: Long = 0,
    val temporary: Long = 0,
    val activeTemporary: Long = 0,
    val offlineMedia: Long = 0,
    val other: Long = 0,
) {
    val privateFiles get() = database + images + emoticons + temporary + offlineMedia + other
}

/** Counts each private file once; symbolic links never lead a scan outside the application. */
internal fun measureReaderStorage(dataDirectory: File, apkFiles: List<File>): ReaderStorageSnapshot {
    val root = dataDirectory.canonicalFile
    var database = 0L; var images = 0L; var emoticons = 0L; var temporary = 0L
    var active = 0L; var offline = 0L; var other = 0L
    root.walkTopDown().onEnter { it.canonicalFile == it.absoluteFile }.forEach { file ->
        if (!file.isFile || file.canonicalFile != file.absoluteFile) return@forEach
        val path = file.relativeTo(root).invariantSeparatorsPath
        val parts = path.split('/')
        val bytes = file.length()
        when {
            path.startsWith("databases/") -> database += bytes
            path.startsWith("files/saved-thread-media/") -> offline += bytes
            parts.size == 3 && parts[0] == "cache" && parts[1] == "trusted-images" && MediaCacheFiles.isImageName(file.name) -> images += bytes
            parts.size == 3 && parts[0] == "cache" && parts[1] == "tieba-emoticons" && MediaCacheFiles.isEmoticonName(file.name) -> emoticons += bytes
            parts.size == 3 && parts[0] == "cache" && MediaCacheFiles.isTemporaryDirectory(parts[1]) && MediaCacheFiles.isTemporaryName(file.name) -> {
                temporary += bytes
                if (MediaCacheFiles.isInUse(file)) active += bytes
            }
            else -> other += bytes
        }
    }
    return ReaderStorageSnapshot(apkFiles.map { it.canonicalFile }.distinct().sumOf(File::length),
        database, images, emoticons, temporary, active, offline, other)
}
