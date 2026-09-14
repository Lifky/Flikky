package com.example.flikky.ui.serving.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.util.formatBytes
import com.example.flikky.ui.theme.Motion

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
 * 是在制造噪音，所以整体淡入淡出。
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val elsewhere = StorageSelectionScope.split(selected, currentPath).elsewhere
    val visible = summary.count > 0
    // 选择被清空（发送完 / 点清除）时菜单必须跟着收起，否则下次有选中时它是展开状态。
    if (!visible && expanded) expanded = false

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(Motion.spatial()) + fadeIn(Motion.effects()),
        exit = scaleOut(Motion.spatialFast()) + fadeOut(Motion.effectsFast()),
    ) {
        FloatingActionButtonMenu(
            expanded = expanded,
            button = {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    // large 档（≈80dp）。左下的通道锁是 56dp 标准档 —— 主操作（发送选中项）
                    // 更大，两者大小差异是刻意的，参考装机时给的并存形态。
                    containerSize = ToggleFloatingActionButtonDefaults.containerSizeLarge(),
                ) {
                    // checkedProgress 是 0..1 的形变进度。过半再换图标，
                    // 让「箭头 → ×」的切换发生在形状已经明显在动之后，而不是一开始就跳。
                    val closing = checkedProgress > 0.5f
                    Icon(
                        painter = painterResource(
                            if (closing) R.drawable.ic_close else R.drawable.ic_arrow_upward,
                        ),
                        contentDescription = stringResource(
                            if (closing) R.string.serving_storage_actions_close
                            else R.string.serving_storage_actions,
                        ),
                    )
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onSend()
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = null,
                    )
                },
                // 计数在这里，不在容器里塞自由文本 —— 见类注释。
                text = {
                    // 选择集跨目录保留，所以「已选 N 项」可能包含当前屏幕上看不到的行。
                    // 有别处的就把它写出来 —— 否则用户既不知道那些在哪，
                    // 也无从判断按下发送会发出什么（装机验收 2026-09-03）。
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
                onClick = {
                    expanded = false
                    onClear()
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_deselect),
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.serving_storage_clear)) },
            )
        }
    }
}
