package com.example.flikky.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AppLanguageManager
import com.example.flikky.data.settings.DEVICE_NAME_MAX
import com.example.flikky.data.settings.BackgroundSetting
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.AnimationSpeed
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.THUMBNAIL_CACHE_LIMIT_DEFAULT_MB
import com.example.flikky.data.settings.THUMBNAIL_CACHE_LIMIT_OPTIONS_MB
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.export.ExportFileName
import com.example.flikky.export.ExportScope
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.AvatarKey
import com.example.flikky.ui.components.ChoiceDialog
import com.example.flikky.ui.components.ChoiceRow
import com.example.flikky.ui.components.maxContentWidth
import com.example.flikky.ui.components.UpdateAvailableDialog
import com.example.flikky.ui.exporting.ArchiveViewModel
import com.example.flikky.ui.exporting.ExportDestinationSheet
import com.example.flikky.ui.settings.components.SettingExpandableGroup
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.settings.sheets.AvatarPickerSheet
import com.example.flikky.ui.settings.sheets.BackgroundPickerSheet
import com.example.flikky.ui.settings.sheets.ThemePickerSheet
import com.example.flikky.util.formatThemeSeed
import com.example.flikky.util.formatBytes
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val OPEN_SOURCE_REPOSITORY_URL = "https://github.com/Lifky/Flikky"

internal fun openExternalLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

