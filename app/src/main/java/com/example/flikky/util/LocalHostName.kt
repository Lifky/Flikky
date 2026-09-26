package com.example.flikky.util

import kotlin.random.Random

/**
 * 局域网名称 `flikky{X}.local` 与端口的格式和取值规则（spec §0、§4.3、§4.6、§5）。
 * 设置页校验、服务端扫端口、mDNS 候选序列读的是同一份，别在调用点另写一套边界。
 */
object LocalHostName {
    const val PREFIX = "flikky"
    const val NUMBER_MIN = 0
    const val NUMBER_MAX = 999
    const val PORT_MIN = 1024
    const val PORT_MAX = 65535
    const val DEFAULT_PORT = 8080
    const val NAME_CANDIDATES = 10
    const val PORT_SCAN = 20

    /**
     * 在 1024–65535 里、但 Chrome/Firefox 拒绝打开的端口（`ERR_UNSAFE_PORT`）。
     * 绑得上、手机上看着正常，电脑却打不开 —— 设置页不收，扫描也跳过。
     */
    val BROWSER_BLOCKED_PORTS: Set<Int> = setOf(
        1719, 1720, 1723, 2049, 3659, 4045, 4190, 5060, 5061, 6000, 6566,
        6665, 6666, 6667, 6668, 6669, 6679, 6697, 10080,
    )

    private val NUMBER_TEXT = Regex("0|[1-9][0-9]{0,2}")
    private val PORT_TEXT = Regex("[1-9][0-9]{3,4}")

    fun label(number: Int): String = "$PREFIX$number"
    fun fqdn(number: Int): String = "${label(number)}.local"
    fun url(host: String, port: Int): String = "http://$host:$port"

    fun isValidNumber(number: Int): Boolean = number in NUMBER_MIN..NUMBER_MAX
    fun isValidPort(port: Int): Boolean = port in PORT_MIN..PORT_MAX && port !in BROWSER_BLOCKED_PORTS

    /** 冲突时依次尝试的编号：自己 + 往上 9 个，越过 999 回绕到 0。 */
    fun candidates(start: Int): List<Int> =
        (0 until NAME_CANDIDATES).map { (start + it).mod(NUMBER_MAX + 1) }

    fun portRange(start: Int): IntRange = start..minOf(start + PORT_SCAN - 1, PORT_MAX)

    fun randomDefaultNumber(random: Random = Random.Default): Int = random.nextInt(1, 100)

    /** 输入框文本 → 编号。前导零拒绝：`07` 与 `7` 是两个名字，不替用户猜。 */
    fun parseNumber(text: String): Int? =
        text.takeIf { NUMBER_TEXT.matches(it) }?.toInt()

    /** 只看格式与范围、不看浏览器限制 —— 设置页据此区分「超出范围」与「浏览器不让用」两种错误。 */
    fun parsePortInRange(text: String): Int? =
        text.takeIf { PORT_TEXT.matches(it) }?.toInt()?.takeIf { it in PORT_MIN..PORT_MAX }

    fun parsePort(text: String): Int? = parsePortInRange(text)?.takeIf(::isValidPort)
}
