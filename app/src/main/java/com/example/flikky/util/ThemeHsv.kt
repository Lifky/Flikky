package com.example.flikky.util

import kotlin.math.abs
import kotlin.math.roundToInt

data class ThemeHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

fun themeSeedToHsv(argb: Long): ThemeHsv {
    val red = ((argb shr 16) and 0xFF).toFloat() / 255f
    val green = ((argb shr 8) and 0xFF).toFloat() / 255f
    val blue = (argb and 0xFF).toFloat() / 255f
    val maximum = maxOf(red, green, blue)
    val minimum = minOf(red, green, blue)
    val delta = maximum - minimum
    val rawHue = when {
        delta == 0f -> 0f
        maximum == red -> 60f * (((green - blue) / delta) % 6f)
        maximum == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return ThemeHsv(
        hue = if (rawHue < 0f) rawHue + 360f else rawHue,
        saturation = if (maximum == 0f) 0f else delta / maximum,
        value = maximum,
    )
}

fun themeHsvToSeed(hsv: ThemeHsv): Long {
    val hue = ((hsv.hue % 360f) + 360f) % 360f
    val saturation = hsv.saturation.coerceIn(0f, 1f)
    val value = hsv.value.coerceIn(0f, 1f)
    val chroma = value * saturation
    val hueSection = hue / 60f
    val intermediate = chroma * (1f - abs((hueSection % 2f) - 1f))
    val (redPrime, greenPrime, bluePrime) = when (hueSection.toInt()) {
        0 -> Triple(chroma, intermediate, 0f)
        1 -> Triple(intermediate, chroma, 0f)
        2 -> Triple(0f, chroma, intermediate)
        3 -> Triple(0f, intermediate, chroma)
        4 -> Triple(intermediate, 0f, chroma)
        else -> Triple(chroma, 0f, intermediate)
    }
    val match = value - chroma
    val red = ((redPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val green = ((greenPrime + match) * 255f).roundToInt().coerceIn(0, 255)
    val blue = ((bluePrime + match) * 255f).roundToInt().coerceIn(0, 255)
    return 0xFF000000L or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()
}
