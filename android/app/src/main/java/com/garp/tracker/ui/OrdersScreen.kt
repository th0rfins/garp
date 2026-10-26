package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*

private const val PAGE_SIZE = 15

@Composable
fun OrdersScreen(vm: GarpViewModel, onOrderClick: (String) -> Unit) {
    val state = vm.state
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(1) }

    val filtered = state.orders.filter { o ->
        query.isBlank() || (o.merchant ?: "").contains(query, true) ||
            (o.bookingCode ?: "").contains(query, true)
    }
    val pages = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    if (page > pages) page = pages
    val pageItems = filtered.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Pesanan", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(
            "${state.orders.size} pesanan • ${state.stats?.active ?: 0} aktif",
            style = MaterialTheme.typography.bodySmall, color = TextMuted
        )
        Spacer(Modifier.height(10.dp))

        // Search bar
        Surface(color = CardBg, shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = TextFaint, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it; page = 1 },
                    singleLine = true,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(Accent),
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Cari merchant atau kode booking…", color = TextFaint, fontSize = 13.sp)
                        inner()
                    }
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        state.error?.let {
            Surface(color = AccentRed.copy(alpha = 0.12f), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = AccentRed, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (state.loading && state.orders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(pageItems, key = { it.id }) { order ->
                Row(Modifier.animateItem()) {
                    OrderRow(order = order, onClick = { onOrderClick(order.id) }, onDelete = { vm.deleteOrder(order.id) }, modifier = Modifier)
                }
            }
        }

        // Pagination
        if (filtered.size > PAGE_SIZE) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${(page - 1) * PAGE_SIZE + 1}–${minOf(page * PAGE_SIZE, filtered.size)} dari ${filtered.size}",
                    color = TextFaint, fontSize = 11.sp, fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (page > 1) page-- }, enabled = page > 1,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted, disabledContentColor = TextFaint)) { Icon(Icons.Filled.ChevronLeft, "Sebelumnya", modifier = Modifier.size(18.dp)) }
                    (1..pages).take(5).forEach { p ->
                        Text(p.toString(),
                            color = if (p == page) Color.White else TextMuted,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (p == page) Accent else Color.Transparent)
                                .clickable { page = p }
                                .padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                    TextButton(onClick = { if (page < pages) page++ }, enabled = page < pages,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMuted, disabledContentColor = TextFaint)) { Icon(Icons.Filled.ChevronRight, "Berikutnya", modifier = Modifier.size(18.dp)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Baris 1-line: [avatar] nama+kode | badge | jam | hapus — selaras web. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderRow(order: Order, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val (color, label) = statusStyle(order.status)
    var confirmDelete by remember { mutableStateOf(false) }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OrderAvatar(order, size = 38)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(order.merchant ?: "Tanpa nama", color = TextPrimary, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(order.bookingCode ?: "—", color = TextFaint, fontSize = 10.5.sp,
                    fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
            }
            Spacer(Modifier.width(8.dp))
            Surface(color = color.copy(alpha = 0.13f), shape = RoundedCornerShape(7.dp)) {
                Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text((order.createdAt ?: "").take(16).takeLast(5), color = TextFaint, fontSize = 11.sp,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.Delete, "Hapus", tint = TextFaint, modifier = Modifier.size(15.dp))
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CardBg, titleContentColor = TextPrimary, textContentColor = TextMuted,
            title = { Text("Hapus pesanan?") },
            text = { Text("Data pesanan ini akan dihapus permanen dari server.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Hapus", color = AccentRed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal", color = TextMuted) } }
        )
    }
}

/** Avatar: foto driver kalau ada, kalau tidak inisial merchant. */
@Composable
fun OrderAvatar(order: Order, size: Int) {
    val (color, _) = statusStyle(order.status)
    if (!order.driverPhoto.isNullOrBlank()) {
        AsyncImage(
            model = order.driverPhoto, contentDescription = order.driverName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape).background(CardBg)
        )
    } else {
        Box(
            Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text((order.merchant ?: "G").take(1).uppercase(), color = color,
                fontWeight = FontWeight.Bold, fontSize = (size * 0.4).sp)
        }
    }
}
