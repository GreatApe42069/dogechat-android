package com.dogechat.android.util

/**
 * Converts a ByteArray to a lowercase hexadecimal string.
 * Example: byteArrayOf(0x0F, 0xA0.toByte()) -> "0fa0"
 */
fun ByteArray.toHexString(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        val hi = v ushr 4
        val lo = v and 0x0F
        sb.append("0123456789abcdef"[hi])
        sb.append("0123456789abcdef"[lo])
    }
    return sb.toString()
}

/**
 * Converts a UByteArray to a lowercase hexadecimal string.
 */
fun UByteArray.toHexString(): String = this.asByteArray().toHexString()

/**
 * Nullable-safe hex conversion.
 * Returns empty string if the array is null.
 */
fun ByteArray?.toHexStringOrEmpty(): String = this?.toHexString() ?: ""

/**
 * Converts a hexadecimal string to ByteArray.
 * Accepts uppercase/lowercase hex, ignores spaces.
 * Returns empty ByteArray if input is invalid.
 */
fun String.hexToByteArray(): ByteArray {
    val cleanInput = this.trim().replace(" ", "").lowercase()
    if (cleanInput.length % 2 != 0) return ByteArray(0)

    val result = ByteArray(cleanInput.length / 2)
    for (i in result.indices) {
        val index = i * 2
        val hi = cleanInput[index].digitToIntOrNull(16) ?: return ByteArray(0)
        val lo = cleanInput[index + 1].digitToIntOrNull(16) ?: return ByteArray(0)
        result[i] = ((hi shl 4) or lo).toByte()
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
