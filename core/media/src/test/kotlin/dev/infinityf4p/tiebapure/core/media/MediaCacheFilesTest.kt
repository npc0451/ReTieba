package dev.infinityf4p.tiebapure.core.media

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaCacheFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(directory: String, name: String, bytes: Int) = File(temp.root, "$directory/$name").apply {
        parentFile!!.mkdirs(); writeBytes(ByteArray(bytes))
    }

    @Test fun automaticImageTrimOnlyCrossesTheHighWatermarkThenTargetsEightyFivePercent() {
        val first = file("trusted-images", "1".repeat(64), 40).apply { setLastModified(1) }
        val second = file("trusted-images", "2".repeat(64), 35).apply { setLastModified(2) }
        assertEquals(0L, MediaCacheFiles.trimImages(temp.root, 100, targetBytes = 85))
        val third = file("trusted-images", "3".repeat(64), 35).apply { setLastModified(3) }
        assertEquals(40L, MediaCacheFiles.trimImages(temp.root, 100, targetBytes = 85))
        assertFalse(first.exists()); assertTrue(second.exists()); assertTrue(third.exists())
    }

    @Test fun idleMaintenanceRetriesImagesThatPreviouslyHadReaders() {
        val active = file("trusted-images", "4".repeat(64), 80)
        file("trusted-images", "5".repeat(64), 20)
        MediaCacheFiles.protect(active).use {
            assertEquals(20L, MediaCacheFiles.maintain(temp.root, 64, totalBytes = 20L shl 30, freeBytes = 2L shl 30))
            assertTrue(active.exists())
        }
        assertEquals(80L, MediaCacheFiles.maintain(temp.root, 64, totalBytes = 20L shl 30, freeBytes = 2L shl 30))
        assertFalse(active.exists())
    }

    @Test fun lowStorageClearsIdleTemporaryFilesBeforeOldImagesAndPreservesRecentImages() {
        val reserve = 256L shl 20
        file("image-exports", "TiebaPure-unused.image", 3)
        val old = file("trusted-images", "6".repeat(64), 5).apply { setLastModified(1) }
        val recent = file("trusted-images", "7".repeat(64), 4)
        val emoticon = file("tieba-emoticons", "8".repeat(64) + ".png", 4)
        val offline = file("offline", "TiebaPure-keep.mp4", 9)
        assertEquals(8L, MediaCacheFiles.maintain(temp.root, 100, totalBytes = 1L shl 30, freeBytes = reserve - 8))
        assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(emoticon.exists()); assertTrue(offline.exists())
    }

    @Test fun lowStorageCannotDeleteActiveOrUnknownFilesEvenWhenTheReserveCannotBeReached() {
        val image = file("trusted-images", "9".repeat(64), 6)
        val foreign = file("trusted-images", "unknown-image", 9)
        val offline = file("offline", "TiebaPure-keep.mp4", 9)
        file("trusted-images", "a".repeat(64), 5)
        file("tieba-emoticons", "b".repeat(64) + ".png", 7)
        MediaCacheFiles.protect(image).use {
            assertEquals(12L, MediaCacheFiles.maintain(temp.root, 100, totalBytes = 1L shl 30,
                freeBytes = (256L shl 20) - 20))
            assertTrue(image.exists()); assertTrue(foreign.exists()); assertTrue(offline.exists())
        }
    }

    @Test fun systemPressureCanUseAHigherThresholdAndReserveScalesWithinBounds() {
        assertEquals(256L shl 20, MediaCacheFiles.storageReserveBytes(1L shl 30))
        assertEquals(512L shl 20, MediaCacheFiles.storageReserveBytes(10L shl 30))
        assertEquals(1L shl 30, MediaCacheFiles.storageReserveBytes(100L shl 30))
        val old = file("trusted-images", "c".repeat(64), 20).apply { setLastModified(1) }
        val recent = file("trusted-images", "d".repeat(64), 20)
        assertEquals(20L, MediaCacheFiles.maintain(temp.root, 100, storageLow = true,
            totalBytes = 20L shl 30, freeBytes = 2L shl 30))
        assertFalse(old.exists()); assertTrue(recent.exists())
    }

    @Test fun activeOldPlaybackSurvivesExpiryAndManualCleanup() {
        val directory = temp.newFolder("video-playback")
        val owner = MediaCacheFiles.createTemporary(directory, ".mp4")
        try {
            owner.file.writeBytes(ByteArray(5)); owner.file.setLastModified(1)
            val orphan = file("video-playback", "TiebaPure-orphan.mp4", 3).apply { setLastModified(1) }
            val foreign = file("video-playback", "unrelated.mp4", 9).apply { setLastModified(1) }
            assertEquals(3L, MediaCacheFiles.expire(directory, TimeUnit.DAYS.toMillis(2)))
            assertFalse(orphan.exists()); assertTrue(owner.file.exists()); assertTrue(foreign.exists())
            assertEquals(0L, MediaCacheFiles.clearTemporary(temp.root))
        } finally { owner.release() }
        assertFalse(owner.file.exists())
        assertFalse(MediaCacheFiles.isInUse(owner.file))
    }

    @Test fun twoReadersProtectTheSameCachedImageAndReleaseIsIdempotent() {
        val image = file("trusted-images", "a".repeat(64), 4)
        val first = MediaCacheFiles.protect(image)
        val second = MediaCacheFiles.protect(image)
        try {
            assertEquals(0L, MediaCacheFiles.clearImages(temp.root))
            first.close(); first.close()
            assertEquals(0L, MediaCacheFiles.trimImages(temp.root, 0))
            assertTrue(image.exists())
        } finally { first.close(); second.close() }
        assertEquals(4L, MediaCacheFiles.clearImages(temp.root))
        assertFalse(image.exists())
    }

    @Test fun committedImageKeepsItsReadProtectionAfterTemporaryOwnerTransfer() {
        val directory = temp.newFolder("trusted-images")
        val owner = MediaCacheFiles.createTemporary(directory, ".image")
        val destination = File(directory, "b".repeat(64))
        MediaCacheFiles.protect(destination).use {
            owner.file.writeBytes(ByteArray(8))
            owner.moveTo(destination)
            owner.release()
            assertFalse(MediaCacheFiles.isInUse(owner.file))
            assertTrue(destination.exists())
            assertEquals(0L, MediaCacheFiles.clearImages(temp.root))
        }
        assertEquals(8L, MediaCacheFiles.clearImages(temp.root))
    }

    @Test fun cleanupCannotTouchOfflineOrUnknownFiles() {
        val offline = file("offline", "TiebaPure-saved.mp4", 3)
        val unknown = file("image-exports", "do-not-touch.part", 4)
        file("voice-playback", "TiebaPure-orphan.audio", 5)
        assertEquals(5L, MediaCacheFiles.clearTemporary(temp.root))
        assertTrue(offline.exists()); assertTrue(unknown.exists())
    }

    @Test fun cancellationDuringAcquisitionReleasesProtectionWithoutDeletingTheCache() = runBlocking {
        val image = file("trusted-images", "c".repeat(64), 4)
        val started = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        val job = launch { acquireMediaFile(image) { started.complete(Unit); blocked.await() }.close() }
        started.await()
        assertTrue(MediaCacheFiles.isInUse(image))
        job.cancelAndJoin()
        assertFalse(MediaCacheFiles.isInUse(image))
        assertTrue(image.exists())
    }
}
