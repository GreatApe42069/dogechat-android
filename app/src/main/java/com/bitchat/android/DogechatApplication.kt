package com.dogechat.android

import android.app.Application
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.WalletManager

class DogechatApplication : Application() {
    lateinit var bluetoothMeshService: BluetoothMeshService
    lateinit var walletManager: WalletManager

    override fun onCreate() {
        super.onCreate()
        
        // Initialize WalletManager
        walletManager = WalletManager()
        walletManager.initializeWallet()
        
        // Initialize the Dogecoin wallet and update the ViewModel
        val dogecoinWallet = walletManager.getWallet()
        val mainViewModel = MainViewModel()
        mainViewModel.updateWalletStatus(MainViewModel.WalletStatus.READY)
        mainViewModel.updateDogeBalance(dogecoinWallet.balance.value.toDouble())

        // Initialize BluetoothMeshService
        bluetoothMeshService = BluetoothMeshService(this)
        bluetoothMeshService.startServices()
        
        // Set up delegate for BluetoothMeshService if needed
        bluetoothMeshService.delegate = object : BluetoothMeshDelegate {
            override fun didReceiveMessage(message: dogechatMessage) {
                // Handle received messages
            }

            override fun didUpdatePeerList(peers: List<String>) {
                // Handle peer list updates
            }

            override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
                // Handle channel leave
            }

            override fun didReceiveDeliveryAck(ack: DeliveryAck) {
                // Handle delivery acknowledgment
            }

            override fun didReceiveReadReceipt(receipt: ReadReceipt) {
                // Handle read receipt
            }

            override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
                return null // Implement decryption if needed
            }

            override fun getNickname(): String? {
                return null // Implement nickname retrieval if needed
            }

            override fun isFavorite(peerID: String): Boolean {
                return false // Implement favorite status if needed
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        bluetoothMeshService.stopServices()
    }
}
