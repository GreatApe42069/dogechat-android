package com.dogechat.watch.ui

import com.dogechat.android.model.DogechatFilePacket
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.DogechatMessageType
import com.dogechat.android.model.DeliveryStatus
import com.dogechat.android.services.AppStateStore
import com.dogechat.watch.mesh.WearMeshService
import java.io.File
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal fun sendPublicMessage(mesh: WearMeshService, content: String) {
    mesh.sendMessage(content)
    AppStateStore.addPublicMessage(
        DogechatMessage(
            sender = mesh.nickname,
            content = content,
            timestamp = Date(),
            senderPeerID = mesh.myPeerID,
            deliveryStatus = DeliveryStatus.Sent
        )
    )
}

/**
 * DM send with honest delivery state. MeshCore drops pre-handshake content (it only kicks
 * off the Noise handshake), so when no session exists we must not echo "Sent": the echo
 * stays "Sending" while a retry loop waits for the session and completes the send.
 */
internal fun sendPrivateMessage(
    mesh: WearMeshService,
    peerID: String,
    recipientNickname: String,
    content: String,
    scope: CoroutineScope
) {
    val established = mesh.hasEstablishedSession(peerID)
    val messageID = java.util.UUID.randomUUID().toString()
    if (established) {
        mesh.sendPrivateMessageWithId(content, peerID, recipientNickname, messageID)
    } else {
        mesh.initiateNoiseHandshake(peerID)
        scope.launch {
            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                if (mesh.hasEstablishedSession(peerID)) {
                    mesh.sendPrivateMessageWithId(content, peerID, recipientNickname, messageID)
                    AppStateStore.updatePrivateMessageStatus(messageID, DeliveryStatus.Sent)
                    return@launch
                }
                delay(400)
            }
            // Session never came up: the echo honestly stays "Sending" (AppStateStore
            // refuses status downgrades, so it cannot be marked Failed from here).
        }
    }
    AppStateStore.addPrivateMessage(
        peerID,
        DogechatMessage(
            id = messageID,
            sender = mesh.nickname,
            content = content,
            timestamp = Date(),
            isPrivate = true,
            recipientNickname = recipientNickname,
            senderPeerID = mesh.myPeerID,
            deliveryStatus = if (established) DeliveryStatus.Sent else DeliveryStatus.Sending
        )
    )
}

/**
 * Send a recorded voice note. Global chat: broadcast file packet. DM thread: Noise-encrypted
 * private file transfer. Local echo renders immediately (content = local path, type = Audio).
 */
internal fun sendVoiceNote(mesh: WearMeshService, peerID: String?, path: String) {
    val file = File(path)
    if (!file.isFile) return
    val packet = DogechatFilePacket(
        fileName = file.name,
        fileSize = file.length(),
        mimeType = "audio/mp4",
        content = file.readBytes()
    )
    if (peerID == null) {
        mesh.sendFileBroadcast(packet)
    } else {
        mesh.sendFilePrivateEncrypted(peerID, packet)
    }
    val echo = DogechatMessage(
        sender = mesh.nickname,
        content = path,
        type = DogechatMessageType.Audio,
        timestamp = Date(),
        isPrivate = peerID != null,
        senderPeerID = mesh.myPeerID,
        deliveryStatus = DeliveryStatus.Sent
    )
    if (peerID == null) {
        AppStateStore.addPublicMessage(echo)
    } else {
        AppStateStore.addPrivateMessage(peerID, echo)
    }
}
