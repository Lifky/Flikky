package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.session.PeerDevice
import com.example.flikky.session.displayName
import com.example.flikky.ui.components.Avatar
import com.example.flikky.ui.components.AvatarStack
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.theme.Sizes
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.ui.theme.connected

/**
 * 「已连接设备」面板（spec §6.2）。容器与「对端权限」面板一致；行与文件页列表行同组件、同槽位。
 * 离线设备也列出来：它的 Cookie 还在，随时能回来 —— 用户要能看清此刻谁还拿着访问权（spec §4.5）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevicesSheet(
    devices: List<PeerDevice>,
    onRename: (PeerDevice) -> Unit,
    onChangeAvatar: (PeerDevice) -> Unit,
    onDisconnect: (PeerDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenEdge)
                .padding(bottom = Spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.serving_devices_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val ordered = devicesInSheetOrder(devices)
            SettingSection(title = null) {
                ordered.forEachIndexed { index, device ->
                    DeviceRow(device, index, ordered.size, onRename, onChangeAvatar, onDisconnect)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceRow(
    device: PeerDevice,
    index: Int,
    count: Int,
    onRename: (PeerDevice) -> Unit,
    onChangeAvatar: (PeerDevice) -> Unit,
    onDisconnect: (PeerDevice) -> Unit,
) {
    val unknown = stringResource(R.string.device_unknown_browser)
    val status = stringResource(if (device.online) R.string.serving_device_online else R.string.serving_device_offline)
    val statusColor = if (device.online) MaterialTheme.colorScheme.connected else MaterialTheme.colorScheme.onSurfaceVariant
    val label = deviceSupportingLabel(device)
    SegmentedListItem(
        onClick = { onRename(device) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingContentColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.fillMaxWidth().testTag("device-row-${device.deviceId}"),
        leadingContent = { Avatar(avatarKey = device.avatarKey, size = Sizes.avatar) },
        supportingContent = {
            Text(
                text = buildAnnotatedString {
                    if (label != null) append("$label · ")
                    withStyle(SpanStyle(color = statusColor)) { append(status) }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        content = { Text(device.displayName(unknown), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = { DeviceRowMenu(device, onRename, onChangeAvatar, onDisconnect) },
    )
}

/** 与文件页行菜单同一写法（`FilesScreen` 的 trailing）：⋮ + DropdownMenu，危险项用 error 色。 */
@Composable
private fun DeviceRowMenu(
    device: PeerDevice,
    onRename: (PeerDevice) -> Unit,
    onChangeAvatar: (PeerDevice) -> Unit,
    onDisconnect: (PeerDevice) -> Unit,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.serving_device_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.serving_device_rename)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = { expanded = false; onRename(device) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.serving_device_change_avatar)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_face), contentDescription = null) },
                onClick = { expanded = false; onChangeAvatar(device) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.serving_device_disconnect)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link_off), contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = { expanded = false; onDisconnect(device) },
            )
        }
    }
}

/** 在线优先，同状态按连接时间（spec §6.2 #10）。 */
internal fun devicesInSheetOrder(devices: List<PeerDevice>): List<PeerDevice> =
    devices.sortedWith(compareByDescending<PeerDevice> { it.online }.thenBy { it.connectedAt })

/** 副标题始终是完整的「浏览器 · 系统」（装机 Screenshot_8）；默认名只含浏览器，系统信息靠这里补全。 */
internal fun deviceSupportingLabel(device: PeerDevice): String? = device.defaultLabel

/** 叠放头像最多几个：再多就看不清了，台数交给旁边的文字。 */
internal const val MAX_STACKED_AVATARS = 3

/**
 * 顶栏（装机 Screenshot_2）：一台时显示它的名字，多台时显示「N 台设备在线」，配叠放头像。
 * 原先拼「X 等 N 台」必然被截断，而且只认得最早那一台。没有在线设备时为 null。
 */
data class HeaderSummary(val primary: PeerDevice, val onlineCount: Int, val avatarKeys: List<String>)

internal fun headerSummary(devices: List<PeerDevice>): HeaderSummary? {
    val online = devices.filter { it.online }.sortedBy { it.connectedAt }
    val first = online.firstOrNull() ?: return null
    return HeaderSummary(first, online.size, online.take(MAX_STACKED_AVATARS).map { it.avatarKey })
}

/** 没有设备在线、但会话里还有设备（都离线了）时的等待行（装机 Screenshot_3）。否则为 null。 */
data class OfflinePeers(val count: Int, val firstName: String, val avatarKeys: List<String>)

internal fun offlinePeers(devices: List<PeerDevice>, unknownLabel: String): OfflinePeers? {
    if (devices.isEmpty() || devices.any { it.online }) return null
    val ordered = devices.sortedBy { it.connectedAt }
    return OfflinePeers(ordered.size, ordered.first().displayName(unknownLabel), ordered.take(MAX_STACKED_AVATARS).map { it.avatarKey })
}

/** 这次列表里新出现的设备；[previousIds] 为 null（第一次拿到列表，比如刚进页面）时不算任何「加入」。 */
internal fun newlyJoined(previousIds: Set<String>?, current: List<PeerDevice>): List<PeerDevice> =
    if (previousIds == null) emptyList() else current.filter { it.deviceId !in previousIds }

/** 连接卡片要额外说明的对端状态（spec §6.4）。两项都为 null 时卡片与改版前完全一致。 */

/**
 * 都离线时的等待行，同时就是「管理设备」的入口（装机 Screenshot_3：原先「等待行 + 管理设备按钮」两层，
 * 而且只说最早那一台离线了）。
 */
@Composable
internal fun OfflinePeersRow(peers: OfflinePeers, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().testTag("offline-peers"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            AvatarStack(peers.avatarKeys, size = 32.dp, ringColor = MaterialTheme.colorScheme.surfaceContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (peers.count == 1) {
                        stringResource(R.string.serving_device_offline_one, peers.firstName)
                    } else {
                        pluralStringResource(R.plurals.serving_devices_offline, peers.count, peers.count)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.serving_offline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}
