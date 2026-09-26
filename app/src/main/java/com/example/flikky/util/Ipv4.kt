package com.example.flikky.util

/** IPv4 地址。比较按**无符号**进行（同时探测的让名规则依赖它，spec §4.3）。 */
@JvmInline
value class Ipv4(val bits: Int) : Comparable<Ipv4> {

    fun toBytes(): ByteArray = byteArrayOf(
        (bits ushr 24).toByte(), (bits ushr 16).toByte(), (bits ushr 8).toByte(), bits.toByte(),
    )

    fun sameSubnet(other: Ipv4, prefixLength: Int): Boolean {
        if (prefixLength <= 0) return true
        if (prefixLength >= 32) return bits == other.bits
        val mask = -1 shl (32 - prefixLength)
        return (bits and mask) == (other.bits and mask)
    }

    override fun compareTo(other: Ipv4): Int = bits.toUInt().compareTo(other.bits.toUInt())

    override fun toString(): String =
        "${bits ushr 24 and 0xFF}.${bits ushr 16 and 0xFF}.${bits ushr 8 and 0xFF}.${bits and 0xFF}"

    companion object {
        private val OCTET = Regex("0|[1-9][0-9]{0,2}")

        fun parse(text: String): Ipv4? {
            val parts = text.split('.')
            if (parts.size != 4) return null
            var bits = 0
            for (p in parts) {
                if (!OCTET.matches(p)) return null
                val v = p.toInt()
                if (v > 255) return null
                bits = (bits shl 8) or v
            }
            return Ipv4(bits)
        }

        fun fromBytes(b: ByteArray, offset: Int = 0): Ipv4 = Ipv4(
            (b[offset].toInt() and 0xFF shl 24) or (b[offset + 1].toInt() and 0xFF shl 16) or
                (b[offset + 2].toInt() and 0xFF shl 8) or (b[offset + 3].toInt() and 0xFF),
        )
    }
}
