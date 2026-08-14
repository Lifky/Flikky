package com.example.flikky.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 会话时间戳分隔条的分组规则与展示格式。
 * 双端同构：Web 端对应 app.js 的 maybeInsertTimeDivider / formatSessionTimestamp，改任一侧必须同步另一侧。
 */
object SessionTimestamp {
    /** 相邻消息间隔超过该值时，在后一条消息前插入分隔条。 */
    const val GAP_MS: Long = 5 * 60 * 1000

    /** 首条消息（prev 为 null）恒插；否则按 [GAP_MS] 判定。 */
    fun shouldInsertDividerBefore(prevTimestampMs: Long?, timestampMs: Long): Boolean =
        prevTimestampMs == null || timestampMs - prevTimestampMs > GAP_MS

    /** 固定完整格式 `yy/MM/dd HH:mm`（用户裁决 D8：不做当天简化）。 */
    fun format(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yy/MM/dd HH:mm", Locale.US)
            .apply { this.timeZone = timeZone }
            .format(Date(timestampMs))
}
