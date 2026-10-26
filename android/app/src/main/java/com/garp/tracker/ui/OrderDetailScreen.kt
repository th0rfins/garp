package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = Accent)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Kembali")
            }
        }

        if (order == null) {
            Text("Pesanan tidak ditemukan", color = TextMuted)
            return@Column
        }

        val (color, label) = statusStyle(order.status)

        // Status card
        Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val done = order.status == "completed"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    val etaText = when {
                        done -> "Tiba ✓"
                        order.etaSeconds != null -> "ETA ${formatEta(order.etaSeconds)}"
                        else -> null
                    }
                    etaText?.let { Text(it, color = AccentYellow, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
                Text(order.merchant ?: "—", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text("Booking: ${order.bookingCode ?: "—"}", color = TextFaint, fontSize = 11.sp,
                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
                order.statusMessage?.takeIf { it.isNotBlank() }?.let {
                    Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(it, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                    }
                }
                if (order.multiStop == 1 && order.etaSeconds != null && !done) {
                    Surface(color = AccentYellow.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Double order — ETA dihitung ke stop berikutnya rute driver",
                            color = AccentYellow, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        // Driver card: foto + rating + kendaraan
        Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Driver", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!order.driverPhoto.isNullOrBlank()) {
                        AsyncImage(model = order.driverPhoto, contentDescription = order.driverName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(Surface))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column {
                        Text(order.driverName ?: "Mencari driver…", color = TextPrimary,
                            fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            listOfNotNull(
                                order.driverRating?.let { "★ $it" },
                                order.driverVehicle,
                                order.driverPlate
                            ).joinToString(" · ").ifEmpty { "—" },
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                }
                if (order.driverLat != null && order.driverLng != null) {
                    DriverRow("Posisi", "%.5f, %.5f".format(order.driverLat!!, order.driverLng!!))
                }
                toWibTime(order.driverArrivalTime)?.let { DriverRow("Driver tiba", "$it WIB") }
            }
        }

        // Tujuan + waktu card
        Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perjalanan", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (!order.destText.isNullOrBlank()) DriverRow("Tujuan", order.destText)
                if (order.destLat != null && order.destLng != null) {
                    DriverRow("Koordinat", "%.5f, %.5f".format(order.destLat!!, order.destLng!!))
                }
                (order.createdAt ?: "").take(16).takeLast(5).takeIf { it.isNotBlank() }?.let {
                    DriverRow("Masuk", it)
                }
                toWibTime(order.dropoffTime)?.let { DriverRow("Selesai", "$it WIB") }
            }
        }

        if (!order.link.isNullOrBlank()) {
            TextButton(onClick = { /* open link via android intent di luar */ },
                colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)) {
                Text("Buka link asli", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DriverRow(label: String, value: String?) {
    Row {
        Text(label, color = TextFaint, fontSize = 12.sp, modifier = Modifier.width(90.dp))
        Text(value ?: "—", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
    }
}
