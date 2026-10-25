package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*

/**
 * Tab Peta: canvas peta "bersih" dengan marker driver (biru = driver, hijau = tujuan).
 * Peta statis berbasis koordinat (tanpa tile eksternal agar APK tetap ringan);
 * digambar dengan Canvas skala sederhana sekitar Jakarta.
 */
@Composable
fun MapScreen(vm: GarpViewModel, onOrderClick: (String) -> Unit) {
    val state by vm.state
    val activeOrders = state.orders.filter {
        it.driverLat != null && it.driverLng != null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            MiniMap(orders = activeOrders, onMarkerClick = onOrderClick, modifier = Modifier.fillMaxSize())

            // Kartu order aktif teratas (floating, seperti float-card di frontend)
            val focused = activeOrders.firstOrNull()
            if (focused != null) {
                Surface(
                    color = CardBg.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .fillMaxWidth()
                        .clickable { onOrderClick(focused.id) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(AccentBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("🛵", fontSize = 16.sp) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                focused.merchant ?: "—",
                                color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(focused.driverName, focused.driverPlate).joinToString(" • "),
                                color = TextMuted, fontSize = 12.sp
                            )
                        }
                        focused.etaSeconds?.let {
                            Text(formatEta(it), color = AccentYellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Strip pesanan aktif di bawah peta
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeOrders.take(4).forEach { o ->
                val (color, _) = statusStyle(o.status)
                Surface(
                    color = CardBg,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    modifier = Modifier.clickable { onOrderClick(o.id) }
                ) {
                    Text(
                        (o.merchant ?: "—").take(14),
                        color = color, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Peta mini sederhana: grid jalan + marker driver/tujuan berdasarkan min-max lat/lng. */
@Composable
fun MiniMap(orders: List<Order>, onMarkerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<String?>(null) }
    val lats = orders.mapNotNull { it.driverLat } + orders.mapNotNull { it.destLat }
    val lngs = orders.mapNotNull { it.driverLng } + orders.mapNotNull { it.destLng }
    val pad = 0.01
    val minLat = (lats.minOrNull() ?: -6.25) - pad
    val maxLat = (lats.maxOrNull() ?: -6.18) + pad
    val minLng = (lngs.minOrNull() ?: 106.81) - pad
    val maxLng = (lngs.maxOrNull() ?: 106.87) + pad

    fun norm(lat: Double, lng: Double): Pair<Float, Float> {
        val x = ((lng - minLng) / (maxLng - minLng)).toFloat().coerceIn(0.06f, 0.94f)
        val y = (1f - ((lat - minLat) / (maxLat - minLat))).toFloat().coerceIn(0.06f, 0.94f)
        return x to y
    }

    Surface(color = Surface, modifier = modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // grid jalan
            val stepX = size.width / 8
            val stepY = size.height / 10
            for (i in 1..7) {
                drawLine(
                    color = Border, start = Offset(i * stepX, 0f), end = Offset(i * stepX, size.height),
                    strokeWidth = 1.5f
                )
            }
            for (j in 1..9) {
                drawLine(
                    color = Border, start = Offset(0f, j * stepY), end = Offset(size.width, j * stepY),
                    strokeWidth = 1.5f
                )
            }
        }

        // Marker
        orders.forEach { o ->
            val lat = o.driverLat ?: return@forEach
            val lng = o.driverLng ?: return@forEach
            val (fx, fy) = norm(lat, lng)
            val dest = if (o.destLat != null && o.destLng != null) norm(o.destLat!!, o.destLng!!) else null
            val dx = dest?.first
            val dy = dest?.second
            BoxWithConstraintsOverlay(
                fx = fx, fy = fy,
                destF = dx?.let { it to (dy ?: 0f) },
                color = statusStyle(o.status).first,
                onClick = {
                    selected = if (selected == o.id) null else o.id
                    if (selected != null) onMarkerClick(o.id)
                }
            )
        }
    }
}

@Composable
private fun BoxWithConstraintsOverlay(
    fx: Float, fy: Float,
    destF: Pair<Float, Float>?,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
    ) {
        with(androidx.compose.ui.platform.LocalDensity.current) { }
        androidx.compose.ui.layout.Layout(
            content = {
                MarkerDot(color, onClick)
            },
            measurePolicy = { measurables, constraints ->
                val dot = measurables.first()
                val dotSize = dot.measure(constraints)
                val x = (constraints.maxWidth * fx).toInt() - dotSize.width / 2
                val y = (constraints.maxHeight * fy).toInt() - dotSize.height / 2
                layout(constraints.maxWidth, constraints.maxHeight) {
                    dot.place(x, y)
                }
            }
        )
        if (destF != null) {
            androidx.compose.ui.layout.Layout(
                content = { MarkerDot(AccentGreen, onClick = {}) },
                measurePolicy = { measurables, constraints ->
                    val dot = measurables.first()
                    val dotSize = dot.measure(constraints)
                    val df = destF
                    val px = (constraints.maxWidth * df.first).toInt() - dotSize.width / 2
                    val py = (constraints.maxHeight * df.second).toInt() - dotSize.height / 2
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        dot.place(px, py)
                    }
                }
            )
        }
    }
}

@Composable
private fun MarkerDot(color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Bg, CircleShape)
            .clickable(onClick = onClick)
    )
}
