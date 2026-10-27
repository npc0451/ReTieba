package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderStorageFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(path: String, bytes: Int) = File(temp.root, path).apply {
        parentFile!!.mkdirs(); writeBytes(ByteArray(bytes))
    }

    @Test fun privateFilesAreCountedOnceIncludingWalAndActiveMedia() {
        val apk = file("base.apk", 10)
        file("data/databases/reader.db", 20)
        file("data/databases/reader.db-wal", 30)
        file("data/databases/reader.db-shm", 40)
        file("data/cache/trusted-images/${"a".repeat(64)}", 4)
        file("data/cache/tieba-emoticons/${"b".repeat(64)}.png", 5)
        file("data/cache/video-playback/TiebaPure-old.mp4", 6)
        val playing = file("data/cache/voice-playback/TiebaPure-current.audio", 7)
        file("data/files/saved-thread-media/1/asset.img", 8)
        file("data/shared_prefs/settings.xml", 9)
        file("data/files/fonts/reader.ttf", 11)
        file("data/cache/unrecognized", 12)
        file("data/cache/trusted-images/unknown.part", 3)
        MediaCacheFiles.protect(playing).use {
            val result = measureReaderStorage(File(temp.root, "data"), listOf(apk, apk))
            assertEquals(10L, result.apk)
            assertEquals(90L, result.database)
            assertEquals(4L, result.images); assertEquals(5L, result.emoticons)
            assertEquals(13L, result.temporary); assertEquals(7L, result.activeTemporary)
            assertEquals(8L, result.offlineMedia); assertEquals(35L, result.other)
            assertEquals(155L, result.privateFiles)
        }
    }

    @Test fun sizeLabelsExposeSmallFilesAndLargeTotals() {
        assertEquals("0 B", cacheSizeLabel(-1))
        assertEquals("512 B", cacheSizeLabel(512))
        assertEquals("1.5 KiB", cacheSizeLabel(1536))
        assertEquals("1.0 MiB", cacheSizeLabel(1_048_576))
        assertEquals("1.0 GiB", cacheSizeLabel(1_073_741_824))
    }
}
