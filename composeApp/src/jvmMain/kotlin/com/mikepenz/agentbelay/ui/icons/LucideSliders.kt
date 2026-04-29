package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideSliders: ImageVector
    get() {
        val current = _lucidesliders
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-sliders",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            val s: (Float) -> Unit = { _ -> }
            // three horizontal rows of: line with circle toggle
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 4.0f, y = 6.0f)
                horizontalLineTo(x = 20.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 4.0f, y = 12.0f)
                horizontalLineTo(x = 20.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 4.0f, y = 18.0f)
                horizontalLineTo(x = 20.0f)
            }
            // toggles
            path(
                fill = SolidColor(Color(0xFF000000)),
            ) {
                moveTo(x = 14.0f, y = 6.0f)
                moveToRelative(dx = -2.0f, dy = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 4.0f, dy1 = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -4.0f, dy1 = 0.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
            ) {
                moveTo(x = 8.0f, y = 12.0f)
                moveToRelative(dx = -2.0f, dy = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 4.0f, dy1 = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -4.0f, dy1 = 0.0f)
                close()
            }
            path(
                fill = SolidColor(Color(0xFF000000)),
            ) {
                moveTo(x = 17.0f, y = 18.0f)
                moveToRelative(dx = -2.0f, dy = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 4.0f, dy1 = 0.0f)
                arcToRelative(a = 2.0f, b = 2.0f, theta = 0.0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -4.0f, dy1 = 0.0f)
                close()
            }
        }.build().also { _lucidesliders = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidesliders: ImageVector? = null
