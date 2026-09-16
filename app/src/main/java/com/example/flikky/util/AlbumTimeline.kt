package com.example.flikky.util

/**
 * 相册分组头要显示的日期，**结构化而非字符串**。
 *
 * 格式化要用 `R.string`（那是 Android），所以纯逻辑只回答「这是哪一档」，
 * 由 UI 层变成文字。浏览器端同样只做这一步映射。
 */
sealed interface AlbumDateLabel {
    data object Today : AlbumDateLabel
    data object Yesterday : AlbumDateLabel

    /** 本年内的更早日期：只显示月日。 */
    data class SameYear(val month: Int, val day: Int) : AlbumDateLabel

    /** 往年：带上年份。 */
    data class Older(val year: Int, val month: Int, val day: Int) : AlbumDateLabel
}

data class AlbumSection<T>(val label: AlbumDateLabel, val items: List<T>)

/**
 * 按 [AlbumDateKey] 把相册项切成分组。
 *
 * ## 它不再自己算日期
 *
 * v1.21.0 装机验收暴露的双端不一致（Screenshot_12 / 13）根因是「两端各自从 epoch
 * 算日期」。现在日期由服务端算好、以键的形式下发（见 [AlbumDateKey] 的 KDoc 与 D65），
 * 本对象只负责**按键切段并决定标签**，不接 `ZoneId`、不接 `nowMs`。
 *
 * 于是「手机和浏览器分组一致」是**结构保证**的：两端拿到的是同一批键。
 *
 * ## 排序仍由本函数负责
 *
 * MediaStore 的查询顺序与浏览器端流式到达顺序都可能乱。让调用方排序就等于给了它
 * 一个用错的机会；在这里排一次，所有调用方都不可能拿到乱序。
 */
object AlbumTimeline {

    fun <T> group(
        items: List<T>,
        dateKeyOf: (T) -> String,
        sortKey: (T) -> Long,
        todayKey: String,
        yesterdayKey: String,
    ): List<AlbumSection<T>> {
        if (items.isEmpty()) return emptyList()
        return items
            .sortedByDescending(sortKey)
            .groupBy(dateKeyOf)
            .map { (key, rows) -> AlbumSection(labelFor(key, todayKey, yesterdayKey), rows) }
    }

    /**
     * 键 → 标签档位。
     *
     * `key >= todayKey` 而不是 `==`：相机时间设错的照片真实存在，它们该归到「今天」，
     * 而不是造出一个用户看不懂的「未来」分组，也不该消失。零填充的键字典序等于
     * 时间序，所以这个比较可以直接在字符串上做。
     *
     * 键无法解析时回落到「今天」——那是唯一不需要额外数字的档位，
     * 比显示一个 `1970年1月1日` 或者让分组整段消失都更不容易误导。
     */
    fun labelFor(key: String, todayKey: String, yesterdayKey: String): AlbumDateLabel {
        if (key >= todayKey) return AlbumDateLabel.Today
        if (key == yesterdayKey) return AlbumDateLabel.Yesterday
        val (year, month, day) = AlbumDateKey.parse(key) ?: return AlbumDateLabel.Today
        val todayYear = AlbumDateKey.parse(todayKey)?.first
        return if (todayYear != null && year == todayYear) {
            AlbumDateLabel.SameYear(month, day)
        } else {
            AlbumDateLabel.Older(year, month, day)
        }
    }
}
