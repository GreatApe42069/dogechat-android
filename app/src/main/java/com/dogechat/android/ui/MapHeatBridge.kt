package com.dogechat.android.ui

import android.webkit.WebView
import com.google.gson.Gson
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * MapHeatBridge
 * - Holds a weak ref to the active WebView.
 * - Buffers recent heat points for warm start.
 * - Streams batches every call (caller decides throttling).
 * - Pushes label counts to the map.
 */
object MapHeatBridge {

    data class HeatPoint(val lat: Double, val lng: Double, val intensity: Double, val ts: Long)

    private val gson = Gson()
    private var webViewRef: WeakReference<WebView>? = null

    // Rolling buffer for warm start (default 5 minutes TTL; adjustable via setter)
    private val recent = CopyOnWriteArrayList<HeatPoint>()
    @Volatile private var ttlMs: Long = 300_000L

    fun attach(webView: WebView?) {
        if (webView == null) return
        webViewRef = WeakReference(webView)
        // Push TTL and warm start immediately
        val jsTTL = "window.setHeatmapTTL(${ttlMs});"
        webView.evaluateJavascript(jsTTL, null)
        pushWarmStart()
    }

    fun setTTL(ms: Long) {
        ttlMs = ms
        webViewRef?.get()?.evaluateJavascript("window.setHeatmapTTL(${ttlMs});", null)
        prune()
    }

    fun warmStart(points: List<HeatPoint>) {
        val now = System.currentTimeMillis()
        recent.clear()
        recent.addAll(points.filter { now - it.ts <= ttlMs })
        pushWarmStart()
    }

    private fun pushWarmStart() {
        val wv = webViewRef?.get() ?: return
        val arr = recent.map { listOf(it.lat, it.lng, it.intensity) }
        val json = gson.toJson(arr)
        wv.evaluateJavascript("window.setHeatmap(${json});", null)
    }

    fun streamBatch(points: List<HeatPoint>) {
        if (points.isEmpty()) return
        val now = System.currentTimeMillis()
        recent.addAll(points)
        prune()
        val arr = points.map { listOf(it.lat, it.lng, it.intensity) }
        val json = gson.toJson(arr)
        webViewRef?.get()?.evaluateJavascript("window.addHeatPoints(${json});", null)
    }

    fun setCounts(counts: Map<String, Int>) {
        val json = gson.toJson(counts)
        webViewRef?.get()?.evaluateJavascript("window.setGeohashCounts(${json});", null)
    }

    private fun prune() {
        val cutoff = System.currentTimeMillis() - ttlMs
        recent.removeAll { it.ts < cutoff }
    }
}