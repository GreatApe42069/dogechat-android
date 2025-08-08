import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import org.dogecoin.bitcore.Dogecoin
import org.dogecoin.bitcore.network.DogecoinNetwork
import org.dogecoin.bitcore.wallet.Wallet
import org.dogecoin.bitcore.wallet.listeners.WalletEventListener

class MainActivity : ComponentActivity() {
    private lateinit var dogecoinWallet: Wallet
    private lateinit var peerGroup: PeerGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    // Your Compose UI here
                }
            }
        }

        // Initialize Dogecoin wallet
        val network = DogecoinNetwork.MAINNET
        dogecoinWallet = Wallet(network)
        dogecoinWallet.addEventListener(object : WalletEventListener {
            override fun onCoinsReceived(wallet: Wallet, tx: Transaction, coin: Coin, newBalance: Coin) {
                // Handle received coins
            }
        })

        // Initialize SPV node
        peerGroup = PeerGroup(network, dogecoinWallet)
        peerGroup.startAsync()
        peerGroup.downloadBlockchain()
    }

    override fun onDestroy() {
        super.onDestroy()
        peerGroup.stopAsync()
        peerGroup = null
    }
}
