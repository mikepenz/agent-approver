package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideExpand: ImageVector
    get() {
        val current = _lucideexpand
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-expand",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            // Four corner arrows pointing outward (diagonal expand)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 2.0f,
            ) {
                moveTo(x = 4.0f, y = 14.0f)
                verticalLineToRelative(dy = 4.0f)
                horizontalLineToRelative(dx = 4.0f)
                moveTo(x = 20.0f, y = 10.0f)
                verticalLineTo(y = 6.0f)
                horizontalLineToRelative(dx = -4.0f)
                moveTo(x = 15.0f, y = 9.0f)
                lineToRelative(dx = 5.0f, dy = -5.0f)
                moveTo(x = 4.0f, y = 20.0f)
                lineToRelative(dx = 5.0f, dy = -5.0f)
            }
        }.build().also { _lucideexpand = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideexpand: ImageVector? = null
