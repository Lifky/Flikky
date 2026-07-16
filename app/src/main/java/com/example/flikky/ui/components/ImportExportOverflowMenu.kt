package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.example.flikky.R

@Composable
fun ImportExportOverflowMenu(
    importLabel: String,
    exportLabel: String,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = "更多",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(importLabel) },
                onClick = {
                    expanded = false
                    onImport()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_download),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(exportLabel) },
                onClick = {
                    expanded = false
                    onExport()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_upload),
                        contentDescription = null,
                    )
                },
            )
        }
    }
}
