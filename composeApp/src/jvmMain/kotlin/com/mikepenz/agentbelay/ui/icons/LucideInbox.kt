package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideInbox: ImageVector
    get() {
        val current = _lucideinbox
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-inbox",
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
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(4.0f)
                lineToRelative(2.0f, 3.0f)
                horizontalLineToRelative(6.0f)
                lineToRelative(2.0f, -3.0f)
                horizontalLineToRelative(4.0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(5.0f, 4.0f)
                horizontalLineToRelative(14.0f)
                arcToRelative(1.0f, 1.0f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, 0.95f, 0.68f)
                lineTo(22.0f, 13.0f)
                verticalLineToRelative(5.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, -2.0f, 2.0f)
                horizontalLineTo(4.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, -2.0f, -2.0f)
                verticalLineToRelative(-5.0f)
                lineTo(4.05f, 4.68f)
                arcTo(1.0f, 1.0f, 0.0f, isMoreThanHalf = false, isPositiveArc = true, 5.0f, 4.0f)
                close()
            }
        }.build().also { _lucideinbox = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideinbox: ImageVector? = null
