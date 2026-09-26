package com.example.flikky.util

/**
 * 记住最后一个非空值：输入变成 null 时，继续给出上一次的值。
 *
 * 连接卡片用它撑过「停止服务 → 返回首页」的退场动画：服务一停地址就清空，
 * 页面却还要再显示几百毫秒，卡片若跟着消失，布局会塌成半截（2026-09-27 装机反馈）。
 */
class LastNonNull<T : Any> {
    private var last: T? = null

    fun update(value: T?): T? {
        if (value != null) last = value
        return last
    }
}
