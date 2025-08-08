import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.util.Log
import org.dogecoin.bitcore.transaction.Transaction
import org.dogecoin.bitcore.script.Script
import org.dogecoin.bitcore.crypto.Transaction.Signature

class BluetoothService(private val handler: Handler) {
    private var connectedThread: ConnectedThread? = null

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream
        private val mmOutStream: OutputStream

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                Log.e(TAG, "Temp sockets not created", e)
            }

            mmInStream = tmpIn!!
            mmOutStream = tmpOut!!
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            while (true) {
                try {
                    bytes = mmInStream.read(buffer)
                    val receivedTx = Transaction.fromBytes(buffer, 0, bytes)
                    if (receivedTx.verify()) {
                        val msg = handler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                        msg.sendToTarget()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Disconnected", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Close of connect socket failed", e)
            }
        }
    }

    fun start(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    fun write(out: ByteArray) {
        connectedThread?.write(out)
    }

    fun cancel() {
        connectedThread?.cancel()
    }

    companion object {
        private const val TAG = "BluetoothService"
    }
}
