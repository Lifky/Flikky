package com.example.flikky.ui.components

import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter

data class CompactActionGroupItem(
    val label: String,
    val painter: Painter,
    val onClick: () -> Unit,
)

/** Standard button group for compact icon actions, with the official overflow behaviour. */
@Composable
fun CompactActionGroup(
    items: List<CompactActionGroupItem>,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            customItem(
                buttonGroupContent = {
                    FilledTonalIconButton(onClick = item.onClick) {
                        Icon(
                            painter = item.painter,
                            contentDescription = item.label,
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        leadingIcon = {
                            Icon(
                                painter = item.painter,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            item.onClick()
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}
