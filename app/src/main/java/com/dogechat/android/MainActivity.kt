package com.dogechat.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.onboarding.BatteryOptimizationManager
import com.dogechat.android.onboarding.BatteryOptimizationPreferenceManager
import com.dogechat.android.onboarding.BatteryOptimizationScreen
import com.dogechat.android.onboarding.BatteryOptimizationStatus
import com.dogechat.android.onboarding.BluetoothCheckScreen
import com.dogechat.android.onboarding.BluetoothStatus
import com.dogechat.android.onboarding.BluetoothStatusManager
import com.dogechat.android.onboarding.InitializationErrorScreen
import com.dogechat.android.onboarding.InitializingScreen
import com.dogechat.android.onboarding.LocationCheckScreen
import com.dogechat.android.onboarding.LocationStatus
import com.dogechat.android.onboarding.LocationStatusManager
import com.dogechat.android.onboarding.OnboardingCoordinator
import com.dogechat.android.onboarding.OnboardingState
import com.dogechat.android.onboarding.PermissionExplanationScreen
import com.dogechat.android.onboarding.PermissionManager
import com.dogechat.android.ui.ChatScreen
import com.dogechat.android.ui.ChatViewModel
import com.dogechat.android.ui.theme.DogechatTheme
import com.dogechat.android.nostr.PoWPreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * Coordinates:
 *  - App-wide onboarding flow (permissions, Bluetooth, location, battery optimization)
 *  - Initialization of the mesh service
 *  - Transition into main Chat UI
 *  - Notification intent routing (private chats / geohash chats)
 *
 * Design Goals:
 *  - Keep logic explicit and audit-friendly (verbose comments so external reviewers can follow trust model)
 *  - Separate "checking" phases from UI flow transitions
 *  - Avoid restarting onboarding on configuration changes (state kept in MainViewModel)
 */
class MainActivity : ComponentActivity() {

    // Managers / Coordinators used across onboarding + runtime
    private lateinit var permissionManager: PermissionManager
    private lateinit var onboardingCoordinator: OnboardingCoordinator
    private lateinit var bluetoothStatusManager: BluetoothStatusManager
    private lateinit var locationStatusManager: LocationStatusManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager

    // Core mesh service (lifecycle tied to app runtime; started after permissions)
    private lateinit var meshService: BluetoothMeshService

    // ViewModels (MainViewModel retains onboarding state; ChatViewModel handles chat logic)
    private val mainViewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels {
        // Custom factory so we can inject meshService (which we create in onCreate)
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, meshService) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for modern gesture nav visuals
        enableEdgeToEdge()
        // Allow content behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Theming status bar (opaque yellow w/ dark icons)
        window.statusBarColor = 0xFFFFFF00.toInt()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        // Initialize permission + environment managers
        permissionManager = PermissionManager(this)
        meshService = BluetoothMeshService(this)
        bluetoothStatusManager = BluetoothStatusManager(
            activity = this,
            context = this,
            onBluetoothEnabled = ::handleBluetoothEnabled,
            onBluetoothDisabled = ::handleBluetoothDisabled
        )
        locationStatusManager = LocationStatusManager(
            activity = this,
            context = this,
            onLocationEnabled = ::handleLocationEnabled,
            onLocationDisabled = ::handleLocationDisabled
        )
        batteryOptimizationManager = BatteryOptimizationManager(
            activity = this,
            context = this,
            onBatteryOptimizationDisabled = ::handleBatteryOptimizationDisabled,
            onBatteryOptimizationFailed = ::handleBatteryOptimizationFailed
        )
        onboardingCoordinator = OnboardingCoordinator(
            activity = this,
            permissionManager = permissionManager,
            onOnboardingComplete = ::handleOnboardingComplete,
            onOnboardingFailed = ::handleOnboardingFailed
        )

        // Compose UI root
        setContent {
            DogechatTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    OnboardingFlowScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }

