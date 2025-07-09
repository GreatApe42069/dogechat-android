package com.bitchat.android.mesh

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bitchat.android.protocol.BitchatPacket
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Power-optimized Bluetooth connection manager with comprehensive memory management
 * Integrates with PowerManager for adaptive power consumption
 */
class BluetoothConnectionManager(
    private val context: Context, 
    private val myPeerID: String
) : PowerManagerDelegate {
    
    companion object {
        private const val TAG = "BluetoothConnectionManager"
        // Use exact same UUIDs as iOS version
        private val SERVICE_UUID = UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
        private val CHARACTERISTIC_UUID = UUID.fromString("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
        
        // Connection management constants
        private const val CONNECTION_RETRY_DELAY = 5000L
        private const val MAX_CONNECTION_ATTEMPTS = 3
        private const val CLEANUP_DELAY = 500L
        private const val CLEANUP_INTERVAL = 30000L // 30 seconds
    }
    
    // Core Bluetooth components
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val bleAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser
    
    // Power management
    private val powerManager = PowerManager(context)
    
    // GATT server for peripheral mode
    private var gattServer: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    
    // Simplified connection tracking - reduced memory footprint
    private val connectedDevices = ConcurrentHashMap<String, DeviceConnection>()
    private val subscribedDevices = CopyOnWriteArrayList<BluetoothDevice>()
    
    // Connection attempt tracking with automatic cleanup
    private val pendingConnections = ConcurrentHashMap<String, ConnectionAttempt>()
    
    // Service state
    private var isActive = false
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    
    // Delegate for callbacks
    var delegate: BluetoothConnectionManagerDelegate? = null
    
    // Coroutines
    private val connectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Consolidated device connection information
     */
    private data class DeviceConnection(
        val device: BluetoothDevice,
        val gatt: BluetoothGatt? = null,
        val characteristic: BluetoothGattCharacteristic? = null,
        val rssi: Int = Int.MIN_VALUE,
        val isClient: Boolean = false,
        val connectedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Connection attempt tracking with automatic expiry
     */
    private data class ConnectionAttempt(
        val attempts: Int,
        val lastAttempt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = 
            System.currentTimeMillis() - lastAttempt > CONNECTION_RETRY_DELAY * 2
        
        fun shouldRetry(): Boolean = 
            attempts < MAX_CONNECTION_ATTEMPTS && 
            System.currentTimeMillis() - lastAttempt > CONNECTION_RETRY_DELAY
    }
    
    init {
        powerManager.delegate = this
    }
    
    /**
     * Start all Bluetooth services with power optimization
     */
    fun startServices(): Boolean {
        Log.i(TAG, "Starting power-optimized Bluetooth services...")
        
        if (!hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        if (bleScanner == null || bleAdvertiser == null) {
            Log.e(TAG, "BLE scanner or advertiser not available")
            return false
        }
        
        try {
            isActive = true
            setupGattServer()
            
            // Start power manager and services
            connectionScope.launch {
                powerManager.start()
                delay(500) // Ensure GATT server is ready
                
                startAdvertising()
                delay(200)
                
                if (powerManager.shouldUseDutyCycle()) {
                    Log.i(TAG, "Using power-aware duty cycling")
                } else {
                    startScanning()
                }
                
                startPeriodicCleanup()
                
                Log.i(TAG, "Power-optimized Bluetooth services started successfully")
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Bluetooth services: ${e.message}")
            return false
        }
    }
    
    /**
     * Stop all Bluetooth services with proper cleanup
     */
    fun stopServices() {
        Log.i(TAG, "Stopping power-optimized Bluetooth services")
        
        isActive = false
        
        connectionScope.launch {
            // Stop power manager first
            powerManager.stop()
            
            // Stop scanning and advertising  
            stopScanning()
            stopAdvertising()
            
            // Cleanup all GATT connections with delay
            cleanupAllConnections()
            
            // Close GATT server
            gattServer?.close()
            gattServer = null
            
            // Clear tracking
            clearAllConnections()
            
            connectionScope.cancel()
            
            Log.i(TAG, "All Bluetooth services stopped")
        }
    }
    
    /**
     * Set app background state for power optimization
     */
    fun setAppBackgroundState(inBackground: Boolean) {
        powerManager.setAppBackgroundState(inBackground)
    }
    
    /**
     * Broadcast packet to connected devices with connection limit enforcement
     */
    fun broadcastPacket(packet: BitchatPacket) {
        if (!isActive) return
        
        val data = packet.toBinaryData() ?: return
        
        Log.d(TAG, "Broadcasting packet type ${packet.type} to ${subscribedDevices.size} server + ${connectedDevices.size} client connections")
        
        // Send to server connections (devices connected to our GATT server)
        subscribedDevices.forEach { device ->
            try {
                characteristic?.let { char ->
                    char.value = data
                    gattServer?.notifyCharacteristicChanged(device, char, false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error sending to server connection ${device.address}: ${e.message}")
                // Clean up failed connection
                connectionScope.launch {
                    delay(CLEANUP_DELAY)
                    subscribedDevices.remove(device)
                }
            }
        }
        
        // Send to client connections
        connectedDevices.values.forEach { deviceConn ->
            if (deviceConn.isClient && deviceConn.gatt != null && deviceConn.characteristic != null) {
                try {
                    deviceConn.characteristic.value = data
                    deviceConn.gatt.writeCharacteristic(deviceConn.characteristic)
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending to client connection ${deviceConn.device.address}: ${e.message}")
                    // Clean up failed connection
                    connectionScope.launch {
                        delay(CLEANUP_DELAY)
                        cleanupDeviceConnection(deviceConn.device.address)
                    }
                }
            }
        }
    }
    
    /**
     * Get connected device count
     */
    fun getConnectedDeviceCount(): Int = connectedDevices.size
    
    /**
     * Get debug information including power management
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Power-Optimized Bluetooth Connection Manager ===")
            appendLine("Active: $isActive")
            appendLine("Bluetooth Enabled: ${bluetoothAdapter?.isEnabled}")
            appendLine("Has Permissions: ${hasBluetoothPermissions()}")
            appendLine("GATT Server Active: ${gattServer != null}")
            appendLine()
            appendLine(powerManager.getPowerInfo())
            appendLine()
            appendLine("Connected Devices: ${connectedDevices.size} / ${powerManager.getMaxConnections()}")
            connectedDevices.forEach { (address, deviceConn) ->
                val age = (System.currentTimeMillis() - deviceConn.connectedAt) / 1000
                appendLine("  - $address (${if (deviceConn.isClient) "client" else "server"}, ${age}s, RSSI: ${deviceConn.rssi})")
            }
            appendLine()
            appendLine("Subscribed Devices (server mode): ${subscribedDevices.size}")
            appendLine()
            appendLine("Pending Connections: ${pendingConnections.size}")
            val now = System.currentTimeMillis()
            pendingConnections.forEach { (address, attempt) ->
                val elapsed = (now - attempt.lastAttempt) / 1000
                appendLine("  - $address: ${attempt.attempts} attempts, last ${elapsed}s ago")
            }
        }
    }
    
    // MARK: - PowerManagerDelegate Implementation
    
    override fun onPowerModeChanged(newMode: PowerManager.PowerMode) {
        Log.i(TAG, "Power mode changed to: $newMode")
        
        connectionScope.launch {
            // Update advertising and scanning based on new power mode
            stopAdvertising()
            delay(100)
            startAdvertising()
            
            // Restart scanning with new settings if not using duty cycle
            if (!powerManager.shouldUseDutyCycle()) {
                stopScanning()
                delay(100)
                startScanning()
            }
            
            // Enforce connection limits
            enforceConnectionLimits()
        }
    }
    
    override fun onScanStateChanged(shouldScan: Boolean) {
        if (shouldScan) {
            startScanning()
        } else {
            stopScanning()
        }
    }
    
    // MARK: - Private Implementation
    
    private fun hasBluetoothPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
        
        permissions.addAll(listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
        
        return permissions.all { 
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }
    }
    
    @Suppress("DEPRECATION")
    private fun setupGattServer() {
        if (!hasBluetoothPermissions()) return
        
        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Server: Device connected ${device.address}")
                        val deviceConn = DeviceConnection(
                            device = device,
                            isClient = false
                        )
                        connectedDevices[device.address] = deviceConn
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Server: Device disconnected ${device.address}")
                        cleanupDeviceConnection(device.address)
                    }
                }
            }
            
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (characteristic.uuid == CHARACTERISTIC_UUID) {
                    val packet = BitchatPacket.fromBinaryData(value)
                    if (packet != null) {
                        val peerID = String(packet.senderID).replace("\u0000", "")
                        delegate?.onPacketReceived(packet, peerID, device)
                    }
                    
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            }
            
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.contentEquals(value)) {
                    Log.d(TAG, "Device ${device.address} subscribed to notifications")
                    subscribedDevices.add(device)
                    
                    connectionScope.launch {
                        delay(100)
                        delegate?.onDeviceConnected(device)
                    }
                }
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
        
        // Clean up existing server
        gattServer?.close()
        
        gattServer = bluetoothManager.openGattServer(context, serverCallback)
        
        // Create characteristic with notification support
        characteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE or 
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or 
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        
        val descriptor = BluetoothGattDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        characteristic?.addDescriptor(descriptor)
        
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(characteristic)
        
        gattServer?.addService(service)
        
        Log.i(TAG, "GATT server setup complete")
    }
    
    @Suppress("DEPRECATION")
    private fun startAdvertising() {
        if (!hasBluetoothPermissions() || bleAdvertiser == null || !isActive) return
        
        val settings = powerManager.getAdvertiseSettings()
        
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .setIncludeTxPowerLevel(false)
            .setIncludeDeviceName(false)
            .build()
        
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(TAG, "Advertising started (power mode: ${powerManager.getPowerInfo().split("Current Mode: ")[1].split("\n")[0]})")
            }
            
            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
            }
        }
        
        try {
            bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting advertising: ${e.message}")
        }
    }
    
    @Suppress("DEPRECATION")
    private fun stopAdvertising() {
        if (!hasBluetoothPermissions() || bleAdvertiser == null) return
        try {
            advertiseCallback?.let { bleAdvertiser.stopAdvertising(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        }
    }
    
    @Suppress("DEPRECATION")
    private fun startScanning() {
        if (!hasBluetoothPermissions() || bleScanner == null || !isActive) return
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
            }
        }
        
        try {
            bleScanner.startScan(listOf(scanFilter), powerManager.getScanSettings(), scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting scan: ${e.message}")
        }
    }
    
    @Suppress("DEPRECATION")
    private fun stopScanning() {
        if (!hasBluetoothPermissions() || bleScanner == null) return
        try {
            scanCallback?.let { bleScanner.stopScan(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan: ${e.message}")
        }
    }
    
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val deviceAddress = device.address
        
        // Power-aware RSSI filtering
        if (rssi < powerManager.getRSSIThreshold()) {
            return
        }
        
        // Check if already connected or at connection limit
        if (connectedDevices.containsKey(deviceAddress)) {
            return
        }
        
        if (connectedDevices.size >= powerManager.getMaxConnections()) {
            Log.d(TAG, "Connection limit reached (${powerManager.getMaxConnections()})")
            return
        }
        
        // Check connection attempts
        val currentTime = System.currentTimeMillis()
        val existingAttempt = pendingConnections[deviceAddress]
        
        if (existingAttempt != null) {
            if (existingAttempt.isExpired()) {
                pendingConnections.remove(deviceAddress)
            } else if (!existingAttempt.shouldRetry()) {
                return
            }
        }
        
        // Update connection attempt
        val attempts = (existingAttempt?.attempts ?: 0) + 1
        pendingConnections[deviceAddress] = ConnectionAttempt(attempts)
        
        Log.i(TAG, "Connecting to $deviceAddress (RSSI: $rssi, attempt: $attempts)")
        
        connectToDevice(device, rssi)
    }
    
    @Suppress("DEPRECATION")
    private fun connectToDevice(device: BluetoothDevice, rssi: Int) {
        if (!hasBluetoothPermissions()) return
        
        val deviceAddress = device.address
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(TAG, "Client: Connected to $deviceAddress")
                            val deviceConn = DeviceConnection(
                                device = device,
                                gatt = gatt,
                                rssi = rssi,
                                isClient = true
                            )
                            connectedDevices[deviceAddress] = deviceConn
                            pendingConnections.remove(deviceAddress)
                            
                            connectionScope.launch {
                                delay(200)
                                gatt.discoverServices()
                            }
                        } else {
                            gatt.disconnect()
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Client: Disconnected from $deviceAddress")
                        cleanupDeviceConnection(deviceAddress)
                        
                        connectionScope.launch {
                            delay(CLEANUP_DELAY)
                            try {
                                gatt.close()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error closing GATT: ${e.message}")
                            }
                        }
                    }
                }
            }
            
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                    
                    if (characteristic != null) {
                        // Update device connection with characteristic
                        connectedDevices[deviceAddress]?.let { deviceConn ->
                            val updatedConn = deviceConn.copy(characteristic = characteristic)
                            connectedDevices[deviceAddress] = updatedConn
                        }
                        
                        // Enable notifications
                        gatt.setCharacteristicNotification(characteristic, true)
                        
                        val descriptor = characteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        )
                        descriptor?.let {
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(it)
                        }
                        
                        connectionScope.launch {
                            delay(100)
                            delegate?.onDeviceConnected(device)
                        }
                    } else {
                        gatt.disconnect()
                    }
                } else {
                    gatt.disconnect()
                }
            }
            
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value
                val packet = BitchatPacket.fromBinaryData(value)
                if (packet != null) {
                    val peerID = String(packet.senderID).replace("\u0000", "")
                    delegate?.onPacketReceived(packet, peerID, gatt.device)
                }
            }
        }
        
        try {
            device.connectGatt(context, false, gattCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to $deviceAddress: ${e.message}")
            pendingConnections.remove(deviceAddress)
        }
    }
    
    private fun startPeriodicCleanup() {
        connectionScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL)
                
                if (!isActive) break
                
                try {
                    // Clean up expired pending connections
                    val expiredConnections = pendingConnections.filter { it.value.isExpired() }
                    expiredConnections.keys.forEach { pendingConnections.remove(it) }
                    
                    // Log cleanup if any
                    if (expiredConnections.isNotEmpty()) {
                        Log.d(TAG, "Cleaned up ${expiredConnections.size} expired connection attempts")
                    }
                    
                    // Log current state
                    Log.d(TAG, "Periodic cleanup: ${connectedDevices.size} connections, ${pendingConnections.size} pending")
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error in periodic cleanup: ${e.message}")
                }
            }
        }
    }
    
    private fun enforceConnectionLimits() {
        val maxConnections = powerManager.getMaxConnections()
        if (connectedDevices.size > maxConnections) {
            Log.i(TAG, "Enforcing connection limit: ${connectedDevices.size} > $maxConnections")
            
            // Disconnect oldest client connections first
            val sortedConnections = connectedDevices.values
                .filter { it.isClient }
                .sortedBy { it.connectedAt }
            
            val toDisconnect = sortedConnections.take(connectedDevices.size - maxConnections)
            toDisconnect.forEach { deviceConn ->
                Log.d(TAG, "Disconnecting ${deviceConn.device.address} due to connection limit")
                deviceConn.gatt?.disconnect()
            }
        }
    }
    
    private fun cleanupDeviceConnection(deviceAddress: String) {
        connectedDevices.remove(deviceAddress)?.let { deviceConn ->
            subscribedDevices.removeAll { it.address == deviceAddress }
        }
        pendingConnections.remove(deviceAddress)
    }
    
    private fun cleanupAllConnections() {
        connectedDevices.values.forEach { deviceConn ->
            deviceConn.gatt?.disconnect()
        }
        
        connectionScope.launch {
            delay(CLEANUP_DELAY)
            
            connectedDevices.values.forEach { deviceConn ->
                try {
                    deviceConn.gatt?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing GATT during cleanup: ${e.message}")
                }
            }
        }
    }
    
    private fun clearAllConnections() {
        connectedDevices.clear()
        subscribedDevices.clear()
        pendingConnections.clear()
    }
}

/**
 * Delegate interface for Bluetooth connection manager callbacks
 */
interface BluetoothConnectionManagerDelegate {
    fun onPacketReceived(packet: BitchatPacket, peerID: String, device: BluetoothDevice?)
    fun onDeviceConnected(device: BluetoothDevice)
}
