package com.dogechat.android.util

/**
 * Converts a ByteArray to a lowercase hexadecimal string.
 * Example: byteArrayOf(0x0F, 0xA0.toByte()) -> "0fa0"
 *
 * Notes:
 * - Matches upstream bitchat behavior (lowercase hex)
 * - Avoids String.format and locale issues
 * - Uses a CharArray for minimal allocations and better performance
 */

private val HEX_LOWER: CharArray = "0123456789abcdef".toCharArray()

fun ByteArray.toHexString(): String {
    if (isEmpty()) return ""
    val out = CharArray(size * 2)
    var j = 0
    for (b in this) {
        val v = b.toInt() and 0xFF
        out[j++] = HEX_LOWER[v ushr 4]
        out[j++] = HEX_LOWER[v and 0x0F]
    }
    return String(out)
}

/**
 * Converts a UByteArray to a lowercase hexadecimal string.
 * Convenience wrapper that preserves the same lowercase format.
 */
fun UByteArray.toHexString(): String = this.asByteArray().toHexString()

/**
 * Nullable-safe hex conversion.
 * Returns empty string if the array is null.
 */
fun ByteArray?.toHexStringOrEmpty(): String = this?.toHexString() ?: ""

/**
 * Converts a hexadecimal string to ByteArray.
 * - Accepts uppercase/lowercase hex
 * - Ignores spaces
 * - Returns empty ByteArray if input is invalid (odd length or non-hex chars)
 */
fun String.hexToByteArray(): ByteArray {
    val cleanInput = this.trim().replace(" ", "").lowercase()
    if (cleanInput.isEmpty()) return ByteArray(0)
    if (cleanInput.length % 2 != 0) return ByteArray(0)

    val result = ByteArray(cleanInput.length / 2)
    var i = 0
    var ri = 0
    while (i < cleanInput.length) {
        val hi = cleanInput[i++].digitToIntOrNull(16) ?: return ByteArray(0)
        val lo = cleanInput[i++].digitToIntOrNull(16) ?: return ByteArray(0)
        result[ri++] = ((hi shl 4) or lo).toByte()
    }
    return result
}

/**
 * Converts UByteArray to ByteArray.
 */
private fun UByteArray.asByteArray(): ByteArray {
    val out = ByteArray(this.size)
    for (i in indices) {
        out[i] = this[i].toByte()
    }
    return out
}