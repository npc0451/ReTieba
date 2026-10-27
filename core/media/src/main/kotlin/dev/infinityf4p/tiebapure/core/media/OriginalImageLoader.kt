package dev.infinityf4p.tiebapure.core.media

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

class OriginalImageLoader internal constructor(
    context: Context,
    client: OkHttpClient,
) {
    private val appContext = context.applicationContext
    constructor(context: Context) : this(context, defaultMediaHttpClient)

    private val cacheDirectory = File(context.cacheDir, "trusted-images")
    private val downloader = SecureMediaDownloader(
        directory = cacheDirectory,
        suppliedClient = client,
        maximumBytes = MAXIMUM_IMAGE_BYTES,
        kind = RemoteMediaKind.Image,
    )

    suspend fun acquire(url: String, onProgress: (Float) -> Unit = {}): MediaFileReadLease {
        val destination = OfflineMediaPolicy.resolve(url) ?: File(cacheDirectory, url.sha256())
        return acquireMediaFile(destination) { load(url, onProgress) }
    }

    suspend fun load(
        url: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        downloadLocks[(url.hashCode() and Int.MAX_VALUE) % downloadLocks.size].withLock {
            OfflineMediaPolicy.resolve(url)?.let { local ->
                require(MediaFileSignatures.isSupportedImage(local)) { "Unsupported local image" }
                onProgress(1f)
                return@withContext local
            }
            require(MediaUrlPolicy.isAllowed(url)) { "Unsupported media URL" }
            val destination = File(cacheDirectory, url.sha256())
            if (destination.isFile &&
                destination.length() in 1..MAXIMUM_IMAGE_BYTES &&
                MediaFileSignatures.isSupportedImage(destination)
            ) {
                destination.setLastModified(System.currentTimeMillis())
                onProgress(1f)
                return@withContext destination
            }
            destination.delete()

            val downloaded = downloadPermits.withPermit { downloader.download(url, onProgress) }
            try {
                val committed = downloaded.lease.moveTo(destination)
                val limit = ReaderCachePreferences.imageLimitMb(appContext).toLong() * 1_024 * 1_024
                MediaCacheFiles.trimImages(cacheDirectory.parentFile!!, limit, preserving = committed,
                    targetBytes = MediaCacheFiles.imageTrimTarget(limit))
                MediaCacheFiles.requestMaintenance()
                committed
            } catch (error: Throwable) {
                downloaded.lease.release()
                throw error
            }
        }
    }

    private companion object {
        val downloadLocks = Array(8) { Mutex() }
        val downloadPermits = Semaphore(4)
        const val MAXIMUM_IMAGE_BYTES = 96L * 1_024 * 1_024
    }
}

/**
 * A validated private file for an explicit user-requested image save.
 * Copy it to MediaStore and always call [release] (or use [close]) afterwards.
 */
class DownloadedImageLease internal constructor(
    val file: File,
    val mimeType: String,
    val byteCount: Long,
    private val lease: TemporaryMediaFileLease,
) : AutoCloseable {
    fun release() = lease.release()

    override fun close() = release()
}

/** Fetches an image without exposing redirects or untrusted response bytes to DownloadManager. */
class SecureImageDownloadClient internal constructor(
    context: Context,
    client: OkHttpClient,
) {
    constructor(context: Context) : this(context, defaultMediaHttpClient)

    private val directory = File(context.cacheDir, "image-exports")
    private val downloader = SecureMediaDownloader(
        directory = directory,
        suppliedClient = client,
        maximumBytes = MAXIMUM_IMAGE_BYTES,
        kind = RemoteMediaKind.Image,
    )

    suspend fun download(
        url: String,
        onProgress: (Float) -> Unit = {},
    ): DownloadedImageLease {
        OfflineMediaPolicy.resolve(url)?.let { local ->
            val mimeType = MediaFileSignatures.imageMimeTypes.firstOrNull {
                MediaFileSignatures.matches(local, it, RemoteMediaKind.Image)
            } ?: throw IllegalArgumentException("Unsupported local image")
            val lease = MediaCacheFiles.createTemporary(directory.apply { mkdirs() }, ".image")
            try {
                withContext(Dispatchers.IO) { local.copyTo(lease.file, overwrite = true) }
                onProgress(1f)
                return DownloadedImageLease(lease.file, mimeType, lease.file.length(), lease)
            } catch (error: Throwable) { lease.release(); throw error }
        }
        val downloaded = downloader.download(url, onProgress)
        return DownloadedImageLease(
            file = downloaded.lease.file,
            mimeType = downloaded.mimeType,
            byteCount = downloaded.byteCount,
            lease = downloaded.lease,
        )
    }

    private companion object {
        const val MAXIMUM_IMAGE_BYTES = 96L * 1_024 * 1_024
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
