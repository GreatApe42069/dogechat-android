# Media Routing Implementation Guide

## Overview

This document provides a complete implementation guide for routing media (images, voice notes, files) to Nostr geohash channels and DMs, instead of always sending to mesh.

## Problem

Currently, when users try to send media in:
- Geohash public channels → Goes to mesh chat instead
- Geohash private messages → Goes to mesh chat instead

## Solution: Base64 Data URL Approach

For the MVP, we encode media as base64 data URLs embedded in text messages. This works with existing Nostr infrastructure without requiring external file hosting.

### Advantages
- ✅ No external hosting required
- ✅ Works with existing Nostr relays
- ✅ End-to-end encrypted for DMs (NIP-17)
- ✅ Simple implementation (~200 lines)
- ✅ Immediate availability

### Limitations
- ⚠️ 500KB file size limit (reasonable for most photos/voice notes)
- ⚠️ Not suitable for large files (videos, etc.)

## Implementation Steps

### Step 1: Add Helper Functions

Add these to `MediaSendingManager.kt` or a new `MediaEncodingUtils.kt`:

```kotlin
/**
 * Encode file as base64 data URL
 * Format: data:image/jpeg;base64,/9j/4AAQSkZJRg...
 */
private fun encodeFileAsDataUrl(filePacket: DogechatFilePacket): String {
    val base64 = android.util.Base64.encodeToString(
        filePacket.content,
        android.util.Base64.NO_WRAP
    )
    return "data:${filePacket.mimeType};base64,$base64"
}

/**
 * Parse data URL into mime type and decoded bytes
 * Returns Pair(mimeType, decodedBytes) or null if invalid
 */
private fun parseDataUrl(dataUrl: String): Pair<String, ByteArray>? {
    try {
        if (!dataUrl.startsWith("data:")) return null
        
        val parts = dataUrl.removePrefix("data:").split(";base64,", limit = 2)
        if (parts.size != 2) return null
        
        val mimeType = parts[0]
        val base64Data = parts[1]
        val bytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)
        
        return Pair(mimeType, bytes)
    } catch (e: Exception) {
        Log.e("MediaUtils", "Failed to parse data URL: ${e.message}")
        return null
    }
}

/**
 * Save data URL content to cache directory
 * Returns File object or null if failed
 */
private fun saveDataUrlToCache(
    context: Context,
    fileName: String,
    dataUrl: String
): File? {
    try {
        val (mimeType, bytes) = parseDataUrl(dataUrl) ?: return null
        
        val cacheDir = File(context.cacheDir, "received_media")
        cacheDir.mkdirs()
        
        val file = File(cacheDir, fileName)
        file.writeBytes(bytes)
        
        Log.d("MediaUtils", "Saved file to cache: ${file.absolutePath}")
        return file
    } catch (e: Exception) {
        Log.e("MediaUtils", "Failed to save data URL to cache: ${e.message}")
        return null
    }
}
```

### Step 2: Add Geohash File Sending

Add to `MediaSendingManager.kt`:

```kotlin
/**
 * Send a file via Nostr geohash (either channel or DM)
 */
private fun sendGeohashFile(
    geohashOrPeerID: String,
    filePacket: DogechatFilePacket,
    filePath: String,
    messageType: DogechatMessageType,
    isPrivate: Boolean
) {
    // Check file size limit
    if (filePacket.fileSize > 500_000) { // 500KB limit
        Log.w(TAG, "File too large for geohash send: ${filePacket.fileSize} bytes (max 500KB)")
        // TODO: Show user feedback toast/dialog
        return
    }
    
    // Encode as data URL
    val dataUrl = encodeFileAsDataUrl(filePacket)
    
    // Create special formatted content: [FILE:filename]dataUrl
    val content = "[FILE:${filePacket.fileName}]$dataUrl"
    
    // Create UI message immediately (optimistic update)
    val msg = DogechatMessage(
        id = java.util.UUID.randomUUID().toString().uppercase(),
        sender = state.getNicknameValue() ?: meshService.myPeerID,
        content = filePath, // Local file path for UI preview
        type = messageType,
        timestamp = Date(),
        isRelay = false,
        isPrivate = isPrivate,
        recipientNickname = if (isPrivate) {
            // For geohash DMs, try to get nickname from geohash people
            try {
                val nostrPubkey = com.dogechat.android.nostr.GeohashAliasRegistry.get(geohashOrPeerID)
                if (nostrPubkey != null) {
                    // Get from GeohashViewModel (needs reference)
                    null // TODO: Pass GeohashViewModel reference or get nickname another way
                } else null
            } catch (_: Exception) { null }
        } else null,
        senderPeerID = if (isPrivate) geohashOrPeerID else "geohash:$geohashOrPeerID"
    )
    
    if (isPrivate) {
        messageManager.addPrivateMessage(geohashOrPeerID, msg)
        
        // Send via Nostr DM (NIP-17)
        viewModelScope.launch {
            try {
                val nostrPubkey = com.dogechat.android.nostr.GeohashAliasRegistry.get(geohashOrPeerID)
                if (nostrPubkey != null) {
                    val nostrTransport = com.dogechat.android.nostr.NostrTransport.getInstance(context)
                    nostrTransport.sendPrivateMessage(
                        content = content,
                        toPeerID = geohashOrPeerID,
                        displayName = state.getNicknameValue()
                    )
                    Log.d(TAG, "📤 Sent file via Nostr DM to $geohashOrPeerID")
                } else {
                    Log.e(TAG, "Could not resolve nostr pubkey for $geohashOrPeerID")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file via Nostr DM: ${e.message}")
            }
        }
    } else {
        // Public geohash channel
        messageManager.addChannelMessage("geo:$geohashOrPeerID", msg)
        
        // Send via geohash channel (kind 20000)
        viewModelScope.launch {
            try {
                val selectedLocationChannel = state.selectedLocationChannel.value
                if (selectedLocationChannel is com.dogechat.android.geohash.ChannelID.Location) {
                    val channel = selectedLocationChannel.channel
                    geohashViewModel.sendGeohashMessage(
                        content = content,
                        channel = channel,
                        myPeerID = meshService.myPeerID,
                        nickname = state.getNicknameValue()
                    )
                    Log.d(TAG, "📤 Sent file to geohash channel: $geohashOrPeerID")
                } else {
                    Log.e(TAG, "Not in a geohash channel context")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file to geohash channel: ${e.message}")
            }
        }
    }
}
```

