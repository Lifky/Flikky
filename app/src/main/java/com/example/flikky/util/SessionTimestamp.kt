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
    /** 消息距上一个时间戳锚点达到该值时，在消息前插入分隔条。 */
    const val GAP_MS: Long = 5 * 60 * 1000

    /** 首条消息（anchor 为 null）恒插；否则按 [GAP_MS] 判定。 */
    fun shouldInsertDividerBefore(anchorTimestampMs: Long?, timestampMs: Long): Boolean =
        anchorTimestampMs == null || timestampMs - anchorTimestampMs >= GAP_MS

    /**
     * 返回应插入时间戳分隔条的消息索引。
     * 锚点只在插入分隔条时推进，连续聊天也会在累计达到 [GAP_MS] 后显示新时间戳。
     */
    fun dividerIndices(timestampsMs: List<Long>): Set<Int> {
        val result = mutableSetOf<Int>()
        var anchorTimestampMs: Long? = null
        timestampsMs.forEachIndexed { index, timestampMs ->
            if (shouldInsertDividerBefore(anchorTimestampMs, timestampMs)) {
                result += index
                anchorTimestampMs = timestampMs
            }
        }
        return result
    }

    /** 固定完整格式 `yy/MM/dd HH:mm`（用户裁决 D8：不做当天简化）。 */
    fun format(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yy/MM/dd HH:mm", Locale.US)
            .apply { this.timeZone = timeZone }
            .format(Date(timestampMs))
}
