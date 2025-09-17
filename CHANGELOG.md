# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.9.6] - 2025-09-17
This release introduces initial Dogecoin wallet support and syncs with upstream changes through permissionlesstech/bitchat-android commit 1178fc254a81e660fde509dbc9130f4c4940be68.

### Added
- Dogecoin wallet (alpha)
  - Wallet screen and navigation from ChatHeader.
  - DNS seed peer discovery for Dogecoin; connects to peers without Tor.
  - Initial transaction fetch logic and detailed SPV logs for troubleshooting.
  - WIF import support and private key display/backup action.
  - Triple-tap emergency wallet data wipe from header for quick reset.
  - About screen controls for starting the SPV node (exposed entry point for wallet/SPV controls).
- Tor separation for wallet
  - Early support to start wallet/SPV connectivity separately from main app Tor controls; added extensive logging to aid validation.

### Changed
- UI/Theming
  - Wrapped wallet UI in Doge theme and refined wallet colors for visual consistency.
  - Minor UI tweaks to resolve button clashes and improve layout on the Wallet screen.
- About sheet
  - Cleaned up duplicated network section and updated copy to reflect wallet/SPV sections and controls.
- Dependencies
  - Updated Dogecoin/Bitcoin libraries (dogecoinj/bitcoinj jars) during development.
  - Switched back to libdohj snapshot 16 to stabilize builds due to upstream dogecoinj repo build gaps.
- Codebase hygiene
  - Removed unnecessary/obsolete files and simplified routing to reduce duplication.

### Fixed
- Wallet
  - Reliability improvements for wallet wipe and WIF import flows.
  - Resolved wallet UI button overlap and click handling issues.
- App UI
  - Fixed chat text display regressions encountered during wallet integration.
  - Corrected AboutSheet navigation from header and resolved duplicate network section.
- Networking
  - Fixed DNS seed connection flow for Dogecoin when Tor is disabled; peers now connect via DNS seeds as expected.