### Step 3: Update Media Send Methods

Update each send method in `MediaSendingManager.kt`:

```kotlin
fun sendVoiceNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
    try {
        val file = java.io.File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "❌ File does not exist: $filePath")
            return
        }
        
        if (file.length() > MAX_FILE_SIZE) {
            Log.e(TAG, "❌ File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
            return
        }

        val filePacket = DogechatFilePacket(
            fileName = file.name,
            fileSize = file.length(),
            mimeType = "audio/mp4",
            content = file.readBytes()
        )

        // NEW: Check if we're in geohash context
        val selectedLocationChannel = state.selectedLocationChannel.value
        val isGeohashContext = selectedLocationChannel is com.dogechat.android.geohash.ChannelID.Location

        if (toPeerIDOrNull != null) {
            // Private message
            if (toPeerIDOrNull.startsWith("nostr_") && isGeohashContext) {
                // Geohash DM
                sendGeohashFile(toPeerIDOrNull, filePacket, filePath, DogechatMessageType.Audio, isPrivate = true)
            } else {
                // Mesh private message
                sendPrivateFile(toPeerIDOrNull, filePacket, filePath, DogechatMessageType.Audio)
            }
        } else {
            // Public channel/broadcast
            if (isGeohashContext) {
                // Geohash channel
                val channel = (selectedLocationChannel as com.dogechat.android.geohash.ChannelID.Location).channel
                sendGeohashFile(channel.geohash, filePacket, filePath, DogechatMessageType.Audio, isPrivate = false)
            } else {
                // Mesh channel
                sendPublicFile(channelOrNull, filePacket, filePath, DogechatMessageType.Audio)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send voice note: ${e.message}")
    }
}

// Apply same pattern to sendImageNote() and sendFileNote()
```

### Step 4: Update Message Reception

Add to `GeohashMessageHandler.kt`:

```kotlin
fun onEvent(event: NostrEvent, subscribedGeohash: String) {
    scope.launch(Dispatchers.Default) {
        try {
            // ... existing validation ...
            
            // Check if message contains file data
            val isFileMessage = event.content.startsWith("[FILE:")
            
            val msg = if (isFileMessage) {
                // Parse file message
                val parts = event.content.split("]", limit = 2)
                val fileName = parts[0].removePrefix("[FILE:")
                val dataUrl = parts.getOrNull(1) ?: return@launch
                
                // Save file to cache
                val file = saveDataUrlToCache(application, fileName, dataUrl)
                if (file == null) {
                    Log.e(TAG, "Failed to save received file")
                    return@launch
                }
                
                // Determine message type from mime
                val (mimeType, _) = parseDataUrl(dataUrl) ?: return@launch
                val msgType = when {
                    mimeType.startsWith("image/") -> DogechatMessageType.Image
                    mimeType.startsWith("audio/") -> DogechatMessageType.Audio
                    else -> DogechatMessageType.File
                }
                
                DogechatMessage(
                    id = event.id,
                    sender = senderName,
                    content = file.absolutePath, // Local cached file path
                    type = msgType,
                    timestamp = Date(event.createdAt * 1000L),
                    isRelay = false,
                    originalSender = repo.displayNameForNostrPubkey(event.pubkey),
                    senderPeerID = "nostr:${event.pubkey.take(8)}",
                    mentions = null,
                    channel = "#$subscribedGeohash",
                    powDifficulty = try {
                        if (hasNonce) NostrProofOfWork.calculateDifficulty(event.id).takeIf { it > 0 } else null
                    } catch (_: Exception) { null }
                )
            } else {
                // Regular text message (existing code)
                DogechatMessage(
                    id = event.id,
                    sender = senderName,
                    content = event.content,
                    // ... rest of existing message creation ...
                )
            }
            
            withContext(Dispatchers.Main) { 
                messageManager.addChannelMessage("geo:$subscribedGeohash", msg) 
            }
            
            // Save to retention if bookmarked
            try {
                val retentionService = com.dogechat.android.services.MessageRetentionService.getInstance(application)
                retentionService.saveMessage(msg, forChannel = subscribedGeohash)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save message to retention: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onEvent error: ${e.message}")
        }
    }
}
```

