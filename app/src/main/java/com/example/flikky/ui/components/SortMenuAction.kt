package com.example.flikky.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.flikky.R
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec

/**
 * 排序菜单，三个界面唯一实现（文件总览页 / 收藏页 / 会话页文件 tab）。
 *
 * ## 形态取自 MD3 官方 anatomy
 *
 * `docs/others/material-design-3-docs/components/Menu.md:34-42` 的 dropdown menu
 * anatomy 是 `List item / Leading icon / Trailing icon / Trailing text / Container / Divider`。
 * 这里用到两个槽：**trailing icon 只在当前键上显示方向箭头** —— 它既是选中指示，
 * 也是方向指示，一个元素说完两件事（Windows 与 Finder 同一画法）。
 * 因此不需要额外的对勾：有箭头的那一项就是当前项。
 *
 * ## 为什么 [timeLabel] 是参数
 *
 * 三个界面的「时间」是**三个不同的字段**：文件总览页是消息时间，收藏页是收藏时间，
 * 文件浏览是文件修改时间。组件替调用方说话就会说错 —— 与 [GroupWording] 同一个理由
 * （v1.17.0 装机反馈：共享组件硬编码 `home_*` 文案，收藏页复用后同屏混出两个词）。
 *
 * ## 为什么 [onPick] 给的是键而不是算好的 spec
 *
 * 「点当前键翻转、点新键用自然方向」是内核规则（`SortSpec.tap`）。让每个调用方
 * 自己算一遍，迟早有一处算得不一样。
 *
 * @param keys 本界面提供的键。默认三个都给；会话列表没有「大小」，主页因此不用本组件
 *   而用 `HomeSortSheet`（它还带第二个轴）。
 */
@Composable
fun SortMenuAction(
    spec: SortSpec,
    @StringRes timeLabel: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (SortKey) -> Unit,
    keys: List<SortKey> = SortKey.entries,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painterResource(R.drawable.ic_filter_list),
                contentDescription = stringResource(R.string.common_sort),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            keys.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                when (key) {
                                    SortKey.NAME -> R.string.common_sort_by_name
                                    SortKey.TIME -> timeLabel
                                    SortKey.SIZE -> R.string.common_sort_by_size
                                },
                            ),
                        )
                    },
                    trailingIcon = if (spec.key == key) {
                        {
                            Icon(
                                painterResource(
                                    if (spec.descending) {
                                        R.drawable.ic_arrow_downward
                                    } else {
                                        R.drawable.ic_arrow_upward
                                    },
                                ),
                                contentDescription = stringResource(
                                    if (spec.descending) {
                                        R.string.common_sort_descending
                                    } else {
                                        R.string.common_sort_ascending
                                    },
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        onPick(key)
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}
