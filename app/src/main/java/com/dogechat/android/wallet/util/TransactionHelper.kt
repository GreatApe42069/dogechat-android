package com.dogechat.android.wallet.util

import com.dogechat.android.wallet.WalletManager
import org.bitcoinj.core.Coin
import org.bitcoinj.wallet.Wallet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds UI-ready transaction rows and simple formatters.
 * Enhanced with better formatting and fee information.
 */
object TransactionHelper {
    private val timeFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
    
    fun buildRows(wallet: Wallet): List<WalletManager.TxRow> {
        return wallet.getTransactionsByTime().map { tx ->
            val vToMe = tx.getValueSentToMe(wallet)
            val vFromMe = tx.getValueSentFromMe(wallet)
            val delta = vToMe.subtract(vFromMe)
            val time: Date? = tx.updateTime
            
            // Enhanced value display with proper formatting
            val valueDisplay = when {
                delta.signum() > 0 -> "+${delta.toFriendlyString()}"
                delta.signum() < 0 -> "${delta.toFriendlyString()}" // Already negative
                else -> "0 DOGE"
            }
            
            WalletManager.TxRow(
                hash = tx.txId.toString(),
                value = valueDisplay,
                isIncoming = delta.signum() > 0,
                time = time,
                confirmations = tx.confidence.depthInBlocks
            )
        }.sortedByDescending { it.time ?: Date(0) } // Sort by time, newest first
    }

    fun format(row: WalletManager.TxRow): String {
        val dir = if (row.isIncoming) "Received" else "Sent"
        val timeStr = row.time?.let { timeFormatter.format(it) } ?: "pending"
        val confStr = when {
            row.confirmations >= 6 -> "confirmed"
            row.confirmations > 0 -> "${row.confirmations} conf"
            else -> "unconfirmed"
        }
        return "$dir ${row.value} • $timeStr • $confStr"
    }
    
    /**
     * Format for transaction history display in UI
     */
    fun formatForHistory(row: WalletManager.TxRow): String {
        val dir = if (row.isIncoming) "↓" else "↑"
        val timeStr = row.time?.let { timeFormatter.format(it) } ?: "pending"
        val hashShort = row.hash.take(8) + "…"
        return "$dir ${row.value} • $timeStr • $hashShort"
    }
    
    /**
     * Get confidence description for UI
     */
    fun getConfidenceDescription(confirmations: Int): String {
        return when {
            confirmations >= 6 -> "Fully Confirmed"
            confirmations >= 3 -> "Well Confirmed ($confirmations/6)"
            confirmations >= 1 -> "Partially Confirmed ($confirmations/6)"
            else -> "Unconfirmed (0/6)"
        }
    }
    
    /**
     * Get confidence color indicator
     */
    fun getConfidenceLevel(confirmations: Int): String {
        return when {
            confirmations >= 6 -> "GREEN"
            confirmations >= 3 -> "ORANGE"
            confirmations >= 1 -> "YELLOW"
            else -> "RED"
        }
    }
}