package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideGlobe: ImageVector
    get() {
        val current = _lucideglobe
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-globe",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                // Outer circle
                moveTo(x = 21.0f, y = 12.0f)
                curveTo(x1 = 21.0f, y1 = 16.97f, x2 = 16.97f, y2 = 21.0f, x3 = 12.0f, y3 = 21.0f)
                curveTo(x1 = 7.03f, y1 = 21.0f, x2 = 3.0f, y2 = 16.97f, x3 = 3.0f, y3 = 12.0f)
                curveTo(x1 = 3.0f, y1 = 7.03f, x2 = 7.03f, y2 = 3.0f, x3 = 12.0f, y3 = 3.0f)
                curveTo(x1 = 16.97f, y1 = 3.0f, x2 = 21.0f, y2 = 7.03f, x3 = 21.0f, y3 = 12.0f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                // Horizontal equator
                moveTo(x = 3.0f, y = 12.0f)
                horizontalLineTo(x = 21.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                // Meridian ellipse path
                moveTo(x = 12.0f, y = 3.0f)
                curveTo(x1 = 14.5f, y1 = 5.74f, x2 = 15.92f, y2 = 9.78f, x3 = 16.0f, y3 = 12.0f)
                curveTo(x1 = 15.92f, y1 = 14.22f, x2 = 14.5f, y2 = 18.26f, x3 = 12.0f, y3 = 21.0f)
                curveTo(x1 = 9.5f, y1 = 18.26f, x2 = 8.08f, y2 = 14.22f, x3 = 8.0f, y3 = 12.0f)
                curveTo(x1 = 8.08f, y1 = 9.78f, x2 = 9.5f, y2 = 5.74f, x3 = 12.0f, y3 = 3.0f)
                close()
            }
        }.build().also { _lucideglobe = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideglobe: ImageVector? = null
