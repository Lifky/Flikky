package com.example.flikky.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.ui.theme.Spacing

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
 * @param leadingIcon Optional 24dp leading icon.
 * @param trailing Optional trailing widget (e.g. Switch, Avatar, value Text).
 * @param content Optional full-width composable (e.g. a Slider) rendered below the label block.
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
    infoText: String? = null,
    leadingIcon: Painter? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    index: Int = 0,
    total: Int = 1,
) {
    var showInfo by remember { mutableStateOf(false) }
    val trailingGroup: (@Composable () -> Unit)? =
        if (infoText != null || trailing != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (infoText != null) {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_info),
                                contentDescription = stringResource(R.string.settings_show_info),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke()
                }
            }
        } else {
            null
        }

    SegmentedListItem(
        onClick = onClick ?: {},
        shapes = ListItemDefaults.segmentedShapes(index = index, count = total),
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        content = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        painter = leadingIcon,
                        contentDescription = null,
                        tint = if (danger) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(Spacing.lg))
                }

                if (content != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified,
                                modifier = Modifier.weight(1f),
                            )
                            if (trailingGroup != null) {
                                Spacer(Modifier.width(Spacing.md))
                                trailingGroup()
                            }
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        content()
                    }
                } else {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (trailingGroup != null) {
                        Spacer(Modifier.width(Spacing.md))
                        trailingGroup()
                    }
                }
            }
        },
    )

    if (showInfo && infoText != null) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(infoText) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.common_got_it))
                }
            },
        )
    }
}
