package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A labeled group of settings items displayed in the inset-grouped style.
 *
 * Renders a section title above the [content] composable. The title is styled
 * with titleSmall in the primary color. A 1 dp gap is baked between items so
 * callers can pass adjacent [SettingItem]s without needing explicit spacers.
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
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
