package com.garp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.garp.tracker.ui.theme.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@Composable
fun AnalyticsScreen(vm: GarpViewModel) {
    val stats = vm.state.stats

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Analitik", style = MaterialTheme.typography.titleLarge, color = TextPrimary)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Total", "${stats?.total ?: 0}", AccentBlue, Modifier.weight(1f))
            StatCard("Aktif", "${stats?.active ?: 0}", AccentYellow, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Selesai", "${stats?.completed ?: 0}", AccentGreen, Modifier.weight(1f))
            StatCard("Batal", "${stats?.cancelled ?: 0}", AccentRed, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        Text("Distribusi status", style = MaterialTheme.typography.titleMedium, color = TextPrimary)

        val dist = stats?.daily ?: emptyMap()
        val total = dist.values.sum().coerceAtLeast(1)
        Surface(
            color = CardBg, shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                dist.forEach { (k, v) ->
                    val (color, label) = statusStyle(k)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Border)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(v.toFloat() / total)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(color)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("$v", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (dist.isEmpty()) Text("Belum ada data", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = CardBg,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = TextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}
