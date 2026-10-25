package com.garp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garp.tracker.data.GarpApi
import com.garp.tracker.data.Order
import com.garp.tracker.data.Stats
import kotlinx.coroutines.launch

data class AppState(
    val orders: List<Order> = emptyList(),
    val stats: Stats? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class GarpViewModel(serverUrl: String) : ViewModel() {

    private val api = GarpApi(serverUrl)

    var state by mutableStateOf(AppState())
        private set

    init {
        refresh()
        // Polling ringan tiap 8 detik, selaras frontend
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(8000)
                runCatching { refresh(silent = true) }
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) state = state.copy(loading = true, error = null)
            try {
                val stats = api.stats()
                val orders = api.orders()
                state = state.copy(stats = stats, orders = orders, loading = false, error = null)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Gagal memuat data")
            }
        }
    }

    /** Tambah pesanan via link, lalu pindah fokus ke halaman detail pesanan baru. */
    fun addOrder(link: String, onAdded: (String) -> Unit = {}) {
        viewModelScope.launch {
            state = state.copy(loading = true)
            try {
                val order = api.addOrder(link)
                refresh(silent = true)
                onAdded(order.id)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = "Gagal menambah pesanan: ${e.message}")
            }
        }
    }

    fun deleteOrder(id: String) {
        viewModelScope.launch {
            try {
                api.deleteOrder(id)
                state = state.copy(orders = state.orders.filterNot { it.id == id })
                refresh(silent = true)
            } catch (e: Exception) {
                state = state.copy(error = "Gagal menghapus: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        api.close()
        super.onCleared()
    }
}
