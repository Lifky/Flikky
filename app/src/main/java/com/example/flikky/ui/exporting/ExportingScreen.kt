package com.example.flikky.ui.exporting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.ui.components.ConnectionInfoCard
import com.example.flikky.ui.components.NetworkStatusBanner
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.export.ExportScope
import com.example.flikky.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExportingScreen(
    onBack: () -> Unit,
    viewModel: ExportingViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()

    // Block the system back gesture from escaping while an export is live —
    // users must route through the explicit "取消导出" / "保留 / 删除" flows so
    // SessionState.exportMode stays in sync with what's on screen.
    BackHandler(enabled = ui.phase != ExportingUiState.Phase.Gone) { /* no-op */ }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(topBarTitleFor(ui.phase)) },
                    colors = TopAppBarDefaults.topAppBarColors(),
                )
                NetworkStatusBanner(
                    status = ui.networkStatus,
                    onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = ui.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding).fillMaxSize(),
            label = "ExportingPhase",
        ) { phase ->
            when (phase) {
                ExportingUiState.Phase.Armed -> PhaseContainer {
                    ArmedContent(
                        url = ui.url,
                        pin = ui.pin,
                        requirePin = ui.requirePin,
                        onCancel = {
                            viewModel.cancelExport()
                            onBack()
                        },
                    )
                }
                ExportingUiState.Phase.Sending -> PhaseContainer {
                    SendingContent(
                        bytesSent = ui.bytesSent,
                        totalBytes = ui.totalBytes,
                        onCancel = {
                            viewModel.cancelExport()
                            onBack()
                        },
                    )
                }
                ExportingUiState.Phase.Done -> PhaseContainer {
                    DoneContent(
                        sessionCount = ui.sessionCount,
                        sessionIds = ui.sessionIds,
                        scope = ui.scope,
                        favoriteCount = ui.favoriteCount,
                        onKeep = {
                            viewModel.acknowledge()
                            onBack()
                        },
                        onConfirmDelete = { ids ->
                            viewModel.deleteLocal(ids)
                            viewModel.acknowledge()
                            onBack()
                        },
                    )
                }
                ExportingUiState.Phase.Gone -> {
                    // Export was cleared out from under us (cancel / crash-recovery
                    // / service stopped). Pop immediately instead of showing a dead
                    // screen with stale URL/PIN.
                    LaunchedEffect(Unit) { onBack() }
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun PhaseContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.fillMaxSize().maxContentWidth()) {
            content()
        }
    }
}

@Composable
private fun topBarTitleFor(phase: ExportingUiState.Phase): String = when (phase) {
    ExportingUiState.Phase.Armed -> stringResource(R.string.exporting_title_ready)
    ExportingUiState.Phase.Sending -> stringResource(R.string.exporting_title_sending)
    ExportingUiState.Phase.Done -> stringResource(R.string.exporting_title_done)
    ExportingUiState.Phase.Gone -> ""
}

@Composable
private fun ArmedContent(
    url: String,
    pin: String,
    requirePin: Boolean,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionInfoCard(url = url, pin = pin, requirePin = requirePin)

        Text(
            text = if (requirePin) {
                stringResource(R.string.exporting_ready_with_pin)
            } else {
                stringResource(R.string.exporting_ready_without_pin)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) { Text(stringResource(R.string.exporting_cancel)) }
    }
}

@Composable
private fun SendingContent(
    bytesSent: Long,
    totalBytes: Long,
    onCancel: () -> Unit,
) {
    val progress = if (totalBytes > 0L) {
        (bytesSent.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.exporting_browser_downloading),
            style = MaterialTheme.typography.titleMedium,
        )

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(
                R.string.exporting_progress,
                formatSize(bytesSent),
                formatSize(totalBytes),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.exporting_cancel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneContent(
    sessionCount: Int,
    sessionIds: List<Long>,
    scope: ExportScope,
    favoriteCount: Int,
    onKeep: () -> Unit,
    onConfirmDelete: (List<Long>) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxxl))
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Text(
            text = when (scope) {
                ExportScope.SESSIONS -> pluralStringResource(
                    R.plurals.exporting_sessions_done,
                    sessionCount,
                    sessionCount,
                )
                ExportScope.FAVORITES -> pluralStringResource(
                    R.plurals.exporting_favorites_done,
                    favoriteCount,
                    favoriteCount,
                )
                ExportScope.SETTINGS -> stringResource(R.string.exporting_settings_done)
                ExportScope.ALL -> stringResource(R.string.exporting_all_done)
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (scope == ExportScope.SESSIONS) {
                stringResource(R.string.exporting_sessions_saved_description)
            } else {
                stringResource(R.string.exporting_archive_saved_description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.weight(1f))

        Button(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (scope == ExportScope.SESSIONS) R.string.exporting_keep_local
                    else R.string.common_done,
                )
            )
        }

        if (scope == ExportScope.SESSIONS) {
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.exporting_delete_local)) }
        }
    }

    if (showDeleteConfirm && scope == ExportScope.SESSIONS) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.exporting_delete_title,
                        sessionCount,
                        sessionCount,
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.exporting_delete_message,
                        sessionCount,
                        sessionCount,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onConfirmDelete(sessionIds)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.exporting_confirm_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "--"
    if (bytes >= 1024L * 1024L) return "%.1f MB".format(bytes / 1048576.0)
    if (bytes >= 1024L) return "%.1f KB".format(bytes / 1024.0)
    return "$bytes B"
}
