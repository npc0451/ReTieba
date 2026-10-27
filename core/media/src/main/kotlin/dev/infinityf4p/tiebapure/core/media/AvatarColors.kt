package dev.infinityf4p.tiebapure.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

internal val avatarColors = mutableStateMapOf<String, Int>()

@Composable
fun avatarColor(url: String?): Int? = url?.let { avatarColors[it] }

internal fun dominantAvatarColor(pixels: IntArray): Int? {
    val buckets = pixels.filter {
        val r = it ushr 16 and 255; val g = it ushr 8 and 255; val b = it and 255
        (it ushr 24) >= 192 && maxOf(r,g,b) > 35 && minOf(r,g,b) < 235 && maxOf(r,g,b) - minOf(r,g,b) > 18
    }.groupBy { ((it ushr 20 and 15) shl 8) or ((it ushr 12 and 15) shl 4) or (it ushr 4 and 15) }
    return buckets.maxByOrNull { it.value.size }?.value?.firstOrNull()
}

/** Preserve the avatar hue while ensuring white toolbar text stays readable. */
fun readableAvatarBar(argb: Int, dark: Boolean): Color {
    var r = (argb ushr 16 and 255) / 255f
    var g = (argb ushr 8 and 255) / 255f
    var b = (argb and 255) / 255f
    if (dark) { r *= .65f; g *= .65f; b *= .65f }
    fun linear(v: Float) = if (v <= .04045f) v / 12.92f else ((v + .055f) / 1.055f).pow(2.4f)
    while (1.05f / (.2126f * linear(r) + .7152f * linear(g) + .0722f * linear(b) + .05f) < 4.5f) {
        r *= .95f; g *= .95f; b *= .95f
    }
    return Color(r, g, b)
}
