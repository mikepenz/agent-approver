package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucidePlug: ImageVector
    get() {
        val current = _lucideplug
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-plug",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            // Two prongs
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 9.0f, y = 2.0f)
                verticalLineTo(y = 6.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 15.0f, y = 2.0f)
                verticalLineTo(y = 6.0f)
            }
            // Socket body (rounded square)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 7.0f, y = 6.0f)
                horizontalLineTo(x = 17.0f)
                verticalLineTo(y = 12.0f)
                arcToRelative(a = 5.0f, b = 5.0f, theta = 0.0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -10.0f, dy1 = 0.0f)
                close()
            }
            // Cable
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 12.0f, y = 17.0f)
                verticalLineTo(y = 22.0f)
            }
        }.build().also { _lucideplug = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideplug: ImageVector? = null
