package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideZap: ImageVector
    get() {
        val current = _lucidezap
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-zap",
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
                // Lightning bolt polygon: 13 2 L 3 14 L 12 14 L 11 22 L 21 10 L 12 10 Z
                moveTo(x = 13.0f, y = 2.0f)
                lineTo(x = 3.0f, y = 14.0f)
                lineTo(x = 12.0f, y = 14.0f)
                lineTo(x = 11.0f, y = 22.0f)
                lineTo(x = 21.0f, y = 10.0f)
                lineTo(x = 12.0f, y = 10.0f)
                close()
            }
        }.build().also { _lucidezap = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidezap: ImageVector? = null
