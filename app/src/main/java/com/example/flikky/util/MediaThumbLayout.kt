package com.example.flikky.util

/**
 * 媒体气泡缩略图尺寸算法（双端同构，Web 侧由 CSS max/min 约束实现同一策略）。
 *
 * 主流聊天方案：把原始宽高等比缩放进 [maxWidth] x [maxHeight] 边界框，完整显示
 * 不裁剪——竖长图变窄长条、横长图变宽扁条。仅当缩放结果某条边小于 [minEdge]
 * （极端比例长图）时，把该边抬到 [minEdge]、另一边取框边，交由调用方以中央
 * 裁剪（ContentScale.Crop）兜底，避免气泡塌缩成一条线。
 */
object MediaThumbLayout {

    /** 返回目标 (width, height)（与 max/min 同单位）；原始尺寸非法时返回 null。 */
    fun fit(
        srcWidth: Int,
        srcHeight: Int,
        maxWidth: Float,
        maxHeight: Float,
        minEdge: Float,
    ): Pair<Float, Float>? {
        if (srcWidth <= 0 || srcHeight <= 0) return null
        val scale = minOf(maxWidth / srcWidth, maxHeight / srcHeight)
        var w = srcWidth * scale
        var h = srcHeight * scale
        // 等比缩放后恰有一条边贴住框边，两个 clamp 分支互斥。
        if (w < minEdge) {
            w = minEdge
            h = maxHeight
        } else if (h < minEdge) {
            h = minEdge
            w = maxWidth
        }
        return w to h
    }
}
