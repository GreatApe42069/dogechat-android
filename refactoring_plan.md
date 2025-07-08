# BluetoothMeshService Refactoring Plan

## Current State
- Single file: `BluetoothMeshService.kt` (~1000+ lines)
- Multiple responsibilities mixed together
- Hard to test and maintain

## Proposed Structure

### 1. Core Service (BluetoothMeshService.kt)
**Responsibilities:**
- Service lifecycle management
- Coordination between components
- Public API for sending messages
- Delegate management

### 2. Connection Management (BluetoothConnectionManager.kt)
**Responsibilities:**
- BLE scanning and advertising
- GATT server/client setup and management
- Device connection tracking
- Peer discovery and RSSI tracking

### 3. Packet Processing (PacketProcessor.kt)
**Responsibilities:**
- Incoming packet handling
- Message type routing
- TTL and duplicate detection
- Timestamp validation

### 4. Message Handler (MessageHandler.kt)
**Responsibilities:**
- Processing specific message types (ANNOUNCE, MESSAGE, LEAVE, etc.)
- Message parsing and validation
- Relay logic

### 5. Fragment Manager (FragmentManager.kt)
**Responsibilities:**
- Message fragmentation for large messages
- Fragment reassembly
- Fragment cleanup and timeouts

### 6. Store-and-Forward Manager (StoreForwardManager.kt)
**Responsibilities:**
- Message caching for offline peers
- Delivering cached messages when peers come online
- Cache cleanup and management

### 7. Peer Manager (PeerManager.kt)
**Responsibilities:**
- Active peer tracking
- Peer nickname management
- Stale peer cleanup
- Peer list updates

### 8. Security Manager (SecurityManager.kt)
**Responsibilities:**
- Key exchange handling
- Message signing and verification
- Duplicate detection tracking

## Refactoring Strategy
1. Extract each component while maintaining exact functionality
2. Use dependency injection for component communication
3. Ensure all existing tests pass
4. Maintain the same public API

## Benefits
- Easier to test individual components
- Better separation of concerns
- More maintainable code
- Easier to add new features
- Better code reuse
