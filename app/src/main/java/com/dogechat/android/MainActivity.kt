package com.dogechat.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.onboarding.BluetoothCheckScreen
import com.dogechat.android.onboarding.BluetoothStatus
import com.dogechat.android.onboarding.BluetoothStatusManager
import com.dogechat.android.onboarding.BatteryOptimizationManager
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
import com.dogechat.android.ui.theme.dogechatTheme
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

    // ChatViewModel needs meshService - create via factory so it can access meshService when initialized
    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(application, meshService) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize permission management
        permissionManager = PermissionManager(this)

        // Initialize core mesh service first
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
            dogechatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingFlowScreen()
                }
            }
        }

        // Collect onboarding state changes in lifecycle-aware manner
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }

        // Start onboarding check if initial state
        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }

    @Composable
    private fun OnboardingFlowScreen() {
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
                bluetoothStatusManager = bluetoothStatusManager,
                onBluetoothStateChanged = { status ->
                    if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                        checkBluetoothAndProceed()
                    }
                }
            )

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.CHECKING -> InitializingScreen()
            OnboardingState.BLUETOOTH_CHECK -> BluetoothCheckScreen(
                status = bluetoothStatus,
                onEnableBluetooth = {
                    mainViewModel.updateBluetoothLoading(true)
                    bluetoothStatusManager.requestEnableBluetooth()
                },
                onRetry = { checkBluetoothAndProceed() },
                isLoading = isBluetoothLoading
            )
            OnboardingState.LOCATION_CHECK -> LocationCheckScreen(
                status = locationStatus,
                onEnableLocation = {
                    mainViewModel.updateLocationLoading(true)
                    locationStatusManager.requestEnableLocation()
                },
                onRetry = { checkLocationAndProceed() },
                isLoading = isLocationLoading,
                // added fallback for newer signature that expects an 'onReady'
                onReady = { 
                    // called when location system reports it's ready — continue onboarding
                    mainViewModel.updateLocationLoading(false)
                    checkLocationAndProceed()
                }
            )
            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> BatteryOptimizationScreen(
                status = batteryOptimizationStatus,
                onDisableBatteryOptimization = {
                    mainViewModel.updateBatteryOptimizationLoading(true)
                    batteryOptimizationManager.requestDisableBatteryOptimization()
                },
                onRetry = { checkBatteryOptimizationAndProceed() },
                onSkip = { proceedWithPermissionCheck() },
                isLoading = isBatteryOptimizationLoading
            )
            OnboardingState.PERMISSION_EXPLANATION -> PermissionExplanationScreen(
                permissionCategories = permissionManager.getCategorizedPermissions(),
                onContinue = {
                    mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                    onboardingCoordinator.requestPermissions()
                }
            )
            OnboardingState.PERMISSION_REQUESTING -> InitializingScreen()
            OnboardingState.INITIALIZING -> InitializingScreen()
            OnboardingState.COMPLETE -> {
                // Handle back navigation via ChatViewModel
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
                ChatScreen(viewModel = chatViewModel)
            }
            OnboardingState.ERROR -> InitializationErrorScreen(
                errorMessage = errorMessage,
                onRetry = {
                    mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                    checkOnboardingStatus()
                },
                onOpenSettings = { onboardingCoordinator.openAppSettings() }
            )
        }
    }

    private fun handleOnboardingStateChange(state: OnboardingState) {
        when (state) {
            OnboardingState.COMPLETE -> Log.d("MainActivity", "Onboarding completed - app ready")
            OnboardingState.ERROR -> Log.e("MainActivity", "Onboarding error state reached")
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
            proceedWithPermissionCheck()
            return
        }

        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())

        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> checkLocationAndProceed()
            BluetoothStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }

    private fun proceedWithPermissionCheck() {
        lifecycleScope.launch {
            delay(200)
            if (permissionManager.isFirstTimeLaunch()) {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            } else if (permissionManager.areAllPermissionsGranted()) {
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            } else {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
        }
    }

    private fun handleBluetoothEnabled() {
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    private fun checkLocationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }
        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())
        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> checkBatteryOptimizationAndProceed()
            LocationStatus.DISABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    private fun handleLocationEnabled() {
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        checkBatteryOptimizationAndProceed()
    }

    private fun handleLocationDisabled(message: String) {
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
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())
        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission") && permissionManager.isFirstTimeLaunch() -> {
                proceedWithPermissionCheck()
            }
            message.contains("Permission") -> {
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }

    private fun handleOnboardingComplete() {
        lifecycleScope.launch {
            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            val currentBatteryOptimizationStatus = when {
                !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
                batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
                else -> BatteryOptimizationStatus.ENABLED
            }

            when {
                currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                    mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                    mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                    mainViewModel.updateBluetoothLoading(false)
                }
                currentLocationStatus != LocationStatus.ENABLED -> {
                    mainViewModel.updateLocationStatus(currentLocationStatus)
                    mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                    mainViewModel.updateLocationLoading(false)
                }
                currentBatteryOptimizationStatus == BatteryOptimizationStatus.ENABLED -> {
                    mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
                    mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                    mainViewModel.updateBatteryOptimizationLoading(false)
                }
                else -> {
                    mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                    initializeApp()
                }
            }
        }
    }

    private fun handleOnboardingFailed(message: String) {
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }

    private fun checkBatteryOptimizationAndProceed() {
        if (permissionManager.isFirstTimeLaunch()) {
            proceedWithPermissionCheck()
            return
        }

        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)

        when (currentBatteryOptimizationStatus) {
            BatteryOptimizationStatus.DISABLED, BatteryOptimizationStatus.NOT_SUPPORTED -> proceedWithPermissionCheck()
            BatteryOptimizationStatus.ENABLED -> {
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }

    private fun handleBatteryOptimizationDisabled() {
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }

    private fun handleBatteryOptimizationFailed(message: String) {
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
        lifecycleScope.launch {
            try {
                delay(1000)
                if (!permissionManager.areAllPermissionsGranted()) {
                    handleOnboardingFailed("Some permissions were revoked. Please grant all permissions to continue.")
                    return@launch
                }

                // Wire up mesh delegate -> chatViewModel and start services
                meshService.delegate = chatViewModel as com.dogechat.android.mesh.BluetoothMeshDelegate
                meshService.startServices()

                // Handle notification intent
                handleNotificationIntent(intent)

                delay(500)
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
            try {
                meshService.connectionManager.setAppBackgroundState(false)
            } catch (_: Exception) { }
            chatViewModel.setAppBackgroundState(false)

            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            if (currentBluetoothStatus != BluetoothStatus.ENABLED) {
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
                return
            }

            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                meshService.connectionManager.setAppBackgroundState(true)
            } catch (_: Exception) { }
            chatViewModel.setAppBackgroundState(true)
        }
    }

    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.dogechat.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT,
            false
        )

        if (shouldOpenPrivateChat) {
            val peerID = intent.getStringExtra(com.dogechat.android.ui.NotificationManager.EXTRA_PEER_ID)
            if (peerID != null) {
                chatViewModel.startPrivateChat(peerID)
                chatViewModel.clearNotificationsForSender(peerID)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationStatusManager.cleanup()
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up location status manager: ${e.message}")
        }

        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                meshService.stopServices()
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping mesh services in onDestroy: ${e.message}")
            }
        }
    }
}
