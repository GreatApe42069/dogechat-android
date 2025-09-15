package com.dogechat.android.wallet.net

import android.content.Context
import android.util.Log
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.PeerAddress
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Loads and persists peer addresses for Dogecoin P2P bootstrapping.
 * - Reads from internal storage dogecoin_peers.csv (writable).
 * - Falls back to bundled assets/dogecoin_peers.csv (read-only).
 * - Knows Dogecoin default port, and can optionally resolve DNS seeds (clearnet only).
 */
object PeerDirectory {
    private const val TAG = "PeerDirectory"
    private const val PEERS_FILE = "dogecoin_peers.csv"
    private const val ASSET_FILE = "dogecoin_peers.csv"

    // Dogecoin default mainnet port. Params.port is also used when available.
    private const val DOGE_PORT = 22556

    // Minimal hardcoded IP list for Tor (when DNS is unavailable). Keep IPv4 for SOCKS reliability.
    private val HARDCODED_TOR_IPS = listOf(
        "178.63.77.45:22556",
        "88.99.166.55:22556",
        "65.109.17.126:22556",
        "5.9.118.112:22556",
        "136.243.88.188:22556",
        "167.235.7.44:22556"
    )

    fun peersFile(context: Context): File = File(context.filesDir, PEERS_FILE)

    fun readDiskPeers(context: Context): List<String> {
        val f = peersFile(context)
        if (!f.exists()) return emptyList()
        return runCatching { f.readLines() }.getOrElse { emptyList() }
    }

    fun writeDiskPeers(context: Context, lines: Set<String>) {
        val f = peersFile(context)
        runCatching {
            FileWriter(f, false).use { fw ->
                lines.forEach { fw.write(it.trim() + "\n") }
            }
        }.onSuccess {
            Log.i(TAG, "Wrote ${lines.size} peers to ${f.absolutePath}")
        }.onFailure {
            Log.w(TAG, "Failed writing peers: ${it.message}")
        }
    }

    fun readAssetPeers(context: Context): List<String> {
        return try {
            val am = context.assets
            if (am.list("")?.contains(ASSET_FILE) != true) return emptyList()
            am.open(ASSET_FILE).use { ins ->
                BufferedReader(InputStreamReader(ins)).useLines { seq ->
                    seq.map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("#") }
                        .toList()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed reading asset peers: ${t.message}")
            emptyList()
        }
    }

    fun buildPeerAddresses(
        lines: List<String>,
        params: NetworkParameters,
        preferUnresolvedHostnames: Boolean
    ): List<PeerAddress> {
        val out = mutableListOf<PeerAddress>()
        lines.forEach { raw ->
            val entry = raw.trim()
            if (entry.isEmpty() || entry.startsWith("#")) return@forEach
            val host: String
            val port: Int
            if (entry.contains(":")) {
                val parts = entry.split(":")
                host = parts[0].trim()
                port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: params.port
            } else {
                host = entry
                port = params.port
            }
            try {
                val sock: InetSocketAddress = if (preferUnresolvedHostnames) {
                    InetSocketAddress(host, port)
                } else {
                    val ip = runCatching { InetAddress.getByName(host) }.getOrNull()
                    if (ip != null) InetSocketAddress(ip, port) else InetSocketAddress(host, port)
                }
                out.add(PeerAddress(params, sock))
            } catch (e: Throwable) {
                Log.w(TAG, "Bad peer entry '$entry': ${e.message}")
            }
        }
        return out
    }

    /**
     * Returns a combined list of peers for initial seeding:
     * - disk peers (persisted)
     * - asset peers (fallback)
     * - optional hardcoded Tor peers (used only when torMode = true)
     */
    fun initialPeers(
        context: Context,
        params: NetworkParameters,
        torMode: Boolean,
        preferUnresolved: Boolean
    ): List<PeerAddress> {
        val disk = readDiskPeers(context)
        val assets = readAssetPeers(context)
        val torHardcoded = if (torMode) HARDCODED_TOR_IPS else emptyList()
        val allLines = LinkedHashSet<String>().apply {
            addAll(disk)
            addAll(assets)
            addAll(torHardcoded)
        }.toList()
        return buildPeerAddresses(allLines, params, preferUnresolved)
    }

    /**
     * Best-effort DNS seed resolution (clearnet only). Updates disk peers with newly found IPs.
     */
    fun resolveDnsSeedsAndPersist(
        context: Context,
        dnsSeeds: Array<String>,
        defaultPort: Int = DOGE_PORT
    ) {
        val resolved = LinkedHashSet<String>()
        dnsSeeds.forEach { host ->
            runCatching {
                val ips = InetAddress.getAllByName(host)
                ips.forEach { ip -> resolved.add("${ip.hostAddress}:$defaultPort") }
            }.onFailure { Log.w(TAG, "DNS seed resolve failed for $host: ${it.message}") }
        }
        if (resolved.isEmpty()) return
        val existing = readDiskPeers(context).toMutableSet()
        existing.addAll(resolved)
        writeDiskPeers(context, existing)
    }
}