package com.garp.tracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.garp.tracker.data.GarpApi
import com.garp.tracker.ui.GarpApp
import com.garp.tracker.ui.theme.GarpTheme

class MainActivity : ComponentActivity() {

    private var serverUrl by mutableStateOf(GarpApi.DEFAULT_BASE_URL)
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        serverUrl = getPreferences(Context.MODE_PRIVATE)
            .getString(PREF_SERVER_URL, GarpApi.DEFAULT_BASE_URL) ?: GarpApi.DEFAULT_BASE_URL
        sharedLink = extractUrl(intent?.getStringExtra(Intent.EXTRA_TEXT))

        setContent {
            MaterialTheme {
                GarpApp(
                    initialLink = sharedLink,
                    serverUrl = serverUrl,
                    onServerUrlChange = { saveServerUrl(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { sharedLink = it }
    }

    private fun saveServerUrl(url: String) {
        val trimmed = url.trim().trimEnd('/')
        getPreferences(Context.MODE_PRIVATE)
            .edit().putString(PREF_SERVER_URL, trimmed).apply()
        serverUrl = trimmed
    }

    companion object {
        const val PREF_SERVER_URL = "server_url"
        private val URL_REGEX = Regex("https?://\\S+")

        /** Ekstrak URL dari teks share seperti: 'Lacak pesanan saya dari Warung... https://app.grab.com/s/xxxx' */
        fun extractUrl(text: String?): String? =
            text?.let { URL_REGEX.find(it)?.value }?.takeIf {
                it.contains("app.grab.com") || it.contains("sharelocation.grab.com")
            }
    }
}
