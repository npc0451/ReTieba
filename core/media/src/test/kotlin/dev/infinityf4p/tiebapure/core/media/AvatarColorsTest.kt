package dev.infinityf4p.tiebapure.core.media

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarColorsTest {
    @Test fun transparentAndNeutralPixelsFallBackToTheme() {
        assertNull(dominantAvatarColor(intArrayOf(0x00FF4500, 0xFFFFFFFF.toInt(), 0xFF030303.toInt())))
    }
    @Test fun majorityColorWinsOverSmallAccent() {
        val green = 0xFF318050.toInt()
        assertEquals(green, dominantAvatarColor(intArrayOf(green, green, green, 0xFFFF4500.toInt())))
    }
    @Test fun bothThemesKeepWhiteTextReadableForBrightAndDarkColors() {
        listOf(0xFFFFFF00, 0xFF00FF00, 0xFF319EF5, 0xFF030303, 0xFFFF4500).forEach { color ->
            listOf(false, true).forEach { dark ->
                assertTrue(1.05f / (readableAvatarBar(color.toInt(), dark).luminance() + .05f) >= 4.49f)
            }
        }
    }
}
