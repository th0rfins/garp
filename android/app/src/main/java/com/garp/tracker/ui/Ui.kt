package com.garp.tracker.ui

import androidx.compose.ui.graphics.Color
import com.garp.tracker.ui.theme.*
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Warna + label status — selaras STATUS_LABEL frontend. */
fun statusStyle(status: String?): Pair<Color, String> = when (status) {
    "prepare" -> AccentYellow to "Disiapkan"
    "allocated" -> AccentYellow to "Driver ke resto"
    "ontheway" -> AccentBlue to "Melaju"
    "completed" -> AccentGreen to "Tiba"
    "cancelled" -> AccentRed to "Batal"
    "expired" -> AccentRed to "Kedaluwarsa"
    else -> TextMuted to (status ?: "—")
}

fun formatEta(seconds: Long): String {
    val m = seconds / 60
    return if (m >= 60) "${m / 60}j ${m % 60}m" else "${m}m ${seconds % 60}s"
}

/** ISO-8601 UTC dari Grab -> HH:mm zona WIB. */
fun toWibTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        val t = LocalDateTime.parse(iso.take(19))
        t.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.of("Asia/Jakarta"))
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
}
