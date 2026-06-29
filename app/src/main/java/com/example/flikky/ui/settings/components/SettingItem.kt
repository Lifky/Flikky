package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

/**
 * A single settings row, rendered with the official M3 Expressive [SegmentedListItem].
 *
 * The official component supplies the segmented container, per-position corner shapes
 * (via [ListItemDefaults.segmentedShapes]) and the interaction spring out of the box. We
 * only override the container color to surfaceContainer so the rows read as a layered
 * group in *both* light and dark (the segmented default is surfaceBright, which is nearly
 * invisible against a light page background — that was the "no hierarchy in light mode" bug).
 *
 * @param title Primary label, rendered as the headline (the [SegmentedListItem] content slot).
 * @param subtitle Optional secondary label, rendered as supporting text.
 * @param leadingIcon Optional 24dp leading icon (onSurfaceVariant tint from the slot decorator).
 * @param trailing Optional trailing widget (e.g. Switch, Avatar, value Text).
 * @param content Optional full-width composable (e.g. a Slider) rendered below the subtitle,
 *   inside the supporting slot so it aligns under the headline.
 * @param onClick When non-null the whole row triggers it; null rows are inert (no-op click).
 * @param index Position of this item within its section (for the segmented corner shapes).
 * @param total Number of items in the section.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: Painter? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    index: Int = 0,
    total: Int = 1,
) {
    // Subtitle and/or full-width content (Slider) both live in the supporting slot so they
    // align under the headline; the slot's text decorator applies the supporting style/color.
    val supporting: (@Composable () -> Unit)? = when {
        content != null -> {
            {
                Column {
                    if (subtitle != null) {
                        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                    content()
                }
            }
        }
        subtitle != null -> {
            { Text(text = subtitle, style = MaterialTheme.typography.bodyMedium) }
        }
        else -> null
    }

    SegmentedListItem(
        onClick = onClick ?: {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = total),
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        leadingContent = leadingIcon?.let {
            { Icon(painter = it, contentDescription = null) }
        },
        trailingContent = trailing,
        supportingContent = supporting,
        content = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
    )
}
