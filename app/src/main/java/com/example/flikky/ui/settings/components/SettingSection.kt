package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.sectionLabel

/**
 * A labeled group of settings items displayed in the inset-grouped style.
 *
 * Renders a section title above the [content] composable. The title is styled
 * with the sectionLabel semantic style in the primary color. Items are laid out in a plain [Column]
 * with no gap between them; callers should add spacers if separation is needed.
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
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
