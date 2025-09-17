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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.onboarding.BluetoothCheckScreen
import com.dogechat.android.onboarding.BluetoothStatus
import com.dogechat.android.onboarding.BluetoothStatusManager
import com.dogechat.android.onboarding.BatteryOptimizationManager
import com.dogechat.android.onboarding.BatteryOptimizationPreferenceManager
import com.dogechat.android.onboarding.BatteryOptimizationScreen
import com.dogechat.android.onboarding.BatteryOptimizationStatus
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

class MainActivity : ComponentActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var onboardingCoordinator: OnboardingCoordinator
    private lateinit var bluetoothStatusManager: BluetoothStatusManager
    private lateinit var locationStatusManager: LocationStatusManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    
    // Core mesh service - managed at app level
    private lateinit var meshService: BluetoothMeshService
    private val mainViewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels { 
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, meshService) as T
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for modern Android look
        enableEdgeToEdge()
        
        // Make status bar transparent and content can extend behind it
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Opaque yellow status bar with dark icons
        window.statusBarColor = 0xFFFFFF00.toInt()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        // Initialize permission management and services
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
        
        // Collect state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }
        
        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }
    
    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val onboardingState by mainViewModel.onboardingState.collectAsState()
        val bluetoothStatus by mainViewModel.bluetoothStatus.collectAsState()
        val locationStatus by mainViewModel.locationStatus.collectAsState()
        val batteryOptimizationStatus by mainViewModel.batteryOptimizationStatus.collectAsState()
        val errorMessage by mainViewModel.errorMessage.collectAsState()
        val isBluetoothLoading by mainViewModel.isBluetoothLoading.collectAsState()
        val isLocationLoading by mainViewModel.isLocationLoading.collectAsState()
        val isBatteryOptimizationLoading by mainViewModel.isBatteryOptimizationLoading.collectAsState()

        DisposableEffect(context, bluetoothStatusManager) {
            val receiver = bluetoothStatusManager.monitorBluetoothState(
                context = context,
                bluetoothStatusManager = bluetoothStatusManager
            ) { status ->
                if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                    checkBluetoothAndProceed()
                }
            }

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                    Log.d("BluetoothStatusUI", "BroadcastReceiver unregistered")
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.PERMISSION_REQUESTING -> {
                // InitializingScreen has no parameters
                InitializingScreen()
            }
            
            OnboardingState.BLUETOOTH_CHECK -> {
                BluetoothCheckScreen(
                    modifier = modifier,
                    status = bluetoothStatus,
                    onEnableBluetooth = {
                        mainViewModel.updateBluetoothLoading(true)
                        bluetoothStatusManager.requestEnableBluetooth()
                    },
                    onRetry = {
                        checkBluetoothAndProceed()
                    },
                    isLoading = isBluetoothLoading
                )
            }
            
            OnboardingState.LOCATION_CHECK -> {
                LocationCheckScreen(
                    modifier = modifier,
                    status = locationStatus,
                    onEnableLocation = {
                        mainViewModel.updateLocationLoading(true)
                        locationStatusManager.requestEnableLocation()
                    },
                    onRetry = {
                        checkLocationAndProceed()
                    },
                    isLoading = isLocationLoading
                )
            }
            
            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> {
                BatteryOptimizationScreen(
                    modifier = modifier,
                    status = batteryOptimizationStatus,
                    onDisableBatteryOptimization = {
                        mainViewModel.updateBatteryOptimizationLoading(true)
                        batteryOptimizationManager.requestDisableBatteryOptimization()
                    },
                    onRetry = {
                        checkBatteryOptimizationAndProceed()
                    },
                    onSkip = {
                        // Skip battery optimization and proceed
                        proceedWithPermissionCheck()
                    },
                    isLoading = isBatteryOptimizationLoading
                )
            }
            
            OnboardingState.PERMISSION_EXPLANATION -> {
                PermissionExplanationScreen(
                    modifier = modifier,
                    permissionCategories = permissionManager.getCategorizedPermissions(),
                    onContinue = {
                        mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                        onboardingCoordinator.requestPermissions()
                    }
                )
            }
            
            OnboardingState.CHECKING, OnboardingState.INITIALIZING, OnboardingState.COMPLETE -> {

                // Back handling
                val backCallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val handled = chatViewModel.handleBackPressed()
                        if (!handled) {
                            this.isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            this.isEnabled = true
                        }
                    }
                }
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
                InitializationErrorScreen(
                    modifier = modifier,
                    errorMessage = errorMessage,
                    onRetry = {
                        mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                        checkOnboardingStatus()
                    },
                    onOpenSettings = {
                        onboardingCoordinator.openAppSettings()
                    }
                )
            }
        }
    }
    
    private fun handleOnboardingStateChange(state: OnboardingState) {
        when (state) {
            OnboardingState.COMPLETE -> {
                android.util.Log.d("MainActivity", "Onboarding completed - app ready")
            }
            OnboardingState.ERROR -> {
                android.util.Log.e("MainActivity", "Onboarding error state reached")
            }
            else -> {}
        }
    }
    
    private fun checkOnboardingStatus() {
        Log.d("MainActivity", "Checking onboarding status")
        
        lifecycleScope.launch {
            delay(500)
            checkBluetoothAndProceed()
        }
    }
    
    private fun checkBluetoothAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch, skipping Bluetooth check - will check after permissions")
            proceedWithPermissionCheck()
            return
        }
        
        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())
        
        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> {
                checkLocationAndProceed()
            }
            BluetoothStatus.DISABLED -> {
                Log.d("MainActivity", "Bluetooth disabled, showing enable screen")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                android.util.Log.e("MainActivity", "Bluetooth not supported")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }
    
    private fun proceedWithPermissionCheck() {
        Log.d("MainActivity", "Proceeding with permission check")
        
        lifecycleScope.launch {
            delay(200)
            
            if (permissionManager.isFirstTimeLaunch()) {
                Log.d("MainActivity", "First time launch, showing permission explanation")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            } else if (permissionManager.areAllPermissionsGranted()) {
                Log.d("MainActivity", "Existing user with permissions, initializing app")
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            } else {
                Log.d("MainActivity", "Existing user missing permissions, showing explanation")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
        }
    }
    
    private fun handleBluetoothEnabled() {
        Log.d("MainActivity", "Bluetooth enabled by user")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    private fun checkLocationAndProceed() {
        Log.d("MainActivity", "Checking location services status")
        
        if (permissionManager.isFirstTimeLaunch()) {
            Log.d("MainActivity", "First-time launch, skipping location check - will check after permissions")
            proceedWithPermissionCheck()
            return
        }
        
        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())
        
        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> {
                checkBatteryOptimizationAndProceed()
            }
            LocationStatus.DISABLED -> {
                Log.d("MainActivity", "Location services disabled, showing enable screen")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                Log.e("MainActivity", "Location services not available")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    private fun handleLocationEnabled() {
        Log.d("MainActivity", "Location services enabled by user")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        checkBatteryOptimizationAndProceed()
    }

    private fun handleLocationDisabled(message: String) {
        Log.w("MainActivity", "Location services disabled or failed: $message")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        if (mainViewModel.locationStatus.value == LocationStatus.NOT_AVAILABLE) {
            mainViewModel.updateErrorMessage(message)
            mainViewModel.updateOnboardingState(OnboardingState.ERROR)
        } else {
            mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
        }
    }
    
    private fun handleBluetoothDisabled(message: String) {
        Log.w("MainActivity", "Bluetooth disabled or failed: $message")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())
        
        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission") && permissionManager.isFirstTimeLaunch() -> {
                Log.d("MainActivity", "Bluetooth enable requires permissions, proceeding to permission explanation")
                proceedWithPermissionCheck()
            }
            message.contains("Permission") -> {
                Log.d("MainActivity", "Bluetooth enable requires permissions, showing permission explanation")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }
    
    private fun handleOnboardingComplete() {
        Log.d("MainActivity", "Onboarding completed, checking Bluetooth and Location before initializing app")
        
        val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
        val currentLocationStatus = locationStatusManager.checkLocationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        
        when {
            currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                Log.d("MainActivity", "Permissions granted, but Bluetooth still disabled. Showing Bluetooth enable screen.")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            currentLocationStatus != LocationStatus.ENABLED -> {
                Log.d("MainActivity", "Permissions granted, but Location services still disabled. Showing Location enable screen.")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            currentBatteryOptimizationStatus == BatteryOptimizationStatus.ENABLED -> {
                android.util.Log.d("MainActivity", "Permissions granted, but battery optimization still enabled. Showing battery optimization screen.")
                mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
            else -> {
                Log.d("MainActivity", "Both Bluetooth and Location services are enabled, proceeding to initialization")
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            }
        }
    }
    
    private fun handleOnboardingFailed(message: String) {
        Log.e("MainActivity", "Onboarding failed: $message")
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }
    
    private fun checkBatteryOptimizationAndProceed() {
        android.util.Log.d("MainActivity", "Checking battery optimization status")
        
        if (permissionManager.isFirstTimeLaunch()) {
            android.util.Log.d("MainActivity", "First-time launch, skipping battery optimization check - will check after permissions")
            proceedWithPermissionCheck()
            return
        }

        if (BatteryOptimizationPreferenceManager.isSkipped(this)) {
            android.util.Log.d("MainActivity", "User previously skipped battery optimization, proceeding to permissions")
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
            BatteryOptimizationStatus.DISABLED, BatteryOptimizationStatus.NOT_SUPPORTED -> {
                proceedWithPermissionCheck()
            }
            BatteryOptimizationStatus.ENABLED -> {
                android.util.Log.d("MainActivity", "Battery optimization enabled, showing disable screen")
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }
    
    private fun handleBatteryOptimizationDisabled() {
        android.util.Log.d("MainActivity", "Battery optimization disabled by user")
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }
    
    private fun handleBatteryOptimizationFailed(message: String) {
        android.util.Log.w("MainActivity", "Battery optimization disable failed: $message")
        mainViewModel.updateBatteryOptimizationLoading(false)
        val currentStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentStatus)
        mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
    }
    
    private fun initializeApp() {
        Log.d("MainActivity", "Starting app initialization")
        
        lifecycleScope.launch {
            try {
                delay(1000)
                Log.d("MainActivity", "Permissions verified, initializing chat system")
                PoWPreferenceManager.init(this@MainActivity)
                Log.d("MainActivity", "PoW preferences initialized")

                if (!permissionManager.areAllPermissionsGranted()) {
                    val missing = permissionManager.getMissingPermissions()
                    Log.w("MainActivity", "Permissions revoked during initialization: $missing")
                    handleOnboardingFailed("Some permissions were revoked. Please grant all permissions to continue.")
                    return@launch
                }

                meshService.delegate = chatViewModel
                meshService.startServices()
                
                Log.d("MainActivity", "Mesh service started successfully")
                handleNotificationIntent(intent)
                
                delay(500)
                Log.d("MainActivity", "App initialization complete")
                mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to initialize app", e)
                handleOnboardingFailed("Failed to initialize the app: ${e.message}")
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            handleNotificationIntent(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            meshService.connectionManager.setAppBackgroundState(false)
            chatViewModel.setAppBackgroundState(false)

            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            if (currentBluetoothStatus != BluetoothStatus.ENABLED) {
                Log.w("MainActivity", "Bluetooth disabled while app was backgrounded")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
                return
            }
            
            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                Log.w("MainActivity", "Location services disabled while app was backgrounded")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            meshService.connectionManager.setAppBackgroundState(true)
            chatViewModel.setAppBackgroundState(true)
        }
    }
    
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
                val senderNickname = intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_SENDER_NICKNAME)
                
                if (peerID != null) {
                    Log.d("MainActivity", "Opening private chat with $senderNickname (peerID: $peerID) from notification")
                    chatViewModel.startPrivateChat(peerID)
                    chatViewModel.clearNotificationsForSender(peerID)
                }
            }
            shouldOpenGeohashChat -> {
                val geohash = intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_GEOHASH)
                if (geohash != null) {
                    Log.d("MainActivity", "Opening geohash chat #$geohash from notification")
                    val level = when (geohash.length) {
                        7 -> com.dogechat.android.geohash.GeohashChannelLevel.BLOCK
                        6 -> com.dogechat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                        5 -> com.dogechat.android.geohash.GeohashChannelLevel.CITY
                        4 -> com.dogechat.android.geohash.GeohashChannelLevel.PROVINCE
                        2 -> com.dogechat.android.geohash.GeohashChannelLevel.REGION
                        else -> com.dogechat.android.geohash.GeohashChannelLevel.CITY
                    }
                    val geohashChannel = com.dogechat.android.geohash.GeohashChannel(level, geohash)
                    val channelId = com.dogechat.android.geohash.ChannelID.Location(geohashChannel)
                    chatViewModel.selectLocationChannel(channelId)
                    chatViewModel.setCurrentGeohash(geohash)
                    chatViewModel.clearNotificationsForGeohash(geohash)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            locationStatusManager.cleanup()
            Log.d("MainActivity", "Location status manager cleaned up successfully")
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up location status manager: ${e.message}")
        }
        
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                meshService.stopServices()
                Log.d("MainActivity", "Mesh services stopped successfully")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping mesh services in onDestroy: ${e.message}")
            }
        }
    }
}