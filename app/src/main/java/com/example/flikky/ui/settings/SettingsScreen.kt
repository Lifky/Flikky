package com.example.flikky.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.ChoiceDialog
import com.example.flikky.ui.components.ChoiceRow
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.settings.sheets.BackgroundPickerSheet
import com.example.flikky.ui.settings.sheets.ThemePickerSheet
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.launch

// Which sheet / dialog is open
private sealed interface ActiveSheet {
    object Theme      : ActiveSheet
    object Avatar     : ActiveSheet
    object Background : ActiveSheet
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var showActionStyleDialog by remember { mutableStateOf(false) }
    var showAvatarGroupingDialog by remember { mutableStateOf(false) }
    var showAnimSpeedDialog by remember { mutableStateOf(false) }
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // 顶部 inset 交给 LargeTopAppBar 自己处理（标题栏铺到状态栏下方）；底部 inset 已由 MainActivity 施加。
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("设置")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPad ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPad),
            contentAlignment = Alignment.TopCenter,
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .maxContentWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screenEdge, vertical = Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.sectionGap),
        ) {
            // ─── 主题与色彩 ─────────────────────────────────────────────────────
            item {
                val sectionItems = 4
                SettingSection(title = "主题与色彩") {
                    val themeSubtitle = if (s.themeMode == ThemeMode.DYNAMIC) "跟随壁纸"
                    else s.presetTheme.label
                    SettingItem(
                        title = "主题",
                        leadingIcon = painterResource(R.drawable.ic_palette),
                        subtitle = themeSubtitle,
                        onClick = { activeSheet = ActiveSheet.Theme },
                        index = 0, total = sectionItems,
                    )
                    val darkSubtitle = when (s.darkMode) {
                        DarkMode.SYSTEM -> "跟随系统"
                        DarkMode.LIGHT  -> "常亮"
                        DarkMode.DARK   -> "常暗"
                    }
                    SettingItem(
                        title = "深色模式",
                        leadingIcon = painterResource(R.drawable.ic_dark_mode),
                        subtitle = darkSubtitle,
                        onClick = { showDarkModeDialog = true },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = "AMOLED 纯黑",
                        leadingIcon = painterResource(R.drawable.ic_contrast),
                        subtitle = "深色模式下使用纯黑背景",
                        trailing = {
                            Switch(
                                checked = s.amoled,
                                onCheckedChange = { viewModel.setAmoled(it) },
                            )
                        },
                        index = 2, total = sectionItems,
                    )
                    val animSpeedSubtitle = when (s.animationSpeed) {
                        AnimationSpeed.OFF      -> "关闭"
                        AnimationSpeed.SLOW     -> "慢"
                        AnimationSpeed.STANDARD -> "标准"
                        AnimationSpeed.FAST     -> "快"
                    }
                    SettingItem(
                        title = "动画速度",
                        leadingIcon = painterResource(R.drawable.ic_animation),
                        subtitle = animSpeedSubtitle,
                        onClick = { showAnimSpeedDialog = true },
                        index = 3, total = sectionItems,
                    )
                }
            }

            // ─── 身份 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 2
                SettingSection(title = "身份") {
                    SettingItem(
                        title = "本机名称",
                        leadingIcon = painterResource(R.drawable.ic_smartphone),
                        subtitle = s.deviceName,
                        onClick = { showDeviceNameDialog = true },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = "APP 端头像",
                        leadingIcon = painterResource(R.drawable.ic_account_circle),
                        trailing = { Avatar(avatarId = s.phoneAvatarId, size = Sizes.avatar) },
                        onClick = { activeSheet = ActiveSheet.Avatar },
                        index = 1, total = sectionItems,
                    )
                }
            }

            // ─── 会话外观 ───────────────────────────────────────────────────────
            item {
                val sectionItems = 3
                SettingSection(title = "会话外观") {
                    var radiusDraft by remember(s.bubbleCornerRadius) {
                        mutableStateOf(s.bubbleCornerRadius.toFloat())
                    }
                    SettingItem(
                        title = "气泡圆角",
                        leadingIcon = painterResource(R.drawable.ic_rounded_corner),
                        subtitle = "${radiusDraft.toInt()} dp",
                        content = {
                            Slider(
                                value = radiusDraft,
                                onValueChange = { radiusDraft = it },
                                valueRange = com.example.flikky.data.settings.BUBBLE_CORNER_MIN.toFloat()
                                    ..com.example.flikky.data.settings.BUBBLE_CORNER_MAX.toFloat(),
                                steps = (com.example.flikky.data.settings.BUBBLE_CORNER_MAX
                                    - com.example.flikky.data.settings.BUBBLE_CORNER_MIN - 1),
                                onValueChangeFinished = { viewModel.setBubbleCornerRadius(radiusDraft.toInt()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        index = 0, total = sectionItems,
                    )
                    val groupingSubtitle = when (s.avatarGrouping) {
                        AvatarGroupingMode.FIRST -> "组内首条显示"
                        AvatarGroupingMode.LAST  -> "组内末条显示"
                        AvatarGroupingMode.EACH  -> "每条都显示"
                    }
                    SettingItem(
                        title = "头像显示",
                        leadingIcon = painterResource(R.drawable.ic_face),
                        subtitle = groupingSubtitle,
                        onClick = { showAvatarGroupingDialog = true },
                        index = 1, total = sectionItems,
                    )
                    val bgSubtitle = when (val bg = s.background) {
                        is BackgroundSetting.Default  -> "默认"
                        is BackgroundSetting.Blank    -> "空白"
                        is BackgroundSetting.Solid    -> "纯色"
                        // BackgroundSetting.Gradient removed in v1.6.0
                    }
                    SettingItem(
                        title = "进行中会话背景",
                        leadingIcon = painterResource(R.drawable.ic_image),
                        subtitle = bgSubtitle,
                        onClick = { activeSheet = ActiveSheet.Background },
                        index = 2, total = sectionItems,
                    )
                }
            }

            // ─── 会话行为 ───────────────────────────────────────────────────────
            item {
                val sectionItems = 4
                SettingSection(title = "会话行为") {
                    val styleSubtitle = when (s.messageActionStyle) {
                        MessageActionStyle.FLOATING -> "悬浮工具栏"
                        MessageActionStyle.INLINE   -> "常驻按钮"
                    }
                    SettingItem(
                        title = "消息操作样式",
                        leadingIcon = painterResource(R.drawable.ic_touch_app),
                        subtitle = styleSubtitle,
                        onClick = { showActionStyleDialog = true },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = "消息撤回（Beta）",
                        leadingIcon = painterResource(R.drawable.ic_undo),
                        subtitle = "允许撤回已发送的消息",
                        trailing = {
                            Switch(
                                checked = s.recallBetaEnabled,
                                onCheckedChange = { viewModel.setRecallBeta(it) },
                            )
                        },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = "收藏功能（Beta）",
                        leadingIcon = painterResource(R.drawable.ic_star_border),
                        subtitle = "开启后显示收藏入口，并允许收藏消息",
                        trailing = {
                            Switch(
                                checked = s.favoriteBetaEnabled,
                                onCheckedChange = { viewModel.setFavoriteBeta(it) },
                            )
                        },
                        index = 2, total = sectionItems,
                    )
                    SettingItem(
                        title = "允许会话中返回",
                        leadingIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                        subtitle = "默认拦截返回键保护会话；开启后可返回主页查看历史（会话期间设置入口锁定）",
                        trailing = {
                            Switch(
                                checked = s.allowBackDuringSession,
                                onCheckedChange = { viewModel.setAllowBackDuringSession(it) },
                            )
                        },
                        index = 3, total = sectionItems,
                    )
                }
            }

            // ─── 数据 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 3
                SettingSection(title = "数据") {
                    val historySubtitle = when (s.historyRetainLimit) {
                        -1   -> "无限制"
                        0    -> "不保存"
                        20   -> "默认（20 条）"
                        else -> "${s.historyRetainLimit} 条"
                    }
                    SettingItem(
                        title = "历史保存数量",
                        leadingIcon = painterResource(R.drawable.ic_history),
                        subtitle = historySubtitle,
                        onClick = { showHistoryLimitDialog = true },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = "导入",
                        leadingIcon = painterResource(R.drawable.ic_file_download),
                        subtitle = "从 zip 文件导入会话",
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/zip", "application/x-zip-compressed")
                            )
                        },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = "导出",
                        leadingIcon = painterResource(R.drawable.ic_upload),
                        subtitle = "将会话导出为 zip 文件",
                        onClick = onExport,
                        index = 2, total = sectionItems,
                    )
                }
            }

            // ─── 关于 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 2
                SettingSection(title = "关于") {
                    SettingItem(
                        title = "版本",
                        leadingIcon = painterResource(R.drawable.ic_info),
                        subtitle = "v1.11.0",
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = "开源",
                        leadingIcon = painterResource(R.drawable.ic_code),
                        subtitle = "Ktor · mdui · Apache/MIT",
                        index = 1, total = sectionItems,
                    )
                }
            }
        }
        }
    }

    // ─── Picker sheets ────────────────────────────────────────────────────────
    when (activeSheet) {
        ActiveSheet.Theme -> ThemePickerSheet(
            current = s,
            onSelectMode = { viewModel.setThemeMode(it) },
            onSelectPreset = { viewModel.setPreset(it) },
            onSelectContrast = { viewModel.setContrast(it) },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.Avatar -> AvatarPickerSheet(
            currentId = s.phoneAvatarId,
            onSelect = { viewModel.setPhoneAvatar(it); activeSheet = null },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.Background -> BackgroundPickerSheet(
            // 选项点选只更新背景、不关面板，便于连续比较/微调；关闭靠下滑或点外部。
            current = s.background,
            onSelect = { viewModel.setBackground(it) },
            onDismiss = { activeSheet = null },
        )
        null -> Unit
    }

    // ─── Dark mode dialog ─────────────────────────────────────────────────────
    if (showDarkModeDialog) {
        ChoiceDialog(title = "深色模式", onDismiss = { showDarkModeDialog = false }) {
            listOf(
                DarkMode.SYSTEM to "跟随系统",
                DarkMode.LIGHT  to "常亮",
                DarkMode.DARK   to "常暗",
            ).forEach { (mode, label) ->
                ChoiceRow(
                    label = label,
                    selected = s.darkMode == mode,
                    onClick = {
                        viewModel.setDarkMode(mode)
                        showDarkModeDialog = false
                    },
                )
            }
        }
    }

    // ─── Message action style dialog ──────────────────────────────────────────
    if (showActionStyleDialog) {
        ChoiceDialog(title = "消息操作样式", onDismiss = { showActionStyleDialog = false }) {
            listOf(
                MessageActionStyle.FLOATING to "悬浮工具栏",
                MessageActionStyle.INLINE   to "常驻按钮",
            ).forEach { (style, label) ->
                ChoiceRow(
                    label = label,
                    selected = s.messageActionStyle == style,
                    onClick = {
                        viewModel.setMessageActionStyle(style)
                        showActionStyleDialog = false
                    },
                )
            }
        }
    }

    // ─── Avatar grouping dialog ───────────────────────────────────────────────
    if (showAvatarGroupingDialog) {
        ChoiceDialog(title = "头像显示", onDismiss = { showAvatarGroupingDialog = false }) {
            listOf(
                AvatarGroupingMode.FIRST to "组内首条显示",
                AvatarGroupingMode.LAST  to "组内末条显示",
                AvatarGroupingMode.EACH  to "每条都显示",
            ).forEach { (mode, label) ->
                ChoiceRow(
                    label = label,
                    selected = s.avatarGrouping == mode,
                    onClick = {
                        viewModel.setAvatarGrouping(mode)
                        showAvatarGroupingDialog = false
                    },
                )
            }
        }
    }

    // ─── Animation speed dialog ───────────────────────────────────────────────
    if (showAnimSpeedDialog) {
        ChoiceDialog(title = "动画速度", onDismiss = { showAnimSpeedDialog = false }) {
            listOf(
                AnimationSpeed.OFF      to "关闭",
                AnimationSpeed.SLOW     to "慢",
                AnimationSpeed.STANDARD to "标准",
                AnimationSpeed.FAST     to "快",
            ).forEach { (speed, label) ->
                ChoiceRow(
                    label = label,
                    selected = s.animationSpeed == speed,
                    onClick = {
                        viewModel.setAnimationSpeed(speed)
                        showAnimSpeedDialog = false
                    },
                )
            }
        }
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
        ChoiceDialog(
            title = "历史保存数量",
            onDismiss = { showHistoryLimitDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val limit = if (useDefault) 20 else customStr.toIntOrNull() ?: s.historyRetainLimit
                    viewModel.setHistoryRetainLimit(limit)
                    showHistoryLimitDialog = false
                }) { Text("确定") }
            },
        ) {
            ChoiceRow(label = "默认（20 条）", selected = useDefault, onClick = { useDefault = true })
            ChoiceRow(label = "自定义", selected = !useDefault, onClick = { useDefault = false })
            if (!useDefault) {
                OutlinedTextField(
                    value = customStr,
                    onValueChange = { value ->
                        if (value.isEmpty() || value == "-" || value.toIntOrNull() != null) {
                            customStr = value
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    label = { Text("0=不保存，-1=无限制") },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = Spacing.sm),
                )
            }
        }
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
