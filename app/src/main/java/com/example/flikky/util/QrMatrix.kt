package com.example.flikky.util

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

class QrMatrix internal constructor(
    val size: Int,
    private val dark: BooleanArray,
) {
    fun isDark(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return dark[y * size + x]
    }

    companion object {
        const val QUIET_ZONE = 4

        fun encode(text: String): QrMatrix? {
            if (text.isBlank()) return null
            val code = runCatching {
                Encoder.encode(
                    text,
                    ErrorCorrectionLevel.M,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"),
                )
            }.getOrNull() ?: return null
            val matrix = code.matrix ?: return null
            val side = matrix.width
            if (side <= 0 || matrix.height != side) return null
            val dark = BooleanArray(side * side)
            for (y in 0 until side) for (x in 0 until side) {
                dark[y * side + x] = matrix.get(x, y) == 1.toByte()
            }
            return QrMatrix(side, dark)
        }
    }
}
