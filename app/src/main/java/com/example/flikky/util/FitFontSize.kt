package com.example.flikky.util

/**
 * 从 [maxSp] 起按 [stepSp] 往下试，返回第一个 [fits] 的字号；都放不下时返回 [minSp]。
 *
 * 连接卡片用它给两行地址算**同一个**字号：宁可一起缩小，也不让端口号被挤到下一行
 * （用户 2026-09-26 反馈）。纯函数：测量交给调用方，这里只管选哪一档。
 */
fun fitFontSize(maxSp: Float, minSp: Float, stepSp: Float, fits: (Float) -> Boolean): Float {
    var size = maxSp
    while (size > minSp) {
        if (fits(size)) return size
        size -= stepSp
    }
    return minSp
}
