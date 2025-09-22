package com.dogechat.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updateLayoutParams
import com.dogechat.android.geohash.Geohash
import com.dogechat.android.geohash.GeohashBookmarksStore
import com.dogechat.android.geohash.LocationChannelManager
import com.dogechat.android.ui.theme.BASE_FONT_SIZE
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
class GeohashPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_INITIAL_GEOHASH = "initial_geohash"
        const val EXTRA_RESULT_GEOHASH = "result_geohash"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialGeohash = intent.getStringExtra(EXTRA_INITIAL_GEOHASH)?.trim()?.lowercase()
        var geohashToFocus: String? = null
        var (initLat, initLon) = 0.0 to 0.0

        if (!initialGeohash.isNullOrEmpty()) {
            geohashToFocus = initialGeohash
            runCatching {
                val (lat, lon) = Geohash.decodeToCenter(initialGeohash)
                initLat = lat; initLon = lon
            }
        } else {
            val locationManager = LocationChannelManager.getInstance(applicationContext)
            val channels = locationManager.availableChannels.value
            if (!channels.isNullOrEmpty()) {
                val coarsest = channels.minByOrNull { it.geohash.length }
                if (coarsest != null) {
                    geohashToFocus = coarsest.geohash
                    runCatching {
                        val (lat, lon) = Geohash.decodeToCenter(coarsest.geohash)
                        initLat = lat; initLon = lon
                    }
                }
            }
        }

        val initialPrecision = geohashToFocus?.length ?: 5

        setContent {
            MaterialTheme {
                var currentGeohash by remember { mutableStateOf(geohashToFocus ?: "") }
                var precision by remember { mutableStateOf(initialPrecision.coerceIn(1, 12)) }
                var webViewRef by remember { mutableStateOf<WebView?>(null) }

                val dogeGold = Color(0xFFFFD700)

                Scaffold { padding ->
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    webChromeClient = WebChromeClient()

                                    val gson = Gson()

                                    // Batch buffer for ~500ms streaming cadence
                                    val uiHandler = Handler(Looper.getMainLooper())
                                    val batchBuffer = mutableListOf<List<Double>>()
                                    var flushScheduled = false
                                    fun scheduleFlush() {
                                        if (flushScheduled) return
                                        flushScheduled = true
                                        uiHandler.postDelayed({
                                            try {
                                                val payload = ArrayList(batchBuffer)
                                                batchBuffer.clear()
                                                if (payload.isNotEmpty()) {
                                                    evaluateJavascript("window.addHeatPoints(${gson.toJson(payload)});", null)
                                                }
                                            } finally {
                                                flushScheduled = false
                                            }
                                        }, 500)
                                    }

                                    // Listener for counts + points (from GeohashViewModel via HeatStreamBus)
                                    val heatListener = object : HeatStreamBus.Listener {
                                        override fun onCounts(counts: Map<String, Int>) {
                                            runCatching {
                                                evaluateJavascript("window.setGeohashCounts(${gson.toJson(counts)});", null)
                                            }
                                        }
                                        override fun onPoints(points: List<HeatStreamBus.HeatPoint>) {
                                            if (points.isEmpty()) return
                                            points.forEach { p ->
                                                batchBuffer.add(listOf(p.lat, p.lng, p.intensity))
                                            }
                                            scheduleFlush()
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)

                                            // Theme for tiles
                                            val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                                            val theme = if (nightMode == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
                                            evaluateJavascript("window.setMapTheme('$theme')", null)

                                            // Focus center
                                            if (!geohashToFocus.isNullOrEmpty()) {
                                                evaluateJavascript("window.focusGeohash('${geohashToFocus}')", null)
                                            } else {
                                                evaluateJavascript("window.setCenter($initLat, $initLon)", null)
                                            }

                                            // TTL 5 minutes and warm start
                                            evaluateJavascript("window.setHeatmapTTL(300000);", null)
                                            HeatStreamBus.setTTL(300_000L)

                                            val warm = HeatStreamBus.getWarmPointsSnapshot()
                                            if (warm.isNotEmpty()) {
                                                val arr = warm.map { listOf(it.lat, it.lng, it.intensity) }
                                                evaluateJavascript("window.setHeatmap(${gson.toJson(arr)});", null)
                                            }
                                            val cSnap = HeatStreamBus.getCountsSnapshot()
                                            if (cSnap.isNotEmpty()) {
                                                evaluateJavascript("window.setGeohashCounts(${gson.toJson(cSnap)});", null)
                                            }

                                            // Mirror favorites into the map (optional)
                                            runCatching {
                                                val favs = GeohashBookmarksStore.getInstance(applicationContext).bookmarks.value ?: emptyList()
                                                evaluateJavascript("window.setFavorites(${gson.toJson(favs)});", null)
                                            }

                                            // Subscribe live
                                            HeatStreamBus.addListener(heatListener)
                                        }
                                    }

                                    // JS Bridge: selection + favorites + share
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onGeohashChanged(geohash: String) {
                                            runOnUiThread { currentGeohash = geohash }
                                        }

                                        @JavascriptInterface
                                        fun onFavoriteChanged(gh: String, isFav: Boolean) {
                                            runCatching {
                                                val store = GeohashBookmarksStore.getInstance(applicationContext)
                                                val already = store.isBookmarked(gh)
                                                if (isFav && !already) store.toggle(gh)
                                                if (!isFav && already) store.toggle(gh)
                                            }
                                        }

                                        @JavascriptInterface
                                        fun shareText(text: String) {
                                            // Optional hook - your app can route this to Android Sharesheet if desired
                                        }
                                    }, "Android")

                                    // Load local HTML
                                    loadUrl("file:///android_asset/geohash_picker.html")

                                    // NOTE: We remove HeatStreamBus listener in onRelease below,
                                    // so we don't need a fragile ViewGroup.OnHierarchyChangeListener here.
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            update = { webView ->
                                webViewRef = webView
                                webView.updateLayoutParams<ViewGroup.LayoutParams> {
                                    width = ViewGroup.LayoutParams.MATCH_PARENT
                                    height = ViewGroup.LayoutParams.MATCH_PARENT
                                }
                            },
                            onRelease = { webView ->
                                runCatching { HeatStreamBus.clearAllListeners() }
                                runCatching { webView.evaluateJavascript("window.cleanup && window.cleanup()", null) }
                                runCatching { webView.stopLoading() }
                                runCatching { webView.clearHistory() }
                                runCatching { webView.clearCache(true) }
                                runCatching { webView.loadUrl("about:blank") }
                                runCatching { webView.removeAllViews() }
                                runCatching { webView.destroy() }
                            }
                        )

                        // Bottom controls (Doge Gold)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Geohash label
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 3.dp,
                                shadowElevation = 6.dp
                            ) {
                                Text(
                                    text = if (currentGeohash.isNotEmpty()) "#${currentGeohash}" else "select location",
                                    fontSize = BASE_FONT_SIZE.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Decrease precision
                                Button(
                                    onClick = {
                                        precision = (precision - 1).coerceAtLeast(1)
                                        webViewRef?.evaluateJavascript("window.setPrecision($precision)", null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = dogeGold.copy(alpha = 0.12f),
                                        contentColor = dogeGold
                                    )
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Decrease precision"
                                    )
                                }

                                // Increase precision
                                Button(
                                    onClick = {
                                        precision = (precision + 1).coerceAtMost(12)
                                        webViewRef?.evaluateJavascript("window.setPrecision($precision)", null)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = dogeGold.copy(alpha = 0.12f),
                                        contentColor = dogeGold
                                    )
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Increase precision"
                                    )
                                }

                                // Select
                                Button(
                                    onClick = {
                                        webViewRef?.evaluateJavascript("window.getGeohash()") { value ->
                                            val gh = value?.trim('"') ?: currentGeohash
                                            val result = Intent().apply { putExtra(EXTRA_RESULT_GEOHASH, gh) }
                                            setResult(Activity.RESULT_OK, result)
                                            finish()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Select geohash"
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "select",
                                            fontSize = (BASE_FONT_SIZE - 2).sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}