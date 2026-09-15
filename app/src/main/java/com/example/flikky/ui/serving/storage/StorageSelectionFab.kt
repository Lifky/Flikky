package com.example.flikky.ui.serving.storage

import com.example.flikky.ui.components.ChannelSelectionFabMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.util.formatBytes

/**
 * 文件 tab 的选择操作入口：官方 MD3 **FAB 菜单**。
 *
 * ## 为什么不用 floating toolbar
 *
 * 首版用的是 `FlikkySelectingToolbarOverlay` + `FlikkyFloatingToolbar`，而后者的 KDoc
 * 写明「调用方只传 content（**一串 IconButton**）」——我往里塞了一个 `Text`（选中计数），
 * 违反了那个书面契约，结果是屏幕中央一个巨型椭圆（装机验收 Screenshot_4）。
 *
 * 2026-08-31 用户裁决换成 MD3 FAB 菜单：收起态一个 FAB，点击 morph 成 × 并向上弹出
 * 带文案的操作项。计数**长在菜单项文案里**（「发送 3 个文件 · 12.4 MB」），
 * 于是根本不需要往容器里塞自由文本——那正是把 floating toolbar 撑爆的东西。
 *
 * ## 只在有选中时出现
 *
 * 没选任何东西时这个 FAB 没有可做的事。常驻一个点开只有「清除选择（0 项）」的菜单
 * 是在制造噪音，所以只对选择按钮做显隐，通道锁始终挂在同一个中心锚点。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageSelectionFab(
    summary: StorageSelectionSummary,
    /** 当前选中的相对路径集合。用来算「有多少在别处」。 */
    selected: Set<String>,
    /** 当前所在目录，同上。 */
    currentPath: String,
    onClear: () -> Unit,
    onSend: () -> Unit,
    channelLock: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val elsewhere = StorageSelectionScope.split(selected, currentPath).elsewhere
    ChannelSelectionFabMenu(
        visible = summary.count > 0,
        channelLock = channelLock,
        modifier = modifier,
        menuItems = {
            FloatingActionButtonMenuItem(
                onClick = onSend,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        if (elsewhere > 0) {
                            stringResource(
                                R.string.serving_storage_send_n_elsewhere,
                                summary.count,
                                formatBytes(summary.totalBytes),
                                elsewhere,
                            )
                        } else {
                            stringResource(
                                R.string.serving_storage_send_n,
                                summary.count,
                                formatBytes(summary.totalBytes),
                            )
                        },
                    )
                },
            )
            FloatingActionButtonMenuItem(
                onClick = onClear,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_deselect),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.serving_storage_clear)) },
            )
        },
    )
}
