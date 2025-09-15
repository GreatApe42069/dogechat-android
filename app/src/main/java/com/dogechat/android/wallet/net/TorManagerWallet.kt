package com.dogechat.android.wallet.net

import android.app.Application
import android.util.Log
import info.guardianproject.arti.ArtiLogListener
import info.guardianproject.arti.ArtiProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

/**
 * A wallet-exclusive Tor manager so we can run chat Tor and wallet Tor independently.
 * - Uses its own Arti instance and SOCKS port range (starts at 9070).
 * - Only runs during SPV start/stop lifecycle.
 * - Exposes a minimal status and current SOCKS address for bitcoinj P2P.
 *
 * NOTE: JVM SOCKS properties are global. We only set/clear them in WalletManager
 * immediately around bitcoinj start/stop to avoid impacting the rest of the app.
 */
object TorManagerWallet {
    private const val TAG = "TorManagerWallet"
    private const val DEFAULT_SOCKS_PORT = 9070
    private const val MAX_BIND_RETRY = 5
    private const val RETRY_DELAY_MS = 2000L

    data class Status(
        val running: Boolean = false,
        val bootstrapPercent: Int = 0,
        val lastLogLine: String = "",
        val socks: InetSocketAddress? = null,
        val error: String? = null
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val artiRef = AtomicReference<ArtiProxy?>(null)
    private var currentPort: Int = DEFAULT_SOCKS_PORT

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    fun isRunning(): Boolean = _status.value.running && _status.value.bootstrapPercent >= 100 && _status.value.socks != null

    fun start(application: Application) {
        if (artiRef.get() != null) return
        scope.launch {
            var attempts = 0
            var lastError: String? = null
            while (attempts <= MAX_BIND_RETRY) {
                try {
                    val logListener = ArtiLogListener { line ->
                        val s = line?.toString() ?: return@ArtiLogListener
                        _status.value = _status.value.copy(lastLogLine = s)
                        if (s.contains("Sufficiently bootstrapped", true)
                            || s.contains("Running", true)) {
                            // Treat as ready when running; we don't get granular % from Arti here.
                            val socks = InetSocketAddress("127.0.0.1", currentPort)
                            _status.value = _status.value.copy(running = true, bootstrapPercent = 100, socks = socks, error = null)
                            Log.i(TAG, "Tor bootstrapped (wallet) at $socks")
                        }
                    }
                    val proxy = ArtiProxy.Builder(application)
                        .setSocksPort(currentPort)
                        .setDnsPort(currentPort + 1) // not used by JVM resolver; kept for completeness
                        .setLogListener(logListener)
                        .build()
                    artiRef.set(proxy)
                    proxy.start()
                    _status.value = _status.value.copy(running = true, bootstrapPercent = 0, error = null)
                    Log.i(TAG, "Tor (wallet) starting on port $currentPort")
                    return@launch
                } catch (t: Throwable) {
                    lastError = t.message
                    Log.w(TAG, "Start failed on $currentPort: ${t.message}")
                    stopInternal()
                    attempts++
                    currentPort++ // try next port
                    delay(RETRY_DELAY_MS)
                }
            }
            _status.value = _status.value.copy(running = false, error = lastError)
        }
    }

    fun stop() {
        scope.launch { stopInternal() }
    }

    private fun stopInternal() {
        try {
            artiRef.getAndSet(null)?.let {
                Log.i(TAG, "Stopping Tor (wallet)…")
                try { it.stop() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        _status.value = Status(running = false, bootstrapPercent = 0, socks = null, error = null)
        currentPort = DEFAULT_SOCKS_PORT
    }

    fun currentSocks(): InetSocketAddress? = _status.value.socks
}