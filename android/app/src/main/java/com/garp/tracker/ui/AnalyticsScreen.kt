package com.garp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.ui.theme.*

@Composable
fun AnalyticsScreen(vm: GarpViewModel) {
    val stats = vm.state.stats

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Analitik", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Hari ini", "${stats?.todayCount ?: 0}", Accent, Modifier.weight(1f),
                foot = "WIB")
            StatCard("Aktif", "${stats?.active ?: 0}/${stats?.total ?: 0}", AccentYellow, Modifier.weight(1f),
                foot = "sedang berjalan")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Total", "${stats?.total ?: 0}", TextPrimary, Modifier.weight(1f),
                foot = "${stats?.cancelled ?: 0} batal")
            StatCard("Nilai", "Rp ${formatRupiah(stats?.value ?: 0)}", AccentGreen, Modifier.weight(1f),
                foot = "est. 15rb / tiba")
        }

        // Top merchants
        val top = stats?.topMerchants.orEmpty().take(5)
        if (top.isNotEmpty()) {
            Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paling sering dipesan", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    top.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, color = TextMuted, fontSize = 12.sp, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f))
                            Text("${m.count}x", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Daily bars 30 hari
        Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pesanan per hari", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                val daily = stats?.daily.orEmpty().toSortedMap()
                val maxV = (daily.values.maxOrNull() ?: 1).coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    daily.values.forEach { v ->
                        Box(Modifier.weight(1f).fillMaxHeight(v.toFloat() / maxV)
                            .clip(RoundedCornerShape(2.dp)).background(Accent.copy(alpha = 0.85f)))
                    }
                }
                if (daily.isEmpty()) Text("Belum ada data", color = TextFaint, fontSize = 12.sp)
                else {
                    val first = daily.keys.first().takeLast(5)
                    val last = daily.keys.last().takeLast(5)
                    Text("$first  s.d.  $last", color = TextFaint, fontSize = 10.sp,
                        fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
                }
            }
        }
    }
}

private fun formatRupiah(v: Int): String = java.text.NumberFormat.getIntegerInstance().format(v)

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, foot: String? = null) {
    Surface(color = CardBg, shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = TextFaint, fontSize = 10.sp,
                fontFamily = MaterialTheme.typography.labelSmall.fontFamily)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            foot?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = TextFaint, fontSize = 9.5.sp)
            }
        }
    }
}
