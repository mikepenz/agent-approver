package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideGear: ImageVector
    get() {
        val current = _lucidegear
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-gear",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            // Outer gear shape (Lucide "settings" icon)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 2f,
            ) {
                moveTo(12.22f, 2f)
                horizontalLineToRelative(-0.44f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                verticalLineToRelative(0.18f)
                arcToRelative(2f, 2f, 0f, false, true, -1f, 1.73f)
                lineToRelative(-0.43f, 0.25f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 0f)
                lineToRelative(-0.15f, -0.08f)
                arcToRelative(2f, 2f, 0f, false, false, -2.73f, 0.73f)
                lineToRelative(-0.22f, 0.38f)
                arcToRelative(2f, 2f, 0f, false, false, 0.73f, 2.73f)
                lineToRelative(0.15f, 0.1f)
                arcToRelative(2f, 2f, 0f, false, true, 1f, 1.72f)
                verticalLineToRelative(0.51f)
                arcToRelative(2f, 2f, 0f, false, true, -1f, 1.74f)
                lineToRelative(-0.15f, 0.09f)
                arcToRelative(2f, 2f, 0f, false, false, -0.73f, 2.73f)
                lineToRelative(0.22f, 0.38f)
                arcToRelative(2f, 2f, 0f, false, false, 2.73f, 0.73f)
                lineToRelative(0.15f, -0.08f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 0f)
                lineToRelative(0.43f, 0.25f)
                arcToRelative(2f, 2f, 0f, false, true, 1f, 1.73f)
                verticalLineTo(20f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                horizontalLineToRelative(0.44f)
                arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                verticalLineToRelative(-0.18f)
                arcToRelative(2f, 2f, 0f, false, true, 1f, -1.73f)
                lineToRelative(0.43f, -0.25f)
                arcToRelative(2f, 2f, 0f, false, true, 2f, 0f)
                lineToRelative(0.15f, 0.08f)
                arcToRelative(2f, 2f, 0f, false, false, 2.73f, -0.73f)
                lineToRelative(0.22f, -0.39f)
                arcToRelative(2f, 2f, 0f, false, false, -0.73f, -2.73f)
                lineToRelative(-0.15f, -0.08f)
                arcToRelative(2f, 2f, 0f, false, true, -1f, -1.74f)
                verticalLineToRelative(-0.5f)
                arcToRelative(2f, 2f, 0f, false, true, 1f, -1.74f)
                lineToRelative(0.15f, -0.09f)
                arcToRelative(2f, 2f, 0f, false, false, 0.73f, -2.73f)
                lineToRelative(-0.22f, -0.38f)
                arcToRelative(2f, 2f, 0f, false, false, -2.73f, -0.73f)
                lineToRelative(-0.15f, 0.08f)
                arcToRelative(2f, 2f, 0f, false, true, -2f, 0f)
                lineToRelative(-0.43f, -0.25f)
                arcToRelative(2f, 2f, 0f, false, true, -1f, -1.73f)
                verticalLineTo(4f)
                arcToRelative(2f, 2f, 0f, false, false, -2f, -2f)
                close()
            }
            // Inner circle (r=3)
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 2f,
            ) {
                moveTo(12f, 12f)
                moveToRelative(-3f, 0f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 6f, dy1 = 0f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -6f, dy1 = 0f)
            }
        }.build().also { _lucidegear = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidegear: ImageVector? = null
