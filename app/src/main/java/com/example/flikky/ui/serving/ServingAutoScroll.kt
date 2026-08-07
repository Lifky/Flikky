package com.example.flikky.ui.serving

internal fun shouldAutoScrollToLatestMessage(
    previousMessageCount: Int,
    currentMessageCount: Int,
    previousLastMessageId: Long? = null,
    currentLastMessageId: Long? = null,
): Boolean {
    if (currentMessageCount <= previousMessageCount || currentMessageCount <= 0) return false
    if (
        previousLastMessageId != null &&
        currentLastMessageId != null &&
        previousLastMessageId == currentLastMessageId
    ) {
        return false
    }
    return true
}

internal fun shouldAutoScrollToLatestMessageForIme(
    wasAtBottomBeforeIme: Boolean,
    currentImeVisible: Boolean,
    currentMessageCount: Int,
): Boolean = wasAtBottomBeforeIme && currentImeVisible && currentMessageCount > 0
