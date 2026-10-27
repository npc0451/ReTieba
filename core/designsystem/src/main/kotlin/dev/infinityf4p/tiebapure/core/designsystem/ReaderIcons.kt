package dev.infinityf4p.tiebapure.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Outline actions shared by feeds, search and reading; the vote remains read-only. */
object ReaderIcons {
    val Upvote: ImageVector = ImageVector.Builder("ReaderUpvote", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 2.5f)
            quadTo(12.4f, 2.5f, 12.7f, 2.8f)
            lineTo(20.8f, 10.9f); quadTo(22f, 12.1f, 20.3f, 12.1f)
            lineTo(16.1f, 12.1f); lineTo(16.1f, 16.8f)
            curveTo(16.1f, 19.5f, 14.3f, 21.5f, 12f, 21.5f)
            curveTo(9.7f, 21.5f, 7.9f, 19.5f, 7.9f, 16.8f)
            lineTo(7.9f, 12.1f); lineTo(3.7f, 12.1f)
            quadTo(2f, 12.1f, 3.2f, 10.9f); lineTo(11.3f, 2.8f)
            quadTo(11.6f, 2.5f, 12f, 2.5f); close()
        }
    }.build()

    val Comment: ImageVector = ImageVector.Builder("ReaderComment", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f)
            curveTo(6.5f, 3f, 3f, 6.8f, 3f, 11.5f)
            curveTo(3f, 14f, 4f, 16f, 5.8f, 17.5f)
            lineTo(3.5f, 21f); lineTo(11.5f, 20f)
            curveTo(17f, 20f, 21f, 16.5f, 21f, 11.5f)
            curveTo(21f, 6.8f, 17.5f, 3f, 12f, 3f); close()
        }
    }.build()

    val Share: ImageVector = ImageVector.Builder("ReaderShare", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(14f, 3f); lineTo(22f, 10.5f); lineTo(14f, 18f)
            lineTo(14f, 13f); curveTo(8.5f, 13f, 5f, 15f, 2f, 20f)
            curveTo(2f, 11f, 6f, 7f, 14f, 7f); close()
        }
    }.build()
}
