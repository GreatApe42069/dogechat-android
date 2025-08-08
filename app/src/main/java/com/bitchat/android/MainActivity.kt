import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DogechatApp() // Your Compose UI
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

@Composable
fun DogechatApp() {
    // Your Compose UI implementation here
    // For example:
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Add Compose elements that match the functionality of activity_main.xml
        Text(text = "Dogechat App")
        Button(onClick = { /* Handle send Dogecoin */ }) {
            Text("Send Dogecoin")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        DogechatApp()
    }
}
