package com.dogechat.android.wallet.ui

import java.util.Locale

/**
 * Utility functions for Dogechat wallet UI components
 *
 * Dogecoin uses 1 DOGE = 100,000,000 atomic units (same scale as Bitcoin satoshis).
 * These helpers format and parse friendly strings for display in the UI.
 */
object WalletUtils {

    private const val ATOMS_PER_DOGE = 100_000_000L

    /**
     * Format an amount given in atomic units (long) into a user-friendly DOGE string
     * Examples: 123456789 -> "Đ1.23456789"
     */
    fun formatAtoms(atoms: Long): String {
        val doge = atoms / ATOMS_PER_DOGE.toDouble()
        // show up to 8 decimals but trim trailing zeros
        val raw = String.format(Locale.getDefault(), "%.8f", doge)
        val trimmed = raw.trimEnd('0').trimEnd('.')
        return "Đ$trimmed"
    }

    /**
     * Format a Double DOGE amount (e.g. 12.345) to a display string with Đ symbol
     */
    fun formatDoge(doge: Double): String {
        val raw = String.format(Locale.getDefault(), "%.8f", doge)
        val trimmed = raw.trimEnd('0').trimEnd('.')
        return "Đ$trimmed"
    }

    /**
     * Parse a friendly DOGE string (examples: "12.34 DOGE", "Đ12.34", "12.34")
     * into atomic units (Long). Returns null if parsing fails.
     */
    fun parseFriendlyToAtoms(friendly: String): Long? {
        val regex = Regex("[0-9]+(?:\\.[0-9]+)?")
        val match = regex.find(friendly)
        val num = match?.value ?: return null
        return try {
            val d = num.toDouble()
            (d * ATOMS_PER_DOGE).toLong()
        } catch (e: Exception) {
            null
        }
    }
}
