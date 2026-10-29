package com.garp.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private var initialLink: String? = null

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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        web.visibility = View.VISIBLE
        web.loadUrl(url)

        web.setOnLongClickListener {
            showServerDialog()
            true
        }

        pendingLink = try { extractUrl(intent) } catch (_: Exception) { null }
        pendingLink?.let { initialLink = it }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val cur = Uri.parse(getServerUrlSafe())
                val sameHost = u.host != null && (u.host == cur.host || u.host!!.endsWith(".grab.com") || u.host!!.endsWith(".openfreemap.org"))
                return if (sameHost) {
                    false
                } else {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                    true
                }
            }
            override fun onPageFinished(view: WebView, u: String?) {
                initialLink?.let { injectLink(it); initialLink = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            extractUrl(intent)?.let { link ->
                pendingLink = link
                injectLink(link)
            }
        } catch (_: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        try { pendingLink?.let { injectLink(it) } } catch (_: Exception) { }
    }

    private fun getServerUrlSafe(): String =
        getPreferences(Context.MODE_PRIVATE).getString(PREF_SERVER_URL, null) ?: DEFAULT_URL

    private fun showServerDialog() {
        try {
            val input = android.widget.EditText(this).apply {
                setText(getServerUrlSafe())
                hint = "https://grab.projectpop.xyz"
                setSingleLine(true)
                setPadding(48, 40, 48, 16)
            }
            android.app.AlertDialog.Builder(this)
                .setTitle("Server URL")
                .setMessage("Long-press di mana saja untuk buka dialog ini.")
                .setView(input)
                .setPositiveButton("Simpan") { _, _ ->
                    var u = input.text.toString().trim()
                    if (u.isNotEmpty()) {
                        if (!u.startsWith("http")) u = "https://$u"
                        saveServerUrl(u)
                        web.loadUrl(u)
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        } catch (_: Exception) { }
    }

    private fun saveServerUrl(u: String) {
        getPreferences(Context.MODE_PRIVATE).edit().putString(PREF_SERVER_URL, u).apply()
    }

    private fun injectLink(link: String) {
        try {
            val json = org.json.JSONObject.quote(link)
            web.post {
                try {
                    web.evaluateJavascript(
                        "window.__GARP_SHARED_LINK = $json; try { window.dispatchEvent(new Event('garp-shared-link')); } catch (e) {}", null
                    )
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
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
