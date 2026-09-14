package com.example.flikky.ui.serving

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.flikky.R

/**
 * 对端可见性 chip：既显示「对端现在看得到什么」，也是进对端权限面板的入口。
 *
 * ## 为什么状态和入口合成一个控件
 *
 * 它替掉了顶栏原来那个盾形按钮，所以按钮从三个降到两个（用户裁决 2026-09-14）。
 * 一个只显示状态的标签会让用户去别处找开关；一个只有图标的按钮又不说明当前
 * 开放了什么。合起来之后，「看到它」和「改它」是同一个动作的两个阶段。
 *
 * ## 为什么是 AssistChip
 *
 * 本地官方文档 components/Chip.md 的选型问句原文是「Does the chip represent an
 * action (assist chip) or filter results (filter chip)?」—— 这里点下去是打开一个
 * 面板，是 action，所以用 assist 而不是 filter。FilterChip 会带上选中态的视觉，
 * 而这个 chip 没有「被选中」这回事。
 *
 * ## 标签就是通道名，不带前缀
 *
 * 「可见：」那个前缀在有盾形图标之后是冗余的 —— 图标说明了这是什么，
 * 标签只需说清是哪些。一项都没开时显示「未开放任何内容」（那句必须显式说出来：
 * 留空会让用户分不清「没开放」和「这里不显示状态」）。
 */
@Composable
fun PeerVisibilityChip(
    visibleText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = visibleText,
                style = MaterialTheme.typography.labelMedium,
                // 通道多起来（将来加相册）时截断标签，而不是把 chip 撑到挤掉右边的
                // 停止服务按钮。截断的是列表尾部，前几个通道名仍然读得到。
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_shield_toggle),
                contentDescription = stringResource(R.string.peer_permissions_entry),
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}
