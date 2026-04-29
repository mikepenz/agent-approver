package com.mikepenz.agentbelay.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val LucideMinus: ImageVector
    get() {
        val current = _lucideminus
        if (current != null) return current

        return ImageVector.Builder(
            name = "lucide-minus",
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
                moveTo(x = 5.0f, y = 12.0f)
                horizontalLineTo(x = 19.0f)
            }
        }.build().also { _lucideminus = it }
    }

@Suppress("ObjectPropertyName")
private var _lucideminus: ImageVector? = null
