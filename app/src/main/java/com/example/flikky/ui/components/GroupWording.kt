package com.example.flikky.ui.components

import androidx.annotation.StringRes
import com.example.flikky.R

/**
 * 分组类共享组件（`GroupChips` / `MoveToGroupSheet` / `GroupManageDialog`）的文案集。
 *
 * 会话的容器叫「分组」，收藏的容器叫「合集」——两者是不同的东西，各自成套。共享组件原本硬编码
 * `home_*` 文案，收藏页复用后就在同一屏里混出了「合集」和「分组」两个词（v1.17.0 装机反馈）。
 * 文案改成参数后，词汇归调用方决定，组件不再替谁说话。
 */
data class GroupWording(
    @StringRes val manageGroup: Int,
    @StringRes val newGroup: Int,
    @StringRes val groupName: Int,
    @StringRes val deleteGroup: Int,
    @StringRes val moveSheetTitle: Int,
    @StringRes val moveOutOfGroup: Int,
) {
    companion object {
        /** 首页会话：「分组」。 */
        val Sessions = GroupWording(
            manageGroup = R.string.home_manage_group,
            newGroup = R.string.home_new_group,
            groupName = R.string.home_group_name,
            deleteGroup = R.string.home_delete_group,
            moveSheetTitle = R.string.home_move_sheet_title,
            moveOutOfGroup = R.string.home_move_out_of_group,
        )

        /** 收藏页：「合集」。 */
        val Favorites = GroupWording(
            manageGroup = R.string.favorites_manage_group,
            newGroup = R.string.favorites_new_group,
            groupName = R.string.favorites_group_name,
            deleteGroup = R.string.favorites_delete_group,
            moveSheetTitle = R.string.favorites_move_to_group,
            moveOutOfGroup = R.string.favorites_move_out_of_group,
        )
    }
}
