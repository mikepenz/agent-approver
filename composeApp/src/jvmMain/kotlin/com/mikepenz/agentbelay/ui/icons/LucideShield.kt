package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideShield: ImageVector
    get() {
        val current = _lucideshield
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-shield",
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
                moveTo(x = 12.0f, y = 2.5f)
                lineToRelative(dx = 8.0f, dy = 3.0f)
                verticalLineToRelative(dy = 6.5f)
                curveToRelative(dx1 = 0.0f, dy1 = 4.5f, dx2 = -3.1f, dy2 = 8.0f, dx3 = -8.0f, dy3 = 9.5f)
                curveToRelative(dx1 = -4.9f, dy1 = -1.5f, dx2 = -8.0f, dy2 = -5.0f, dx3 = -8.0f, dy3 = -9.5f)
                verticalLineTo(y = 5.5f)
                lineToRelative(dx = 8.0f, dy = -3.0f)
                close()
            }
        }.build().also { _lucideshield = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideshield: ImageVector? = null
