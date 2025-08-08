import org.dogecoin.bitcore.Dogecoin
import org.dogecoin.bitcore.network.DogecoinNetwork
import org.dogecoin.bitcore.wallet.Wallet

class WalletManager {
    private val network = DogecoinNetwork.MAINNET
    private lateinit var wallet: Wallet

    fun initializeWallet() {
        wallet = Wallet(network)
    }

    fun getWallet(): Wallet {
        return wallet
    }

    fun createAddress(): Address {
        return wallet.freshAddress()
    }

    fun getBalance(): Coin {
        return wallet.balance
    }
}
