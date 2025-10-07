package com.dogechat.android.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.dogechat.android.geohash.Geohash
import com.dogechat.android.nostr.GeohashMessageHandler
import com.dogechat.android.nostr.GeohashRepository
import com.dogechat.android.nostr.NostrDirectMessageHandler
import com.dogechat.android.nostr.NostrIdentityBridge
import com.dogechat.android.nostr.NostrProtocol
import com.dogechat.android.nostr.NostrRelayManager
import com.dogechat.android.nostr.NostrSubscriptionManager
import com.dogechat.android.nostr.PoWPreferenceManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.exp

/**
 * HeatStreamBus
 * - Global, lightweight publish/subscribe bus for the map.
 * - ViewModel publishes counts and converts them to heat points.
 * - GeohashPickerActivity subscribes and pushes into WebView (warm start + 500ms batches).
 */
object HeatStreamBus {

    data class HeatPoint(val lat: Double, val lng: Double, val intensity: Double, val ts: Long)

    interface Listener {
        fun onCounts(counts: Map<String, Int>)
        fun onPoints(points: List<HeatPoint>)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val pointsBuffer = Collections.synchronizedList(mutableListOf<HeatPoint>())
    private val countsSnapshot = ConcurrentHashMap<String, Int>()
    @Volatile private var ttlMs: Long = 300_000L // default 5 min

