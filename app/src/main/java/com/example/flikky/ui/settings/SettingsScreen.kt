package com.example.flikky.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.PresetTheme
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.settings.components.groupedItemShape
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.settings.sheets.BackgroundPickerSheet
import com.example.flikky.ui.settings.sheets.ThemePickerSheet
import kotlinx.coroutines.launch

// Which sheet / dialog is open
private sealed interface ActiveSheet {
    object Theme      : ActiveSheet
    object Avatar     : ActiveSheet
    object Background : ActiveSheet
}

@Composable
fun SettingsScreen(
    onExport: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val s by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sheet / dialog state
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportProgress = true
            scope.launch {
                val result = viewModel.importFromZip(uri)
                showImportProgress = false
                val msg = buildString {
                    if (result.imported.isNotEmpty())
                        append("已导入 ${result.imported.size} 个会话")
                    if (result.skipped.isNotEmpty()) {
                        if (isNotEmpty()) append("，")
                        append("跳过 ${result.skipped.size} 个重复")
                    }
                    if (result.errors.isNotEmpty()) {
                        if (isNotEmpty()) append("，")
                        append("${result.errors.size} 个失败")
                    }
                    if (isEmpty()) append("未找到可导入的会话")
                }
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ─── 外观 ─────────────────────────────────────────────────────────
            item {
                val appearanceItems = 6
                SettingSection(title = "外观") {
                    // 主题
                    val themeSubtitle = if (s.themeMode == ThemeMode.DYNAMIC) "跟随壁纸"
                    else when (s.presetTheme) {
                        PresetTheme.CORAL    -> "珊瑚"
                        PresetTheme.MUSHROOM -> "蘑菇"
                        PresetTheme.TEAL     -> "青黛"
                        PresetTheme.MIST     -> "雾霭"
                    }
                    SettingItem(
                        title = "主题",
                        subtitle = themeSubtitle,
                        trailing = {
                            Icon(
                                painter = painterResource(R.drawable.ic_palette),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { activeSheet = ActiveSheet.Theme },
                        shape = groupedItemShape(0, appearanceItems),
                    )
                    // 深色模式
                    val darkSubtitle = when (s.darkMode) {
                        DarkMode.SYSTEM -> "跟随系统"
                        DarkMode.LIGHT  -> "常亮"
                        DarkMode.DARK   -> "常暗"
                    }
                    SettingItem(
                        title = "深色模式",
                        subtitle = darkSubtitle,
                        trailing = {
                            Icon(
                                painter = painterResource(R.drawable.ic_dark_mode),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { showDarkModeDialog = true },
                        shape = groupedItemShape(1, appearanceItems),
                    )
                    // AMOLED 纯黑
                    SettingItem(
                        title = "AMOLED 纯黑",
                        subtitle = "深色模式下使用纯黑背景",
                        trailing = {
                            Switch(
                                checked = s.amoled,
                                onCheckedChange = { viewModel.setAmoled(it) },
                            )
                        },
                        shape = groupedItemShape(2, appearanceItems),
                    )
                    // APP 端头像
                    SettingItem(
                        title = "APP 端头像",
                        trailing = { Avatar(avatarId = s.phoneAvatarId, size = 40.dp) },
                        onClick = { activeSheet = ActiveSheet.Avatar },
                        shape = groupedItemShape(3, appearanceItems),
                    )
                    // 进行中会话背景
                    val bgSubtitle = when (val bg = s.background) {
                        is BackgroundSetting.Default  -> "默认"
                        is BackgroundSetting.Blank    -> "空白"
                        is BackgroundSetting.Solid    -> "纯色"
                        // BackgroundSetting.Gradient removed in v1.6.0
                    }
                    SettingItem(
                        title = "进行中会话背景",
                        subtitle = bgSubtitle,
                        onClick = { activeSheet = ActiveSheet.Background },
                        shape = groupedItemShape(4, appearanceItems),
                    )
                    // 气泡圆角（index 5）
                    var radiusDraft by remember(s.bubbleCornerRadius) {
                        mutableStateOf(s.bubbleCornerRadius.toFloat())
                    }
                    SettingItem(
                        title = "气泡圆角",
                        subtitle = "${radiusDraft.toInt()} dp",
                        trailing = {
                            Slider(
                                value = radiusDraft,
                                onValueChange = { radiusDraft = it },
                                valueRange = com.example.flikky.data.settings.BUBBLE_CORNER_MIN.toFloat()
                                    ..com.example.flikky.data.settings.BUBBLE_CORNER_MAX.toFloat(),
                                steps = (com.example.flikky.data.settings.BUBBLE_CORNER_MAX
                                    - com.example.flikky.data.settings.BUBBLE_CORNER_MIN - 1),
                                onValueChangeFinished = { viewModel.setBubbleCornerRadius(radiusDraft.toInt()) },
                                modifier = Modifier.width(160.dp),
                            )
                        },
                        shape = groupedItemShape(5, appearanceItems),
                    )
                }
            }

            // ─── 会话 ─────────────────────────────────────────────────────────
            item {
                val sessionItems = 2
                SettingSection(title = "会话") {
                    SettingItem(
                        title = "本机名称",
                        subtitle = s.deviceName,
                        onClick = { showDeviceNameDialog = true },
                        shape = groupedItemShape(0, sessionItems),
                    )
                    SettingItem(
                        title = "消息撤回（Beta）",
                        subtitle = "允许撤回已发送的消息",
                        trailing = {
                            Switch(
                                checked = s.recallBetaEnabled,
                                onCheckedChange = { viewModel.setRecallBeta(it) },
                            )
                        },
                        shape = groupedItemShape(1, sessionItems),
                    )
                }
            }

            // ─── 数据 ─────────────────────────────────────────────────────────
            item {
                val dataItems = 3
                SettingSection(title = "数据") {
                    val historySubtitle = when (s.historyRetainLimit) {
                        -1   -> "无限制"
                        0    -> "不保存"
                        20   -> "默认（20 条）"
                        else -> "${s.historyRetainLimit} 条"
                    }
                    SettingItem(
                        title = "历史保存数量",
                        subtitle = historySubtitle,
                        onClick = { showHistoryLimitDialog = true },
                        shape = groupedItemShape(0, dataItems),
                    )
                    SettingItem(
                        title = "导入",
                        subtitle = "从 zip 文件导入会话",
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed")
                            )
                        },
                        shape = groupedItemShape(1, dataItems),
                    )
                    SettingItem(
                        title = "导出",
                        subtitle = "将会话导出为 zip 文件",
                        onClick = onExport,
                        shape = groupedItemShape(2, dataItems),
                    )
                }
            }

            // ─── 关于 ─────────────────────────────────────────────────────────
            item {
                val aboutItems = 2
                SettingSection(title = "关于") {
                    SettingItem(
                        title = "版本",
                        subtitle = "v1.5.0",
                        shape = groupedItemShape(0, aboutItems),
                    )
                    SettingItem(
                        title = "开源",
                        subtitle = "Ktor · mdui · Apache/MIT",
                        shape = groupedItemShape(1, aboutItems),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { Snackbar(it) }
    }

    // ─── Picker sheets ────────────────────────────────────────────────────────
    when (activeSheet) {
        ActiveSheet.Theme -> ThemePickerSheet(
            current = s,
            onSelectMode = { viewModel.setThemeMode(it) },
            onSelectPreset = { viewModel.setPreset(it) },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.Avatar -> AvatarPickerSheet(
            currentId = s.phoneAvatarId,
            onSelect = { viewModel.setPhoneAvatar(it); activeSheet = null },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.Background -> BackgroundPickerSheet(
            current = s.background,
            onSelect = { viewModel.setBackground(it); activeSheet = null },
            onDismiss = { activeSheet = null },
        )
        null -> Unit
    }

    // ─── Dark mode dialog ─────────────────────────────────────────────────────
    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text("深色模式") },
            text = {
                Column {
                    listOf(
                        DarkMode.SYSTEM to "跟随系统",
                        DarkMode.LIGHT  to "常亮",
                        DarkMode.DARK   to "常暗",
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            RadioButton(
                                selected = s.darkMode == mode,
                                onClick = {
                                    viewModel.setDarkMode(mode)
                                    showDarkModeDialog = false
                                },
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDarkModeDialog = false }) { Text("取消") }
            },
        )
    }

    // ─── Device name dialog ───────────────────────────────────────────────────
    if (showDeviceNameDialog) {
        var draft by remember { mutableStateOf(s.deviceName) }
        AlertDialog(
            onDismissRequest = { showDeviceNameDialog = false },
            title = { Text("本机名称") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 20) draft = it },
                    singleLine = true,
                    label = { Text("最多 20 字") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setDeviceName(draft.trim().ifEmpty { s.deviceName })
                    showDeviceNameDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceNameDialog = false }) { Text("取消") }
            },
        )
    }

    // ─── History retain limit dialog ──────────────────────────────────────────
    if (showHistoryLimitDialog) {
        var useDefault by remember { mutableStateOf(s.historyRetainLimit == 20) }
        var customStr by remember {
            mutableStateOf(
                if (s.historyRetainLimit == 20) "" else s.historyRetainLimit.toString()
            )
        }
        AlertDialog(
            onDismissRequest = { showHistoryLimitDialog = false },
            title = { Text("历史保存数量") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = useDefault,
                            onClick = { useDefault = true },
                        )
                        Text("默认（20 条）", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = !useDefault,
                            onClick = { useDefault = false },
                        )
                        Text("自定义", modifier = Modifier.padding(start = 8.dp))
                    }
                    if (!useDefault) {
                        OutlinedTextField(
                            value = customStr,
                            onValueChange = { customStr = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("0=不保存，-1=无限制") },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val limit = if (useDefault) 20 else customStr.toIntOrNull() ?: s.historyRetainLimit
                    viewModel.setHistoryRetainLimit(limit)
                    showHistoryLimitDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showHistoryLimitDialog = false }) { Text("取消") }
            },
        )
    }

    // ─── Import progress dialog ───────────────────────────────────────────────
    if (showImportProgress) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("正在导入...") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }
}
