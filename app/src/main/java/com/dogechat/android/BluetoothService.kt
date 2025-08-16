package com.dogechat.android

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bitcoinj.core.Transaction
import org.libdohj.params.DogecoinMainNetParams
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BluetoothService(
    private val socket: BluetoothSocket,
    private val onTransactionReceived: (Transaction) -> Unit
) : Thread() {

    companion object {
        private const val TAG = "BluetoothService"
        // Defensive maximum size for single transaction payload (adjust as needed)
        private const val MAX_TX_SIZE = 64 * 1024 // 64 KB
    }

    private val mmInStream: InputStream = socket.inputStream
    private val mmOutStream: OutputStream = socket.outputStream
    private val networkParameters = DogecoinMainNetParams.get()

    // single scope to post results to main dispatcher; cancel on close()
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun run() {
        val buffer = ByteArray(4096)

        try {
            while (!isInterrupted && socket.isConnected) {
                val bytesRead = try {
                    mmInStream.read(buffer)
                } catch (io: IOException) {
                    Log.w(TAG, "IO error while reading from input stream: ${io.message}")
                    -1
                }

                // -1 means end of stream / closed
                if (bytesRead == -1) {
                    Log.d(TAG, "Input stream closed (EOF). Exiting read loop.")
                    break
                }

                if (bytesRead > 0) {
                    // safety: reject absurdly large single-read payloads
                    if (bytesRead > MAX_TX_SIZE) {
                        Log.w(TAG, "Received payload too large ($bytesRead bytes), skipping.")
                        continue
                    }

                    val payload = buffer.copyOf(bytesRead)

                    // parse transaction defensively
                    try {
                        val tx = Transaction(networkParameters, payload)
                        mainScope.launch {
                            try {
                                onTransactionReceived(tx)
                            } catch (e: Exception) {
                                Log.e(TAG, "Exception in onTransactionReceived handler: ${e.message}", e)
                            }
                        }
                    } catch (e: Exception) {
                        // bitcoinj can throw various parsing exceptions (ProtocolException etc.)
                        Log.w(TAG, "Failed to parse transaction from payload (${bytesRead} bytes): ${e.message}")
                        // we continue reading; don't break the service for a bad payload
                    }
                }
            }
        } finally {
            // Ensure resources are cleaned up
            shutdown()
        }
    }

    /**
     * Thread-safe write (synchronized) and flush.
     */
    fun write(bytes: ByteArray) {
        try {
            synchronized(mmOutStream) {
                mmOutStream.write(bytes)
                mmOutStream.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to Bluetooth output stream: ${e.message}", e)
        }
    }

    /**
     * Gracefully stop the thread and close streams/socket.
     * Call this when you want to end the service from outside.
     */
    fun shutdown() {
        try {
            // Cancel any posted coroutines first
            try {
                mainScope.cancel()
            } catch (ignored: Exception) { }

            try {
                mmInStream.close()
            } catch (ignored: Exception) { }

            try {
                mmOutStream.close()
            } catch (ignored: Exception) { }

            try {
                socket.close()
            } catch (ignored: Exception) { }

            // interrupt the thread if it's still running
            try {
                if (!isInterrupted) interrupt()
            } catch (ignored: Exception) { }

        } catch (e: Exception) {
            Log.w(TAG, "Error while shutting down BluetoothService: ${e.message}", e)
        }
    }
}
