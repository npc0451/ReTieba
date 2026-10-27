package dev.infinityf4p.tiebapure.core.designsystem

import androidx.compose.ui.text.font.FontFamily
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderTypographyTest {
    @Test fun chosenFontReachesEveryThemeTextRole() {
        val type = readerTypography(FontFamily.Serif)
        listOf(type.displayLarge, type.displayMedium, type.displaySmall,
            type.headlineLarge, type.headlineMedium, type.headlineSmall,
            type.titleLarge, type.titleMedium, type.titleSmall,
            type.bodyLarge, type.bodyMedium, type.bodySmall,
            type.labelLarge, type.labelMedium, type.labelSmall).forEach {
            assertEquals(FontFamily.Serif, it.fontFamily)
        }
        assertEquals(14f, type.bodyMedium.fontSize.value)
        assertEquals(20f, type.labelLarge.lineHeight.value)
    }
}
