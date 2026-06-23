package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.sectionLabel

/** Gap between segmented list items within a section (M3 Expressive segmented look). */
private val SEGMENT_GAP = 2.dp

/**
 * A labeled group of settings items displayed in the M3 Expressive *segmented* list style.
 *
 * Renders a section title above the [content] composable. The title is styled
 * with the sectionLabel semantic style in the primary color. Items are laid out in a [Column]
 * with a small [SEGMENT_GAP] between them, so each item reads as a distinct rounded segment
 * (paired with the per-position corners from [groupedItemShape]).
 *
 * Typical usage:
 * ```kotlin
 * SettingSection(title = "外观") {
 *     val items = listOf("主题", "深色模式", "AMOLED")
 *     items.forEachIndexed { i, label ->
 *         SettingItem(
 *             title = label,
 *             shape = groupedItemShape(i, items.size),
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun SettingSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.sectionLabel,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            content()
        }
    }
}
