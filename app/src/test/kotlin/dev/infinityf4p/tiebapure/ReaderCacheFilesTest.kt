package dev.infinityf4p.tiebapure

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReaderCacheFilesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun file(directory: String, name: String, size: Int = 4): File = File(temp.root, "$directory/$name").apply {
        parentFile?.mkdirs(); writeBytes(ByteArray(size))
    }

    @Test fun clearingOnlyTouchesCompletedWhitelistedCacheFiles() {
        val image = file("trusted-images", "a".repeat(64))
        val emoticon = file("tieba-emoticons", "b".repeat(64) + ".png")
        val pending = file("trusted-images", "active.part")
        val offline = file("offline", "c".repeat(64))
        val video = file("video-playback", "d".repeat(64))
        val manager = ReaderCacheFiles(temp.root)
        assertEquals(4L, manager.imageBytes())
        manager.clear()
        assertFalse(image.exists()); assertFalse(emoticon.exists())
        assertTrue(pending.exists()); assertTrue(offline.exists()); assertTrue(video.exists())
    }

    @Test fun trimmingEvictsOldImagesAndPreservesOtherData() {
        val old = file("trusted-images", "a".repeat(64)).apply { setLastModified(1000) }
        val recent = file("trusted-images", "b".repeat(64)).apply { setLastModified(2000) }
        val emoticon = file("tieba-emoticons", "c".repeat(64) + ".png")
        val manager = ReaderCacheFiles(temp.root)
        manager.trimImages(4)
        assertFalse(old.exists()); assertTrue(recent.exists()); assertTrue(emoticon.exists())
        assertEquals(4L, manager.imageBytes())
    }
}
