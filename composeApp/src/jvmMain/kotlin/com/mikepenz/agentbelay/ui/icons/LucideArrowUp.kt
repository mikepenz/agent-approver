package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideArrowUp: ImageVector
    get() {
        val current = _lucidearrowup
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-arrow-up",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 2.0f,
            ) {
                moveTo(x = 12.0f, y = 19.0f)
                verticalLineTo(y = 5.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 2.0f,
            ) {
                moveTo(x = 5.0f, y = 12.0f)
                lineToRelative(dx = 7.0f, dy = -7.0f)
                lineToRelative(dx = 7.0f, dy = 7.0f)
            }
        }.build().also { _lucidearrowup = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidearrowup: ImageVector? = null
