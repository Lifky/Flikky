package com.example.flikky.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R

@Composable
fun ImportExportOverflowMenu(
    importLabel: String,
    exportLabel: String,
    onImport: () -> Unit,
    onExport: () -> Unit,
    /**
     * 可选的排序入口。非空时渲染成**第一项**并跟一条分隔线 ——
     * 「怎么看」在「导入导出」之前，前者天天用、后者偶尔用。
     * 收藏页不传这两个参数（它的排序菜单是独立的 SortMenuAction）。
     */
    sortLabel: String? = null,
    onSort: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.common_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (sortLabel != null && onSort != null) {
                DropdownMenuItem(
                    text = { Text(sortLabel) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_filter_list),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSort()
                    },
                )
                HorizontalDivider()
            }
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
