package com.mikepenz.agentbuddy.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun StatusBadge(text: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
internal fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
internal fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
internal fun SettingsDiscreteSlider(
    label: String,
    value: Int,
    stops: List<Int>,
    stopLabels: List<String>,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    require(stops.size == stopLabels.size && stops.size >= 2)
    val labelColor = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val tickColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    val currentIndex = stops.indexOf(value).let { if (it < 0) 0 else it }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor)
            Text(
                text = stopLabels[currentIndex],
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { f ->
                val idx = f.toInt().coerceIn(0, stops.lastIndex)
                val newStop = stops[idx]
                if (newStop != value) onValueChange(newStop)
            },
            valueRange = 0f..(stops.size - 1).toFloat(),
            steps = (stops.size - 2).coerceAtLeast(0),
            enabled = enabled,
        )
        // The Material3 Slider insets its track by the thumb radius (~10dp).
        // Inset the label row by the same amount so tick captions line up with
        // thumb positions, then place each label centered on its exact fraction.
        Layout(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            content = {
                stopLabels.forEach { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = tickColor,
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
            val width = constraints.maxWidth
            val height = placeables.maxOfOrNull { it.height } ?: 0
            layout(width, height) {
                placeables.forEachIndexed { i, p ->
                    val fraction =
                        if (placeables.size == 1) 0.5f else i.toFloat() / (placeables.size - 1)
                    val centerX = (fraction * width).toInt()
                    val x = (centerX - p.width / 2).coerceIn(0, width - p.width)
                    p.placeRelative(x, 0)
                }
            }
        }
    }
}
