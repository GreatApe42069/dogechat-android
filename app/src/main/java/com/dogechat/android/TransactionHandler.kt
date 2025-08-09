import org.dogecoin.bitcore.transaction.Transaction
import org.dogecoin.bitcore.script.Script
import org.dogecoin.bitcore.crypto.Transaction.Signature
import org.dogecoin.bitcore.wallet.Wallet

class TransactionHandler {
    companion object {
        fun createDogecoinTransaction(wallet: Wallet, to: Address, amount: Coin): Transaction {
            val tx = Transaction()
            wallet.unspentOutputs.forEach { tx.addInput(it) }
            tx.addOutput(amount, to)
            wallet.signTransaction(tx)
            return tx
        }

        fun verifyDogecoinTransaction(tx: Transaction): Boolean {
            return tx.verify()
        }
    }
}
