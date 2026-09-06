package com.example.flikky.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.flikky.R
import com.example.flikky.data.settings.GroupMode
import com.example.flikky.ui.settings.components.SettingItem
import com.example.flikky.ui.settings.components.SettingSection
import com.example.flikky.ui.theme.Spacing
import com.example.flikky.util.SortKey
import com.example.flikky.util.SortSpec

/**
 * 主页的「排序与分节」bottom sheet。
 *
 * ## 为什么主页用 sheet，其余三处用 [com.example.flikky.ui.components.SortMenuAction]
 *
 * 主页有**两个独立的轴**（排序键、分节方式），平铺进一个 dropdown 会变成一串分不清
 * 层级的条目；其余三处只有一个轴，dropdown 正好。控件跟着内容走，不是不一致。
 *
 * ## 措辞：叫「分节方式」而不是「分组」
 *
 * 项目里「分组」已经指**用户自建的会话分组**（`GroupEntity`、`home_new_group`
 * 「新建分组」、`GroupChips`）。[GroupMode] 是给列表加「今天 / 昨天 / 更早」这类
 * 小标题的方式 —— 两者是不同的东西。同屏出现两个含义不同的「分组」，正是
 * `GroupWording.kt` 当初被造出来消掉的那个缺陷（v1.17.0 装机反馈）。
 *
 * ## 为什么点了不自动关
 *
 * 排序键要能连点两下翻方向（第二下翻转），关掉就翻不了。用户点外部或下拉关闭。
 *
 * 视觉复用快捷设置那一套（[SettingSection] + [SettingItem]，含分段圆角），
 * 所以与设置页、快捷设置零差异。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSortSheet(
    sort: SortSpec,
    group: GroupMode,
    onPickSort: (SortKey) -> Unit,
    onPickGroup: (GroupMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 会话没有「大小」——每个界面给它有意义的键子集，这是刻意的而不是遗漏。
    val sortKeys = listOf(
        SortKey.NAME to R.string.common_sort_by_name,
        SortKey.TIME to R.string.home_sort_time,
    )
    val groupModes = listOf(
        GroupMode.NONE to R.string.home_section_mode_none,
        GroupMode.STATUS to R.string.home_section_mode_status,
        GroupMode.DATE to R.string.home_section_mode_date,
    )

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
                stringResource(R.string.home_sort_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )

            SettingSection(title = stringResource(R.string.home_sort_section)) {
                sortKeys.forEachIndexed { index, (key, label) ->
                    SettingItem(
                        title = stringResource(label),
                        onClick = { onPickSort(key) },
                        index = index,
                        total = sortKeys.size,
                        trailing = if (sort.key == key) {
                            {
                                // 方向箭头既是选中指示也是方向指示，一个元素说完两件事
                                // —— 与 SortMenuAction 同一画法。
                                Icon(
                                    painterResource(
                                        if (sort.descending) {
                                            R.drawable.ic_arrow_downward
                                        } else {
                                            R.drawable.ic_arrow_upward
                                        },
                                    ),
                                    contentDescription = stringResource(
                                        if (sort.descending) {
                                            R.string.common_sort_descending
                                        } else {
                                            R.string.common_sort_ascending
                                        },
                                    ),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            SettingSection(title = stringResource(R.string.home_section_mode)) {
                groupModes.forEachIndexed { index, (mode, label) ->
                    SettingItem(
                        title = stringResource(label),
                        onClick = { onPickGroup(mode) },
                        index = index,
                        total = groupModes.size,
                        trailing = if (group == mode) {
                            {
                                // 分节方式没有方向，所以用对勾而不是箭头。
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.common_enabled),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}
