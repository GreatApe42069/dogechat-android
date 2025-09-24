package com.dogechat.android.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Centralized permission management for dogechat app
 * Handles all Bluetooth and notification permissions required for the app to function
 */
class PermissionManager(private val context: Context) {

    companion object {
        private const val TAG = "PermissionManager"
        private const val PREFS_NAME = "dogechat_permissions"
        private const val KEY_FIRST_TIME_COMPLETE = "first_time_onboarding_complete"

        // Default request codes (you can override when calling)
        const val PERMISSION_REQUEST_CODE_ALL = 1000
        const val PERMISSION_REQUEST_CODE_REQUIRED = 1001
        const val PERMISSION_REQUEST_CODE_OPTIONAL = 1002
        const val PERMISSION_REQUEST_CODE_MEDIA = 1003
    }

    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if this is the first time the user is launching the app
     */
    fun isFirstTimeLaunch(): Boolean {
        return !sharedPrefs.getBoolean(KEY_FIRST_TIME_COMPLETE, false)
    }

    /**
     * Mark the first-time onboarding as complete
     */
    fun markOnboardingComplete() {
        sharedPrefs.edit()
            .putBoolean(KEY_FIRST_TIME_COMPLETE, true)
            .apply()
        Log.d(TAG, "First-time onboarding marked as complete")
    }

    /**
     * Get all permissions required by the app
     * Note: Notification permission is optional and not included here,
     * so the app works without notification access.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()

        // Bluetooth permissions (API level dependent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            permissions.addAll(
                listOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                )
            )
        }

        // Location permissions (required for Bluetooth LE scanning)
        permissions.addAll(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )

        // Notification permission intentionally excluded to keep it optional

        return permissions
    }

    /**
     * Get optional permissions that improve the experience but aren't required.
     * Currently includes POST_NOTIFICATIONS on Android 13+ and media permissions.
     */
    fun getOptionalPermissions(): List<String> {
        val optional = mutableListOf<String>()

        // Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            optional.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Media permissions for file sharing
        optional.addAll(getMediaPermissionsForRuntime())

        // Audio recording for voice messages (optional)
        optional.add(Manifest.permission.RECORD_AUDIO)

        // Camera for photo capture (optional)
        optional.add(Manifest.permission.CAMERA)

        return optional
    }

