package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideSearch: ImageVector
    get() {
        val current = _lucidesearch
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-search",
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
                moveTo(x = 11.0f, y = 11.0f)
                moveToRelative(dx = -6.5f, dy = 0.0f)
                arcToRelative(a = 6.5f, b = 6.5f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 13.0f, dy1 = 0.0f)
                arcToRelative(a = 6.5f, b = 6.5f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -13.0f, dy1 = 0.0f)
                close()
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 20.0f, y = 20.0f)
                lineToRelative(dx = -4.3f, dy = -4.3f)
            }
        }.build().also { _lucidesearch = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidesearch: ImageVector? = null
