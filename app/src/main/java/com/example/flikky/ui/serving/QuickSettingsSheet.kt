package com.example.flikky.ui.serving

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.AppLanguage
import com.example.flikky.data.settings.AppLanguageManager
import com.example.flikky.data.settings.AvatarGroupingMode
import com.example.flikky.data.settings.BUBBLE_CORNER_MAX
import com.example.flikky.data.settings.BUBBLE_CORNER_MIN
import com.example.flikky.data.settings.DarkMode
import com.example.flikky.data.settings.DEVICE_NAME_MAX
import com.example.flikky.data.settings.FlikkySettings
import com.example.flikky.data.settings.MessageActionStyle
import com.example.flikky.data.settings.ThemeMode
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.ChoiceDialog
import com.example.flikky.ui.components.ChoiceRow
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.settings.localizedLabel
import com.example.flikky.ui.theme.Motion
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.formatThemeSeed

/**
 * 快捷设置里需要「钻进去」的三张复用 sheet。它们都是 ModalBottomSheet，不能嵌套在
 * 快捷设置之上，所以由 ServingScreen 托管，见 [QuickSettingsSheet] 的 KDoc。
 */
enum class QuickPicker { Theme, Avatar, Background }

/**
 * 进行中会话的快捷设置 bottom sheet。
 *
 * 会话运行期间底部「设置」tab 被锁（[MainActivity] 的 `settingsEnabled = !servingActive`），
 * 用户进不了设置页。于是**凡是「双端同步」的设置项，只要不在这里，整场会话就都调不了**——
 * 双端同步这个能力也就发挥不出来。所以本 sheet 的收录标准不是「常用」而是「会同步」：
 * 判据就是它是否进了 [com.example.flikky.server.dto.PeerInfoDto]（改动经 `settings_changed`
 * 广播给已连浏览器）。
 *
 * 语言是唯一不走 PeerInfoDto 的收录项：它经 `/api/web-theme` 的 languageTag 同步，
 * 而 i18n.js 每秒轮询一次该端点（`setInterval(refresh, 1000)`），所以浏览器实时跟随。
 * 一度担心 `LocaleManager.applicationLocales` 会重建 Activity 把当前页面拆掉 ——
 * 不会：MainActivity 声明了 `configChanges` 含 `locale`（守卫见 MainActivityManifestTest）。
 *
 * 收录的 13 项（12 个 PeerInfoDto 字段 + 语言）：
 *   主题色 themeSeed · 深色模式 themeDark · AMOLED amoled · 设备名 deviceName ·
 *   两端头像 phoneAvatarKey/browserAvatarKey · 气泡圆角 bubbleCornerRadius ·
 *   头像显示 avatarGrouping · 会话背景 backgroundMode/Value · 会话时间戳
 *   sessionTimestampEnabled · 消息操作样式 messageActionStyle · 撤回 recallEnabled ·
 *   允许对方撤回 allowPeerRecall · 收藏 beta favoriteEnabled · 应用语言 languageTag
 *
 * 刻意不收：
 * - **不进 PeerInfoDto 的**（浏览器跟不了，放这里只是把设置页搬过来）：需要 PIN、
 *   历史保留、允许会话中返回、屏幕常亮、排序/分组、自动检查更新、对比度。
 *   对比度原先作为「复用整张 ThemePickerSheet 的代价」被留下，现按用户裁决去掉了：
 *   ThemePickerSheet 的 onSelectContrast 传 null 即不渲染那一段。
 * - **动效速度**：会同步，但用户裁决「意义不大」，刻意不收（见 QuickSettingsCoverageTest
 *   的 deliberatelyExcluded）。它在正式设置页照旧可调。
 *
 * 形状与设置页完全一致：同一批 [SettingSection] / [SettingItem] / [ChoiceDialog]，
 * 复杂选择器直接复用设置页那三张 sheet（主题色 / 头像 / 背景）。三者是
 * ModalBottomSheet，嵌套在本 sheet 之上不可靠，所以由调用方 [ServingScreen] 托管：
 * 本 sheet 只把请求抛上去，宿主先收起本 sheet 再打开选择器，关掉后本 sheet 自己回来。
 * 而 Dialog 是独立窗口，可以安全地盖在 bottom sheet 上，所以单选类交互全部留在原地。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsSheet(
    settings: FlikkySettings,
    /**
     * 浏览器端头像。会话进行中取 session 的实时值而不是 `settings.browserAvatarKey`：
     * 浏览器可以自己经 client_hello 选头像，此时 DataStore 里那份还没落地。
     */
    browserAvatarKey: String,
    defaultDeviceName: String,
    onSetBubbleCorner: (Int) -> Unit,
    onSetAvatarGrouping: (AvatarGroupingMode) -> Unit,
    onSetDarkMode: (DarkMode) -> Unit,
    onSetAmoled: (Boolean) -> Unit,
    onSetDeviceName: (String) -> Unit,
    onSetSessionTimestamp: (Boolean) -> Unit,
    onSetMessageActionStyle: (MessageActionStyle) -> Unit,
    onSetRecallBeta: (Boolean) -> Unit,
    onSetAllowPeerRecall: (Boolean) -> Unit,
    onSetFavoriteBeta: (Boolean) -> Unit,
    onOpenThemePicker: () -> Unit,
    onOpenAvatarPicker: () -> Unit,
    onOpenBackgroundPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val appLanguage = AppLanguageManager.current(context)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showAvatarGroupingDialog by remember { mutableStateOf(false) }
    var showActionStyleDialog by remember { mutableStateOf(false) }
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.quick_settings_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.quick_settings_scope_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingSection(title = stringResource(R.string.settings_section_general)) {
                SettingItem(
                    title = stringResource(R.string.settings_language),
                    leadingIcon = painterResource(R.drawable.ic_language),
                    subtitle = appLanguage.localizedLabel(),
                    onClick = { showLanguageDialog = true },
                )
            }

            // ─── 主题与外观 ───────────────────────────────────────────────────
            run {
                val total = 3
                SettingSection(title = stringResource(R.string.settings_section_theme_color)) {
                    val themeSubtitle = when (settings.themeMode) {
                        ThemeMode.DYNAMIC -> stringResource(R.string.settings_theme_follow_wallpaper)
                        ThemeMode.PRESET -> settings.presetTheme.localizedLabel()
                        ThemeMode.CUSTOM -> formatThemeSeed(settings.customThemeSeedArgb)
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_theme),
                        leadingIcon = painterResource(R.drawable.ic_palette),
                        subtitle = themeSubtitle,
                        onClick = onOpenThemePicker,
                        index = 0, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_dark_mode),
                        leadingIcon = painterResource(R.drawable.ic_dark_mode),
                        subtitle = settings.darkMode.localizedLabel(),
                        onClick = { showDarkModeDialog = true },
                        index = 1, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_amoled),
                        leadingIcon = painterResource(R.drawable.ic_contrast),
                        subtitle = stringResource(R.string.settings_amoled_summary),
                        trailing = {
                            Switch(checked = settings.amoled, onCheckedChange = onSetAmoled)
                        },
                        index = 2, total = total,
                    )
                }
            }

            // ─── 身份 ─────────────────────────────────────────────────────────
            run {
                val total = 2
                SettingSection(title = stringResource(R.string.settings_section_identity)) {
                    SettingItem(
                        title = stringResource(R.string.settings_device_name),
                        leadingIcon = painterResource(R.drawable.ic_smartphone),
                        subtitle = settings.deviceName.ifBlank { defaultDeviceName },
                        onClick = { showDeviceNameDialog = true },
                        index = 0, total = total,
                    )
                    // 与设置页同一行：两个头像并排。原先只放手机头像，用户指出这会让人
                    // 疑惑 —— 快捷设置处处像设置页，偏偏头像少一半，而浏览器头像的入口
                    // （顶栏点对方头像）用户未必知道可点。顶栏那个入口保留，这里是第二个。
                    SettingItem(
                        title = stringResource(R.string.settings_avatar),
                        leadingIcon = painterResource(R.drawable.ic_account_circle),
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(avatarKey = settings.phoneAvatarKey, size = Sizes.avatar)
                                Avatar(avatarKey = browserAvatarKey, size = Sizes.avatar)
                            }
                        },
                        onClick = onOpenAvatarPicker,
                        index = 1, total = total,
                    )
                }
            }

            // ─── 会话外观 ─────────────────────────────────────────────────────
            run {
                val total = 4
                SettingSection(title = stringResource(R.string.settings_section_session_appearance)) {
                    // 与设置页同一份样板：本地 draft 实时反馈，松手才提交到 DataStore
                    // （每一帧都写会把 DataStore 和 settings_changed 广播都打爆）。
                    var radiusDraft by remember(settings.bubbleCornerRadius) {
                        mutableStateOf(settings.bubbleCornerRadius.toFloat())
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
                                valueRange = BUBBLE_CORNER_MIN.toFloat()..BUBBLE_CORNER_MAX.toFloat(),
                                steps = BUBBLE_CORNER_MAX - BUBBLE_CORNER_MIN - 1,
                                onValueChangeFinished = { onSetBubbleCorner(radiusDraft.toInt()) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        index = 0, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_avatar_display),
                        leadingIcon = painterResource(R.drawable.ic_face),
                        subtitle = settings.avatarGrouping.localizedLabel(),
                        onClick = { showAvatarGroupingDialog = true },
                        index = 1, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_session_background),
                        leadingIcon = painterResource(R.drawable.ic_image),
                        subtitle = settings.background.localizedLabel(),
                        onClick = onOpenBackgroundPicker,
                        index = 2, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_session_timestamp),
                        leadingIcon = painterResource(R.drawable.ic_pin_history),
                        infoText = stringResource(R.string.settings_session_timestamp_summary),
                        trailing = {
                            Switch(
                                checked = settings.sessionTimestampEnabled,
                                onCheckedChange = onSetSessionTimestamp,
                            )
                        },
                        index = 3, total = total,
                    )
                }
            }

            // ─── 会话行为 ─────────────────────────────────────────────────────
            run {
                // 「允许对方撤回」跟着撤回开关折叠，所以总数随之变化。
                //
                // 这里曾是 5/4 —— 照搬了设置页的数字，却没搬它那三行（需要 PIN、允许
                // 会话中返回、屏幕常亮）。差一的后果不是少画一行，而是**最后一行永远
                // 拿不到 index == total - 1**，于是「收藏功能」的底部圆角一直是中间行
                // 的小圆角，跟首行「消息操作样式」的顶部大圆角不对称（用户截图 29）。
                // 本区实际可见行数：操作样式 + 撤回 + [允许对端撤回] + 收藏。
                val total = if (settings.recallBetaEnabled) 4 else 3
                SettingSection(title = stringResource(R.string.settings_section_session_behavior)) {
                    SettingItem(
                        title = stringResource(R.string.settings_message_action_style),
                        leadingIcon = painterResource(R.drawable.ic_touch_app),
                        subtitle = settings.messageActionStyle.localizedLabel(),
                        onClick = { showActionStyleDialog = true },
                        index = 0, total = total,
                    )
                    SettingItem(
                        title = stringResource(R.string.settings_recall),
                        leadingIcon = painterResource(R.drawable.ic_undo),
                        subtitle = stringResource(R.string.settings_recall_summary),
                        trailing = {
                            Switch(
                                checked = settings.recallBetaEnabled,
                                onCheckedChange = onSetRecallBeta,
                            )
                        },
                        index = 1, total = total,
                    )
                    AnimatedVisibility(
                        visible = settings.recallBetaEnabled,
                        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()),
                        exit = shrinkVertically(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
                    ) {
                        SettingItem(
                            title = stringResource(R.string.settings_allow_peer_recall),
                            leadingIcon = painterResource(R.drawable.ic_redo),
                            subtitle = stringResource(R.string.settings_allow_peer_recall_summary),
                            trailing = {
                                Switch(
                                    checked = settings.allowPeerRecall,
                                    onCheckedChange = onSetAllowPeerRecall,
                                )
                            },
                            index = 2, total = total,
                        )
                    }
                    SettingItem(
                        title = stringResource(R.string.settings_favorites),
                        leadingIcon = painterResource(R.drawable.ic_star_border),
                        subtitle = stringResource(R.string.settings_favorites_summary),
                        trailing = {
                            Switch(
                                checked = settings.favoriteBetaEnabled,
                                onCheckedChange = onSetFavoriteBeta,
                            )
                        },
                        index = if (settings.recallBetaEnabled) 3 else 2, total = total,
                    )
                }
            }
        }
    }

    // ── 单选类交互留在原地：Dialog 是独立窗口，可以安全盖在 bottom sheet 之上 ──

    if (showDarkModeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_dark_mode),
            onDismiss = { showDarkModeDialog = false },
        ) {
            DarkMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.localizedLabel(),
                    selected = settings.darkMode == mode,
                    onClick = { onSetDarkMode(mode); showDarkModeDialog = false },
                )
            }
        }
    }

    if (showAvatarGroupingDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_avatar_display),
            onDismiss = { showAvatarGroupingDialog = false },
        ) {
            AvatarGroupingMode.entries.forEach { mode ->
                ChoiceRow(
                    label = mode.localizedLabel(),
                    selected = settings.avatarGrouping == mode,
                    onClick = { onSetAvatarGrouping(mode); showAvatarGroupingDialog = false },
                )
            }
        }
    }

    if (showActionStyleDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_message_action_style),
            onDismiss = { showActionStyleDialog = false },
        ) {
            MessageActionStyle.entries.forEach { style ->
                ChoiceRow(
                    label = style.localizedLabel(),
                    selected = settings.messageActionStyle == style,
                    onClick = { onSetMessageActionStyle(style); showActionStyleDialog = false },
                )
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

    if (showDeviceNameDialog) {
        // 与设置页逐字一致：预填「当前生效的名字」（空则是默认名），保存时把
        // 「改回默认名」归一化成空串，否则默认名会被当成用户自定义值存下来。
        var draft by remember { mutableStateOf(settings.deviceName.ifBlank { defaultDeviceName }) }
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
                    onSetDeviceName(
                        draft.trim().takeUnless { it == defaultDeviceName }.orEmpty(),
                    )
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
}