    fun setTTL(ms: Long) {
        ttlMs = ms
        prune()
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun clearAllListeners() {
        listeners.clear()
    }

    fun getWarmPointsSnapshot(): List<HeatPoint> {
        val cutoff = System.currentTimeMillis() - ttlMs
        return synchronized(pointsBuffer) { pointsBuffer.filter { it.ts >= cutoff } }
    }

    fun getCountsSnapshot(): Map<String, Int> = HashMap(countsSnapshot)

    fun publishCounts(counts: Map<String, Int>) {
        countsSnapshot.clear()
        countsSnapshot.putAll(counts)
        val snapshot = getCountsSnapshot()
        for (l in listeners) runCatching { l.onCounts(snapshot) }
    }

    fun publishPoints(points: List<HeatPoint>) {
        if (points.isEmpty()) return
        synchronized(pointsBuffer) { pointsBuffer.addAll(points) }
        prune()
        for (l in listeners) runCatching { l.onPoints(points) }
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - ttlMs
        synchronized(pointsBuffer) {
            val it = pointsBuffer.iterator()
            while (it.hasNext()) {
                if (it.next().ts < cutoff) it.remove()
            }
        }
    }
}

class GeohashViewModel(
    application: Application,
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val privateChatManager: PrivateChatManager,
    private val meshDelegateHandler: MeshDelegateHandler,
    private val dataManager: DataManager,
    private val notificationManager: NotificationManager
) : AndroidViewModel(application) {

    companion object { private const val TAG = "GeohashViewModel" }

    private val repo = GeohashRepository(application, state, dataManager)
    private val subscriptionManager = NostrSubscriptionManager(application, viewModelScope)
    private val geohashMessageHandler = GeohashMessageHandler(
        application = application,
        state = state,
        messageManager = messageManager,
        repo = repo,
        scope = viewModelScope,
        dataManager = dataManager
    )
    private val dmHandler = NostrDirectMessageHandler(
        application = application,
        state = state,
        privateChatManager = privateChatManager,
        meshDelegateHandler = meshDelegateHandler,
        scope = viewModelScope,
        repo = repo,
        dataManager = dataManager
    )

    private var currentGeohashSubId: String? = null
    private var currentDmSubId: String? = null
    private var geoTimer: Job? = null
    private var locationChannelManager: com.dogechat.android.geohash.LocationChannelManager? = null

    val geohashPeople: LiveData<List<GeoPerson>> = state.geohashPeople
    val geohashParticipantCounts: LiveData<Map<String, Int>> = state.geohashParticipantCounts
    val selectedLocationChannel: LiveData<com.dogechat.android.geohash.ChannelID?> = state.selectedLocationChannel

    // Bridge observer (publish counts -> labels; counts -> heat points)
    private var countsObserver: Observer<Map<String, Int>>? = null

    fun initialize() {
        subscriptionManager.connect()
        val identity = NostrIdentityBridge.getCurrentNostrIdentity(getApplication())
        if (identity != null) {
            subscriptionManager.subscribeGiftWraps(
                pubkey = identity.publicKeyHex,
                sinceMs = System.currentTimeMillis() - 172800000L,
                id = "chat-messages",
                handler = { event -> dmHandler.onGiftWrap(event, "", identity) }
            )
        }
        try {
            locationChannelManager = com.dogechat.android.geohash.LocationChannelManager.getInstance(getApplication())
            locationChannelManager?.selectedChannel?.observeForever { channel ->
                state.setSelectedLocationChannel(channel)
                switchLocationChannel(channel)
            }
            locationChannelManager?.teleported?.observeForever { teleported ->
                state.setIsTeleported(teleported)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize location channel state: ${e.message}")
            state.setSelectedLocationChannel(com.dogechat.android.geohash.ChannelID.Mesh)
            state.setIsTeleported(false)
        }

        // Reactively publish counts for label totals and convert to heat points (aggregate layer)
        if (countsObserver == null) {
            countsObserver = Observer<Map<String, Int>> { counts ->
                runCatching {
                    // 1) Publish counts for labels "gh (N)"
                    HeatStreamBus.publishCounts(counts)

                    // 2) Convert counts into soft heat at geohash centers (aggregate density)
                    val now = System.currentTimeMillis()
                    val pts = counts.mapNotNull { (gh, c) ->
                        runCatching {
                            val (lat, lon) = Geohash.decodeToCenter(gh)
                            val intensity = (0.2 + ln(1.0 + c) / ln(50.0)).coerceIn(0.2, 1.0)
                            HeatStreamBus.HeatPoint(lat, lon, intensity, now)
                        }.getOrNull()
                    }
                    HeatStreamBus.publishPoints(pts)
                }
            }
            geohashParticipantCounts.observeForever(countsObserver!!)
        }
    }

    override fun onCleared() {
        super.onCleared()
        countsObserver?.let { geohashParticipantCounts.removeObserver(it) }
        countsObserver = null
    }

    fun panicReset() {
        repo.clearAll()
        subscriptionManager.disconnect()
        currentGeohashSubId = null
        currentDmSubId = null
        geoTimer?.cancel()
        geoTimer = null
        runCatching { NostrIdentityBridge.clearAllAssociations(getApplication()) }
        initialize()
    }

    fun sendGeohashMessage(content: String, channel: com.dogechat.android.geohash.GeohashChannel, myPeerID: String, nickname: String?) {
        viewModelScope.launch {
            try {
                val tempId = "temp_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
                val pow = PoWPreferenceManager.getCurrentSettings()
                val localMsg = com.dogechat.android.model.DogechatMessage(
                    id = tempId,
                    sender = nickname ?: myPeerID,
                    content = content,
                    timestamp = Date(),
                    isRelay = false,
                    senderPeerID = "geohash:${channel.geohash}",
                    channel = "#${channel.geohash}",
                    powDifficulty = if (pow.enabled) pow.difficulty else null
                )
                messageManager.addChannelMessage("geo:${channel.geohash}", localMsg)
                val startedMining = pow.enabled && pow.difficulty > 0
                if (startedMining) {
                    com.dogechat.android.ui.PoWMiningTracker.startMiningMessage(tempId)
                }
                try {
                    val identity = NostrIdentityBridge.deriveIdentity(forGeohash = channel.geohash, context = getApplication())
                    val teleported = state.isTeleported.value ?: false
                    val event = NostrProtocol.createEphemeralGeohashEvent(content, channel.geohash, identity, nickname, teleported)
                    val relayManager = NostrRelayManager.getInstance(getApplication())
                    relayManager.sendEventToGeohash(event, channel.geohash, includeDefaults = false, nRelays = 5)
                } finally {
                    if (startedMining) {
                        com.dogechat.android.ui.PoWMiningTracker.stopMiningMessage(tempId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send geohash message: ${e.message}")
            }
        }
    }

    fun beginGeohashSampling(geohashes: List<String>) {
        if (geohashes.isEmpty()) return
        Log.d(TAG, "🌍 Beginning geohash sampling for ${geohashes.size} geohashes")
        viewModelScope.launch {
            geohashes.forEach { gh ->
                subscriptionManager.subscribeGeohash(
                    geohash = gh,
                    sinceMs = System.currentTimeMillis() - 86400000L,
                    limit = 200,
                    id = "sampling-$gh",
                    handler = { event ->
                        // Keep original behavior
                        geohashMessageHandler.onEvent(event, gh)
                        // Also publish a message-based pulse for this event
                        publishPulseForEvent(gh, event)
                    }
                )
            }
        }
    }

    fun endGeohashSampling() { Log.d(TAG, "🌍 Ending geohash sampling") }
    fun geohashParticipantCount(geohash: String): Int = repo.geohashParticipantCount(geohash)
    fun isPersonTeleported(pubkeyHex: String): Boolean = repo.isPersonTeleported(pubkeyHex)

    fun startGeohashDM(pubkeyHex: String, onStartPrivateChat: (String) -> Unit) {
        val convKey = "nostr_${pubkeyHex.take(16)}"
        repo.putNostrKeyMapping(convKey, pubkeyHex)
        val current = state.selectedLocationChannel.value
        val gh = (current as? com.dogechat.android.geohash.ChannelID.Location)?.channel?.geohash
        if (!gh.isNullOrEmpty()) {
            repo.setConversationGeohash(convKey, gh)
            com.dogechat.android.nostr.GeohashConversationRegistry.set(convKey, gh)
        }
        onStartPrivateChat(convKey)
        Log.d(TAG, "🗨️ Started geohash DM with ${pubkeyHex} -> ${convKey} (geohash=${gh})")
    }

    fun getNostrKeyMapping(): Map<String, String> = repo.getNostrKeyMapping()

    fun blockUserInGeohash(targetNickname: String) {
        val pubkey = repo.findPubkeyByNickname(targetNickname)
        if (pubkey != null) {
            dataManager.addGeohashBlockedUser(pubkey)
            repo.refreshGeohashPeople()
            repo.updateReactiveParticipantCounts()
            val sysMsg = com.dogechat.android.model.DogechatMessage(
                sender = "system",
                content = "blocked $targetNickname in geohash channels",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(sysMsg)
        } else {
            val sysMsg = com.dogechat.android.model.DogechatMessage(
                sender = "system",
                content = "user '$targetNickname' not found in current geohash",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(sysMsg)
        }
    }

    fun selectLocationChannel(channel: com.dogechat.android.geohash.ChannelID) {
        locationChannelManager?.select(channel) ?: run { Log.w(TAG, "Cannot select location channel - not initialized") }
    }

    fun displayNameForNostrPubkeyUI(pubkeyHex: String): String = repo.displayNameForNostrPubkeyUI(pubkeyHex)

    fun colorForNostrPubkey(pubkeyHex: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
        val seed = "nostr:${pubkeyHex.lowercase()}"
        return colorForPeerSeed(seed, isDark).copy()
    }

    private fun switchLocationChannel(channel: com.dogechat.android.geohash.ChannelID?) {
        geoTimer?.cancel(); geoTimer = null
        currentGeohashSubId?.let { subscriptionManager.unsubscribe(it); currentGeohashSubId = null }
        currentDmSubId?.let { subscriptionManager.unsubscribe(it); currentDmSubId = null }

        when (channel) {
            is com.dogechat.android.geohash.ChannelID.Mesh -> {
                Log.d(TAG, "📡 Switched to mesh channel")
                repo.setCurrentGeohash(null)
                notificationManager.setCurrentGeohash(null)
                notificationManager.clearMeshMentionNotifications()
                repo.refreshGeohashPeople()
            }
            is com.dogechat.android.geohash.ChannelID.Location -> {
                Log.d(TAG, "📍 Switching to geohash channel: ${channel.channel.geohash}")
                repo.setCurrentGeohash(channel.channel.geohash)
                notificationManager.setCurrentGeohash(channel.channel.geohash)
                notificationManager.clearNotificationsForGeohash(channel.channel.geohash)
                runCatching { messageManager.clearChannelUnreadCount("geo:${channel.channel.geohash}") }

                runCatching {
                    val identity = NostrIdentityBridge.deriveIdentity(channel.channel.geohash, getApplication())
                    repo.updateParticipant(channel.channel.geohash, identity.publicKeyHex, Date())
                    val teleported = state.isTeleported.value ?: false
                    if (teleported) repo.markTeleported(identity.publicKeyHex)
                }.onFailure { Log.w(TAG, "Failed identity setup: ${it.message}") }

                // Load cached messages if channel is bookmarked
                viewModelScope.launch {
                    try {
                        val retentionService = com.dogechat.android.services.MessageRetentionService.getInstance(getApplication())
                        val cachedMessages = retentionService.loadMessagesForChannel(channel.channel.geohash)
                        if (cachedMessages.isNotEmpty()) {
                            Log.d(TAG, "📦 Loaded ${cachedMessages.size} cached messages for ${channel.channel.geohash}")
                            // Add cached messages to channel (they will be deduplicated by MessageManager)
                            cachedMessages.forEach { msg ->
                                messageManager.addChannelMessage("geo:${channel.channel.geohash}", msg)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load cached messages: ${e.message}")
                    }
                }

                startGeoParticipantsTimer()

                viewModelScope.launch {
                    val geohash = channel.channel.geohash
                    val subId = "geohash-$geohash"; currentGeohashSubId = subId
                    subscriptionManager.subscribeGeohash(
                        geohash = geohash,
                        sinceMs = System.currentTimeMillis() - 3600000L,
                        limit = 200,
                        id = subId,
                        handler = { event ->
                            // Original handler
                            geohashMessageHandler.onEvent(event, geohash)
                            // Message-based pulse
                            publishPulseForEvent(geohash, event)
                        }
                    )
                    val dmIdentity = NostrIdentityBridge.deriveIdentity(geohash, getApplication())
                    val dmSubId = "geo-dm-$geohash"; currentDmSubId = dmSubId
                    subscriptionManager.subscribeGiftWraps(
                        pubkey = dmIdentity.publicKeyHex,
                        sinceMs = System.currentTimeMillis() - 172800000L,
                        id = dmSubId,
                        handler = { event -> dmHandler.onGiftWrap(event, geohash, dmIdentity) }
                    )
                    com.dogechat.android.nostr.GeohashAliasRegistry.put("nostr_${dmIdentity.publicKeyHex.take(16)}", dmIdentity.publicKeyHex)
                }
            }
            null -> {
                Log.d(TAG, "📡 No channel selected")
                repo.setCurrentGeohash(null)
                repo.refreshGeohashPeople()
            }
        }
    }

    private fun startGeoParticipantsTimer() {
        geoTimer = viewModelScope.launch {
            while (repo.getCurrentGeohash() != null) {
                delay(30000)
                repo.refreshGeohashPeople()
            }
        }
    }

    // -------- Message-based pulse generation (via reflection) --------

    private fun publishPulseForEvent(geohash: String, event: Any) {
        try {
            val (lat, lon) = Geohash.decodeToCenter(geohash)
            val intensity = intensityFromEvent(event)
            val ts = System.currentTimeMillis()
            val hp = HeatStreamBus.HeatPoint(lat, lon, intensity, ts)
            HeatStreamBus.publishPoints(listOf(hp))
        } catch (e: Exception) {
            Log.d(TAG, "pulse skip: ${e.message}")
        }
    }

    private fun intensityFromEvent(event: Any): Double {
        val nowSec = System.currentTimeMillis() / 1000.0
        val createdSec = readLongReflect(event, "created_at", "createdAt")?.toDouble() ?: nowSec
        val ageSec = max(0.0, nowSec - createdSec)

        // Exponential decay with ~4 min time constant (faster pulses)
        val tau = 240.0
        val decay = exp(-ageSec / tau)

        val contentLen = readStringReflect(event, "content", "getContent")?.length ?: 0
        val sizeBoost = min(1.0, ln(1.0 + contentLen.toDouble()) / ln(400.0)) // 0..1

        // Base + time freshness + content boost
        val base = 0.25
        val intensity = (base + 0.5 * decay + 0.35 * sizeBoost).coerceIn(0.2, 1.0)
        return intensity
    }

    private fun readLongReflect(target: Any, vararg names: String): Long? {
        // Fields
        for (n in names) {
            try {
                val f = target.javaClass.getDeclaredField(n)
                f.isAccessible = true
                val v = f.get(target)
                if (v is Number) return v.toLong()
            } catch (_: Throwable) {}
        }
        // Getters
        for (n in names) {
            val getterNames = arrayOf(n, "get${n.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
            for (gn in getterNames) {
                try {
                    val m = target.javaClass.getMethod(gn)
                    val v = m.invoke(target)
                    if (v is Number) return v.toLong()
                } catch (_: Throwable) {}
            }
        }
        return null
    }

    private fun readStringReflect(target: Any, vararg names: String): String? {
        // Fields
        for (n in names) {
            try {
                val f = target.javaClass.getDeclaredField(n)
                f.isAccessible = true
                val v = f.get(target)
                if (v is String) return v
            } catch (_: Throwable) {}
        }
        // Getters
        for (n in names) {
            val getterNames = arrayOf(n, "get${n.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
            for (gn in getterNames) {
                try {
                    val m = target.javaClass.getMethod(gn)
                    val v = m.invoke(target)
                    if (v is String) return v
                } catch (_: Throwable) {}
            }
        }
        return null
    }
}