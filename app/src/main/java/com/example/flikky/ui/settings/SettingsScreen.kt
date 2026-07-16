package com.example.flikky.ui.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import com.example.flikky.export.ExportFileName
import com.example.flikky.export.ExportScope
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.ChoiceDialog
import com.example.flikky.ui.components.ChoiceRow
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.exporting.ArchiveViewModel
import com.example.flikky.ui.exporting.ExportDestinationSheet
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.settings.sheets.BackgroundPickerSheet
import com.example.flikky.ui.settings.sheets.ThemePickerSheet
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Which sheet / dialog is open
private sealed interface ActiveSheet {
    object Theme      : ActiveSheet
    object Avatar     : ActiveSheet
    object Background : ActiveSheet
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onExportSessions: () -> Unit,
    onExportReady: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    archiveViewModel: ArchiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val s by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionLabel = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
            ?.let { "v$it" }
            ?: "未知"
    }

    // Sheet / dialog state
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showActionStyleDialog by remember { mutableStateOf(false) }
    var showAvatarGroupingDialog by remember { mutableStateOf(false) }
    var showAnimSpeedDialog by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }
    var exportProgressTitle by remember { mutableStateOf("正在准备导出...") }
    var importExportExpanded by rememberSaveable { mutableStateOf(false) }
    var exportDestinationScope by rememberSaveable { mutableStateOf<ExportScope?>(null) }
    var pendingLocalExportScope by rememberSaveable { mutableStateOf<ExportScope?>(null) }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportProgress = true
            scope.launch {
                val message = try {
                    val result = archiveViewModel.importFromZip(uri)
                    buildString {
                        if (result.importedSessions > 0)
                            append("已导入 ${result.importedSessions} 个会话")
                        if (result.importedFavorites > 0) {
                            if (isNotEmpty()) append("，")
                            append("${result.importedFavorites} 条收藏")
                        }
                        if (result.settingsImported) {
                            if (isNotEmpty()) append("，")
                            append("设置已恢复")
                        }
                        val skipped = result.skippedSessions + result.skippedFavorites
                        if (skipped > 0) {
                            if (isNotEmpty()) append("，")
                            append("跳过 $skipped 个重复")
                        }
                        if (result.errors.isNotEmpty()) {
                            if (isNotEmpty()) append("，")
                            append("${result.errors.size} 个失败")
                        }
                        if (isEmpty()) append("未找到可导入的数据")
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    "导入失败，请确认所选文件是 Flikky zip 归档"
                } finally {
                    showImportProgress = false
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val localExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val exportScope = pendingLocalExportScope
        pendingLocalExportScope = null
        if (uri != null && exportScope != null) {
            exportProgressTitle = "正在保存..."
            showExportProgress = true
            scope.launch {
                val result = try {
                    archiveViewModel.saveExport(exportScope, uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } finally {
                    showExportProgress = false
                }
                val message = when (result) {
                    ArchiveViewModel.ExportStartResult.Success -> "已保存到所选位置"
                    ArchiveViewModel.ExportStartResult.NoFavorites -> "暂无收藏可导出"
                    ArchiveViewModel.ExportStartResult.TransferRunning,
                    ArchiveViewModel.ExportStartResult.UseSessionSelection,
                    null -> "保存失败，请重试或选择其他位置"
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val launchExport: (ExportScope) -> Unit = { exportScope ->
        exportProgressTitle = "正在准备导出..."
        showExportProgress = true
        scope.launch {
            val result = try {
                archiveViewModel.startExport(exportScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } finally {
                showExportProgress = false
            }
            when (result) {
                ArchiveViewModel.ExportStartResult.Success -> onExportReady()
                ArchiveViewModel.ExportStartResult.NoFavorites ->
                    snackbarHostState.showSnackbar("暂无收藏可导出")
                ArchiveViewModel.ExportStartResult.TransferRunning ->
                    snackbarHostState.showSnackbar("请先停止当前传输或导出")
                ArchiveViewModel.ExportStartResult.UseSessionSelection -> onExportSessions()
                null -> snackbarHostState.showSnackbar("准备导出失败，请重试")
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
                        trailing = { Avatar(avatarKey = s.phoneAvatarKey, size = Sizes.avatar) },
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
                        trailing = {
                            Text(
                                text = "${radiusDraft.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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
                val sectionItems = 5
                SettingSection(title = "会话行为") {
                    SettingItem(
                        title = "连接需要 PIN 码",
                        leadingIcon = painterResource(R.drawable.ic_fiber_pin),
                        subtitle = "开启后浏览器需输入 6 位 PIN；关闭后同一 Wi-Fi 内访问地址即可连接",
                        trailing = {
                            Switch(
                                checked = s.requirePin,
                                onCheckedChange = { viewModel.setRequirePin(it) },
                            )
                        },
                        index = 0, total = sectionItems,
                    )
                    val styleSubtitle = when (s.messageActionStyle) {
                        MessageActionStyle.FLOATING -> "悬浮工具栏"
                        MessageActionStyle.INLINE   -> "常驻按钮"
                    }
                    SettingItem(
                        title = "消息操作样式",
                        leadingIcon = painterResource(R.drawable.ic_touch_app),
                        subtitle = styleSubtitle,
                        onClick = { showActionStyleDialog = true },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = "消息撤回",
                        leadingIcon = painterResource(R.drawable.ic_undo),
                        subtitle = "允许撤回已发送的消息",
                        trailing = {
                            Switch(
                                checked = s.recallBetaEnabled,
                                onCheckedChange = { viewModel.setRecallBeta(it) },
                            )
                        },
                        index = 2, total = sectionItems,
                    )
                    SettingItem(
                        title = "收藏功能",
                        leadingIcon = painterResource(R.drawable.ic_star_border),
                        subtitle = "开启后显示收藏入口，并允许收藏消息",
                        trailing = {
                            Switch(
                                checked = s.favoriteBetaEnabled,
                                onCheckedChange = { viewModel.setFavoriteBeta(it) },
                            )
                        },
                        index = 3, total = sectionItems,
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
                        index = 4, total = sectionItems,
                    )
                }
            }

            // ─── 数据 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = if (importExportExpanded) 7 else 2
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
                        title = "导入与导出",
                        leadingIcon = painterResource(R.drawable.ic_swap_vert),
                        subtitle = if (importExportExpanded) "选择一项操作" else "备份、恢复或迁移数据",
                        trailing = {
                            val rotation by animateFloatAsState(
                                targetValue = if (importExportExpanded) 180f else 0f,
                                animationSpec = Motion.spatial(),
                                label = "ImportExportChevron",
                            )
                            Icon(
                                painter = painterResource(R.drawable.ic_expand_more),
                                contentDescription = null,
                                modifier = Modifier.rotate(rotation),
                            )
                        },
                        onClick = { importExportExpanded = !importExportExpanded },
                        modifier = Modifier.semantics {
                            stateDescription = if (importExportExpanded) "已展开" else "已收起"
                            customActions = listOf(
                                CustomAccessibilityAction(
                                    label = if (importExportExpanded) "收起" else "展开",
                                    action = {
                                        importExportExpanded = !importExportExpanded
                                        true
                                    },
                                )
                            )
                        },
                        index = 1, total = sectionItems,
                    )
                    AnimatedVisibility(
                        visible = importExportExpanded,
                        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                        exit = shrinkVertically(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            SettingItem(
                                title = "导入",
                                leadingIcon = painterResource(R.drawable.ic_file_download),
                                subtitle = "从 Flikky zip 归档恢复",
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                    )
                                },
                                index = 2, total = sectionItems,
                            )
                            SettingItem(
                                title = "导出会话",
                                leadingIcon = painterResource(R.drawable.ic_upload),
                                subtitle = "选择要导出的会话",
                                onClick = onExportSessions,
                                index = 3, total = sectionItems,
                            )
                            SettingItem(
                                title = "导出收藏",
                                leadingIcon = painterResource(R.drawable.ic_star_border),
                                subtitle = "导出全部收藏",
                                onClick = { exportDestinationScope = ExportScope.FAVORITES },
                                index = 4, total = sectionItems,
                            )
                            SettingItem(
                                title = "导出设置",
                                leadingIcon = painterResource(R.drawable.ic_settings_outline),
                                subtitle = "导出当前设置",
                                onClick = { exportDestinationScope = ExportScope.SETTINGS },
                                index = 5, total = sectionItems,
                            )
                            SettingItem(
                                title = "全部导出",
                                leadingIcon = painterResource(R.drawable.ic_swap_vert),
                                subtitle = "导出会话、收藏和设置",
                                onClick = { exportDestinationScope = ExportScope.ALL },
                                index = 6, total = sectionItems,
                            )
                        }
                    }
                }
            }

            // ─── 关于 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 2
                SettingSection(title = "关于") {
                    SettingItem(
                        title = "版本",
                        leadingIcon = painterResource(R.drawable.ic_info),
                        trailing = {
                            Text(
                                text = versionLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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
            currentKey = s.phoneAvatarKey,
            onSelect = { viewModel.setPhoneAvatarKey(it); activeSheet = null },
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
    if (showExportProgress) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(exportProgressTitle) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }

    exportDestinationScope?.let { exportScope ->
        ExportDestinationSheet(
            onSaveLocal = {
                exportDestinationScope = null
                pendingLocalExportScope = exportScope
                localExportLauncher.launch(
                    ExportFileName.build(exportScope, System.currentTimeMillis())
                )
            },
            onDownloadToComputer = {
                exportDestinationScope = null
                launchExport(exportScope)
            },
            onDismiss = { exportDestinationScope = null },
        )
    }
}
