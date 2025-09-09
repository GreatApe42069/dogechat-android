package com.dogechat.android.wallet.viewmodel

import com.dogechat.android.wallet.WalletManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin abstraction around WalletManager for transaction history & formatting.
 * Mirrors bitchat's TransactionManager for consistency.
 */
@Singleton
class TransactionManager @Inject constructor(
    private val walletManager: WalletManager
) {
    fun getTransactions(): List<WalletManager.TxRow> {
        return walletManager.history.value
    }

    fun formatTransaction(row: WalletManager.TxRow): String {
        val dir = if (row.isIncoming) "⬇️ Received" else "⬆️ Sent"
        return "$dir ${row.value} (conf: ${row.confirmations})"
    }
}
