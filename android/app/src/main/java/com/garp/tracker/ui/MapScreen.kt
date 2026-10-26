package com.garp.tracker.ui

import android.graphics.Color as AwtColor
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.garp.tracker.data.Order
import com.garp.tracker.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Tab Peta: peta OSM nyata (osmdroid, libre & keyless) dengan SEMUA orderan aktif
 * tampil bersamaan (multi-track). Auto-fit bounds; float card order pertama.
 */
@Composable
fun MapScreen(vm: GarpViewModel, onOrderClick: (String) -> Unit) {
    val state = vm.state
    // Hanya order benar-benar aktif dengan lokasi valid — (0,0) = reset post-completion
    val activeOrders = state.orders.filter {
        it.status in listOf("prepare", "allocated", "ontheway") &&
            it.driverLat != null && it.driverLng != null &&
            !(it.driverLat == 0.0 && it.driverLng == 0.0)
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Configuration.getInstance()
            .load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "GARP-Tracker/2.0"
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (activeOrders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tidak ada pelacakan aktif", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Tempel link Grab untuk mulai memantau", color = TextFaint, fontSize = 12.sp)
                    }
                }
            } else {
                OsmMap(
                    orders = activeOrders,
                    onMarkerTap = onOrderClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Float card order aktif pertama — foto driver, animated in/out
            val focused = activeOrders.firstOrNull()
            androidx.compose.animation.AnimatedVisibility(
                visible = focused != null,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(220)) { it / 3 },
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val fc = focused ?: return@AnimatedVisibility
                Surface(
                    color = CardBg.copy(alpha = 0.97f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth()
                        .clickable { onOrderClick(fc.id) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OrderAvatar(fc, size = 38)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(fc.merchant ?: "—", color = TextPrimary, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    fc.driverName,
                                    fc.driverRating?.let { "★ $it" },
                                    fc.driverPlate
                                ).joinToString(" · ").ifEmpty { "menunggu driver" },
                                color = TextMuted, fontSize = 11.sp, maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            fc.etaSeconds?.let {
                                Text(formatEta(it), color = AccentYellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (fc.multiStop == 1 && fc.etaSeconds != null) {
                                Text("double order", color = AccentYellow.copy(alpha = 0.7f), fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Strip order aktif (multi-track chips)
        if (activeOrders.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(Surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
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

@Composable
private fun OsmMap(orders: List<Order>, onMarkerTap: (String) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.5)
                // Scrim gelap tipis di atas tile agar nyaman di tema gelap
                overlayManager.tilesOverlay.setColorFilter(AwtColor.argb(120, 10, 10, 11))
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker || it is Polyline }
            val points = mutableListOf<org.osmdroid.util.GeoPoint>()
            orders.forEach { o ->
                val lat = o.driverLat ?: return@forEach
                val lng = o.driverLng ?: return@forEach
                val driver = org.osmdroid.util.GeoPoint(lat, lng)
                points.add(driver)
                val (dColor, _) = statusStyle(o.status)
                Marker(map).apply {
                    position = driver
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = o.merchant ?: "Driver"
                    // dot berwarna: pakai icon default tapi tint icon via default marker? gunakan icon bawaan
                    setOnMarkerClickListener { _, _ -> onMarkerTap(o.id); true }
                }.also { map.overlays.add(it) }

                if (o.destLat != null && o.destLng != null && !(o.destLat == 0.0 && o.destLng == 0.0)) {
                    val dest = org.osmdroid.util.GeoPoint(o.destLat!!, o.destLng!!)
                    points.add(dest)
                    Polyline(map).apply {
                        setPoints(listOf(driver, dest))
                        outlinePaint.color = dColor.copy(alpha = 0.65f).toArgb()
                        outlinePaint.strokeWidth = 6f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 9f), 0f)
                    }.also { map.overlays.add(it) }
                    Marker(map).apply {
                        position = dest
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Tujuan"
                    }.also { map.overlays.add(it) }
                }
            }
            if (points.isNotEmpty()) {
                val bb = BoundingBox.fromGeoPoints(points)
                map.post {
                    map.zoomToBoundingBox(bb.increaseByScale(1.4f), false, 64)
                }
            }
            map.invalidate()
        }
    )
}

private fun androidx.compose.ui.graphics.Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
