package com.example.flikky.ui.serving

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.flikky.R
import com.example.flikky.ui.components.ConfirmDialog
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.AppEntry
import com.example.flikky.util.AppListPolicy
import com.example.flikky.util.formatBytes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerContent(apps: List<AppEntry>, loading: Boolean, onSend: (AppEntry) -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    var splitTarget by remember { mutableStateOf<AppEntry?>(null) }
    val shown = remember(apps, includeSystem, query) { AppListPolicy.shape(apps, includeSystem, query) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                query, { query = it }, Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.apps_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton({ query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.favorite_quick_clear_search)) } },
                singleLine = true,
                shape = SearchBarDefaults.inputFieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
            FilterChip(includeSystem, { includeSystem = !includeSystem }, { Text(stringResource(R.string.apps_include_system)) })
        }
        when {
            loading -> Column(Modifier.fillMaxWidth().padding(Spacing.sectionGap), horizontalAlignment = Alignment.CenterHorizontally) {
                LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.apps_loading))
            }
            shown.isEmpty() -> Text(stringResource(R.string.apps_empty), Modifier.padding(Spacing.sectionGap), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.screenEdge, vertical = Spacing.md)) {
                items(shown, key = { it.packageName }) { app -> AppPickerRow(app) { if (app.splitCount > 0) splitTarget = app else onSend(app) } }
            }
        }
    }
    splitTarget?.let { target -> ConfirmDialog(
        stringResource(R.string.apps_split_warning_title),
        stringResource(R.string.apps_split_warning_body, target.label),
        stringResource(R.string.apps_split_warning_confirm),
        onConfirm = { splitTarget = null; onSend(target) },
        onDismiss = { splitTarget = null },
    ) }
}

@Composable
private fun AppPickerRow(app: AppEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) { runCatching { context.packageManager.getApplicationIcon(app.packageName).toBitmap().asImageBitmap() }.getOrNull() }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { if (icon != null) Image(icon, null, Modifier.size(40.dp)) }
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${app.versionName ?: app.versionCode} · ${formatBytes(app.apkBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (app.splitCount > 0) Text(stringResource(R.string.apps_split_badge), style = MaterialTheme.typography.labelSmall)
    }
}
