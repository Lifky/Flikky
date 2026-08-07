package com.example.flikky.ui.theme

import androidx.compose.ui.unit.dp

/** Component size tokens, kept separate from spacing. */
object Sizes {
    val touchTarget = 48.dp
    val avatar = 40.dp

    /**
     * 会话气泡的头像直径，同时也是消息操作钮（`CircleActionButton`）的可见圆直径——
     * 两者按设计契约必须相等（§12.5：钮与头像同大），共用一个 token 保证永不漂移。
     * Web 端对应 `app.css .msg-actions mdui-button-icon` 的 36px。
     */
    val bubbleAvatar = 36.dp
    val rowMinH = 56.dp
}
