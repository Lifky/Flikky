package com.example.flikky.util

import java.time.Instant
import java.time.ZoneId

/**
 * 相册项属于「哪一天」，表示为零填充的 `yyyy-MM-dd`。
 *
 * ## 为什么要有这个键，而不是让两端各自从 epoch 算日期
 *
 * v1.21.0 装机验收（2026-09-16，Screenshot_12 / 13）暴露过一次双端不一致：手机按
 * `ZoneId.systemDefault()` 算日期，浏览器按**电脑**的时区算，两台设备时区不同，
 * 于是跨午夜拍的照片在两端掉进不同的分组 —— 分组数、每组张数、日期标签全都不一样，
 * 看起来像「相册把文件搞混了」。
 *
 * 裁决（D65）：**日期以拍摄设备（手机）为准，服务端算一次、把键下发给两端。**
 * 照片的拍摄日期是照片自己的属性，不是观察者的属性；而且这与 Android 系统相册的行为
 * 一致（它也用设备当前时区解释 `DATE_TAKEN`），用户拿手机对比时基准才对得上。
 *
 * 所以**除了这里，任何地方都不应该再把 epoch 变成「哪一天」** ——
 * 浏览器端只消费键，不做日期运算（`panel-album.test.js` 有一条守卫盯着）。
 *
 * ## 为什么零填充
 *
 * 零填充让键的**字典序等于时间序**，于是 `key >= todayKey` 这种比较可以直接在
 * 字符串上做 —— 两端都不需要再解析成日期对象才能判断先后。
 */
object AlbumDateKey {

    /** epoch 毫秒 → 指定时区下的日期键。 */
    fun of(epochMs: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        return "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
    }

    /**
     * 键 → (年, 月, 日)。非法输入返回 `null`，**不抛**。
     *
     * 键会经 DTO 跨网络到达、也可能被浏览器回传，所以解析必须能拒绝垃圾：
     * 这是一道输入边界，不只是格式化的逆运算。
     */
    fun parse(key: String): Triple<Int, Int, Int>? {
        if (key.length != 10) return null
        if (key[4] != '-' || key[7] != '-') return null
        val year = key.substring(0, 4).toIntOrNull() ?: return null
        val month = key.substring(5, 7).toIntOrNull() ?: return null
        val day = key.substring(8, 10).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        // 逐字符确认全是 ASCII 数字：toIntOrNull 会接受 "+1" / 全角数字之类，
        // 而那些拼写不可能由 of() 产生，接受它们等于让同一天有多种键。
        if (key.withIndex().any { (i, c) -> i != 4 && i != 7 && c !in '0'..'9' }) return null
        return Triple(year, month, day)
    }
}
