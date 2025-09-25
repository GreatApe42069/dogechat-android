package com.dogechat.android.wallet.util

import com.dogechat.android.wallet.WalletManager
import org.bitcoinj.wallet.Wallet
import java.util.Date

/**
 * Builds UI-ready transaction rows and simple formatters.
 */
object TransactionHelper {
    fun buildRows(wallet: Wallet): List<WalletManager.TxRow> {
        return wallet.getTransactionsByTime().map { tx ->
            val vToMe = tx.getValueSentToMe(wallet)
            val vFromMe = tx.getValueSentFromMe(wallet)
            val delta = vToMe.subtract(vFromMe)
            val time: Date? = tx.updateTime
            WalletManager.TxRow(
                hash = tx.txId.toString(),
                value = delta.toPlainString() + " DOGE",
                isIncoming = delta.signum() > 0,
                time = time,
                confirmations = tx.confidence.depthInBlocks
            )
        }
    }

    fun format(row: WalletManager.TxRow): String {
        val dir = if (row.isIncoming) "Received" else "Sent"
        return "$dir ${row.value} (conf: ${row.confirmations})"
    }
}