// Which sheet / dialog is open
private sealed interface ActiveSheet {
    object Theme      : ActiveSheet
    object LeadingShape : ActiveSheet
    object LeadingColor : ActiveSheet
    object Avatar     : ActiveSheet
    object Background : ActiveSheet
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onExportSessions: () -> Unit,
    onExportReady: () -> Unit,
    onOpenFiles: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
    archiveViewModel: ArchiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val context = LocalContext.current
    val s by viewModel.settings.collectAsState()
    val updateChecking by viewModel.updateChecking.collectAsState()
    val updateAvailable by viewModel.updateAvailable.collectAsState()
    val cacheUsageBytes by viewModel.thumbnailCacheUsageBytes.collectAsState()
    val appLanguage = AppLanguageManager.current(context)
    val defaultDeviceName = stringResource(R.string.settings_default_device_name)
    val checkingUpdateLabel = stringResource(R.string.settings_checking_update)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }
    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
    }
    val versionLabel = versionName?.let { "v$it" } ?: stringResource(R.string.common_unknown)

    // Sheet / dialog state
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var avatarSheetTab by remember { mutableStateOf(0) } // 0 = App, 1 = Browser
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showThumbnailCacheDialog by remember { mutableStateOf(false) }
    var showActionStyleDialog by remember { mutableStateOf(false) }
    var showAvatarGroupingDialog by remember { mutableStateOf(false) }
    var showAnimSpeedDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }
    var importConflictCount by remember { mutableStateOf<Int?>(null) }
    var showExportProgress by remember { mutableStateOf(false) }
    var exportProgressTitle by remember(context) {
        mutableStateOf(context.getString(R.string.settings_preparing_export))
    }
    var importExportExpanded by rememberSaveable { mutableStateOf(false) }
    var exportDestinationScope by rememberSaveable { mutableStateOf<ExportScope?>(null) }
    var pendingLocalExportScope by rememberSaveable { mutableStateOf<ExportScope?>(null) }

    fun importResultMessage(result: ArchiveViewModel.ImportResult): String = buildList {
        if (result.importedSessions > 0) {
            add(context.resources.getQuantityString(
                R.plurals.settings_imported_sessions,
                result.importedSessions,
                result.importedSessions,
            ))
        }
        if (result.importedFavorites > 0) {
            add(context.resources.getQuantityString(
                R.plurals.settings_imported_favorites,
                result.importedFavorites,
                result.importedFavorites,
            ))
        }
        if (result.settingsImported) {
            add(context.getString(R.string.settings_imported_settings))
        }
        val skipped = result.skippedSessions + result.skippedFavorites
        if (skipped > 0) {
            add(context.resources.getQuantityString(
                R.plurals.settings_import_skipped,
                skipped,
                skipped,
            ))
        }
        if (result.errors.isNotEmpty()) {
            add(context.resources.getQuantityString(
                R.plurals.settings_import_failed_count,
                result.errors.size,
                result.errors.size,
            ))
        }
    }.ifEmpty {
        listOf(context.getString(R.string.settings_import_empty))
    }.joinToString(context.getString(R.string.common_list_separator))

    fun finishSettingsImport(overwriteExisting: Boolean) {
        importConflictCount = null
        showImportProgress = true
        scope.launch {
            val message = try {
                importResultMessage(archiveViewModel.resolveImport(overwriteExisting))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                context.getString(R.string.settings_import_failed)
            } finally {
                showImportProgress = false
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportProgress = true
            scope.launch {
                val message = try {
                    when (val start = archiveViewModel.beginImport(uri)) {
                        is ArchiveViewModel.ImportStart.Done -> importResultMessage(start.result)
                        is ArchiveViewModel.ImportStart.NeedsDecision -> {
                            importConflictCount = start.conflictCount
                            null
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    context.getString(R.string.settings_import_failed)
                } finally {
                    showImportProgress = false
                }
                if (message != null) snackbarHostState.showSnackbar(message)
            }
        }
    }

    val localExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val exportScope = pendingLocalExportScope
        pendingLocalExportScope = null
        if (uri != null && exportScope != null) {
            exportProgressTitle = context.getString(R.string.settings_saving)
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
                    ArchiveViewModel.ExportStartResult.Success ->
                        context.getString(R.string.settings_export_saved)
                    ArchiveViewModel.ExportStartResult.NoFavorites ->
                        context.getString(R.string.settings_no_favorites)
                    ArchiveViewModel.ExportStartResult.TransferRunning,
                    ArchiveViewModel.ExportStartResult.UseSessionSelection,
                    null -> context.getString(R.string.settings_export_save_failed)
                }
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    val launchExport: (ExportScope) -> Unit = { exportScope ->
        exportProgressTitle = context.getString(R.string.settings_preparing_export)
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
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_no_favorites))
                ArchiveViewModel.ExportStartResult.TransferRunning ->
                    snackbarHostState.showSnackbar(context.getString(R.string.settings_stop_transfer_first))
                ArchiveViewModel.ExportStartResult.UseSessionSelection -> onExportSessions()
                null -> snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_export_prepare_failed)
                )
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
                        Text(stringResource(R.string.settings_title))
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
            item {
                SettingSection(title = stringResource(R.string.settings_section_general)) {
                    SettingItem(
                        title = stringResource(R.string.settings_language),
                        leadingIcon = painterResource(R.drawable.ic_language),
                        trailingValue = appLanguage.localizedLabel(),
                        onClick = { showLanguageDialog = true },
                    )
                }
            }

            // ─── 主题与色彩 ─────────────────────────────────────────────────────
            item {
                val sectionItems = 6
                SettingSection(title = stringResource(R.string.settings_section_theme_color)) {
                    val themeValue = when (s.themeMode) {
                        ThemeMode.DYNAMIC -> stringResource(R.string.settings_theme_follow_wallpaper)
                        ThemeMode.PRESET -> s.presetTheme.localizedLabel()
                        ThemeMode.CUSTOM -> formatThemeSeed(s.customThemeSeedArgb)
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_theme),
                        leadingIcon = painterResource(R.drawable.ic_palette),
                        trailingValue = themeValue,
                        onClick = { activeSheet = ActiveSheet.Theme },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.leading_shape_title),
                        leadingIcon = painterResource(R.drawable.ic_interests),
                        subtitle = stringResource(R.string.leading_shape_summary),
                        trailing = {
                            Text(
                                text = s.leadingShape.localizedLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { activeSheet = ActiveSheet.LeadingShape },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.leading_color_title),
                        leadingIcon = painterResource(R.drawable.ic_interests_fill),
                        subtitle = stringResource(R.string.leading_color_summary),
                        trailing = {
                            Text(
                                text = s.leadingColorMode.localizedLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = { activeSheet = ActiveSheet.LeadingColor },
                        index = 2, total = sectionItems,
                    )
                    val darkValue = s.darkMode.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_dark_mode),
                        leadingIcon = painterResource(R.drawable.ic_dark_mode),
                        trailingValue = darkValue,
                        onClick = { showDarkModeDialog = true },
                        index = 3, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_amoled),
                        leadingIcon = painterResource(R.drawable.ic_contrast),
                        subtitle = stringResource(R.string.settings_amoled_summary),
                        trailing = {
                            Switch(
                                checked = s.amoled,
                                onCheckedChange = { viewModel.setAmoled(it) },
                            )
                        },
                        index = 4, total = sectionItems,
                    )
                    val animSpeedValue = s.animationSpeed.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_animation_speed),
                        leadingIcon = painterResource(R.drawable.ic_animation),
                        trailingValue = animSpeedValue,
                        onClick = { showAnimSpeedDialog = true },
                        index = 5, total = sectionItems,
                    )
                }
            }

            // ─── 身份 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 2
                SettingSection(title = stringResource(R.string.settings_section_identity)) {
                    SettingItem(
                        title = stringResource(R.string.settings_device_name),
                        leadingIcon = painterResource(R.drawable.ic_smartphone),
                        trailingValue = s.deviceName.ifBlank { defaultDeviceName },
                        onClick = { showDeviceNameDialog = true },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_avatar),
                        leadingIcon = painterResource(R.drawable.ic_account_circle),
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(avatarKey = s.phoneAvatarKey, size = Sizes.avatar)
                                Avatar(avatarKey = s.browserAvatarKey, size = Sizes.avatar)
                            }
                        },
                        onClick = { avatarSheetTab = 0; activeSheet = ActiveSheet.Avatar },
                        index = 1, total = sectionItems,
                    )
                }
            }

            // ─── 会话外观 ───────────────────────────────────────────────────────
            item {
                val sectionItems = 4
                SettingSection(title = stringResource(R.string.settings_section_session_appearance)) {
                    var radiusDraft by remember(s.bubbleCornerRadius) {
                        mutableStateOf(s.bubbleCornerRadius.toFloat())
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_bubble_corner),
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
                    val groupingValue = s.avatarGrouping.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_avatar_display),
                        leadingIcon = painterResource(R.drawable.ic_face),
                        trailingValue = groupingValue,
                        onClick = { showAvatarGroupingDialog = true },
                        index = 1, total = sectionItems,
                    )
                    val bgValue = s.background.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_session_background),
                        leadingIcon = painterResource(R.drawable.ic_image),
                        trailingValue = bgValue,
                        onClick = { activeSheet = ActiveSheet.Background },
                        index = 2, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_session_timestamp),
                        leadingIcon = painterResource(R.drawable.ic_pin_history),
                        infoText = stringResource(R.string.settings_session_timestamp_summary),
                        trailing = {
                            Switch(
                                checked = s.sessionTimestampEnabled,
                                onCheckedChange = viewModel::setSessionTimestampEnabled,
                            )
                        },
                        index = 3, total = sectionItems,
                    )
                }
            }

            // ─── 会话行为 ───────────────────────────────────────────────────────
            item {
                // +1 是「显示隐藏文件」那一行。这个数是分组圆角的依据（第一行与
                // 最后一行外圆角更大），少算一个会让最后一行画成中间行的形状。
                // 分段圆角的 index/total **不许随展开态变化**（2026-09-09 装机反馈）。
                //
                // `AnimatedVisibility` 收起动画要跑 ~200ms，期间那几行**仍在组合里**。
                // 若这个值跟着 flag 瞬间翻转，那几行就带着越界的 index 继续显示一段：
                // index 3 在 total=4 下被当成「末行」而突然变成下圆角，
                // 位置正好在两个 listitem 的交界处 —— 就是用户看到的「白线卡一下」。
                //
                // 展开时不会有这个现象：flag 先变 true、total 先变大，
                // 那几行是带着**正确**的 index 出现的。这正是缺陷只在收起时出现的原因。
                //
                // 用固定的上界安全，因为 `segmentedShapes` 只区分
                // 首行（index 0）/ 末行（index == count - 1）/ 中间行：
                // 收起后可见的 index 有断档（3..7 缺失）也不影响任何一行的圆角。
                val sectionItems = 9
                SettingSection(title = stringResource(R.string.settings_section_session_behavior)) {
                    SettingItem(
                        title = stringResource(R.string.settings_require_pin),
                        leadingIcon = painterResource(R.drawable.ic_fiber_pin),
                        infoText = stringResource(R.string.settings_require_pin_summary),
                        trailing = {
                            Switch(
                                checked = s.requirePin,
                                onCheckedChange = { viewModel.setRequirePin(it) },
                            )
                        },
                        index = 0, total = sectionItems,
                    )
                    val styleValue = s.messageActionStyle.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_message_action_style),
                        leadingIcon = painterResource(R.drawable.ic_touch_app),
                        trailingValue = styleValue,
                        onClick = { showActionStyleDialog = true },
                        index = 1, total = sectionItems,
                    )
                    // 表头 + 展开区收成单一子项：AnimatedVisibility 直接坐在
                    // SettingSection 的 spacedBy Column 里时，收起动画期间会是
                    // 两倍间隔（2026-09-08 装机反馈）。详见 SettingExpandableGroup。
                    SettingExpandableGroup(
                        expanded = s.recallBetaEnabled,
                        header = {
                            SettingItem(
                                title = stringResource(R.string.settings_recall),
                                leadingIcon = painterResource(R.drawable.ic_undo),
                                subtitle = stringResource(R.string.settings_recall_summary),
                                trailing = {
                                    Switch(
                                        checked = s.recallBetaEnabled,
                                        onCheckedChange = { viewModel.setRecallBeta(it) },
                                    )
                                },
                                index = 2, total = sectionItems,
                            )
                        },
                    ) {
                        SettingItem(
                            title = stringResource(R.string.settings_allow_peer_recall),
                            leadingIcon = painterResource(R.drawable.ic_redo),
                            subtitle = stringResource(R.string.settings_allow_peer_recall_summary),
                            trailing = {
                                Switch(
                                    checked = s.allowPeerRecall,
                                    onCheckedChange = { viewModel.setAllowPeerRecall(it) },
                                )
                            },
                            index = 3, total = sectionItems,
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_favorites),
                        leadingIcon = painterResource(R.drawable.ic_star_border),
                        subtitle = stringResource(R.string.settings_favorites_summary),
                        trailing = {
                            Switch(
                                checked = s.favoriteBetaEnabled,
                                onCheckedChange = { viewModel.setFavoriteBeta(it) },
                            )
                        },
                        index = 4, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_allow_back),
                        leadingIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                        infoText = stringResource(R.string.settings_allow_back_summary),
                        trailing = {
                            Switch(
                                checked = s.allowBackDuringSession,
                                onCheckedChange = { viewModel.setAllowBackDuringSession(it) },
                            )
                        },
                        index = 5, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_keep_screen_on),
                        leadingIcon = painterResource(R.drawable.ic_wb_sunny),
                        trailing = {
                            Switch(
                                checked = s.keepScreenOnDuringSession,
                                onCheckedChange = viewModel::setKeepScreenOnDuringSession,
                            )
                        },
                        index = 6, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_storage_browsing),
                        leadingIcon = painterResource(R.drawable.ic_folder),
                        infoText = stringResource(R.string.settings_storage_browsing_summary),
                        trailing = {
                            Switch(
                                checked = s.storageBrowsingEnabled,
                                onCheckedChange = viewModel::setStorageBrowsingEnabled,
                            )
                        },
                        index = 7, total = sectionItems,
                    )
                    // 「显示隐藏文件」紧跟在存储浏览下面：它只在浏览存储时才起作用。
                    // 两端共用这一个值，副标题的计数也走它——三者用不同判据就是
                    // 2026-09-03「副标题 5 项、进去只有 4 行」的成因。
                    SettingItem(
                        title = stringResource(R.string.settings_show_hidden),
                        // Folder Eye：这一行说的是「看不看得见隐藏项」，
                        // 普通 folder 与上面那行「浏览手机存储」撞图（用户点名换的）。
                        leadingIcon = painterResource(R.drawable.ic_folder_eye),
                        infoText = stringResource(R.string.settings_show_hidden_summary),
                        trailing = {
                            Switch(
                                checked = s.showHiddenFiles,
                                onCheckedChange = viewModel::setShowHiddenFiles,
                            )
                        },
                        index = sectionItems - 1, total = sectionItems,
                    )
                }
            }

            // ─── 数据 ─────────────────────────────────────────────────────────
            item {
                // 分段圆角的 index/total **不许随展开态变化**（2026-09-09 装机反馈）。
                //
                // `AnimatedVisibility` 收起动画要跑 ~200ms，期间那几行**仍在组合里**。
                // 若这个值跟着 flag 瞬间翻转，那几行就带着越界的 index 继续显示一段：
                // index 3 在 total=4 下被当成「末行」而突然变成下圆角，
                // 位置正好在两个 listitem 的交界处 —— 就是用户看到的「白线卡一下」。
                //
                // 展开时不会有这个现象：flag 先变 true、total 先变大，
                // 那几行是带着**正确**的 index 出现的。这正是缺陷只在收起时出现的原因。
                //
                // 用固定的上界安全，因为 `segmentedShapes` 只区分
                // 首行（index 0）/ 末行（index == count - 1）/ 中间行：
                // 收起后可见的 index 有断档（3..7 缺失）也不影响任何一行的圆角。
                val sectionItems = 10
                SettingSection(title = stringResource(R.string.settings_section_data)) {
                    val historySubtitle = when (s.historyRetainLimit) {
                        -1 -> stringResource(R.string.settings_history_unlimited)
                        0 -> stringResource(R.string.settings_history_disabled)
                        20 -> stringResource(R.string.settings_history_default)
                        else -> pluralStringResource(
                            R.plurals.settings_history_count,
                            s.historyRetainLimit,
                            s.historyRetainLimit,
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.files_entry),
                        leadingIcon = painterResource(R.drawable.ic_folder_open),
                        subtitle = stringResource(R.string.files_entry_summary),
                        onClick = onOpenFiles,
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_history_limit),
                        leadingIcon = painterResource(R.drawable.ic_history),
                        subtitle = historySubtitle,
                        onClick = { showHistoryLimitDialog = true },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_thumbnail_cache),
                        leadingIcon = painterResource(R.drawable.ic_image),
                        subtitle = if (cacheUsageBytes == null) {
                            stringResource(R.string.settings_thumbnail_cache_calculating)
                        } else {
                            stringResource(
                                R.string.settings_thumbnail_cache_usage,
                                formatBytes(requireNotNull(cacheUsageBytes)),
                                if (s.thumbnailCacheLimitMb == 0) {
                                    stringResource(R.string.settings_thumbnail_cache_none)
                                } else {
                                    stringResource(
                                        R.string.settings_thumbnail_cache_megabytes,
                                        s.thumbnailCacheLimitMb,
                                    )
                                },
                            )
                        },
                        onClick = { showThumbnailCacheDialog = true },
                        index = 2, total = sectionItems,
                    )
                    // 与撤回那处同一修法：收成单一子项，gap 归 AnimatedVisibility 内部。
                    SettingExpandableGroup(
                        expanded = importExportExpanded,
                        header = {
                        SettingItem(
                            title = stringResource(R.string.settings_import_export),
                            leadingIcon = painterResource(R.drawable.ic_swap_vert),
                            subtitle = if (importExportExpanded) {
                                stringResource(R.string.settings_import_export_choose)
                            } else {
                                stringResource(R.string.settings_import_export_summary)
                            },
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
                                stateDescription = context.getString(
                                    if (importExportExpanded) R.string.common_expanded
                                    else R.string.common_collapsed
                                )
                                customActions = listOf(
                                    CustomAccessibilityAction(
                                        label = context.getString(
                                            if (importExportExpanded) R.string.common_collapse
                                            else R.string.common_expand
                                        ),
                                        action = {
                                            importExportExpanded = !importExportExpanded
                                            true
                                        },
                                    )
                                )
                            },
                            index = 3, total = sectionItems,
                        )
                        },
                    ) {
                        SettingItem(
                            title = stringResource(R.string.settings_import),
                            leadingIcon = painterResource(R.drawable.ic_file_download),
                            subtitle = stringResource(R.string.settings_import_summary),
                            onClick = {
                                importLauncher.launch(
                                    arrayOf("application/zip", "application/x-zip-compressed")
                                )
                            },
                            index = 4, total = sectionItems,
                        )
                        SettingItem(
                            title = stringResource(R.string.settings_export_sessions),
                            leadingIcon = painterResource(R.drawable.ic_upload),
                            subtitle = stringResource(R.string.settings_export_sessions_summary),
                            onClick = onExportSessions,
                            index = 5, total = sectionItems,
                        )
                        SettingItem(
                            title = stringResource(R.string.settings_export_favorites),
                            leadingIcon = painterResource(R.drawable.ic_star_border),
                            subtitle = stringResource(R.string.settings_export_favorites_summary),
                            onClick = { exportDestinationScope = ExportScope.FAVORITES },
                            index = 6, total = sectionItems,
                        )
                        SettingItem(
                            title = stringResource(R.string.settings_export_settings),
                            leadingIcon = painterResource(R.drawable.ic_settings_outline),
                            subtitle = stringResource(R.string.settings_export_settings_summary),
                            onClick = { exportDestinationScope = ExportScope.SETTINGS },
                            index = 7, total = sectionItems,
                        )
                        SettingItem(
                            title = stringResource(R.string.settings_export_all),
                            leadingIcon = painterResource(R.drawable.ic_publish),
                            subtitle = stringResource(R.string.settings_export_all_summary),
                            onClick = { exportDestinationScope = ExportScope.ALL },
                            index = 8, total = sectionItems,
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_delete_all),
                        leadingIcon = painterResource(R.drawable.ic_delete_forever),
                        onClick = { showDeleteAllDialog = true },
                        index = sectionItems - 1, total = sectionItems,
                    )
                }
            }

            // ─── 关于 ─────────────────────────────────────────────────────────
            item {
                val sectionItems = 3
                SettingSection(title = stringResource(R.string.settings_section_about)) {
                    SettingItem(
                        title = stringResource(R.string.settings_version),
                        leadingIcon = painterResource(R.drawable.ic_info),
                        subtitle = versionLabel,
                        trailing = {
                            if (updateChecking) {
                                Box(
                                    // TextButton 的最小触控目标是 48dp，占位高度必须一致，
                                    // 否则按钮换转圈时整行高度跳动。
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .semantics {
                                                contentDescription = checkingUpdateLabel
                                            },
                                    )
                                }
                            } else {
                                TextButton(onClick = { viewModel.checkForUpdate() }) {
                                    Text(stringResource(R.string.settings_check_update))
                                }
                            }
                        },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_auto_check_update),
                        leadingIcon = painterResource(R.drawable.ic_update),
                        trailing = {
                            Switch(
                                checked = s.autoCheckUpdate,
                                onCheckedChange = viewModel::setAutoCheckUpdate,
                            )
                        },
                        index = 1, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_open_source),
                        leadingIcon = painterResource(R.drawable.ic_code),
                        subtitle = stringResource(R.string.settings_open_source_summary),
                        trailing = {
                            Icon(
                                painter = painterResource(R.drawable.ic_open_in_new),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            if (!openExternalLink(context, OPEN_SOURCE_REPOSITORY_URL)) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_open_source_open_failed)
                                    )
                                }
                            }
                        },
                        index = 2, total = sectionItems,
                    )
                }
            }
        }
        }
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            onDismiss = { showLanguageDialog = false },
        ) {
            AppLanguage.entries.forEach { language ->
                ChoiceRow(
                    label = language.localizedLabel(),
                    selected = appLanguage == language,
                    onClick = {
                        showLanguageDialog = false
                        AppLanguageManager.set(context, language)
                    },
                )
            }
        }
    }

    updateAvailable?.let { info ->
        UpdateAvailableDialog(
            info = info,
            onConfirm = {
                viewModel.dismissUpdateDialog()
                if (!openExternalLink(context, info.htmlUrl)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.settings_open_source_open_failed),
                        )
                    }
                }
            },
            onDismiss = { viewModel.dismissUpdateDialog() },
        )
    }

    // ─── Picker sheets ────────────────────────────────────────────────────────
    when (activeSheet) {
        ActiveSheet.Theme -> ThemePickerSheet(
            current = s,
            onSelectMode = { viewModel.setThemeMode(it) },
            onSelectPreset = { viewModel.setPreset(it) },
            onSelectCustomSeed = { viewModel.setCustomThemeSeed(it) },
            onSelectContrast = { viewModel.setContrast(it) },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.LeadingShape -> LeadingShapeSheet(
            current = s.leadingShape,
            onSelect = { viewModel.setLeadingShape(it) },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.LeadingColor -> LeadingColorSheet(
            current = s.leadingColorMode,
            onSelect = { viewModel.setLeadingColorMode(it) },
            onDismiss = { activeSheet = null },
        )
        ActiveSheet.Avatar -> {
            val isApp = avatarSheetTab == 0
            AvatarPickerSheet(
                currentKey = if (isApp) s.phoneAvatarKey else s.browserAvatarKey,
                fallbackKey = if (isApp) AvatarKey.DEFAULT_PHONE else AvatarKey.DEFAULT_BROWSER,
                onSelect = {
                    if (isApp) viewModel.setPhoneAvatarKey(it) else viewModel.setBrowserAvatarKey(it)
                    activeSheet = null
                },
                onDismiss = { activeSheet = null },
                header = {
                    // 专名直显，不进 i18n（spec §4.2）。
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
        ChoiceDialog(
            title = stringResource(R.string.settings_dark_mode),
            onDismiss = { showDarkModeDialog = false },
        ) {
            DarkMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.localizedLabel(),
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
        ChoiceDialog(
            title = stringResource(R.string.settings_message_action_style),
            onDismiss = { showActionStyleDialog = false },
        ) {
            MessageActionStyle.entries.forEach { style ->
                ChoiceRow(
                    label = style.localizedLabel(),
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
        ChoiceDialog(
            title = stringResource(R.string.settings_avatar_display),
            onDismiss = { showAvatarGroupingDialog = false },
        ) {
            AvatarGroupingMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.localizedLabel(),
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
        ChoiceDialog(
            title = stringResource(R.string.settings_animation_speed),
            onDismiss = { showAnimSpeedDialog = false },
        ) {
            AnimationSpeed.entries.forEach { speed ->
                ChoiceRow(
                    label = speed.localizedLabel(),
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
        var draft by remember { mutableStateOf(s.deviceName.ifBlank { defaultDeviceName }) }
        AlertDialog(
            onDismissRequest = { showDeviceNameDialog = false },
            title = { Text(stringResource(R.string.settings_device_name)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= DEVICE_NAME_MAX) draft = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.settings_device_name_limit)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = draft.trim()
                        .takeUnless { it == defaultDeviceName }
                        .orEmpty()
                    viewModel.setDeviceName(normalized)
                    showDeviceNameDialog = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeviceNameDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
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
            title = stringResource(R.string.settings_history_limit),
            onDismiss = { showHistoryLimitDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val limit = if (useDefault) 20 else customStr.toIntOrNull() ?: s.historyRetainLimit
                    viewModel.setHistoryRetainLimit(limit)
                    showHistoryLimitDialog = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
        ) {
            ChoiceRow(
                label = stringResource(R.string.settings_history_default),
                selected = useDefault,
                onClick = { useDefault = true },
            )
            ChoiceRow(
                label = stringResource(R.string.settings_history_custom),
                selected = !useDefault,
                onClick = { useDefault = false },
            )
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
                    label = { Text(stringResource(R.string.settings_history_input_hint)) },
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = Spacing.sm),
                )
            }
        }
    }

    if (showThumbnailCacheDialog) {
        var selectedLimitMb by remember(s.thumbnailCacheLimitMb) {
            mutableStateOf(s.thumbnailCacheLimitMb)
        }
        ChoiceDialog(
            title = stringResource(R.string.settings_thumbnail_cache),
            onDismiss = { showThumbnailCacheDialog = false },
            neutralButton = {
                TextButton(onClick = { viewModel.clearThumbnailCache() }) {
                    Text(stringResource(R.string.settings_thumbnail_cache_clear))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setThumbnailCacheLimitMb(selectedLimitMb)
                    showThumbnailCacheDialog = false
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.settings_thumbnail_cache_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = Spacing.sm),
            )
            Text(
                text = stringResource(R.string.settings_thumbnail_cache_limit),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = Spacing.xs),
            )
            THUMBNAIL_CACHE_LIMIT_OPTIONS_MB.forEach { limitMb ->
                val label = when (limitMb) {
                    0 -> stringResource(R.string.settings_thumbnail_cache_none)
                    THUMBNAIL_CACHE_LIMIT_DEFAULT_MB -> stringResource(
                        R.string.settings_thumbnail_cache_default,
                    )
                    else -> stringResource(R.string.settings_thumbnail_cache_megabytes, limitMb)
                }
                ChoiceRow(
                    label = label,
                    selected = selectedLimitMb == limitMb,
                    onClick = { selectedLimitMb = limitMb },
                )
            }
            Text(
                text = if (cacheUsageBytes == null) {
                    stringResource(R.string.settings_thumbnail_cache_calculating)
                } else {
                    stringResource(
                        R.string.settings_thumbnail_cache_current_usage,
                        formatBytes(requireNotNull(cacheUsageBytes)),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = Spacing.sm),
            )
        }
    }

    if (showDeleteAllDialog) {
        var resetSettings by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_delete_all_body))
                    Spacer(Modifier.height(Spacing.md))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { resetSettings = !resetSettings },
                    ) {
                        Checkbox(
                            checked = resetSettings,
                            onCheckedChange = { resetSettings = it },
                        )
                        Text(stringResource(R.string.settings_delete_all_reset))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        viewModel.deleteAllData(resetSettings)
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_delete_all_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    importConflictCount?.let { conflictCount ->
        AlertDialog(
            onDismissRequest = {
                importConflictCount = null
                archiveViewModel.cancelImport()
            },
            title = { Text(stringResource(R.string.home_import_conflict_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.home_import_conflict_message,
                        conflictCount,
                        conflictCount,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { finishSettingsImport(overwriteExisting = true) }) {
                    Text(stringResource(R.string.home_import_overwrite))
                }
            },
            dismissButton = {
                TextButton(onClick = { finishSettingsImport(overwriteExisting = false) }) {
                    Text(stringResource(R.string.home_import_skip))
                }
            },
        )
    }

    // ─── Import progress dialog ───────────────────────────────────────────────
    if (showImportProgress) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(stringResource(R.string.settings_importing)) },
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
