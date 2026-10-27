package dev.infinityf4p.tiebapure.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderFontFamilyTest {
    @Test
    fun builtInAndImportedValuesRoundTrip() {
        val digest = "a".repeat(64)
        val imported = checkNotNull(ReaderFontFamily.imported(digest))

        ReaderFontFamily.builtIn.forEach { assertEquals(it, ReaderFontFamily.fromRaw(it.rawValue)) }
        assertEquals(ReaderFontFamily.Plex, ReaderFontFamily.fromRaw("plex"))
        assertEquals(ReaderFontFamily.System, ReadingPreferences().fontFamily)
        assertEquals(imported, ReaderFontFamily.fromRaw(imported.rawValue))
        assertEquals(digest, imported.importedId)
    }

    @Test
    fun malformedValuesFallBackToSystem() {
        assertEquals(ReaderFontFamily.System, ReaderFontFamily.fromRaw("imported:../../font.ttf"))
        assertNull(ReaderFontFamily.imported("not-a-digest"))
    }
}
