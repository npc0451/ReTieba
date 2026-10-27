package dev.infinityf4p.tiebapure.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalLibraryPolicyTest {
    @Test
    fun normalizesExistingForumAliasesWithoutRequiringAnId() {
        assertEquals("摄影", localForumKey(" 摄影吧 "))
        assertEquals(localForumKey("Android"), localForumKey(" android吧 "))
        assertEquals("吧务", localForumKey("吧务吧"))
    }

    @Test
    fun rejectsEmptyAndUnsafeForumNames() {
        listOf("", "  ", "吧", "bad/name", "bad\\name", "bad\u0000name", "bad\nname")
            .forEach { assertNull(it, localForumKey(it)) }
    }
}
