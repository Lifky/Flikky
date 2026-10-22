package com.example.flikky.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.flikky.ui.theme.Spacing

/** M3 Scaffold 把 FAB 放在内容底边上方 16dp（`Scaffold` 的 FabSpacing，未公开）。 */
private val ScaffoldFabSpacing = 16.dp

/**
 * 列表底部要给右下角 FAB 留出的高度（v1.24 装机 Screenshot_5）：末行能滚到 FAB 上方，行尾的菜单 / 按钮不会被盖住点不到。
 * 与多选时的 [FlikkyFloatingToolbarLift] 是同一类留白。[fabHeight] 用实测值 —— 字号放大时 FAB 会变高。
 */
fun fabClearance(fabHeight: Dp): Dp = fabHeight + ScaffoldFabSpacing + Spacing.sm
