package com.example.flikky.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A single settings row inside a grouped section.
 *
 * @param title Primary label (displayed in bodyLarge).
 * @param subtitle Optional secondary label (displayed in bodyMedium, onSurfaceVariant).
 * @param trailing Optional composable trailing widget (e.g. Switch, Text value).
 * @param onClick When non-null the whole row becomes clickable.
 * @param shape Corner shape — use [groupedItemShape] to get the correct value per index.
 */
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * Returns the appropriate [Shape] for an item at [index] within a group of [total] items.
 *
 * - Single item (total == 1): all corners rounded (16.dp).
 * - First item: top corners rounded, bottom corners square.
 * - Last item: bottom corners rounded, top corners square.
 * - Middle items: all corners square (rectangle).
 */
@Composable
fun groupedItemShape(index: Int, total: Int): Shape {
    val r = 16.dp
    val zero = 0.dp
    val shapes = MaterialTheme.shapes
    return when {
        total == 1 -> androidx.compose.foundation.shape.RoundedCornerShape(r)
        index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = r, topEnd = r, bottomStart = zero, bottomEnd = zero,
        )
        index == total - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = zero, topEnd = zero, bottomStart = r, bottomEnd = r,
        )
        else -> androidx.compose.foundation.shape.RoundedCornerShape(zero)
    }
}
