package com.garp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tab Peta: peta kanvas gelap (gaya Vercel/Linear), SEMUA orderan aktif tampil
 * bersamaan (multi-track, selaras frontend fitAllActive), garis rute driver→tujuan,
 * float card order pertama dengan foto driver.
 */
@Composable
fun MapScreen(vm: GarpViewModel, onOrderClick: (String) -> Unit) {
    val state = vm.state
    val activeOrders = state.orders.filter {
        it.status in listOf("prepare", "allocated", "ontheway") ||
            (it.driverLat != null && it.driverLng != null && it.status !in listOf("completed", "cancelled"))
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MiniMap(orders = activeOrders, onMarkerClick = onOrderClick, modifier = Modifier.fillMaxSize())

            if (activeOrders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tidak ada pelacakan aktif", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Tempel link Grab untuk mulai memantau", color = TextFaint, fontSize = 12.sp)
                    }
                }
            }

            // Float card order aktif pertama — foto driver (bukan icon sepeda)
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
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrderAvatar(focused, size = 38)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(focused.merchant ?: "—", color = TextPrimary, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    focused.driverName,
                                    focused.driverRating?.let { "★ $it" },
                                    focused.driverPlate
                                ).joinToString(" · ").ifEmpty { "menunggu driver" },
                                color = TextMuted, fontSize = 11.sp, maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            focused.etaSeconds?.let {
                                Text(formatEta(it), color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (focused.multiStop && focused.etaSeconds != null) {
                                Text("double order", color = AccentYellow.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Strip pesanan aktif (multi-track chips)
        if (activeOrders.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeOrders.forEach { o ->
                    val (color, _) = statusStyle(o.status)
                    Surface(
                        color = CardBg, shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        modifier = Modifier.clickable { onOrderClick(o.id) }
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(6.dp))
                            Text(o.merchant ?: "—", color = TextPrimary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Peta kanvas gelap: grid halus, garis rute dashed driver→tujuan, marker semua order aktif. */
@Composable
fun MiniMap(orders: List<Order>, onMarkerClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val lats = orders.mapNotNull { it.driverLat } + orders.mapNotNull { it.destLat }
    val lngs = orders.mapNotNull { it.driverLng } + orders.mapNotNull { it.destLng }
    val pad = 0.008
    val minLat = (lats.minOrNull() ?: -6.25) - pad
    val maxLat = (lats.maxOrNull() ?: -6.18) + pad
    val minLng = (lngs.minOrNull() ?: 106.81) - pad
    val maxLng = (lngs.maxOrNull() ?: 106.87) + pad
    val spanLat = (maxLat - minLat).coerceAtLeast(1e-5)
    val spanLng = (maxLng - minLng).coerceAtLeast(1e-5)

    fun norm(lat: Double, lng: Double): Pair<Float, Float> {
        val x = ((lng - minLng) / spanLng).toFloat().coerceIn(0.06f, 0.94f)
        val y = (1f - ((lat - minLat) / spanLat)).toFloat().coerceIn(0.06f, 0.94f)
        return x to y
    }

    Surface(color = Surface, modifier = modifier) {
        with(androidx.compose.ui.platform.LocalDensity.current) {
            Canvas(Modifier.fillMaxSize()) {
                // grid halus ala Linear
                val stepX = size.width / 8
                val stepY = size.height / 10
                for (i in 1..7) drawLine(BorderSoft, Offset(i * stepX, 0f), Offset(i * stepX, size.height), 1f)
                for (j in 1..9) drawLine(BorderSoft, Offset(0f, j * stepY), Offset(size.width, j * stepY), 1f)

                // rute dashed driver→tujuan per order
                orders.forEach { o ->
                    if (o.driverLat != null && o.driverLng != null && o.destLat != null && o.destLng != null) {
                        val (x1, y1) = norm(o.driverLat!!, o.driverLng!!)
                        val (x2, y2) = norm(o.destLat!!, o.destLng!!)
                        val color = statusStyle(o.status).first
                        val p1 = Offset(x1 * size.width, y1 * size.height)
                        val p2 = Offset(x2 * size.width, y2 * size.height)
                        val path = Path().apply { moveTo(p1.x, p1.y); quadraticBezierTo(
                            (p1.x + p2.x) / 2 + 24f, (p1.y + p2.y) / 2 - 24f, p2.x, p2.y) }
                        drawPath(path, color.copy(alpha = 0.55f), style = Stroke(
                            width = 2.5f, cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    }
                }
            }
        }

        // Marker overlay
        orders.forEach { o ->
            val lat = o.driverLat ?: return@forEach
            val lng = o.driverLng ?: return@forEach
            val (fx, fy) = norm(lat, lng)
            val dest = if (o.destLat != null && o.destLng != null) norm(o.destLat!!, o.destLng!!) else null
            MarkerOverlay(fx, fy, dest, statusStyle(o.status).first) {
                onMarkerClick(o.id)
            }
        }
    }
}

@Composable
private fun MarkerOverlay(
    fx: Float, fy: Float,
    destF: Pair<Float, Float>?,
    color: Color,
    onClick: () -> Unit,
) {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { size = it.size }) {
        val w = size.width.toFloat().coerceAtLeast(1f)
        val h = size.height.toFloat().coerceAtLeast(1f)
        Box(Modifier.offset { IntOffset(((w * fx).toInt()) - 9, ((h * fy).toInt()) - 9) }) {
            MarkerDot(color, onClick)
        }
        if (destF != null) {
            Box(Modifier.offset { IntOffset(((w * destF.first).toInt()) - 7, ((h * destF.second).toInt()) - 7) }) {
                MarkerDot(AccentGreen, onClick = {})
            }
        }
    }
}

@Composable
private fun MarkerDot(color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, Bg, CircleShape)
            .clickable(onClick = onClick)
    )
}
