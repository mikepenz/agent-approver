package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideRefreshCw: ImageVector
    get() {
        val current = _lucideRefreshCw
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-refresh-cw",
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
                // M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8
                moveTo(x = 21.0f, y = 12.0f)
                arcTo(9.0f, 9.0f, 0.0f, false, false, 12.0f, 3.0f)
                arcTo(9.75f, 9.75f, 0.0f, false, false, 5.26f, 5.74f)
                lineTo(x = 3.0f, y = 8.0f)
                // M3 3v5h5
                moveTo(x = 3.0f, y = 3.0f)
                verticalLineToRelative(dy = 5.0f)
                horizontalLineToRelative(dx = 5.0f)
                // M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16
                moveTo(x = 3.0f, y = 12.0f)
                arcTo(9.0f, 9.0f, 0.0f, false, false, 12.0f, 21.0f)
                arcTo(9.75f, 9.75f, 0.0f, false, false, 18.74f, 18.26f)
                lineTo(x = 21.0f, y = 16.0f)
                // M16 16h5v5
                moveTo(x = 16.0f, y = 16.0f)
                horizontalLineToRelative(dx = 5.0f)
                verticalLineToRelative(dy = 5.0f)
            }
        }.build().also { _lucideRefreshCw = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideRefreshCw: ImageVector? = null
