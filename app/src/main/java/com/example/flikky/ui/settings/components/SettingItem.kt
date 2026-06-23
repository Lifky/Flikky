package com.example.flikky.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

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
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = Sizes.rowMinH)
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
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
 * Returns the [Shape] for an item at [index] within a group of [total] items,
 * for the M3 Expressive *segmented* list look: large outer corners at the group
 * ends, small inner corners between adjacent segments (paired with the small gap
 * [SettingSection] puts between items).
 *
 * - Single item (total == 1): all corners large (16.dp).
 * - First item: top corners large, bottom corners small (4.dp).
 * - Last item: bottom corners large, top corners small.
 * - Middle items: all corners small.
 */
@Composable
fun groupedItemShape(index: Int, total: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    return when {
        total == 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner,
        )
        index == total - 1 -> RoundedCornerShape(
            topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}
