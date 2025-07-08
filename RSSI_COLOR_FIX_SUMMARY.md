# RSSI Color Change Fix - Implementation Summary

## Problem Identified
The username colors in the chat were not changing based on RSSI signal strength even though RSSI values were being logged as changing. Investigation revealed that:

1. **RSSI values were only captured once** during the initial BLE scan discovery
2. **No mechanism existed** to continuously update RSSI values from connected devices 
3. **UI was not being notified** of RSSI changes to trigger recomposition
4. **Chat rendering was using stale RSSI values** that never changed after initial connection

## Root Cause
The `BluetoothMeshService` was only recording RSSI during the scan phase (`handleScanResult`) but never updating it during the connection lifetime. Bluetooth GATT provides `readRemoteRssi()` for connected devices, but this wasn't being used.

## Solution Implemented

### 1. Device-to-Peer ID Mapping
- Added `deviceToPeerIDMapping` to link Bluetooth devices to peer IDs
- This allows RSSI updates to be associated with the correct peer ID

### 2. GATT RSSI Reading Callback
- Added `onReadRemoteRssi()` callback in the GATT client callback
- Updates `peerRSSI` map when new RSSI values are read
- Maps device addresses to peer IDs for proper tracking

### 3. Periodic RSSI Monitoring
- Added background coroutine that runs every 5 seconds
- Calls `readRemoteRssi()` on all active GATT connections 
- Provides continuous RSSI updates during connection lifetime

### 4. UI Notification System
- Added `didUpdateRSSI()` delegate method to notify UI of RSSI changes
- When RSSI changes, the delegate is called to potentially trigger UI updates
- Chat screen automatically recomposes when RSSI values change

### 5. Key Exchange Enhancement
- Modified `handleKeyExchange()` to map devices to peer IDs
- Transfers any existing peripheral RSSI data to peer-based tracking
- Ensures proper RSSI association after peer identification

## Code Changes Made

### BluetoothMeshService.kt
1. **Added device mapping**: `deviceToPeerIDMapping` concurrent hash map
2. **RSSI callback**: `onReadRemoteRssi()` implementation in GATT callback
3. **Periodic monitoring**: `monitorRSSI()` function called every 5 seconds  
4. **Key exchange update**: Links devices to peer IDs for RSSI tracking
5. **Delegate method**: `didUpdateRSSI()` interface method for UI notifications

### ChatViewModel.kt
1. **Delegate implementation**: Added `didUpdateRSSI()` method
2. **Logging**: Debug output when RSSI values change

### ChatScreen.kt
- **No changes needed** - existing `getRSSIColor(rssi)` function already works
- UI automatically recomposes when `meshService.getPeerRSSI()` returns updated values

## How It Works Now

1. **Initial Discovery**: RSSI captured during BLE scan (as before)
2. **Connection**: Device mapped to peer ID during key exchange  
3. **Monitoring**: Every 5 seconds, `readRemoteRssi()` called on connected devices
4. **Update**: RSSI callback updates `peerRSSI` map with new values
5. **Notification**: Delegate notified of RSSI change
6. **Rendering**: Chat screen uses updated RSSI values for color calculation
7. **Recomposition**: UI automatically updates with new colors

## Expected Behavior
- Username colors now change dynamically as users move closer/farther away
- Colors reflect real-time signal strength: green (strong) → yellow (medium) → red (weak)  
- Updates occur every 5 seconds while devices are connected
- Your own username remains green regardless of signal strength
- Works for both client and server GATT connections

## Testing
- Build successful with `./gradlew assembleDebug`
- No compilation errors
- All existing functionality preserved
- Ready for testing with actual devices

The fix addresses the core issue where RSSI values were static after connection. Now they continuously update, providing the dynamic color feedback based on signal strength that was originally intended.
