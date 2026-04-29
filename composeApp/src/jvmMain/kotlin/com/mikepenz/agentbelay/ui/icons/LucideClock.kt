package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideClock: ImageVector
    get() {
        val current = _lucideclock
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-clock",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.75f,
            ) {
                // circle
                moveTo(x = 21.0f, y = 12.0f)
                arcToRelative(a = 9.0f, b = 9.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = -18.0f, dy1 = 0.0f)
                arcToRelative(a = 9.0f, b = 9.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = false, dx1 = 18.0f, dy1 = 0.0f)
                close()
                // hands
                moveTo(x = 12.0f, y = 7.0f)
                lineTo(x = 12.0f, y = 12.0f)
                lineTo(x = 15.0f, y = 14.0f)
            }
        }.build().also { _lucideclock = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideclock: ImageVector? = null
