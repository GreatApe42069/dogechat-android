package com.dogechat.android.onboarding

import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

/**
 * LocationCheckScreen
 *
 * Ensures location is enabled + permission granted before Dogechat enables
 * geohash-based local channels and peer discovery improvements from upstream.
 *
 * onReady: called when location services are enabled AND the permission is granted.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationCheckScreen(
    modifier: Modifier = Modifier,
    onReady: () -> Unit,
    // allow custom titles if you want to theme later
    title: String = "Enable Location for Dogechat",
    subtitle: String = "Dogechat uses approximate location to power geohash-based channels near you. " +
            "We never store your precise location."
) {
    val context = LocalContext.current
    val locationManager = remember {
        context.getSystemService(LocationManager::class.java)
    }

    val fineLocationPermission = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    val isProviderEnabled by remember {
        mutableStateOf(
            (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) ||
            (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true)
        )
    }

    // If permission is already granted and provider is enabled, proceed
    SideEffect {
        if (fineLocationPermission.status.isGranted && isProviderEnabled) {
            onReady()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        if (!fineLocationPermission.status.isGranted) {
            Button(
                onClick = { fineLocationPermission.launchPermissionRequest() }
            ) {
                Text("Allow Location Permission")
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!isProviderEnabled) {
            Button(
                onClick = {
                    // Open system Location settings so user can enable GPS/Network location
                    context.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            ) {
                Text("Open Location Settings")
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = {
                if (fineLocationPermission.status.isGranted && isProviderEnabled) {
                    onReady()
                }
            },
            enabled = fineLocationPermission.status.isGranted && isProviderEnabled
        ) {
            Text("Continue to Dogechat")
        }
    }
}