        // Observe onboarding state changes in a lifecycle-aware scope
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }

        // Kick off onboarding only if starting fresh (not after rotation / process restore)
        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }

    /**
     * Composable controlling the visible onboarding or main chat UI state.
     * Each branch has comments explaining intent so auditors can trace user flow.
     */
    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current

        // Reactive onboarding state slices
        val onboardingState by mainViewModel.onboardingState.collectAsState()
        val bluetoothStatus by mainViewModel.bluetoothStatus.collectAsState()
        val locationStatus by mainViewModel.locationStatus.collectAsState()
        val batteryOptimizationStatus by mainViewModel.batteryOptimizationStatus.collectAsState()
        val errorMessage by mainViewModel.errorMessage.collectAsState()
        val isBluetoothLoading by mainViewModel.isBluetoothLoading.collectAsState()
        val isLocationLoading by mainViewModel.isLocationLoading.collectAsState()
        val isBatteryOptimizationLoading by mainViewModel.isBatteryOptimizationLoading.collectAsState()

        // Monitor Bluetooth state asynchronously and react when user enables it
        DisposableEffect(context, bluetoothStatusManager) {
            val receiver = bluetoothStatusManager.monitorBluetoothState(
                context = context,
                bluetoothStatusManager = bluetoothStatusManager
            ) { status ->
                // If user just enabled Bluetooth while on the Bluetooth check screen, continue flow
                if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                    checkBluetoothAndProceed()
                }
            }

            onDispose {
                // Receiver may not be registered if lifecycle ended before registration returned
                try {
                    context.unregisterReceiver(receiver)
                    Log.d("BluetoothStatusUI", "BroadcastReceiver unregistered")
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered (safe to ignore)")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.PERMISSION_REQUESTING -> {
                // Shows a minimal initializing screen while system permission dialog(s) may be up
                InitializingScreen(modifier)
            }

            OnboardingState.BLUETOOTH_CHECK -> {
                // Ask user to enable Bluetooth (hard requirement for mesh)
                BluetoothCheckScreen(
                    modifier = modifier,
                    status = bluetoothStatus,
                    onEnableBluetooth = {
                        mainViewModel.updateBluetoothLoading(true)
                        bluetoothStatusManager.requestEnableBluetooth()
                    },
                    onRetry = { checkBluetoothAndProceed() },
                    isLoading = isBluetoothLoading
                )
            }

            OnboardingState.LOCATION_CHECK -> {
                // Location may be needed for discovering peers (OS-level scan rules) + geohash channels
                LocationCheckScreen(
                    modifier = modifier,
                    status = locationStatus,
                    onEnableLocation = {
                        mainViewModel.updateLocationLoading(true)
                        locationStatusManager.requestEnableLocation()
                    },
                    onRetry = { checkLocationAndProceed() },
                    isLoading = isLocationLoading
                )
            }

            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> {
                // Battery optimization relax is required for consistent background relays
                BatteryOptimizationScreen(
                    modifier = modifier,
                    status = batteryOptimizationStatus,
                    onDisableBatteryOptimization = {
                        mainViewModel.updateBatteryOptimizationLoading(true)
                        batteryOptimizationManager.requestDisableBatteryOptimization()
                    },
                    onRetry = { checkBatteryOptimizationAndProceed() },
                    onSkip = {
                        // User can skip (trade-off: potentially less reliable background operations)
                        proceedWithPermissionCheck()
                    },
                    isLoading = isBatteryOptimizationLoading
                )
            }

            OnboardingState.PERMISSION_EXPLANATION -> {
                // Explain why we need permissions BEFORE requesting (UX transparency + auditability)
                PermissionExplanationScreen(
                    modifier = modifier,
                    permissionCategories = permissionManager.getCategorizedPermissions(),
                    onContinue = {
                        mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                        onboardingCoordinator.requestPermissions()
                    }
                )
            }

            OnboardingState.CHECKING,
            OnboardingState.INITIALIZING,
            OnboardingState.COMPLETE -> {
                // We are past the permission + environment hoops. Show chat UI.
                // Add back handler to allow ChatViewModel to manage in-app "navigation stack".
                val backCallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val handled = chatViewModel.handleBackPressed()
                        if (!handled) {
                            // Temporarily disable to call system back, then re-enable.
                            isEnabled = false
                            this@MainActivity.onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
                // Avoid multiples (the dispatcher will ignore duplicates with same owner; safe here)
                onBackPressedDispatcher.addCallback(this@MainActivity, backCallback)

                ChatScreen(
                    viewModel = chatViewModel,
                    onWalletClick = {
                        val intent = Intent(context, com.dogechat.android.wallet.WalletActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }

            OnboardingState.ERROR -> {
                // Non-recoverable state (e.g. device missing required hardware)
                InitializationErrorScreen(
                    modifier = modifier,
                    errorMessage = errorMessage,
                    onRetry = {
                        mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                        checkOnboardingStatus()
                    },
                    onOpenSettings = { onboardingCoordinator.openAppSettings() }
                )
            }
        }
    }

    /**
     * Respond to high-level onboarding state transitions (logging / analytics / troubleshooting).
     */
    private fun handleOnboardingStateChange(state: OnboardingState) {
        when (state) {
            OnboardingState.COMPLETE -> Log.d("MainActivity", "Onboarding completed - app ready")
            OnboardingState.ERROR -> Log.e("MainActivity", "Onboarding error state reached")
            else -> { /* other transitions are routine */ }
        }
    }

    /**
     * Entry point into the staged environment validation:
     * 1. Bluetooth
     * 2. Location
     * 3. Battery optimization
     * 4. Permissions (if first time)
     */
    private fun checkOnboardingStatus() {
        Log.d("MainActivity", "Checking onboarding status")
        lifecycleScope.launch {
            // Small delay so user briefly sees "checking" instead of abrupt screen flash
            delay(500)
            checkBluetoothAndProceed()
        }
    }

    /**
     * Core Bluetooth readiness gate.
     * Skipped on first launch until after permissions are granted (to avoid confusing users).
     */
    private fun checkBluetoothAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch: defer Bluetooth check until after permissions")
            proceedWithPermissionCheck()
            return
        }

        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> checkLocationAndProceed()
            BluetoothStatus.DISABLED -> {
                Log.d("MainActivity", "Bluetooth disabled -> show enable prompt")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                Log.e("MainActivity", "Device lacks Bluetooth support")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }

    /**
     * When we are ready to request / re-check permissions.
     * Maintains user context if they already granted things previously.
     */
    private fun proceedWithPermissionCheck() {
        Log.d("MainActivity", "Proceeding with permission check flow")
        lifecycleScope.launch {
            delay(200) // Smooth visual transition
            when {
                permissionManager.isFirstTimeLaunch() -> {
                    mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
                }
                permissionManager.areAllPermissionsGranted() -> {
                    mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                    initializeApp()
                }
                else -> {
                    mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
                }
            }
        }
    }

    /** Bluetooth enabled callback from manager */
    private fun handleBluetoothEnabled() {
        Log.d("MainActivity", "Bluetooth enabled by user")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    /**
     * Location readiness gate. Similar logic to Bluetooth (skip first-time until permissions).
     */
    private fun checkLocationAndProceed() {
        Log.d("MainActivity", "Checking location services status")

        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch: defer location check until after permissions")
            proceedWithPermissionCheck()
            return
        }

        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> checkBatteryOptimizationAndProceed()
            LocationStatus.DISABLED -> {
                Log.d("MainActivity", "Location disabled -> show enable prompt")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                Log.e("MainActivity", "Location not available on device")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    /** Location enabled callback */
    private fun handleLocationEnabled() {
        Log.d("MainActivity", "Location enabled by user")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        checkBatteryOptimizationAndProceed()
    }

    /**
     * Location disabled callback with context message (permission denial, settings off, etc.)
     */
    private fun handleLocationDisabled(message: String) {
        Log.w("MainActivity", "Location disabled/failed: $message")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        if (mainViewModel.locationStatus.value == LocationStatus.NOT_AVAILABLE) {
            mainViewModel.updateErrorMessage(message)
            mainViewModel.updateOnboardingState(OnboardingState.ERROR)
        } else {
            mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
        }
    }

    /**
     * Bluetooth disabled callback (permissions denial, hardware off, etc.)
     * Handles nuanced first-time vs returning flow.
     */
    private fun handleBluetoothDisabled(message: String) {
        Log.w("MainActivity", "Bluetooth disabled/failed: $message")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission", ignoreCase = true) && permissionManager.isFirstTimeLaunch() -> {
                Log.d("MainActivity", "Bluetooth needs permission -> go to explanation (first launch)")
                proceedWithPermissionCheck()
            }
            message.contains("Permission", ignoreCase = true) -> {
                Log.d("MainActivity", "Bluetooth needs permission -> show explanation (returning user)")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }

    /**
     * Called when permissions were granted via onboardingCoordinator.
     * We re-check environment states before fully initializing.
     */
    private fun handleOnboardingComplete() {
        Log.d("MainActivity", "Onboarding complete -> verifying environment again")

        val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
        val currentLocationStatus = locationStatusManager.checkLocationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }

        when {
            currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                Log.d("MainActivity", "Bluetooth still disabled -> returning to Bluetooth check")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            currentLocationStatus != LocationStatus.ENABLED -> {
                Log.d("MainActivity", "Location still disabled -> returning to Location check")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            currentBatteryOptimizationStatus == BatteryOptimizationStatus.ENABLED -> {
                Log.d("MainActivity", "Battery optimization still ON -> show optimization screen")
                mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
            else -> {
                Log.d("MainActivity", "All environment checks passed -> initializing app")
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            }
        }
    }

    /** Transitions onboarding flow into error state with a message */
    private fun handleOnboardingFailed(message: String) {
        Log.e("MainActivity", "Onboarding failed: $message")
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }

    /**
     * Battery optimization relax step. Users can skip; we respect that decision
     * but record it in preference manager so we don't nag unnecessarily.
     */
    private fun checkBatteryOptimizationAndProceed() {
        Log.d("MainActivity", "Checking battery optimization state")

        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch: skipping optimization check pre-permissions")
            proceedWithPermissionCheck()
            return
        }

        if (BatteryOptimizationPreferenceManager.isSkipped(this)) {
            Log.d("MainActivity", "User previously skipped optimization -> continuing")
            proceedWithPermissionCheck()
            return
        }

        batteryOptimizationManager.logBatteryOptimizationStatus()

        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }

        mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)

        when (currentBatteryOptimizationStatus) {
            BatteryOptimizationStatus.DISABLED,
            BatteryOptimizationStatus.NOT_SUPPORTED -> proceedWithPermissionCheck()
            BatteryOptimizationStatus.ENABLED -> {
                Log.d("MainActivity", "Battery optimization enabled -> prompting user")
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }

    /** Callback when user successfully disables battery optimization */
    private fun handleBatteryOptimizationDisabled() {
        Log.d("MainActivity", "Battery optimization disabled by user")
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }

    /** Callback when disabling battery optimization fails or is denied */
    private fun handleBatteryOptimizationFailed(message: String) {
        Log.w("MainActivity", "Battery optimization disable failed: $message")
        mainViewModel.updateBatteryOptimizationLoading(false)
        val currentStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentStatus)
        mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
    }

    /**
     * Final initialization step after environment validated.
     * Adds intentional delays to allow system services (BT stack etc.) to settle.
     */
    private fun initializeApp() {
        Log.d("MainActivity", "Beginning initialization sequence")
        lifecycleScope.launch {
            try {
                // Allow system to settle after granting permissions (avoids cold-start race conditions)
                delay(1000)

                // Initialize Proof-of-Work settings for geohash spam deterrence
                PoWPreferenceManager.init(this@MainActivity)
                Log.d("MainActivity", "PoW preferences initialized")

                // Double-check permissions weren't revoked mid-process
                if (!permissionManager.areAllPermissionsGranted()) {
                    val missing = permissionManager.getMissingPermissions()
                    Log.w("MainActivity", "Permissions revoked during init: $missing")
                    handleOnboardingFailed("Some permissions were revoked. Please grant all permissions to continue.")
                    return@launch
                }

                // Start mesh services + set delegate to ChatViewModel (receives mesh events)
                meshService.delegate = chatViewModel
                meshService.startServices()
                Log.d("MainActivity", "Mesh service started")

                // Check if launch intent came from a notification (open the chat accordingly)
                handleNotificationIntent(intent)

                // Short stabilization delay before marking complete
                delay(500)
                Log.d("MainActivity", "Initialization complete")
                mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
            } catch (e: Exception) {
                Log.e("MainActivity", "Initialization failure", e)
                handleOnboardingFailed("Failed to initialize the app: ${e.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Notification intents while already running
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            handleNotificationIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-assert environment when returning to foreground
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            meshService.connectionManager.setAppBackgroundState(false)
            chatViewModel.setAppBackgroundState(false)

            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            if (currentBluetoothStatus != BluetoothStatus.ENABLED) {
                Log.w("MainActivity", "Bluetooth disabled while backgrounded")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
                return
            }

            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                Log.w("MainActivity", "Location disabled while backgrounded")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Mark background state only when fully initialized to avoid false state during onboarding
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            meshService.connectionManager.setAppBackgroundState(true)
            chatViewModel.setAppBackgroundState(true)
        }
    }

    /**
     * Processes intents originating from notifications (private chat or geohash chat deep links).
     */
    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.dogechat.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT,
            false
        )
        val shouldOpenGeohashChat = intent.getBooleanExtra(
            com.dogechat.android.ui.NotificationManager.EXTRA_OPEN_GEOHASH_CHAT,
            false
        )

        when {
            shouldOpenPrivateChat -> {
                val peerID = intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_PEER_ID)
                val senderNickname =
                    intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_SENDER_NICKNAME)
                if (peerID != null) {
                    Log.d(
                        "MainActivity",
                        "Notification -> open private chat with $senderNickname (peerID=$peerID)"
                    )
                    chatViewModel.startPrivateChat(peerID)
                    chatViewModel.clearNotificationsForSender(peerID)
                }
            }

            shouldOpenGeohashChat -> {
                val geohash =
                    intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_GEOHASH)
                if (geohash != null) {
                    Log.d("MainActivity", "Notification -> open geohash #$geohash")
                    val level = when (geohash.length) {
                        7 -> com.dogechat.android.geohash.GeohashChannelLevel.BLOCK
                        6 -> com.dogechat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                        5 -> com.dogechat.android.geohash.GeohashChannelLevel.CITY
                        4 -> com.dogechat.android.geohash.GeohashChannelLevel.PROVINCE
                        2 -> com.dogechat.android.geohash.GeohashChannelLevel.REGION
                        else -> com.dogechat.android.geohash.GeohashChannelLevel.CITY // fallback
                    }
                    val geohashChannel = com.dogechat.android.geohash.GeohashChannel(level, geohash)
                    val channelId =
                        com.dogechat.android.geohash.ChannelID.Location(geohashChannel)
                    chatViewModel.selectLocationChannel(channelId)
                    chatViewModel.setCurrentGeohash(geohash)
                    chatViewModel.clearNotificationsForGeohash(geohash)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up location state observer resources
        try {
            locationStatusManager.cleanup()
            Log.d("MainActivity", "Location status manager cleaned up successfully")
        } catch (e: Exception) {
            Log.w("MainActivity", "Location status manager cleanup failed: ${e.message}")
        }

        // Stop mesh only if app made it past initialization
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                meshService.stopServices()
                Log.d("MainActivity", "Mesh services stopped")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping mesh services: ${e.message}")
            }
        }
    }
}