### Upstream sync
- Merged upstream through permissionlesstech/bitchat-android commit `1178fc254a81e660fde509dbc9130f4c4940be68`:
  - “remove the noise handshake if peer goes offline” (#435)
- Previous upstream Tor-related improvements listed in 0.9.5 were already incorporated.

### Known limitations
- Tor-backed SPV for the wallet is experimental. Earlier iterations noted the SPV node not starting via Tor; this release separates wallet Tor management and adds detailed logs, but further validation across devices/networks is recommended.

### Notes for testers
- This is an alpha wallet integration. Back up any keys you import or generate. Use test funds first.
- Try both connectivity modes:
  - Without Tor: confirm DNS seed discovery and peer connections.
  - With wallet Tor separation: verify SPV startup and sync behavior.
- Validate WIF import/export, private key backup action, and emergency wipe gesture.

## [0.9.5] - 2025-09-04
### Updated

# Commit 686e2e7    (Done)
## 12 Files Changed
- app/build.gradle.kts  (DONE)
- app/src/main/java/com/dogechat/android/DogechatApplication.kt   (DONE)
- app/src/main/java/com/dogechat/android/net/OkHttpProvider.kt   (DONE)
- app/src/main/java/com/dogechat/android/net/TorManager.kt    (DONE)
- app/src/main/java/com/dogechat/android/net/TorMode.kt     (DONE)
- app/src/main/java/com/dogechat/android/net/TorPreferenceManager.kt    (DONE)
- app/src/main/java/com/dogechat/android/nostr/NostrRelayManager.kt    (DONE)
- app/src/main/java/com/dogechat/android/nostr/RelayDirectory.kt     (DONE)
- app/src/main/java/com/dogechat/android/ui/AboutSheet.kt      (DONE)
- app/src/main/java/com/dogechat/android/ui/ChatHeader.kt     (DONE)
- gradle/libs.versions.toml   (DONE)
- settings.gradle.kts    (DONE)
### Details
* Bundle tor

* tor started

* tor works

* tor code

* improve manager

* works

* move tor icon

* werks

* arti works

* arti works

* arti works with reconnect

* delay fix

* refactor



# Commit 4c7786a  (DONE)
## 1 file changed
- app/proguard-rules.pro (DONE)
### Details
* proguard arti


# Commit 3248f37   (DONE)
## 3 Files Changed
- PRIVACY_POLICY.md   (DONE)
- README.md     (DONE)
- android/onboarding/PermissionExplanationScreen.kt   (DONE)
### Details 
* Update Info in Readme, Privacy Policy, & Permisions screen


# Commit 85ddf3c   (Done)
## 1 file changed
- app/src/main/java/com/dogechat/android/net/TorManager.kt    (DONE)
### details 
* retry on bind error


# Commit b1234ff    (Done)
## 3 Files Changed 
- app/src/main/java/com/dogechat/android/net/TorManager.kt    (DONE)
- app/src/main/java/com/dogechat/android/net/TorMode.kt     (DONE)
- app/src/main/java/com/dogechat/android/ui/AboutSheet.kt    (DONE)
### Details
* Simplify


# Commit 0aecaf5   (DONE)
## 3 Files Changed
- app/src/main/java/com/dogechchat/android/geohash/LocationChannelManager.kt (DONE)
- app/src/main/java/com/dogechat/android/ui/DataManager.kt     (DONE)
- app/src/main/java/com/dogechat/android/ui/LocationChannelsSheet.kt    (DONE)
### Details
* manually disable location, design needs to be fixed
* fix ui


# Commit 0b44b85    (DONE)
## 1 file changed
- app/src/main/java/com/dogechat/android/ui/DataManager.kt    (DONE)
### Details
* restore default


# Commit cedc655      (DONE)
## 2 files changed
- app/src/main/java/com/dogechat/android/onboarding/OnboardingCoordinator.kt (DONE)
- app/src/main/java/com/dogechat/android/onboarding/PermissionManager.kt   (DONE)
### Details
* make notifications optional


# Commit 33b5814 (DONE)
## 2 Files changed
- app/src/main/java/com/dogechat/android/mesh/BluetoothMeshService.kt    (DONE)
- app/src/main/java/com/dogechat/android/mesh/SecurityManager.kt    (DONE)
### Details
* Sign mesh message
* sign BLE packets
* fix x


# Commit 202c8ed  (DONE)
## 1 File changed
- app/build.gradle.kts
### Details
* bump version


# Commit 6b77eb9 (DONE)
## 1 File changed 
- app/src/main/java/com/dogechat/android/ui/ChatHeader.kt   (DONE)
### Details
* move tor icon

## [0.9.4] - 2025-08-30
### Updated

# Commit 846cd97    (DONE)
## 1 Files Changed
- app/src/main/java/com/dogechat/android/geohash/LocationChannelManager.kt
### Details
* Bug fix: the app should show the correct geohash 

# Commit 28abd3c    (DONE)
## 6 Files Changed
- app/src/main/java/com/dogechat/android/DogechatApplication.kt
- app/src/main/java/com/dogechat/android/MainActivity.kt
- app/src/main/java/com/dogechat/android/ui/AboutSheet.kt
- app/src/main/java/com/dogechat/android/ui/InputComponents.kt
- app/src/main/java/com/dogechat/android/ui/theme/Theme.kt
- app/src/main/java/com/dogechat/android/ui/theme/ThemePreference.kt
### Details
* allow user to select a theme preference over light/dark/system

# Commit 02d5466    (DONE)
## 6 Files Changed
- app/src/main/java/com/dogechat/android/ui/ChatViewModel.kt
- app/src/main/java/com/dogechat/android/ui/MeshDelegateHandler.kt
- app/src/main/java/com/dogechat/android/ui/NotificationManager.kt
- app/src/main/java/com/dogechat/android/util/NotificationIntervalManager.kt
- app/src/test/kotlin/com/dogechat/NotificationManagerTest.kt
- gradle/libs.versions.toml
### Details
* adding notification for active peers + tests

* adding a recently seen peer set to track if we've seen that peer before

* changing back to notificationManager naming

* fixing some weird formatting that occurred during merge conflict fix

# Commit 3ea2aed    (DONE)
##  Files Changed
1 file changed
- CHANGELOG.md
### Details
* fixed bulletpoint

# Commit 5505207      (DONE)
## 12 Files Changed
- app/src/main/java/com/dogechat/android/mesh/MessageHandler.kt
- app/src/main/java/com/dogechat/android/nostr/NostrGeohashService.kt
- app/src/main/java/com/dogechat/android/nostr/NostrRelayManager.kt
- app/src/main/java/com/dogechat/android/onboarding/LocationCheckScreen.kt
- app/src/main/java/com/dogechat/android/services/ConversationAliasResolver.kt
- app/src/main/java/com/dogechat/android/ui/ChatUIUtils.kt
- app/src/main/java/com/dogechat/android/ui/ChatViewModel.kt 
- app/src/main/java/com/dogechat/android/ui/MessageComponents.kt
- app/src/main/java/com/dogechat/android/ui/MessageManager.kt   
- app/src/main/java/com/dogechat/android/ui/MessageSpecialParser.kt 
- app/src/main/java/com/dogechat/android/ui/PrivateChatManager.kt 
- app/src/main/java/com/dogechat/android/ui/NotificationManager.kt
### Details
* location name in notifications

* remove tests

* panic nostr

* mentions with hashes, otherwise none

* fix timestamps

* parse geohashes in messages

* works

* fix country name

* mention notifications work

# Commit 9c10318     (DONE)
## 3 FILES CHANGED 
- app/src/main/java/com/dogechat/android/ui/ChatUIUtils.kt
- app/src/main/java/com/dogechat/android/ui/GeohashPeopleList.kt
- app/src/main/java/com/dogechat/android/ui/SidebarComponents.kt 
### Details
* limit nick length 

# Commit 2ec3141
## 3 Files Changed
- app/src/main/java/com/dogechat/android/ui/ChatUIUtils.kt 
- app/src/main/java/com/dogechat/android/ui/MessageComponents.kt
- app/src/main/java/com/dogechat/android/ui/MessageSpecialParser.kt
### Details
* render links normally

# Commit 7f4bd96  (DONE)
## 1 File Changed
- app/src/main/java/com/dogechat/android/ui/ChatUIConstants.kt
### Details
* fix missing file 



## [0.9.3] - 2025-08-17
### Added
- Implemented all geohash updates from bitchat
- fixed UI colors 
- Added dogechat-android v0.9.3 apk & aab geohash, 2nd release of dogechat Android Much LFG!!!!!

## [0.8.2] - 2025-08-17
### Added
- Initial apk & aab release of dogechat Android app Much LFG!!!!!
- Added git pages `https://greatape42069.github.io/dogechat-android/`

## [0.7.3] - 2025-08-08
### Fixed
- fix: - 🐶 Forked for the Đogecoin community, Much Better So Fun!!!!
- 🐶 Added Đogecoin yellow colors added
- 🐕 changed primary green colors to yellow and all instances of "bit"

## [0.7.2] - 2025-07-20
### Fixed
- fix: battery optimization screen content scrollable with fixed buttons

## [0.7.1] - 2025-07-19

### Added
- feat(battery): add battery optimization management for background reliability

### Fixed
- fix: center align toolbar item in ChatHeader - passed modifier.fillmaxHeight so the content inside the row can actually be centered
- fix: update sidebar text to use string resources
- fix(chat): cursor location and enhance message input with slash command styling

### Changed
- refactor: remove context attribute at ChatViewModel.kt
- Refactor: Migrate MainViewModel to use StateFlow

### Improved
- Use HorizontalDivider instead of deprecated Divider
- Use contentPadding instead of padding so items remain fully visible


## [0.7]

### Added
- Location services check during app startup with educational UI
- Message text selection functionality in chat interface
- Enhanced RSSI tracking and unread message indicators
- Major Bluetooth connection architecture refactoring with dedicated managers

### Fixed
- **Critical**: Android-iOS message fragmentation compatibility issues
  - Fixed fragment size (500→150 bytes) and ID generation for cross-platform messaging
  - Ensures Android can properly communicate with iOS devices
- DirectMessage notifications and text copying functionality
- Smart routing optimizations (no relay loops, targeted delivery)
- Build system compilation issues and null pointer exceptions

### Changed
- Comprehensive dependency updates (AGP 8.10.1, Kotlin 2.2.0, Compose 2025.06.01)
- Optimized BLE scan intervals for better battery performance
- Reduced excessive logging output

### Improved
- Cross-platform compatibility with iOS and Rust implementations
- Connection stability through architectural improvements
- Battery performance via scan duty cycling
- User onboarding with location services education

## [0.6]

### Added
- Channel password management with `/pass` command for channel owners
- Monochrome/themed launcher icon for Android 12+ dynamic theming support
- Unit tests package with initial testing infrastructure
- Production build optimization with code minification and shrinking
- Native back gesture/button handling for all app views

### Fixed
- Favorite peer functionality completely restored and improved
  - Enhanced favorite system with fallback mechanism for peers without key exchange
  - Fixed UI state updates for favorite stars in both header and sidebar
  - Improved favorite persistence across app sessions
- `/w` command now displays user nicknames instead of peer IDs
- Button styling and layout improvements across the app
  - Enhanced back button positioning and styling
  - Improved private chat and channel header button layouts
  - Fixed button padding and alignment issues
- Color scheme consistency updates
  - Updated orange color throughout the app to match iOS version
  - Consistent color usage for private messages and UI elements
- App startup reliability improvements
  - Better initialization sequence handling
  - Fixed null pointer exceptions during startup
  - Enhanced error handling and logging
- Input field styling and behavior improvements
- Sidebar user interaction enhancements
- Permission explanation screen layout fixes with proper vertical padding

### Changed
- Updated GitHub organization references in project files
- Improved README documentation with updated clone URLs
- Enhanced logging throughout the application for better debugging

## [0.5.1] - 2025-07-10

### Added
- Bluetooth startup check with user prompt to enable Bluetooth if disabled

### Fixed
- Improved Bluetooth initialization reliability on first app launch

## [0.5] - 2025-07-10

### Added
- New user onboarding screen with permission explanations
- Educational content explaining why each permission is required
- Privacy assurance messaging (no tracking, no servers, local-only data)

### Fixed
- Comprehensive permission validation - ensures all required permissions are granted
- Proper Bluetooth stack initialization on first app load
- Eliminated need for manual app restart after installation
- Enhanced permission request coordination and error handling

### Changed
- Improved first-time user experience with guided setup flow

## [0.4] - 2025-07-10

### Added
- Push notifications for direct messages
- Enhanced notification system with proper click handling and grouping

### Improved
- Direct message (DM) view with better user interface
- Enhanced private messaging experience

### Known Issues
- Favorite peer functionality currently broken

## [0.3] - 2025-07-09

### Added
- Battery-aware scanning policies for improved power management
- Dynamic scan behavior based on device battery state

### Fixed
- Android-to-Android Bluetooth Low Energy connections
- Peer discovery reliability between Android devices
- Connection stability improvements

## [0.2] - 2025-07-09

### Added
- Initial Android implementation of dogechat protocol
- Bluetooth Low Energy mesh networking
- End-to-end encryption for private messages
- Channel-based messaging with password protection
- Store-and-forward message delivery
- IRC-style commands (/msg, /join, /clear, etc.)
- RSSI-based signal quality indicators

### Fixed
- Various Bluetooth handling improvements
- User interface refinements
- Connection reliability enhancements

## [0.1] - 2025-07-08

### Added
- Initial release of Dogechat Android client
- Basic mesh networking functionality
- Core messaging features
- Protocol compatibility with iOS Dogechat client

[Unreleased]: https://github.com/GreatApe42069/dogechat-android/compare/0.10.0...HEAD
[0.10.0]: https://github.com/GreatApe42069/dogechat-android/compare/0.9.5...0.10.0

<!-- Upstream historical references retained below -->
[0.5.1]: https://github.com/permissionlesstech/bitchat-android/compare/0.5...0.5.1
[0.5]: https://github.com/permissionlesstech/bitchat-android/compare/0.4...0.5
[0.4]: https://github.com/permissionlesstech/bitchat-android/compare/0.3...0.4
[0.3]: https://github.com/permissionlesstech/bitchat-android/compare/0.2...0.3
[0.2]: https://github.com/permissionlesstech/bitchat-android/compare/0.1...0.2
[0.1]: https://github.com/permissionlesstech/bitchat-android/releases/tag/0.1