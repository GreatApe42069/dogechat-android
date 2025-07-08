# ChatScreen.kt Refactoring Plan

## Current State
- Single file: `ChatScreen.kt` (~1,100+ lines)
- Multiple UI responsibilities mixed together
- Hard to maintain and test individual components

## Proposed Component Structure

### 1. Main Screen (ChatScreen.kt)
**Responsibilities:**
- Main layout orchestration
- State management delegation
- Window insets handling
- Component coordination

### 2. Header Components (ChatHeader.kt)
**Responsibilities:**
- TopAppBar with different states (main, private, channel)
- Nickname editor
- Peer counter with status indicators
- Navigation controls

### 3. Message Components (MessageComponents.kt)
**Responsibilities:**
- MessagesList composable
- MessageItem with formatting
- Message text parsing and styling
- Delivery status indicators
- RSSI-based coloring

### 4. Input Components (InputComponents.kt)
**Responsibilities:**
- MessageInput with different modes
- Command suggestions box
- Command suggestion items
- Input validation and handling

### 5. Sidebar Components (SidebarComponents.kt)
**Responsibilities:**
- SidebarOverlay with navigation
- ChannelsSection for channel management
- PeopleSection for peer list
- Sidebar state management

### 6. Dialog Components (DialogComponents.kt)
**Responsibilities:**
- Password prompt dialog
- App info dialog
- Other modal dialogs

### 7. UI Utils (ChatUIUtils.kt)
**Responsibilities:**
- RSSI color mapping
- Text formatting utilities
- Common styling constants
- Helper functions

## Benefits
- Each file has a single, clear responsibility
- Components are easier to test in isolation
- Better code organization and navigation
- Simplified debugging
- Easier to add new UI features
