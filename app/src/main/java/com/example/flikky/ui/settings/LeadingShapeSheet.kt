package com.example.flikky.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.ui.components.FileLeadingVisual
import com.example.flikky.ui.theme.LocalLeadingVisual
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.LeadingShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadingShapeSheet(
    current: LeadingShape,
    onSelect: (LeadingShape) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentStyle = LocalLeadingVisual.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
        ) {
            Text(
                text = stringResource(R.string.leading_shape_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = Spacing.md),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                items(
                    count = LeadingShape.entries.size,
                    key = { LeadingShape.entries[it].id },
                ) { index ->
                    val shape = LeadingShape.entries[index]
                    val selected = shape == current
                    val label = shape.localizedLabel()
                    Box(
                        modifier = Modifier
                            .padding(Spacing.xs)
                            .size(64.dp)
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .semantics {
                                contentDescription = label
                                this.selected = selected
                                role = Role.RadioButton
                            }
                            .clickable { onSelect(shape) },
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(
                            LocalLeadingVisual provides currentStyle.copy(shape = shape),
                        ) {
                            FileLeadingVisual(
                                iconRes = R.drawable.ic_draft,
                                thumbnailModel = null,
                            )
                        }
                        if (selected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(Spacing.xs)
                                    .size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
