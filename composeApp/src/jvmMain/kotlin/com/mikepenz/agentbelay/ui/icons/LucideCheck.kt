package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideCheck: ImageVector
    get() {
        val current = _lucidecheck
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-check",
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
                moveTo(x = 4.0f, y = 12.5f)
                lineToRelative(dx = 4.5f, dy = 4.5f)
                lineTo(x = 20.0f, y = 6.0f)
            }
        }.build().also { _lucidecheck = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidecheck: ImageVector? = null
