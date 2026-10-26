package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.data.GarpApi
import com.garp.tracker.ui.theme.*

/** Navigasi 3 tab + layar tambah link, selaras frontend (map / orders / analytics). */
@Composable
fun GarpApp(
    initialLink: String?,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
) {
    val vm = remember(serverUrl) { GarpViewModel(serverUrl) }
    var tab by remember { mutableStateOf(0) }
    var selectedOrderId by remember { mutableStateOf<String?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(true) } // dialog first-launch; ditutup user

    LaunchedEffect(initialLink) {
        if (initialLink != null) {
            vm.addOrder(initialLink) { newId -> selectedOrderId = newId }
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { showServerDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Server URL", tint = TextMuted)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                listOf(
                    Triple(Icons.Filled.Map, "Peta", 0),
                    Triple(Icons.Filled.List, "Pesanan", 1),
                    Triple(Icons.Filled.BarChart, "Analitik", 2),
                ).forEach { (icon, label, idx) ->
                    NavigationBarItem(
                        selected = tab == idx && selectedOrderId == null,
                        onClick = { tab = idx; selectedOrderId = null },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedOrderId == null) {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = Accent,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Add, "Tempel link")
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                selectedOrderId != null -> {
                    val order = selectedOrderId?.let { id -> vm.state.orders.find { it.id == id } }
                    OrderDetailScreen(order = order, loading = false, onBack = { selectedOrderId = null })
                }
                tab == 0 -> MapScreen(vm, onOrderClick = { selectedOrderId = it })
                tab == 1 -> OrdersScreen(vm, onOrderClick = { selectedOrderId = it })
                tab == 2 -> AnalyticsScreen(vm)
            }
        }
    }

    if (showAddSheet) {
        AddOrderSheet(onDismiss = { showAddSheet = false }, onSubmit = { link ->
            showAddSheet = false
            vm.addOrder(link) { newId -> selectedOrderId = newId }
        })
    }

    if (showServerDialog) {
        ServerUrlDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = { url ->
                showServerDialog = false
                onServerUrlChange(url)
            }
        )
    }
}

@Composable
fun ServerUrlDialog(currentUrl: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(currentUrl) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        titleContentColor = TextPrimary,
        textContentColor = TextMuted,
        title = { Text("Server URL") },
        text = {
            Column {
                Text("Alamat backend GARP (FastAPI). Default emulator: http://10.0.2.2:8000", fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text(GarpApi.DEFAULT_BASE_URL, color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Border,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent,
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onSave(url) }, enabled = url.isNotBlank()) {
                Text("Simpan", color = Accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TextMuted) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOrderSheet(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBg) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tempel link GrabFood", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                "Contoh: https://app.grab.com/s/XXXXXXXX atau https://sharelocation.grab.com/o/TOKEN",
                color = TextMuted, fontSize = 12.sp
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = { Text("https://app.grab.com/s/…", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent,
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = { if (link.isNotBlank()) onSubmit(link.trim()) },
                enabled = link.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = androidx.compose.ui.graphics.Color(0xFF06130B)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Lacak pesanan", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
