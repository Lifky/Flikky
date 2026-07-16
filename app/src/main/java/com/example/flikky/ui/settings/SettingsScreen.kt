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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flikky.R
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AppLanguageManager
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
    val appLanguage = AppLanguageManager.current(context)
    val defaultDeviceName = stringResource(R.string.settings_default_device_name)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val versionName = remember(context) {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
    }
    val versionLabel = versionName?.let { "v$it" } ?: stringResource(R.string.common_unknown)

    // Sheet / dialog state
    var activeSheet by remember { mutableStateOf<ActiveSheet?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }
    var showActionStyleDialog by remember { mutableStateOf(false) }
    var showAvatarGroupingDialog by remember { mutableStateOf(false) }
    var showAnimSpeedDialog by remember { mutableStateOf(false) }
    var showImportProgress by remember { mutableStateOf(false) }
    var showExportProgress by remember { mutableStateOf(false) }
    var exportProgressTitle by remember(context) {
        mutableStateOf(context.getString(R.string.settings_preparing_export))
    }
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
                    buildList {
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
                        subtitle = appLanguage.localizedLabel(),
                        onClick = { showLanguageDialog = true },
                    )
                }
            }

            // ─── 主题与色彩 ─────────────────────────────────────────────────────
            item {
                val sectionItems = 4
                SettingSection(title = stringResource(R.string.settings_section_theme_color)) {
                    val themeSubtitle = if (s.themeMode == ThemeMode.DYNAMIC) {
                        stringResource(R.string.settings_theme_follow_wallpaper)
                    } else {
                        s.presetTheme.localizedLabel()
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_theme),
                        leadingIcon = painterResource(R.drawable.ic_palette),
                        subtitle = themeSubtitle,
                        onClick = { activeSheet = ActiveSheet.Theme },
                        index = 0, total = sectionItems,
                    )
                    val darkSubtitle = s.darkMode.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_dark_mode),
                        leadingIcon = painterResource(R.drawable.ic_dark_mode),
                        subtitle = darkSubtitle,
                        onClick = { showDarkModeDialog = true },
                        index = 1, total = sectionItems,
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
                        index = 2, total = sectionItems,
                    )
                    val animSpeedSubtitle = s.animationSpeed.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_animation_speed),
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
                SettingSection(title = stringResource(R.string.settings_section_identity)) {
                    SettingItem(
                        title = stringResource(R.string.settings_device_name),
                        leadingIcon = painterResource(R.drawable.ic_smartphone),
                        subtitle = s.deviceName.ifBlank { defaultDeviceName },
                        onClick = { showDeviceNameDialog = true },
                        index = 0, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_app_avatar),
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
                    val groupingSubtitle = s.avatarGrouping.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_avatar_display),
                        leadingIcon = painterResource(R.drawable.ic_face),
                        subtitle = groupingSubtitle,
                        onClick = { showAvatarGroupingDialog = true },
                        index = 1, total = sectionItems,
                    )
                    val bgSubtitle = s.background.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_session_background),
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
                SettingSection(title = stringResource(R.string.settings_section_session_behavior)) {
                    SettingItem(
                        title = stringResource(R.string.settings_require_pin),
                        leadingIcon = painterResource(R.drawable.ic_fiber_pin),
                        subtitle = stringResource(R.string.settings_require_pin_summary),
                        trailing = {
                            Switch(
                                checked = s.requirePin,
                                onCheckedChange = { viewModel.setRequirePin(it) },
                            )
                        },
                        index = 0, total = sectionItems,
                    )
                    val styleSubtitle = s.messageActionStyle.localizedLabel()
                    SettingItem(
                        title = stringResource(R.string.settings_message_action_style),
                        leadingIcon = painterResource(R.drawable.ic_touch_app),
                        subtitle = styleSubtitle,
                        onClick = { showActionStyleDialog = true },
                        index = 1, total = sectionItems,
                    )
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
                        index = 3, total = sectionItems,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_allow_back),
                        leadingIcon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
                        subtitle = stringResource(R.string.settings_allow_back_summary),
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
                        title = stringResource(R.string.settings_history_limit),
                        leadingIcon = painterResource(R.drawable.ic_history),
                        subtitle = historySubtitle,
                        onClick = { showHistoryLimitDialog = true },
                        index = 0, total = sectionItems,
                    )
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
                                title = stringResource(R.string.settings_import),
                                leadingIcon = painterResource(R.drawable.ic_file_download),
                                subtitle = stringResource(R.string.settings_import_summary),
                                onClick = {
                                    importLauncher.launch(
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                    )
                                },
                                index = 2, total = sectionItems,
                            )
                            SettingItem(
                                title = stringResource(R.string.settings_export_sessions),
                                leadingIcon = painterResource(R.drawable.ic_upload),
                                subtitle = stringResource(R.string.settings_export_sessions_summary),
                                onClick = onExportSessions,
                                index = 3, total = sectionItems,
                            )
                            SettingItem(
                                title = stringResource(R.string.settings_export_favorites),
                                leadingIcon = painterResource(R.drawable.ic_star_border),
                                subtitle = stringResource(R.string.settings_export_favorites_summary),
                                onClick = { exportDestinationScope = ExportScope.FAVORITES },
                                index = 4, total = sectionItems,
                            )
                            SettingItem(
                                title = stringResource(R.string.settings_export_settings),
                                leadingIcon = painterResource(R.drawable.ic_settings_outline),
                                subtitle = stringResource(R.string.settings_export_settings_summary),
                                onClick = { exportDestinationScope = ExportScope.SETTINGS },
                                index = 5, total = sectionItems,
                            )
                            SettingItem(
                                title = stringResource(R.string.settings_export_all),
                                leadingIcon = painterResource(R.drawable.ic_swap_vert),
                                subtitle = stringResource(R.string.settings_export_all_summary),
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
                SettingSection(title = stringResource(R.string.settings_section_about)) {
                    SettingItem(
                        title = stringResource(R.string.settings_version),
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
                        index = 1, total = sectionItems,
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
                    onValueChange = { if (it.length <= 20) draft = it },
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
