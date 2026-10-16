package com.example.flikky.util

/** 对端设备名的规范化（spec §5.2）。两端同一套规则：浏览器端见 `device-self.js` 的 `clampName`。 */
object DeviceNamePolicy {
    /** 与 `FlikkySettings.DEVICE_NAME_MAX` 相同。util 不依赖 data 包，由测试钉住两者相等。 */
    const val MAX_LENGTH = 20

    /**
     * 去控制字符、去首尾空白、按 UTF-16 code unit 截到 [MAX_LENGTH]。
     * 截断点落在高代理位时少截一位，免得存进半个 emoji。空串表示「用默认名」。
     */
    fun normalize(raw: String?): String {
        val cleaned = raw.orEmpty().filterNot { it.isISOControl() }.trim()
        if (cleaned.length <= MAX_LENGTH) return cleaned
        var end = MAX_LENGTH
        if (Character.isHighSurrogate(cleaned[end - 1])) end -= 1
        return cleaned.substring(0, end).trimEnd()
    }
}
