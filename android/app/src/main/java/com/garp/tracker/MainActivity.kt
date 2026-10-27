package com.garp.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * GARP v3 — WebView shell di atas web app (port 8000 / grab.projectpop.xyz).
 * Fitur native:
 *  - ACTION_SEND: share link Grab dari app lain -> langsung masuk form add di web (JS bridge).
 *  - AndroidBridge.addLink(url): dipanggil web JS (dipakai web untuk terima link dari share).
 *  - Back button = navigasi history WebView.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var pendingLink: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        var url = prefs.getString(PREF_SERVER_URL, null) ?: DEFAULT_URL

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.setBackgroundColor(0xFF0A0A0B.toInt())
        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Buka link external (app.grab.com dll) di browser, bukan di app
                val u = request.url
                return if (u.host != null && u.host!!.endsWith("projectpop.xyz") || u.host == "10.0.2.2" || u.host == "localhost") {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, u))
                    true
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        // Hilangkan splash flash: visible setelah page pertama
        web.visibility = View.VISIBLE
        web.loadUrl(url)

        pendingLink = extractUrl(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractUrl(intent)?.let { link ->
            pendingLink = link
            // Halaman belum tentu selesai load; coba inject, kalau belum tunggu onLoad end
            injectLink(link)
        }
    }

    override fun onResume() {
        super.onResume()
        pendingLink?.let { injectLink(it) }
    }

    private fun injectLink(link: String) {
        val json = org.json.JSONStringer().value(link).toString()
        web.evaluateJavascript(
            "window.__GARP_SHARED_LINK = $json; window.dispatchEvent(new Event('garp-shared-link'));", null
        )
        pendingLink = null
    }

    private fun extractUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val m = Regex("https?://\\S+").find(text) ?: return null
        val u = m.value
        return if (u.contains("app.grab.com") || u.contains("sharelocation.grab.com")) u else null
    }

    inner class Bridge {
        /** Dipakai web JS kalau mau ambil shared link terakhir. */
        @JavascriptInterface
        fun consumeSharedLink(): String? {
            val l = pendingLink
            pendingLink = null
            return l
        }

        /** Simpan server URL dari web (web panggil saat user ganti server). */
        @JavascriptInterface
        fun saveServerUrl(u: String) {
            getPreferences(Context.MODE_PRIVATE).edit().putString(PREF_SERVER_URL, u).apply()
        }

        @JavascriptInterface
        fun getServerUrl(): String = getPreferences(Context.MODE_PRIVATE).getString(PREF_SERVER_URL, null) ?: DEFAULT_URL
    }

    companion object {
        const val PREF_SERVER_URL = "server_url"
        // TODO ganti kalau domain berubah; web sendiri menangani input link
        const val DEFAULT_URL = "https://grab.projectpop.xyz"
    }
}
