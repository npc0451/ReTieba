package dev.infinityf4p.tiebapure.core.designsystem

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import dev.infinityf4p.tiebapure.core.model.ReaderFontFamily
import java.util.concurrent.ConcurrentHashMap

val LocalReaderFontRevision = staticCompositionLocalOf { 0L }
val LocalReadingPreferences = staticCompositionLocalOf { dev.infinityf4p.tiebapure.core.model.ReadingPreferences() }
val LocalCommunityColors = staticCompositionLocalOf { true }
val LocalReaderTransitionActive = staticCompositionLocalOf { false }
val LocalPostBarColor = staticCompositionLocalOf<(androidx.compose.ui.graphics.Color?) -> Unit> { {} }

@Composable
fun readerContentStyle(size: Int, lineHeight: Int): androidx.compose.ui.text.TextStyle {
    val preferences = LocalReadingPreferences.current
    return androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
        fontFamily = readerFontFamily(preferences.fontFamily),
        fontSize = androidx.compose.ui.unit.TextUnit(size * preferences.fontSize.scale, androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(lineHeight * preferences.fontSize.scale * preferences.lineSpacing.multiplier, androidx.compose.ui.unit.TextUnitType.Sp),
    )
}

object ReaderTypefaceRegistry {
    private val imported = ConcurrentHashMap<String, Typeface>()
    private val rounded = FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL))

    fun register(id: String, typeface: Typeface) {
        val family = ReaderFontFamily.imported(id) ?: return
        imported[family.rawValue] = typeface
    }

    fun unregister(id: String) {
        ReaderFontFamily.imported(id)?.let { imported.remove(it.rawValue) }
    }

    fun resolve(family: ReaderFontFamily): FontFamily = when (family) {
        ReaderFontFamily.System -> FontFamily.Default
        ReaderFontFamily.Plex -> BrandFontFamily
        ReaderFontFamily.Serif -> FontFamily.Serif
        ReaderFontFamily.Rounded -> rounded
        ReaderFontFamily.Monospace -> FontFamily.Monospace
        else -> imported[family.rawValue]?.let(::FontFamily) ?: FontFamily.Default
    }
}

@Composable
fun readerFontFamily(family: ReaderFontFamily): FontFamily {
    LocalReaderFontRevision.current
    return ReaderTypefaceRegistry.resolve(family)
}
