package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*

@Composable
fun OrdersScreen(vm: GarpViewModel, onOrderClick: (String) -> Unit) {
    val state by vm.state

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp)
    ) {
        Text("Pesanan", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "${state.orders.size} pesanan • ${state.stats?.active ?: 0} aktif",
            style = MaterialTheme.typography.bodyMedium, color = TextMuted
        )
        Spacer(Modifier.height(12.dp))

        state.error?.let {
            Surface(
                color = AccentRed.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.loading && state.orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentGreen)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.orders, key = { it.id }) { order ->
                OrderCard(order = order, onClick = { onOrderClick(order.id) }, onDelete = { vm.deleteOrder(order.id) })
            }
        }
    }
}

@Composable
fun OrderCard(order: Order, onClick: () -> Unit, onDelete: () -> Unit) {
    val (color, label) = statusStyle(order.status)
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (order.merchant ?: "G").take(1).uppercase(),
                    color = color, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    order.merchant ?: "Tanpa merchant",
                    color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp
                )
                Text(
                    listOfNotNull(order.driverName, order.driverPlate).joinToString(" • ")
                        .ifEmpty { order.bookingCode ?: "—" },
                    color = TextMuted, fontSize = 12.sp
                )
                order.etaSeconds?.let {
                    Text("ETA ${formatEta(it)}", color = AccentYellow, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        label,
                        color = color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                    Text("Hapus", fontSize = 11.sp)
                }
            }
        }
    }
}

fun statusStyle(status: String?): Pair<Color, String> = when (status) {
    "prepare" -> AccentYellow to "Disiapkan"
    "ontheway" -> AccentBlue to "Di jalan"
    "completed" -> AccentGreen to "Selesai"
    "cancelled" -> AccentRed to "Dibatalkan"
    else -> TextMuted to (status ?: "—")
}

fun formatEta(seconds: Long): String {
    val m = seconds / 60
    return if (m >= 60) "${m / 60}j ${m % 60}m" else "${m}m ${seconds % 60}s"
}
