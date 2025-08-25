package com.dogechat.android.ui

import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.mesh.BluetoothMeshService
import java.util.*

/**
 * Handles processing of IRC-style commands
 */
class CommandProcessor(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val privateChatManager: PrivateChatManager
) {

    // Available commands list
    private val baseCommands = listOf(
        CommandSuggestion("/block", emptyList(), "[nickname]", "block or list blocked peers"),
        CommandSuggestion("/channels", emptyList(), null, "show all discovered channels"),
        CommandSuggestion("/clear", emptyList(), null, "clear chat messages"),
        CommandSuggestion("/hug", emptyList(), "<nickname>", "send someone a warm hug"),
        CommandSuggestion("/j", listOf("/join"), "<channel>", "join or create a channel"),
        CommandSuggestion("/m", listOf("/msg"), "<nickname> [message]", "send private message"),
        CommandSuggestion("/slap", emptyList(), "<nickname>", "slap someone with a trout"),
        CommandSuggestion("/unblock", emptyList(), "<nickname>", "unblock a peer"),
        CommandSuggestion("/w", emptyList(), null, "see who's online")
    )

    // MARK: - Command Processing

    fun processCommand(
        command: String,
        meshService: BluetoothMeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit,
        viewModel: ChatViewModel? = null
    ): Boolean {
        if (!command.startsWith("/")) return false

        val parts = command.split(" ")
        val cmd = parts.first()

        when (cmd) {
            "/j", "/join" -> handleJoinCommand(parts, myPeerID)
            "/m", "/msg" -> handleMessageCommand(parts, meshService)
            "/w" -> handleWhoCommand(meshService, viewModel)
            "/clear" -> handleClearCommand()
            "/pass" -> handlePassCommand(parts, myPeerID)
            "/block" -> handleBlockCommand(parts, meshService)
            "/unblock" -> handleUnblockCommand(parts, meshService)
            "/hug" -> handleActionCommand(parts, "gives", "a warm hug 🫂", meshService, myPeerID, onSendMessage)
            "/slap" -> handleActionCommand(parts, "slaps", "around a doge with a large trout 🐟", meshService, myPeerID, onSendMessage)
            "/channels" -> handleChannelsCommand()
            else -> handleUnknownCommand(cmd)
        }

        return true
    }

    private fun handleJoinCommand(parts: List<String>, myPeerID: String) {
        if (parts.size > 1) {
            val channelName = parts[1]
            val channel = if (channelName.startsWith("#")) channelName else "#$channelName"
            val password = if (parts.size > 2) parts[2] else null
            val success = channelManager.joinChannel(channel, password, myPeerID)
            if (success) {
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "joined channel $channel",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "usage: /join <channel>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleMessageCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val peerID = getPeerIDForNickname(targetName, meshService)

            if (peerID != null) {
                val success = privateChatManager.startPrivateChat(peerID, meshService)

                if (success) {
                    if (parts.size > 2) {
                        val messageContent = parts.drop(2).joinToString(" ")
                        val recipientNickname = getPeerNickname(peerID, meshService)
                        privateChatManager.sendPrivateMessage(
                            messageContent,
                            peerID,
                            recipientNickname,
                            state.getNicknameValue(),
                            getMyPeerID(meshService)
                        ) { content, peerIdParam, recipientNicknameParam, messageId ->
                            // This would trigger the actual mesh service send
                            sendPrivateMessageVia(meshService, content, peerIdParam, recipientNicknameParam, messageId)
                        }
                    } else {
                        val systemMessage = DogechatMessage(
                            sender = "system",
                            content = "started private chat with $targetName",
                            timestamp = Date(),
                            isRelay = false
                        )
                        messageManager.addMessage(systemMessage)
                    }
                }
            } else {
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "user '$targetName' not found. they may be offline or using a different nickname.",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "usage: /msg <nickname> [message]",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleWhoCommand(meshService: BluetoothMeshService, viewModel: ChatViewModel? = null) {
        val (peerList, contextDescription) = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.dogechat.android.geohash.ChannelID.Mesh,
                null -> {
                    val connectedPeers = state.getConnectedPeersValue()
                    val peerList = connectedPeers.joinToString(", ") { peerID ->
                        getPeerNickname(peerID, meshService)
                    }
                    Pair(peerList, "online users")
                }

                is com.dogechat.android.geohash.ChannelID.Location -> {
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()

                    val participantList = geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }.joinToString(", ")

                    Pair(participantList, "participants in ${selectedChannel.channel.geohash}")
                }
            }
        } else {
            val connectedPeers = state.getConnectedPeersValue()
            val peerList = connectedPeers.joinToString(", ") { peerID ->
                getPeerNickname(peerID, meshService)
            }
            Pair(peerList, "online users")
        }

        val systemMessage = DogechatMessage(
            sender = "system",
            content = if (peerList.isEmpty()) {
                "no one else is around right now."
            } else {
                "$contextDescription: $peerList"
            },
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleClearCommand() {
        messageManager.clearAllMessages()
        val systemMessage = DogechatMessage(
            sender = "system",
            content = "chat history cleared",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handlePassCommand(parts: List<String>, myPeerID: String) {
        val currentChannel = state.getCurrentChannelValue() ?: return
        if (parts.size > 1) {
            val password = parts[1]
            if (channelManager.isChannelCreator(currentChannel, myPeerID)) {
                channelManager.setChannelPassword(currentChannel, password)
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "channel password set",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            } else {
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "only channel creator can set password",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "usage: /pass <password>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleBlockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val peerID = getPeerIDForNickname(targetName, meshService)
            if (peerID != null) {
                privateChatManager.blockPeer(peerID, meshService)
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "$targetName blocked",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            } else {
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "user '$targetName' not found",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val blockedPeers = privateChatManager.getBlockedPeers(meshService)
            val blockedList = if (blockedPeers.isEmpty()) "none" else blockedPeers.joinToString(", ")
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "blocked users: $blockedList",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleUnblockCommand(parts: List<String>, meshService: BluetoothMeshService) {
        if (parts.size > 1) {
            val targetName = parts[1].removePrefix("@")
            val peerID = getPeerIDForNickname(targetName, meshService)
            if (peerID != null) {
                privateChatManager.unblockPeer(peerID, meshService)
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "$targetName unblocked",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            } else {
                val systemMessage = DogechatMessage(
                    sender = "system",
                    content = "user '$targetName' not found",
                    timestamp = Date(),
                    isRelay = false
                )
                messageManager.addMessage(systemMessage)
            }
        } else {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "usage: /unblock <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleActionCommand(
        parts: List<String>,
        actionVerb: String,
        actionSuffix: String,
        meshService: BluetoothMeshService,
        myPeerID: String,
        onSendMessage: (String, List<String>, String?) -> Unit
    ) {
        if (parts.size > 1) {
            val target = parts[1]
            val actionMessage = "${state.getNicknameValue()} $actionVerb $target $actionSuffix"
            onSendMessage(actionMessage, emptyList(), null)
        } else {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "usage: ${parts[0]} <nickname>",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
        }
    }

    private fun handleChannelsCommand() {
        val allChannels = channelManager.getJoinedChannelsList()
        val channelList = if (allChannels.isEmpty()) "none" else "joined channels: ${allChannels.joinToString(", ")}"

        val systemMessage = DogechatMessage(
            sender = "system",
            content = channelList,
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    private fun handleUnknownCommand(cmd: String) {
        val systemMessage = DogechatMessage(
            sender = "system",
            content = "unknown command: $cmd. type / to see available commands.",
            timestamp = Date(),
            isRelay = false
        )
        messageManager.addMessage(systemMessage)
    }

    // MARK: - Command Autocomplete

    fun updateCommandSuggestions(input: String) {
        if (!input.startsWith("/")) {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
            return
        }

        val allCommands = getAllAvailableCommands()
        val filteredCommands = filterCommands(allCommands, input.lowercase())

        if (filteredCommands.isNotEmpty()) {
            state.setCommandSuggestions(filteredCommands)
            state.setShowCommandSuggestions(true)
        } else {
            state.setShowCommandSuggestions(false)
            state.setCommandSuggestions(emptyList())
        }
    }

    private fun getAllAvailableCommands(): List<CommandSuggestion> {
        val channelCommands = if (state.getCurrentChannelValue() != null) {
            listOf(
                CommandSuggestion("/pass", emptyList(), "[password]", "change channel password"),
                CommandSuggestion("/save", emptyList(), null, "save channel messages locally"),
                CommandSuggestion("/transfer", emptyList(), "<nickname>", "transfer channel ownership")
            )
        } else {
            emptyList()
        }

        return baseCommands + channelCommands
    }

    private fun filterCommands(commands: List<CommandSuggestion>, input: String): List<CommandSuggestion> {
        return commands.filter { command ->
            command.command.startsWith(input) ||
                    command.aliases.any { it.startsWith(input) }
        }.sortedBy { it.command }
    }

    fun selectCommandSuggestion(suggestion: CommandSuggestion): String {
        state.setShowCommandSuggestions(false)
        state.setCommandSuggestions(emptyList())
        return "${suggestion.command} "
    }

    // MARK: - Mention Autocomplete

    fun updateMentionSuggestions(input: String, meshService: BluetoothMeshService, viewModel: ChatViewModel? = null) {
        val atIndex = input.lastIndexOf('@')
        if (atIndex == -1) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }

        val textAfterAt = input.substring(atIndex + 1)

        if (textAfterAt.contains(' ')) {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
            return
        }

        val peerCandidates: List<String> = if (viewModel != null) {
            when (val selectedChannel = viewModel.selectedLocationChannel.value) {
                is com.dogechat.android.geohash.ChannelID.Mesh,
                null -> {
                    meshService.getPeerNicknames().values.filter { it != meshService.getPeerNicknames()[meshService.myPeerID] }
                }

                is com.dogechat.android.geohash.ChannelID.Location -> {
                    val geohashPeople = viewModel.geohashPeople.value ?: emptyList()
                    val currentNickname = state.getNicknameValue()

                    geohashPeople.mapNotNull { person ->
                        val displayName = person.displayName
                        if (displayName.startsWith("${currentNickname}#")) {
                            null
                        } else {
                            displayName
                        }
                    }
                }
            }
        } else {
            meshService.getPeerNicknames().values.filter { it != meshService.getPeerNicknames()[meshService.myPeerID] }
        }

        val filteredNicknames = peerCandidates.filter { nickname ->
            nickname.startsWith(textAfterAt, ignoreCase = true)
        }.sorted()

        if (filteredNicknames.isNotEmpty()) {
            state.setMentionSuggestions(filteredNicknames)
            state.setShowMentionSuggestions(true)
        } else {
            state.setShowMentionSuggestions(false)
            state.setMentionSuggestions(emptyList())
        }
    }

    fun selectMentionSuggestion(nickname: String, currentText: String): String {
        state.setShowMentionSuggestions(false)
        state.setMentionSuggestions(emptyList())

        val atIndex = currentText.lastIndexOf('@')
        if (atIndex == -1) {
            return "$currentText@$nickname "
        }

        val textBeforeAt = currentText.substring(0, atIndex)
        return "$textBeforeAt@$nickname "
    }

    // MARK: - Utility Functions

    private fun getPeerIDForNickname(nickname: String, meshService: BluetoothMeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }

    private fun getPeerNickname(peerID: String, meshService: BluetoothMeshService): String {
        return meshService.getPeerNicknames()[peerID] ?: peerID
    }

    private fun getMyPeerID(meshService: BluetoothMeshService): String {
        return meshService.myPeerID
    }

    private fun sendPrivateMessageVia(meshService: BluetoothMeshService, content: String, peerID: String, recipientNickname: String, messageId: String) {
        meshService.sendPrivateMessage(content, peerID, recipientNickname, messageId)
    }
}
