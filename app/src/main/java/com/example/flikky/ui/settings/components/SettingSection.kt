package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.sectionLabel

/**
 * A labeled group of settings items displayed in the M3 Expressive *segmented* list style.
 *
 * Renders a section title above the [content] composable. Items are laid out in a [Column]
 * with the official [ListItemDefaults.SegmentedGap] between them, so each [SettingItem] reads
 * as a distinct rounded segment (the per-position corners come from
 * [ListItemDefaults.segmentedShapes], driven by the `index`/`total` passed to each item).
 *
 * Typical usage:
 * ```kotlin
 * SettingSection(title = "外观") {
 *     val items = listOf("主题", "深色模式", "AMOLED")
 *     items.forEachIndexed { i, label ->
 *         SettingItem(title = label, index = i, total = items.size)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            content()
        }
    }
}