    /**
     * Media permissions tailored to SDK:
     * - API 33+ (Tiramisu): READ_MEDIA_IMAGES/VIDEO/AUDIO
     * - API <= 32: READ_EXTERNAL_STORAGE
     */
    fun getMediaPermissionsForRuntime(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Check if a specific permission is granted
     */
    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if all required permissions are granted
     */
    fun areAllPermissionsGranted(): Boolean {
        return getRequiredPermissions().all { isPermissionGranted(it) }
    }

    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking battery optimization status", e)
                false
            }
        } else {
            // Battery optimization doesn't exist on Android < 6.0
            true
        }
    }

    /**
     * Check if battery optimization is supported on this device
     */
    fun isBatteryOptimizationSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    /**
     * Get the list of permissions that are missing (required only)
     */
    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { !isPermissionGranted(it) }
    }

    /**
     * Get the list of OPTIONAL permissions that are missing
     */
    fun getMissingOptionalPermissions(): List<String> {
        return getOptionalPermissions()
            .distinct()
            .filter { !isPermissionGranted(it) }
    }

    /**
     * Get the list of MEDIA permissions that are missing (SDK-aware)
     */
    fun getMissingMediaPermissions(): List<String> {
        return getMediaPermissionsForRuntime().filter { !isPermissionGranted(it) }
    }

    /**
     * Build a combined list of required + (optionally) optional permissions for runtime request,
     * de-duplicated and filtered to only those not yet granted.
     */
    fun buildRuntimePermissions(includeOptional: Boolean = true): Array<String> {
        val all = mutableListOf<String>()
        all += getRequiredPermissions()
        if (includeOptional) {
            all += getOptionalPermissions()
        }
        // Only request missing ones; dedupe
        return all.distinct().filter { !isPermissionGranted(it) }.toTypedArray()
    }

    /**
     * Request ONLY required permissions at runtime (if any are missing).
     * Returns true if a request was launched.
     */
    fun requestRequiredPermissions(
        activity: Activity,
        requestCode: Int = PERMISSION_REQUEST_CODE_REQUIRED
    ): Boolean {
        val missing = getMissingPermissions()
        return if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
            true
        } else {
            false
        }
    }

    /**
     * Request ONLY optional permissions at runtime (if any are missing).
     * Returns true if a request was launched.
     */
    fun requestOptionalPermissions(
        activity: Activity,
        requestCode: Int = PERMISSION_REQUEST_CODE_OPTIONAL
    ): Boolean {
        val missing = getMissingOptionalPermissions()
        return if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
            true
        } else {
            false
        }
    }

    /**
     * Request SDK-aware MEDIA permissions:
     * - API 33+: READ_MEDIA_IMAGES/VIDEO/AUDIO
     * - API <= 32: READ_EXTERNAL_STORAGE
     * Returns true if a request was launched.
     */
    fun requestMediaPermissions(
        activity: Activity,
        requestCode: Int = PERMISSION_REQUEST_CODE_MEDIA
    ): Boolean {
        val missing = getMissingMediaPermissions()
        return if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
            true
        } else {
            false
        }
    }

    /**
     * Request BOTH required and optional permissions at runtime (if any are missing).
     * Uses a single request code for the combined request.
     * Returns true if a request was launched.
     */
    fun requestAllRuntimePermissions(
        activity: Activity,
        includeOptional: Boolean = true,
        requestCode: Int = PERMISSION_REQUEST_CODE_ALL
    ): Boolean {
        val toRequest = buildRuntimePermissions(includeOptional)
        return if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, toRequest, requestCode)
            true
        } else {
            false
        }
    }

    /**
     * Get categorized permission information for display
     */
    fun getCategorizedPermissions(): List<PermissionCategory> {
        val categories = mutableListOf<PermissionCategory>()

        // Bluetooth/Nearby Devices category
        val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        categories.add(
            PermissionCategory(
                type = PermissionType.NEARBY_DEVICES,
                description = "Required to discover dogechat users via Bluetooth",
                permissions = bluetoothPermissions,
                isGranted = bluetoothPermissions.all { isPermissionGranted(it) },
                systemDescription = "Allow dogechat to connect to nearby devices"
            )
        )

        // Location category
        val locationPermissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        categories.add(
            PermissionCategory(
                type = PermissionType.PRECISE_LOCATION,
                description = "Required by Android to discover nearby dogechat users via Bluetooth",
                permissions = locationPermissions,
                isGranted = locationPermissions.all { isPermissionGranted(it) },
                systemDescription = "dogechat needs this to scan for nearby devices"
            )
        )

        // Notifications category (if applicable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            categories.add(
                PermissionCategory(
                    type = PermissionType.NOTIFICATIONS,
                    description = "Receive notifications when you receive private messages",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    isGranted = isPermissionGranted(Manifest.permission.POST_NOTIFICATIONS),
                    systemDescription = "Allow dogechat to send you notifications"
                )
            )
        }

        // Media access category (optional)
        val mediaPermissions = getMediaPermissionsForRuntime()

        categories.add(
            PermissionCategory(
                type = PermissionType.MEDIA_ACCESS,
                description = "Optional: Access photos, videos, and audio files for sharing in chats",
                permissions = mediaPermissions,
                isGranted = mediaPermissions.all { isPermissionGranted(it) },
                systemDescription = "Allow dogechat to access your media files"
            )
        )

        // Microphone category (optional)
        categories.add(
            PermissionCategory(
                type = PermissionType.MICROPHONE,
                description = "Optional: Record voice messages to share in chats",
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
                isGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO),
                systemDescription = "Allow dogechat to record audio"
            )
        )

        // Camera category (optional)
        categories.add(
            PermissionCategory(
                type = PermissionType.CAMERA,
                description = "Optional: Take photos to share in chats",
                permissions = listOf(Manifest.permission.CAMERA),
                isGranted = isPermissionGranted(Manifest.permission.CAMERA),
                systemDescription = "Allow dogechat to take pictures"
            )
        )

        // Battery optimization category (if applicable)
        if (isBatteryOptimizationSupported()) {
            categories.add(
                PermissionCategory(
                    type = PermissionType.BATTERY_OPTIMIZATION,
                    description = "Disable battery optimization to ensure dogechat runs reliably in the background and maintains mesh network connections",
                    permissions = listOf("BATTERY_OPTIMIZATION"), // Custom identifier
                    isGranted = isBatteryOptimizationDisabled(),
                    systemDescription = "Allow dogechat to run without battery restrictions"
                )
            )
        }

        return categories
    }

    /**
     * Get detailed diagnostic information about permission status
     */
    fun getPermissionDiagnostics(): String {
        return buildString {
            appendLine("Permission Diagnostics:")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("First time launch: ${isFirstTimeLaunch()}")
            appendLine("All permissions granted (required): ${areAllPermissionsGranted()}")
            appendLine()

            getCategorizedPermissions().forEach { category ->
                appendLine("${category.type.nameValue}: ${if (category.isGranted) "✅ GRANTED" else "❌ MISSING"}")
                category.permissions.forEach { permission ->
                    val granted = isPermissionGranted(permission)
                    appendLine("  - ${permission.substringAfterLast(".")}: ${if (granted) "✅" else "❌"}")
                }
                appendLine()
            }

            val missingRequired = getMissingPermissions()
            val missingOptional = getMissingOptionalPermissions()
            if (missingRequired.isNotEmpty() || missingOptional.isNotEmpty()) {
                appendLine("Missing permissions:")
                missingRequired.forEach { permission ->
                    appendLine("- [REQUIRED] $permission")
                }
                missingOptional.forEach { permission ->
                    appendLine("- [OPTIONAL] $permission")
                }
            }
        }
    }

    /**
     * Log permission status for debugging
     */
    fun logPermissionStatus() {
        Log.d(TAG, getPermissionDiagnostics())
    }
}

/**
 * Data class representing a category of related permissions
 */
data class PermissionCategory(
    val type: PermissionType,
    val description: String,
    val permissions: List<String>,
    val isGranted: Boolean,
    val systemDescription: String
)

enum class PermissionType(val nameValue: String) {
    NEARBY_DEVICES("Nearby Devices"),
    PRECISE_LOCATION("Precise Location"),
    NOTIFICATIONS("Notifications"),
    BATTERY_OPTIMIZATION("Battery Optimization"),
    MEDIA_ACCESS("Media Access"),
    MICROPHONE("Microphone"),
    CAMERA("Camera"),
    OTHER("Other")
}