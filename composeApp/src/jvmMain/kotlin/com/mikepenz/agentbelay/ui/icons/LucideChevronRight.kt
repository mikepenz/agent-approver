package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideChevronRight: ImageVector
    get() {
        val current = _lucidechevronright
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-chevron-right",
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
                moveTo(x = 9.0f, y = 6.0f)
                lineToRelative(dx = 6.0f, dy = 6.0f)
                lineToRelative(dx = -6.0f, dy = 6.0f)
            }
        }.build().also { _lucidechevronright = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidechevronright: ImageVector? = null
