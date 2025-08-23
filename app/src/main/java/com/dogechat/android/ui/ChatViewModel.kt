package com.dogechat.android.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.dogechat.android.mesh.BluetoothMeshDelegate
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.model.dogechatMessage
import com.dogechat.android.model.DeliveryAck
import com.dogechat.android.model.ReadReceipt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*
import kotlin.random.Random

/**
 * Refactored ChatViewModel - Main coordinator for dogechat functionality
 * Delegates specific responsibilities to specialized managers while maintaining 100% iOS compatibility
 */
class ChatViewModel(
    application: Application,
    val meshService: BluetoothMeshService
) : AndroidViewModel(application), BluetoothMeshDelegate {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // State management
    private val state = ChatState()
    
    // Specialized managers
    private val dataManager = DataManager(application.applicationContext)
    private val messageManager = MessageManager(state)
    private val channelManager = ChannelManager(state, messageManager, dataManager, viewModelScope)
    
    // Create Noise session delegate for clean dependency injection
    private val noiseSessionDelegate = object : NoiseSessionDelegate {
        override fun hasEstablishedSession(peerID: String): Boolean = meshService.hasEstablishedSession(peerID)
        override fun initiateHandshake(peerID: String) = meshService.initiateNoiseHandshake(peerID) 
        override fun getMyPeerID(): String = meshService.myPeerID
    }
    
    val privateChatManager = PrivateChatManager(state, messageManager, dataManager, noiseSessionDelegate)
    private val commandProcessor = CommandProcessor(state, messageManager, channelManager, privateChatManager)
    private val notificationManager = NotificationManager(application.applicationContext)
    // Delegate handler for mesh callbacks
    private val meshDelegateHandler = MeshDelegateHandler(
        state = state,
        messageManager = messageManager,
        channelManager = channelManager,
        privateChatManager = privateChatManager,
        notificationManager = notificationManager,
        coroutineScope = viewModelScope,
        onHapticFeedback = { ChatViewModelUtils.triggerHapticFeedback(application.applicationContext) },
        getMyPeerID = { meshService.myPeerID },
        getMeshService = { meshService }
    )
    
    // Nostr and Geohash service - initialize singleton
    private val nostrGeohashService = NostrGeohashService.initialize(
        application = application,
        state = state,
        messageManager = messageManager,
        privateChatManager = privateChatManager,
        meshDelegateHandler = meshDelegateHandler,
        coroutineScope = viewModelScope
    )
    
    // Expose state through LiveData (maintaining the same interface)
    val messages: LiveData<List<dogechatMessage>> = state.messages
    val connectedPeers: LiveData<List<String>> = state.connectedPeers
    val nickname: LiveData<String> = state.nickname
    val isConnected: LiveData<Boolean> = state.isConnected
    val privateChats: LiveData<Map<String, List<dogechatMessage>>> = state.privateChats
    val selectedPrivateChatPeer: LiveData<String?> = state.selectedPrivateChatPeer
    val unreadPrivateMessages: LiveData<Set<String>> = state.unreadPrivateMessages
    val joinedChannels: LiveData<Set<String>> = state.joinedChannels
    val currentChannel: LiveData<String?> = state.currentChannel
    val channelMessages: LiveData<Map<String, List<dogechatMessage>>> = state.channelMessages
    val unreadChannelMessages: LiveData<Map<String, Int>> = state.unreadChannelMessages
    val passwordProtectedChannels: LiveData<Set<String>> = state.passwordProtectedChannels
    val showPasswordPrompt: LiveData<Boolean> = state.showPasswordPrompt
    val passwordPromptChannel: LiveData<String?> = state.passwordPromptChannel
    val showSidebar: LiveData<Boolean> = state.showSidebar
    val hasUnreadChannels = state.hasUnreadChannels
    val hasUnreadPrivateMessages = state.hasUnreadPrivateMessages
    val showCommandSuggestions: LiveData<Boolean> = state.showCommandSuggestions
    val commandSuggestions: LiveData<List<CommandSuggestion>> = state.commandSuggestions
    val showMentionSuggestions: LiveData<Boolean> = state.showMentionSuggestions
    val mentionSuggestions: LiveData<List<String>> = state.mentionSuggestions
    val favoritePeers: LiveData<Set<String>> = state.favoritePeers
    val peerSessionStates: LiveData<Map<String, String>> = state.peerSessionStates
    val peerFingerprints: LiveData<Map<String, String>> = state.peerFingerprints
    val peerNicknames: LiveData<Map<String, String>> = state.peerNicknames
    val peerRSSI: LiveData<Map<String, Int>> = state.peerRSSI
    val showAppInfo: LiveData<Boolean> = state.showAppInfo
    val selectedLocationChannel: LiveData<com.dogechat.android.geohash.ChannelID?> = state.selectedLocationChannel
    val isTeleported: LiveData<Boolean> = state.isTeleported
    val geohashPeople: LiveData<List<GeoPerson>> = state.geohashPeople
    val teleportedGeo: LiveData<Set<String>> = state.teleportedGeo
    val geohashParticipantCounts: LiveData<Map<String, Int>> = state.geohashParticipantCounts
    
    init {
        // Note: Mesh service delegate is now set by MainActivity
        loadAndInitialize()
    }
    
    private fun loadAndInitialize() {
        // Load nickname
        val nickname = dataManager.loadNickname()
        state.setNickname(nickname)
        
        // Load data
        val (joinedChannels, protectedChannels) = channelManager.loadChannelData()
        state.setJoinedChannels(joinedChannels)
        state.setPasswordProtectedChannels(protectedChannels)
        
        // Initialize channel messages
        joinedChannels.forEach { channel ->
            if (!state.getChannelMessagesValue().containsKey(channel)) {
                val updatedChannelMessages = state.getChannelMessagesValue().toMutableMap()
                updatedChannelMessages[channel] = emptyList()
                state.setChannelMessages(updatedChannelMessages)
            }
        }
        
        // Load favorites and blocked users (fingerprints only)
        state.setFavoritePeers(dataManager.favoritePeers.toSet())
        state.setBlockedPeers(dataManager.blockedPeers.toSet())
        
        // Load peer nicknames from mesh service
        state.setPeerNicknames(meshService.getPeerNicknames())
        
        // Load peer RSSI from mesh service
        state.setPeerRSSI(meshService.getPeerRSSI())
        
        // Load peer session states from mesh service
        state.setPeerSessionStates(meshService.getPeerSessionStates())
        
        // Load peer fingerprints from centralized manager
        state.setPeerFingerprints(privateChatManager.getAllPeerFingerprints())
        
        // Initialize geohash channels
        nostrGeohashService.loadGeohashChannels()
        
        // Start geohash monitoring
        nostrGeohashService.startGeohashMonitoring()
        
        // Start sampling nearby geohashes
        beginGeohashSampling(listOf("9z", "9y", "9x")) // Example geohashes
        
        // Periodic cleanup
        viewModelScope.launch {
            while (true) {
                cleanupDisconnectedPeers()
                delay(30000) // Every 30 seconds
            }
        }
    }
    
    // MARK: - Nickname Management
    
    fun updateNickname(newNickname: String) {
        state.setNickname(newNickname)
        dataManager.saveNickname(newNickname)
        meshService.updateNickname(newNickname)
    }
    
    // MARK: - Message Sending
    
    fun sendMessage(
        content: String,
        mentions: List<String>,
        meshService: BluetoothMeshService,
        viewModel: ChatViewModel? = null
    ) {
        val currentChannel = state.getCurrentChannelValue()
        val selectedPrivatePeer = state.getSelectedPrivateChatPeerValue()
        val senderNickname = state.getNicknameValue()
        val myPeerID = meshService.myPeerID
        
        if (content.startsWith("/")) {
            if (commandProcessor.processCommand(content, meshService, myPeerID, ::sendMessage, viewModel)) {
                return
            }
        }
        
        val message = dogechatMessage(
            sender = senderNickname,
            content = content,
            timestamp = Date(),
            isRelay = false,
            senderPeerID = myPeerID,
            mentions = if (mentions.isNotEmpty()) mentions else null,
            channel = currentChannel,
            isPrivate = selectedPrivatePeer != null,
            recipientNickname = selectedPrivatePeer?.let { getPeerNickname(it, meshService) }
        )
        
        when {
            selectedPrivatePeer != null -> {
                privateChatManager.sendPrivateMessage(
                    content,
                    selectedPrivatePeer,
                    message.recipientNickname,
                    senderNickname,
                    myPeerID
                ) { cont, peerID, recipNick, msgId ->
                    meshService.sendPrivateMessage(cont, peerID, recipNick, msgId)
                }
            }
            currentChannel != null -> {
                if (channelManager.isChannelPasswordProtected(currentChannel) && channelManager.hasChannelKey(currentChannel)) {
                    channelManager.sendEncryptedChannelMessage(
                        content,
                        mentions,
                        currentChannel,
                        senderNickname,
                        myPeerID,
                        onEncryptedPayload = { payload ->
                            meshService.broadcastMessage(payload)
                        },
                        onFallback = {
                            val fallbackMessage = dogechatMessage(
                                sender = "system",
                                content = "encryption failed - sending unencrypted",
                                timestamp = Date(),
                                isRelay = false
                            )
                            messageManager.addMessage(fallbackMessage)
                            meshService.broadcastMessage(message.toBinaryPayload() ?: return@sendEncryptedChannelMessage)
                        }
                    )
                } else {
                    meshService.broadcastMessage(message.toBinaryPayload() ?: return)
                    channelManager.addChannelMessage(currentChannel, message, myPeerID)
                }
            }
            else -> {
                meshService.broadcastMessage(message.toBinaryPayload() ?: return)
                messageManager.addMessage(message)
            }
        }
    }
    
    // MARK: - Channel Operations
    
    fun joinChannel(channel: String, password: String? = null) {
        channelManager.joinChannel(channel, password, meshService.myPeerID)
    }
    
    fun leaveChannel(channel: String) {
        channelManager.leaveChannel(channel)
    }
    
    fun switchToChannel(channel: String?) {
        channelManager.switchToChannel(channel)
    }
    
    fun setChannelPassword(channel: String, password: String) {
        channelManager.setChannelPassword(channel, password)
    }
    
    // MARK: - Private Chat Operations
    
    fun startPrivateChat(peerID: String) {
        privateChatManager.startPrivateChat(peerID, meshService)
    }
    
    fun endPrivateChat() {
        privateChatManager.endPrivateChat()
    }
    
    fun toggleFavorite(peerID: String) {
        privateChatManager.toggleFavorite(peerID)
    }
    
    fun blockPeer(peerID: String) {
        privateChatManager.blockPeer(peerID, meshService)
    }
    
    fun unblockPeer(peerID: String) {
        privateChatManager.unblockPeer(peerID, meshService)
    }
    
    // MARK: - Mesh Delegate Callbacks (via handler)
    
    override fun didReceiveMessage(message: dogechatMessage) {
        meshDelegateHandler.didReceiveMessage(message)
    }
    
    override fun didUpdatePeerList(peers: List<String>) {
        meshDelegateHandler.didUpdatePeerList(peers)
    }
    
    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
        meshDelegateHandler.didReceiveChannelLeave(channel, fromPeer)
    }
    
    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
        meshDelegateHandler.didReceiveDeliveryAck(messageID, recipientPeerID)
    }
    
    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
        meshDelegateHandler.didReceiveReadReceipt(messageID, recipientPeerID)
    }
    
    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
        return meshDelegateHandler.decryptChannelMessage(encryptedContent, channel)
    }
    
    override fun getNickname(): String? {
        return meshDelegateHandler.getNickname()
    }
    
    override fun isFavorite(peerID: String): Boolean {
        return meshDelegateHandler.isFavorite(peerID)
    }
    
    // MARK: - Emergency Clear
    
    fun panicClearAllData() {
        Log.w(TAG, "🚨 PANIC MODE ACTIVATED - Clearing Much sensitive data Very Fast")
        
        // Clear all UI managers
        messageManager.clearAllMessages()
        channelManager.clearAllChannels()
        privateChatManager.clearAllPrivateChats()
        dataManager.clearAllData()
        
        // Clear all mesh service data
        clearAllMeshServiceData()
        
        // Clear all cryptographic data
        clearAllCryptographicData()
        
        // Clear all notifications
        notificationManager.clearAllNotifications()
        
        // Clear geohash message history
        nostrGeohashService.clearGeohashMessageHistory()
        
        // Reset nickname
        val newNickname = "anon${Random.nextInt(1000, 9999)}"
        state.setNickname(newNickname)
        dataManager.saveNickname(newNickname)
        
        Log.w(TAG, "🚨 PANIC MODE COMPLETED - All sensitive data VERY cleared")
        
        // Note: Mesh service restart is now handled by MainActivity
        // This method now only clears data, not mesh service lifecycle
    }
    
    /**
     * Clear all mesh service related data
     */
    private fun clearAllMeshServiceData() {
        try {
            // Request mesh service to clear all its internal data
            meshService.clearAllInternalData()
            
            Log.d(TAG, "✅ Cleared all mesh service data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing mesh service data: ${e.message}")
        }
    }
    
    /**
     * Clear all cryptographic data including persistent identity
     */
    private fun clearAllCryptographicData() {
        try {
            // Clear encryption service persistent identity (Ed25519 signing keys)
            meshService.clearAllEncryptionData()
            
            // Clear secure identity state (if used)
            try {
                val identityManager = com.dogechat.android.identity.SecureIdentityStateManager(getApplication())
                identityManager.clearIdentityData()
                Log.d(TAG, "✅ Cleared secure identity state")
            } catch (e: Exception) {
                Log.d(TAG, "SecureIdentityStateManager not available or already cleared: ${e.message}")
            }
            
            Log.d(TAG, "✅ Cleared all cryptographic data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cryptographic data: ${e.message}")
        }
    }
    
    /**
     * Get participant count for a specific geohash (5-minute activity window)
     */
    fun geohashParticipantCount(geohash: String): Int {
        return nostrGeohashService.geohashParticipantCount(geohash)
    }
    
    /**
     * Begin sampling multiple geohashes for participant activity
     */
    fun beginGeohashSampling(geohashes: List<String>) {
        nostrGeohashService.beginGeohashSampling(geohashes)
    }
    
    /**
     * End geohash sampling
     */
    fun endGeohashSampling() {
        nostrGeohashService.endGeohashSampling()
    }
    
    /**
     * Check if a geohash person is teleported (iOS-compatible)
     */
    fun isPersonTeleported(pubkeyHex: String): Boolean {
        return nostrGeohashService.isPersonTeleported(pubkeyHex)
    }
    
    /**
     * Start geohash DM with pubkey hex (iOS-compatible)
     */
    fun startGeohashDM(pubkeyHex: String) {
        nostrGeohashService.startGeohashDM(pubkeyHex) { convKey ->
            startPrivateChat(convKey)
        }
    }
    
    fun selectLocationChannel(channel: com.dogechat.android.geohash.ChannelID) {
        nostrGeohashService.selectLocationChannel(channel)
    }
    
    // MARK: - Navigation Management
    
    fun showAppInfo() {
        state.setShowAppInfo(true)
    }
    
    fun hideAppInfo() {
        state.setShowAppInfo(false)
    }
    
    fun showSidebar() {
        state.setShowSidebar(true)
    }
    
    fun hideSidebar() {
        state.setShowSidebar(false)
    }
    
    /**
     * Handle Android back navigation
     * Returns true if the back press was handled, false if it should be passed to the system
     */
    fun handleBackPressed(): Boolean {
        return when {
            // Close app info dialog
            state.getShowAppInfoValue() -> {
                hideAppInfo()
                true
            }
            // Close sidebar
            state.getShowSidebarValue() -> {
                hideSidebar()
                true
            }
            // Close password dialog
            state.getShowPasswordPromptValue() -> {
                state.setShowPasswordPrompt(false)
                state.setPasswordPromptChannel(null)
                true
            }
            // Exit private chat
            state.getSelectedPrivateChatPeerValue() != null -> {
                endPrivateChat()
                true
            }
            // Exit channel view
            state.getCurrentChannelValue() != null -> {
                switchToChannel(null)
                true
            }
            // No special navigation state - let system handle (usually exits app)
            else -> false
        }
    }
    
    // MARK: - iOS-Compatible Color System
    
    /**
     * Get consistent color for a mesh peer by ID (iOS-compatible)
     */
    fun colorForMeshPeer(peerID: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
        // Try to get stable Noise key, fallback to peer ID
        val seed = "noise:${peerID.lowercase()}"
        return colorForPeerSeed(seed, isDark).copy()
    }
    
    /**
     * Get consistent color for a Nostr pubkey (iOS-compatible)
     */
    fun colorForNostrPubkey(pubkeyHex: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
        return nostrGeohashService.colorForNostrPubkey(pubkeyHex, isDark)
    }
    
    // MARK: - Cleanup
    
    private fun cleanupDisconnectedPeers() {
        val connectedPeers = state.getConnectedPeersValue()
        val myPeerID = meshService.myPeerID
        channelManager.cleanupDisconnectedMembers(connectedPeers, myPeerID)
        privateChatManager.cleanupDisconnectedPeer(myPeerID)
    }
}