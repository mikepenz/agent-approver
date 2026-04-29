package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideChevronDown: ImageVector
    get() {
        val current = _lucidechevrondown
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-chevron-down",
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
                moveTo(x = 6.0f, y = 9.0f)
                lineTo(x = 12.0f, y = 15.0f)
                lineTo(x = 18.0f, y = 9.0f)
            }
        }.build().also { _lucidechevrondown = it }
    }

@Suppress("ObjectPropertyName")
private var _lucidechevrondown: ImageVector? = null
