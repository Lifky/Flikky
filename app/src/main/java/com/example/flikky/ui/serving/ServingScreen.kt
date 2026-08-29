package com.example.flikky.ui.serving

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.di.ServiceLocator
import com.example.flikky.ui.components.ConnectionInfoCard
import com.example.flikky.ui.components.ConversationHeader
import com.example.flikky.ui.components.AvatarKey
import com.example.flikky.ui.components.NetworkStatusBanner
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.settings.sheets.BackgroundPickerSheet
import com.example.flikky.ui.settings.sheets.ThemePickerSheet
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun ServingScreen(
    onStopped: () -> Unit,
    viewModel: ServingViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    val progressMap by viewModel.fileTransferProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(settings.keepScreenOnDuringSession) {
            val window = (view.context as Activity).window
            if (settings.keepScreenOnDuringSession) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    val peerAvatarId by viewModel.peerAvatarId.collectAsState()
    val peerAvatarKey by viewModel.peerAvatarKey.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var actionTarget by remember { mutableStateOf<Long?>(null) }
    var showFilesQuickSheet by remember { mutableStateOf(false) }
    var showQuickSettings by remember { mutableStateOf(false) }
    // 快捷设置里「钻进去」的那三张复用 sheet，见下方托管处的注释。
    var quickPicker by remember { mutableStateOf<QuickPicker?>(null) }
    // 头像选择器的 App / Browser tab，与设置页同一形状（0 = App，1 = Browser）。
    var avatarSheetTab by remember { mutableStateOf(0) }
    var showPeerAvatarPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val backBlockedMessage = stringResource(R.string.serving_back_blocked)
    val fileSentMessage = stringResource(R.string.files_quick_sent)

    val allFiles by ServiceLocator.repository.observeAllFiles().collectAsState(initial = emptyList())
    // System-back dismisses the action target before exiting the screen.
    androidx.activity.compose.BackHandler(enabled = actionTarget != null) { actionTarget = null }
    // 会话进行中默认拦截返回，保护会话稳定（须点停止服务才离开）。开「允许会话中返回」后
    // 不拦截，系统返回正常弹回主页（服务仍运行，可从主页"继续服务"重进）。优先级低于上面关闭工具栏。
    androidx.activity.compose.BackHandler(
        enabled = actionTarget == null && !settings.allowBackDuringSession,
    ) {
        scope.launch { snackbarHostState.showSnackbar(backBlockedMessage) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        // Edge-to-edge IME 标准写法（官方规范）：Scaffold 默认 innerPadding 含 systemBars（不含 ime）。
        // padding(innerPadding) 应用 systemBars → consumeWindowInsets 标记已消费 → imePadding() 再补 ime，
        // 三者配合保证 ime 只生效一次。配合 manifest 的 windowSoftInputMode=adjustResize。
        // 缺 consumeWindowInsets 或 adjustResize 都会导致键盘弹起时 pan/inset 叠加：输入行被顶到顶部、
        // 上方留出键盘高度空白（test2 §5 复盘）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier.fillMaxSize().maxContentWidth(),
        ) {
            NetworkStatusBanner(
                status = ui.networkStatus,
                onAcknowledge = { viewModel.acknowledgeNetworkSwitch() },
            )
            // 真·高度 morph 的 spec 在 composable 体内先取（AnimatedContent.transitionSpec 非 @Composable，
            // 与 NavTransitions 同套路：先取后闭包捕获）：容器高度走 spatial 弹簧（轻微回弹、受全局速度档统辖），
            // 内容仅快速淡入淡出换装。下方对话区随容器高度平滑顶起，不再因高度突变而跳动。
            val headerSizeSpec = Motion.spatial<IntSize>()
            val headerEnterFade = Motion.effects<Float>()
            val headerExitFade = Motion.effectsFast<Float>()
            AnimatedContent(
                targetState = ui.clientConnected,
                transitionSpec = {
                    ContentTransform(
                        targetContentEnter = fadeIn(headerEnterFade),
                        initialContentExit = fadeOut(headerExitFade),
                        sizeTransform = SizeTransform { _, _ -> headerSizeSpec },
                    )
                },
                label = "ConnHeader",
            ) { connected ->
                if (connected) {
                    ConversationHeader(
                        peerAvatarId = peerAvatarId,
                        peerAvatarKey = peerAvatarKey,
                        peerName = "",
                        onAvatarClick = { showPeerAvatarPicker = true },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                FilledTonalIconButton(onClick = { showFilesQuickSheet = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_folder_open),
                                        contentDescription = stringResource(
                                            R.string.serving_files_quick
                                        ),
                                    )
                                }
                                // 快捷设置：会话期间「设置」tab 被锁，这里就近调气泡圆角 / 深色模式。
                                FilledTonalIconButton(onClick = { showQuickSettings = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_settings),
                                        contentDescription = stringResource(R.string.serving_quick_settings),
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { viewModel.stopService(); onStopped() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_power),
                                        contentDescription = stringResource(R.string.serving_stop_service),
                                    )
                                }
                            }
                        },
                    )
                } else {
                    Column(Modifier.padding(Spacing.sectionGap)) {
                        ConnectionInfoCard(url = ui.url, pin = ui.pin, requirePin = ui.requirePin)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            modifier = Modifier.padding(top = Spacing.lg).align(Alignment.CenterHorizontally),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.serving_waiting_browser),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        FilledTonalButton(
                            onClick = { viewModel.stopService(); onStopped() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(stringResource(R.string.serving_stop_service)) }
                    }
                }
            }

            ServingChatTab(
                viewModel = viewModel,
                ui = ui,
                settings = settings,
                progressMap = progressMap,
                peerAvatarId = peerAvatarId,
                peerAvatarKey = peerAvatarKey,
                actionTarget = actionTarget,
                onActionTargetChange = { actionTarget = it },
                snackbarHostState = snackbarHostState,
                scope = scope,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }

    // 快捷设置里的三个复杂选择器复用设置页那三张 sheet，而它们都是 ModalBottomSheet。
    // 两层 ModalBottomSheet 叠加在 Compose M3 里不可靠（scrim / 焦点都会打架），所以
    // 在这里托管：quickPicker 非空时不渲染快捷设置本体 —— 「钻进去，关掉再回来」，
    // 全程只有一层 sheet。showQuickSettings 保持为 true，所以关掉选择器它自己就回来了。
    if (showQuickSettings && quickPicker == null) {
        QuickSettingsSheet(
            settings = settings,
            browserAvatarKey = peerAvatarKey,
            defaultDeviceName = stringResource(R.string.settings_default_device_name),
            onSetBubbleCorner = { viewModel.setBubbleCornerRadius(it) },
            onSetAvatarGrouping = { viewModel.setAvatarGrouping(it) },
            onSetDarkMode = { viewModel.setDarkMode(it) },
            onSetAmoled = { viewModel.setAmoled(it) },
            onSetDeviceName = { viewModel.setDeviceName(it) },
            onSetSessionTimestamp = { viewModel.setSessionTimestampEnabled(it) },
            onSetMessageActionStyle = { viewModel.setMessageActionStyle(it) },
            onSetRecallBeta = { viewModel.setRecallBeta(it) },
            onSetAllowPeerRecall = { viewModel.setAllowPeerRecall(it) },
            onSetFavoriteBeta = { viewModel.setFavoriteBeta(it) },
            onSetStorageBrowsing = { viewModel.setStorageBrowsingEnabled(it) },
            onOpenThemePicker = { quickPicker = QuickPicker.Theme },
            onOpenAvatarPicker = { avatarSheetTab = 0; quickPicker = QuickPicker.Avatar },
            onOpenBackgroundPicker = { quickPicker = QuickPicker.Background },
            onDismiss = { showQuickSettings = false },
        )
    }

    when (quickPicker) {
        QuickPicker.Theme -> ThemePickerSheet(
            current = settings,
            onSelectMode = { viewModel.setThemeMode(it) },
            onSelectPreset = { viewModel.setPresetTheme(it) },
            onSelectCustomSeed = { viewModel.setCustomThemeSeed(it) },
            // null = 不渲染对比度段：对比度不进 PeerInfoDto，浏览器跟不了，
            // 而快捷设置的承诺是「这里每一项都会同步」（用户裁决）。
            onSelectContrast = null,
            onDismiss = { quickPicker = null },
        )
        QuickPicker.Avatar -> {
            // 与设置页逐字一致的两 tab 形状。浏览器侧走 viewModel.setPeerAvatarKey ——
            // 它先更新 session 再落库，把改动实时推给浏览器并抑制回声广播；设置页那边
            // 用的是只落库的 setBrowserAvatarKey，在会话中不够（浏览器不会立刻跟随）。
            val isApp = avatarSheetTab == 0
            AvatarPickerSheet(
                currentKey = if (isApp) settings.phoneAvatarKey else peerAvatarKey,
                fallbackKey = if (isApp) AvatarKey.DEFAULT_PHONE else AvatarKey.DEFAULT_PEER,
                onSelect = {
                    if (isApp) viewModel.setPhoneAvatarKey(it) else viewModel.setPeerAvatarKey(it)
                    quickPicker = null
                },
                onDismiss = { quickPicker = null },
                header = {
                    // 专名直显，不进 i18n（spec §4.2）——与设置页同一处理。
                    val tabs = listOf("App", "Browser")
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.md),
                    ) {
                        tabs.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = avatarSheetTab == index,
                                onClick = { avatarSheetTab = index },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            ) {
                                Text(label)
                            }
                        }
                    }
                },
            )
        }
        QuickPicker.Background -> BackgroundPickerSheet(
            current = settings.background,
            onSelect = { viewModel.setBackground(it) },
            onDismiss = { quickPicker = null },
        )
        null -> Unit
    }

    if (showPeerAvatarPicker) {
        AvatarPickerSheet(
            title = stringResource(R.string.serving_choose_browser_avatar),
            currentKey = peerAvatarKey,
            fallbackKey = AvatarKey.DEFAULT_PEER,
            onSelect = { viewModel.setPeerAvatarKey(it); showPeerAvatarPicker = false },
            onDismiss = { showPeerAvatarPicker = false },
        )
    }

    if (showFilesQuickSheet) {
        FilesQuickSheet(
            rows = allFiles,
            onSend = { row ->
                viewModel.sendStoredFile(row)
                scope.launch { snackbarHostState.showSnackbar(fileSentMessage) }
            },
            onDismiss = { showFilesQuickSheet = false },
        )
    }
}
