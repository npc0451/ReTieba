package dev.infinityf4p.tiebapure.core.media

import java.io.Closeable
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaFileReadLease internal constructor(val file: File, private val release: () -> Unit) : Closeable {
    override fun close() = release()
}

internal suspend fun acquireMediaFile(file: File, load: suspend () -> Unit): MediaFileReadLease {
    var acquired: MediaFileReadLease? = null
    try {
        return withContext(Dispatchers.IO) {
            val lease = MediaCacheFiles.protect(file)
            acquired = lease
            load()
            lease
        }
    } catch (error: Throwable) {
        acquired?.close()
        throw error
    }
}

/** File ownership and cleanup share one lock so acquisition cannot race eviction. */
object MediaCacheFiles {
    private val lock = Any()
    private val trimLock = Any()
    private val expiryLock = Any()
    private val readers = mutableMapOf<String, Int>()
    private val maintenanceSignals = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val maintenanceRequests = maintenanceSignals.asSharedFlow()
    fun requestMaintenance() { maintenanceSignals.tryEmit(Unit) }
    private val lastExpiry = mutableMapOf<String, Long>()
    private val imageName = Regex("[a-f0-9]{64}")
    private val emoticonName = Regex("[a-f0-9]{64}\\.png")
    private val temporaryName = Regex("TiebaPure-[A-Za-z0-9-]+\\.(image|png|mp4|audio)")
    private val temporaryDirectories = setOf("trusted-images", "tieba-emoticons", "video-playback", "voice-playback", "image-exports")

    fun protect(file: File): MediaFileReadLease = synchronized(lock) {
        val key = file.canonicalPath
        readers[key] = (readers[key] ?: 0) + 1
        var closed = false
        MediaFileReadLease(file) {
            val becameUnused = synchronized(lock) {
                if (!closed) {
                    closed = true
                    val count = (readers[key] ?: 1) - 1
                    if (count == 0) readers.remove(key) else readers[key] = count
                    count == 0
                } else false
            }
            if (becameUnused) requestMaintenance()
        }
    }

    internal fun createTemporary(directory: File, suffix: String): TemporaryMediaFileLease = synchronized(lock) {
        TemporaryMediaFileLease(File.createTempFile("TiebaPure-", suffix, directory))
    }

    fun isInUse(file: File): Boolean = synchronized(lock) { readers.containsKey(file.canonicalPath) }
    fun isImageName(name: String) = imageName.matches(name)
    fun isEmoticonName(name: String) = emoticonName.matches(name)
    fun isTemporaryName(name: String) = temporaryName.matches(name)
    fun isTemporaryDirectory(name: String) = name in temporaryDirectories

    internal fun deleteInactive(file: File): Boolean = synchronized(lock) {
        !readers.containsKey(file.canonicalPath) && file.delete()
    }

    private fun files(root: File, name: String): List<File> {
        val expected = File(root.canonicalFile, name)
        if (expected.canonicalFile != expected) return emptyList()
        return expected.listFiles().orEmpty().filter { it.isFile && it.canonicalFile == it.absoluteFile }
    }

    fun imageBytes(root: File) = files(root, "trusted-images").filter { isImageName(it.name) }.sumOf(File::length)
    fun emoticonBytes(root: File) = files(root, "tieba-emoticons").filter { isEmoticonName(it.name) }.sumOf(File::length)

    fun clearImages(root: File): Long {
        return (files(root, "trusted-images").filter { isImageName(it.name) } +
            files(root, "tieba-emoticons").filter { isEmoticonName(it.name) }).sumOf {
            val bytes = it.length()
            if (deleteInactive(it)) bytes else 0L
        }
    }

    fun trimImages(root: File, limitBytes: Long, preserving: File? = null, targetBytes: Long = limitBytes): Long = synchronized(trimLock) {
        require(limitBytes >= 0 && targetBytes in 0..limitBytes)
        val cached = files(root, "trusted-images").filter { isImageName(it.name) }
        if (cached.sumOf(File::length) <= limitBytes) return@synchronized 0L
        trimFiles(cached, targetBytes, preserving)
    }

    private fun trimFiles(cached: List<File>, targetBytes: Long, preserving: File? = null): Long {
        var total = cached.sumOf(File::length)
        var released = 0L
        for (file in cached.sortedWith(compareBy<File>(File::lastModified).thenBy(File::getName))) {
            if (total <= targetBytes) break
            if (file == preserving) continue
            val size = file.length()
            if (deleteInactive(file)) { total -= size; released += size }
        }
        return released
    }

    internal fun imageTrimTarget(limitBytes: Long) = (limitBytes * .85).toLong()

    internal fun storageReserveBytes(totalBytes: Long): Long =
        (totalBytes / 20).coerceIn(256L * 1_024 * 1_024, 1_024L * 1_024 * 1_024)

    /** Only disposable cache files are candidates; active leases and unknown/offline paths are preserved. */
    fun maintain(
        root: File,
        imageLimitBytes: Long,
        storageLow: Boolean = false,
        totalBytes: Long = root.totalSpace,
        freeBytes: Long = root.usableSpace,
    ): Long = synchronized(trimLock) {
        require(imageLimitBytes >= 0 && totalBytes >= 0 && freeBytes >= 0)
        var released = expireAll(root)
        val reserve = storageReserveBytes(totalBytes)
        val low = storageLow || totalBytes > 0 && freeBytes < reserve
        if (low) released += clearTemporary(root)
        val images = files(root, "trusted-images").filter { isImageName(it.name) }
        val imageBytes = images.sumOf(File::length)
        var needed = if (low) (reserve - freeBytes - released).coerceAtLeast(0) else 0
        // A system low-storage signal may use a larger threshold than our own reserve.
        if (storageLow && freeBytes >= reserve) needed = maxOf(needed, imageBytes / 2)
        val normalTarget = if (imageBytes > imageLimitBytes) imageTrimTarget(imageLimitBytes) else imageBytes
        val target = minOf(normalTarget, (imageBytes - needed).coerceAtLeast(0))
        val imageReleased = trimFiles(images, target)
        released += imageReleased
        needed = (needed - imageReleased).coerceAtLeast(0)
        if (needed > 0) {
            val emoticons = files(root, "tieba-emoticons").filter { isEmoticonName(it.name) }
            released += trimFiles(emoticons, (emoticons.sumOf(File::length) - needed).coerceAtLeast(0))
        }
        released
    }

    fun clearTemporary(root: File): Long = temporaryDirectories.sumOf { name ->
        clearDirectory(File(root.canonicalFile, name), Long.MAX_VALUE)
    }

    fun expireAll(root: File): Long = temporaryDirectories.sumOf { expire(File(root.canonicalFile, it)) }

    internal fun expire(directory: File, now: Long = System.currentTimeMillis()): Long = synchronized(expiryLock) {
        val key = directory.canonicalPath
        val previous = lastExpiry[key]
        if (previous != null && now >= previous && now - previous < TimeUnit.HOURS.toMillis(1)) return@synchronized 0L
        val released = clearDirectory(directory, now - TimeUnit.HOURS.toMillis(24))
        lastExpiry[key] = now
        released
    }

    private fun clearDirectory(directory: File, cutoff: Long): Long {
        if (directory.canonicalFile != directory.absoluteFile) return 0L
        return directory.listFiles().orEmpty().filter {
            it.isFile && it.canonicalFile == it.absoluteFile && isTemporaryName(it.name) && it.lastModified() < cutoff
        }.sumOf {
            val bytes = it.length()
            if (deleteInactive(it)) bytes else 0L
        }
    }
}
