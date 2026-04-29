package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideFilter: ImageVector
    get() {
        val current = _lucidefilter
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-filter",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            // JSX spec: <path d="M3 5h18M6 12h12M10 19h4"/>
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 3.0f, y = 5.0f)
                horizontalLineToRelative(dx = 18.0f)
                moveTo(x = 6.0f, y = 12.0f)
                horizontalLineToRelative(dx = 12.0f)
                moveTo(x = 10.0f, y = 19.0f)
                horizontalLineToRelative(dx = 4.0f)
            }
        }.build().also { _lucidefilter = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidefilter: ImageVector? = null
