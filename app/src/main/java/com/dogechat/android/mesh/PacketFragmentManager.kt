package com.dogechat.android.mesh

import android.util.Log
import com.dogechat.android.model.FragmentPayload
import com.dogechat.android.protocol.DogechatPacket
import com.dogechat.android.protocol.MessagePadding
import com.dogechat.android.protocol.MessageType
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Renamed from FragmentManager → FragmentManager to avoid collision with
 * androidx.fragment.app.FragmentManager. Provides identical functionality.
 *
 * iOS-compatible fragmentation and reassembly.
 */
class FragmentManager {

    companion object {
        private const val TAG = "FragmentManager"
        private const val FRAGMENT_SIZE_THRESHOLD = 512
        private const val MAX_FRAGMENT_SIZE = 469
        private const val FRAGMENT_TIMEOUT = 30_000L
        private const val CLEANUP_INTERVAL = 10_000L
    }

    private val incomingFragments = ConcurrentHashMap<String, MutableMap<Int, ByteArray>>()
    private val fragmentMetadata = ConcurrentHashMap<String, Triple<UByte, Int, Long>>()

    var delegate: FragmentManagerDelegate? = null

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startPeriodicCleanup()
    }

    fun createFragments(packet: DogechatPacket): List<DogechatPacket> {
        val encoded = packet.toBinaryData() ?: return emptyList()
        val fullData = MessagePadding.unpad(encoded)

        if (fullData.size <= FRAGMENT_SIZE_THRESHOLD ||
            packet.type == MessageType.fragment.rawValue
        ) {
            return listOf(packet)
        }

        val fragments = mutableListOf<DogechatPacket>()
        val fragmentID = FragmentPayload.generateFragmentID()

        val fragmentChunks = stride(0, fullData.size, MAX_FRAGMENT_SIZE) { offset ->
            val endOffset = minOf(offset + MAX_FRAGMENT_SIZE, fullData.size)
            fullData.sliceArray(offset until endOffset)
        }

        Log.d(TAG, "Creating ${fragmentChunks.size} fragments for ${fullData.size} bytes")

        fragmentChunks.forEachIndexed { index, fragmentData ->
            val fragmentPayload = FragmentPayload(
                fragmentID = fragmentID,
                fragmentIndex = index,
                totalFragments = fragmentChunks.size,
                originalType = packet.type.toUByte(),
                data = fragmentData
            )
            val fragmentPacket = DogechatPacket(
                type = MessageType.fragment.rawValue,
                sender = packet.sender,
                payload = fragmentPayload.encode()
            )
            fragments += fragmentPacket
        }

        return fragments
    }

    fun handleIncomingFragment(packet: DogechatPacket) {
        val payload = FragmentPayload.decode(packet.payload) ?: return
        val key = payload.fragmentIDHex()

        val now = System.currentTimeMillis()
        fragmentMetadata.putIfAbsent(
            key,
            Triple(payload.originalType, payload.totalFragments, now)
        )
        val map = incomingFragments.getOrPut(key) { ConcurrentHashMap<Int, ByteArray>().toMutableMap() }
        map[payload.fragmentIndex] = payload.data

        if (map.size == payload.totalFragments) {
            // Reassemble
            val ordered = (0 until payload.totalFragments).mapNotNull { map[it] }
            if (ordered.size == payload.totalFragments) {
                val reassembled = ordered.reduce { acc, bytes -> acc + bytes }
                val originalPacket = DogechatPacket(
                    type = payload.originalType.toInt(),
                    sender = packet.sender,
                    payload = reassembled
                )
                incomingFragments.remove(key)
                fragmentMetadata.remove(key)
                delegate?.onReassembledPacket(originalPacket)
            }
        }
    }

    private fun startPeriodicCleanup() {
        managerScope.launch {
            while (isActive) {
                try {
                    delay(CLEANUP_INTERVAL)
                    val cutoff = System.currentTimeMillis() - FRAGMENT_TIMEOUT
                    val expired = fragmentMetadata.filter { (_, meta) ->
                        meta.third < cutoff
                    }.keys
                    if (expired.isNotEmpty()) {
                        expired.forEach {
                            incomingFragments.remove(it)
                            fragmentMetadata.remove(it)
                        }
                        Log.d(TAG, "Cleaned up expired fragments: ${expired.size}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cleanup error: ${e.message}")
                }
            }
        }
    }
}

/**
 * Backwards compatibility alias so existing references to FragmentManager
 * still compile until all call sites are migrated.
 */
typealias FragmentManager = FragmentManager