package com.example.flikky.ui.home

/** 首页会话行的行内菜单动作，按展示顺序。 */
enum class HomeRowAction { PIN, RENAME, MOVE, EXPORT, DELETE }

/**
 * 会话行内菜单的纯策略。
 *
 * 这些能力原本只有"长按进多选 → 底部工具栏"一条路，图标也没有文案；放进行内菜单后，
 * 只操作单条会话不必再进多选，菜单项自带文字也比一排图标自解释。
 */
object HomeRowMenuPolicy {

    /** 进行中的会话不给菜单：它在多选里同样不可选（需先停止服务），尾部留给「停止」。 */
    fun rowMenu(inProgress: Boolean): List<HomeRowAction> =
        if (inProgress) {
            emptyList()
        } else {
            listOf(
                HomeRowAction.PIN,
                HomeRowAction.RENAME,
                HomeRowAction.MOVE,
                HomeRowAction.EXPORT,
                HomeRowAction.DELETE,
            )
        }

    /** 置顶项是开关：菜单点下去要落到当前状态的反面。 */
    fun pinTarget(pinned: Boolean): Boolean = !pinned
}