### Step 5: Add Dependencies

Make sure `MediaSendingManager` has access to:

```kotlin
class MediaSendingManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val meshService: BluetoothMeshService,
    private val geohashViewModel: GeohashViewModel,  // ADD THIS
    private val viewModelScope: CoroutineScope,      // ADD THIS
    private val context: Context                      // ADD THIS
) {
    // ... implementation ...
}
```

Update constructor call in `ChatViewModel.kt`:

```kotlin
private val mediaSendingManager = MediaSendingManager(
    state, 
    messageManager, 
    channelManager, 
    meshService,
    geohashViewModel,      // ADD
    viewModelScope,        // ADD
    getApplication()       // ADD
)
```

### Step 6: Add User Feedback

Add size check with user-visible error:

```kotlin
private fun sendGeohashFile(...) {
    if (filePacket.fileSize > 500_000) {
        // Show toast to user
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                "File too large for geohash (max 500KB). Use mesh for larger files.",
                Toast.LENGTH_LONG
            ).show()
        }
        return
    }
    // ... rest of implementation ...
}
```

## Testing

### Test Cases

1. **Geohash Channel - Image**
   - Open geohash channel
   - Click image button
   - Select small image (<500KB)
   - Verify appears in channel
   - Verify other users can see it

2. **Geohash Channel - Voice**
   - Open geohash channel
   - Record voice note
   - Send
   - Verify appears in channel
   - Verify playback works

3. **Geohash DM - Image**
   - Open DM with geohash peer
   - Send image
   - Verify appears in DM
   - Verify private (not in channel)

4. **Geohash DM - File**
   - Open DM with geohash peer
   - Send file
   - Verify appears in DM
   - Verify can download/open

5. **Size Limit**
   - Try to send file >500KB to geohash
   - Verify shows error message
   - Verify doesn't crash

6. **Mesh Still Works**
   - Switch to mesh channel
   - Send image
   - Verify still uses mesh transport
   - Verify no regression

7. **Cached Messages Include Media**
   - Send media to bookmarked geohash channel
   - Close and restart app
   - Open channel
   - Verify media messages loaded from cache

## Future Enhancements

### Phase 2: External File Hosting

For files >500KB, implement NIP-94/NIP-96:

1. Upload file to compatible hosting service (blossom.server, nostr.build, etc.)
2. Get URL back
3. Send Nostr event (kind 1063) with file metadata and URL
4. Receiver downloads from URL

### Phase 3: Progressive Enhancement

- Show encoding/decoding progress bars
- Optimize image compression before encoding
- Implement file chunking for better reliability
- Add automatic retry on failure
- Cache encoded data URLs to avoid re-encoding

### Phase 4: Advanced Features

- Video support with thumbnail generation
- GIF support
- Multiple file attachments
- File preview before sending
- Download progress for received files

## Security Considerations

1. **Data URL Safety**
   - Base64 encoding is safe (no code execution)
   - Always validate mime types
   - Sanitize file names

2. **File Size Limits**
   - Prevents DoS attacks
   - Protects relay resources
   - Ensures reasonable UX

3. **Privacy**
   - Geohash channel media is public
   - DM media is NIP-17 encrypted
   - Cached files stored locally (consider encryption)

4. **Content Validation**
   - Verify file types match declared mime
   - Scan for malware (future)
   - Respect user's storage limits

## Estimated Effort

- Helper functions: 30 minutes
- sendGeohashFile(): 1 hour
- Update send methods: 1 hour
- Update reception: 1.5 hours
- Testing: 2 hours
- **Total: ~6 hours**

## Success Criteria

✅ Images send to geohash channels
✅ Voice notes send to geohash channels
✅ Files send to geohash channels
✅ Media sends to geohash DMs
✅ Received media displays correctly
✅ Mesh media still works
✅ File size limits enforced
✅ User feedback on errors
✅ Cached media messages work
✅ No crashes or data loss

## Notes

- Keep mesh routing intact (backward compatibility)
- Test thoroughly with different file types
- Monitor relay response to large messages
- Consider compression for images
- Document limitations clearly to users
