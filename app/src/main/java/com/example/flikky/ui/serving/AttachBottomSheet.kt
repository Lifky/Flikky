package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.db.FileOverviewRow
import com.example.flikky.ui.components.OptionCard
import com.example.flikky.ui.theme.Spacing

/**
 * One attachment sheet for system pickers and previously transferred files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachBottomSheet(
    existingFiles: List<FileOverviewRow>,
    onSendExistingFile: (FileOverviewRow) -> Unit,
    onPickFile: () -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabState = rememberSaveableStateHolder()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.65f)) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                listOf(R.string.attach_title, R.string.files_quick_title).forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(label)) },
                    )
                }
            }
            Box(Modifier.weight(1f).padding(top = Spacing.lg)) {
                tabState.SaveableStateProvider(selectedTab) {
                    if (selectedTab == 1) {
                        ExistingFilesContent(rows = existingFiles, onSend = onSendExistingFile)
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.screenEdge)
                                .padding(bottom = Spacing.xxxl),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                OptionCard(
                                    iconRes = R.drawable.ic_attach_file,
                                    label = stringResource(R.string.attach_file),
                                    onClick = onPickFile,
                                    modifier = Modifier.weight(1f),
                                    description = stringResource(R.string.attach_file_summary),
                                )
                                OptionCard(
                                    iconRes = R.drawable.ic_image,
                                    label = stringResource(R.string.attach_image),
                                    onClick = onPickImage,
                                    modifier = Modifier.weight(1f),
                                    description = stringResource(R.string.attach_image_summary),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
