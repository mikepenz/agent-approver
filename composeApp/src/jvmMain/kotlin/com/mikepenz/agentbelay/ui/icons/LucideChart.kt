package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideChart: ImageVector
    get() {
        val current = _lucidechart
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-chart",
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
                moveTo(x = 4.0f, y = 4.0f)
                verticalLineToRelative(dy = 16.0f)
                horizontalLineToRelative(dx = 16.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 7.0f, y = 15.0f)
                lineToRelative(dx = 3.0f, dy = -4.0f)
                lineToRelative(dx = 3.0f, dy = 3.0f)
                lineToRelative(dx = 4.0f, dy = -6.0f)
            }
        }.build().also { _lucidechart = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidechart: ImageVector? = null
