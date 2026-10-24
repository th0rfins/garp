package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*

@Composable
fun OrderDetailScreen(order: Order?, loading: Boolean, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = AccentGreen)) {
                Text("← Kembali")
            }
        }

        if (loading) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGreen)
            }
            return@Column
        }

        if (order == null) {
            Text("Pesanan tidak ditemukan", color = TextMuted)
            return@Column
        }

        val (color, label) = statusStyle(order.status)
        Surface(
            color = CardBg, shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape).background(color)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    order.etaSeconds?.let {
                        Text("ETA ${formatEta(it)}", color = AccentYellow, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(order.merchant ?: "—", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("Booking: ${order.bookingCode ?: "—"}", color = TextMuted, fontSize = 13.sp)
                Text(order.link ?: "", color = AccentBlue, fontSize = 12.sp)
            }
        }

        // Driver card
        Surface(
            color = CardBg, shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Driver", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                DriverRow("Nama", order.driverName)
                DriverRow("Kendaraan", order.driverVehicle)
                DriverRow("Plat", order.driverPlate)
                if (order.driverLat != null && order.driverLng != null) {
                    DriverRow("Posisi", "%.5f, %.5f".format(order.driverLat!!, order.driverLng!!))
                }
            }
        }

        // Tujuan card
        Surface(
            color = CardBg, shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Tujuan", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                if (order.destLat != null && order.destLng != null) {
                    Text(
                        "%.5f, %.5f".format(order.destLat!!, order.destLng!!),
                        color = TextMuted, fontSize = 13.sp
                    )
                } else {
                    Text("Belum ada koordinat tujuan", color = TextMuted, fontSize = 13.sp)
                }
                order.createdAt?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Dibuat: $it", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DriverRow(label: String, value: String?) {
    Row {
        Text(label, color = TextMuted, fontSize = 13.sp, modifier = Modifier.width(90.dp))
        Text(value ?: "—", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
