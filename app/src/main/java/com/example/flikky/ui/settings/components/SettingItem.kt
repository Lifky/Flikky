package com.example.flikky.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing

/**
 * A single settings row inside a grouped section.
 *
 * @param title Primary label (displayed in bodyLarge).
 * @param subtitle Optional secondary label (displayed in bodyMedium, onSurfaceVariant).
 * @param leadingIcon Optional 24dp leading icon shown before the title (onSurfaceVariant tint).
 * @param trailing Optional composable trailing widget (e.g. Switch, Text value).
 * @param content Optional full-width composable rendered on its own line *below* the title row
 *   (e.g. a Slider that needs the whole width). Indented to align with the title text.
 * @param onClick When non-null the whole row becomes clickable.
 * @param shape Corner shape — use [groupedItemShape] to get the correct value per index.
 */
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: Painter? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceBright,
        shape = shape,
    ) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .defaultMinSize(minHeight = Sizes.rowMinH)
                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painter = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.lg))
                }
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
                    // 让出固定间隔，避免长标题/副标题顶到 trailing（如 Switch）上。
                    Spacer(Modifier.width(Spacing.lg))
                    trailing()
                }
            }
            if (content != null) {
                Spacer(Modifier.height(Spacing.xs))
                // 与标题文字对齐：让出 leading icon（24dp）占的宽度。
                val startPad = if (leadingIcon != null) 24.dp + Spacing.lg else 0.dp
                Box(modifier = Modifier.fillMaxWidth().padding(start = startPad)) {
                    content()
                }
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
