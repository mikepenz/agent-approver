package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lucide `eye-off` (https://lucide.dev/icons/eye-off). Used for the
 * Redaction settings tab and history pill — visually distinct from
 * [LucideShield] (Protection) while still reading as "this content is
 * being hidden / scrubbed".
 */
val LucideEyeOff: ImageVector
    get() {
        val current = _eyeOff
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-eyeoff",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f,
        ).apply {
            // Top-left "eye open" half: M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 10.733f, y = 5.076f)
                arcToRelative(a = 10.744f, b = 10.744f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 11.205f, dy1 = 6.575f)
                arcToRelative(a = 1f, b = 1f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = 0.696f)
                arcToRelative(a = 10.747f, b = 10.747f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -1.444f, dy1 = 2.49f)
            }
            // Iris / pupil arc: M14.084 14.158a3 3 0 0 1-4.242-4.242
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 14.084f, y = 14.158f)
                arcToRelative(a = 3f, b = 3f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -4.242f, dy1 = -4.242f)
            }
            // Bottom path: M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 17.479f, y = 17.499f)
                arcToRelative(a = 10.75f, b = 10.75f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = -15.417f, dy1 = -5.151f)
                arcToRelative(a = 1f, b = 1f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 0f, dy1 = -0.696f)
                arcToRelative(a = 10.75f, b = 10.75f, theta = 0f, isMoreThanHalf = false, isPositiveArc = true, dx1 = 4.446f, dy1 = -5.143f)
            }
            // Diagonal slash: M2 2l20 20
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineWidth = 1.5f,
            ) {
                moveTo(x = 2.0f, y = 2.0f)
                lineToRelative(dx = 20.0f, dy = 20.0f)
            }
        }.build().also { _eyeOff = it }
    }

@Suppress("ObjectPropertyName")
private var _eyeOff: ImageVector? = null
