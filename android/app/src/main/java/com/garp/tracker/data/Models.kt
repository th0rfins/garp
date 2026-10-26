package com.garp.tracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Order sesuai respons backend (snake_case). `id` adalah STRING dari backend.
 * Sinkron dengan server.py _snapshot() + frontend/app.js norm().
 */
@Serializable
data class Order(
    val id: String,
    @SerialName("merchant") val merchant: String? = null,
    @SerialName("booking_code") val bookingCode: String? = null,
    @SerialName("status") val status: String? = null, // prepare | allocated | ontheway | completed | cancelled
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_vehicle") val driverVehicle: String? = null,
    @SerialName("driver_plate") val driverPlate: String? = null,
    @SerialName("driver_rating") val driverRating: Int? = null,
    @SerialName("driver_photo") val driverPhoto: String? = null,
    @SerialName("status_message") val statusMessage: String? = null,
    @SerialName("driver_lat") val driverLat: Double? = null,
    @SerialName("driver_lng") val driverLng: Double? = null,
    @SerialName("dest_lat") val destLat: Double? = null,
    @SerialName("dest_lng") val destLng: Double? = null,
    @SerialName("dest_text") val destText: String? = null,
    @SerialName("complete_time") val completeTime: String? = null,
    @SerialName("dropoff_time") val dropoffTime: String? = null,
    @SerialName("driver_arrival_time") val driverArrivalTime: String? = null,
    @SerialName("multi_stop") val multiStop: Boolean = false,
    @SerialName("link") val link: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("error") val error: String? = null,
)

/** GET /api/stats — selaras /api/stats server.py */
@Serializable
data class Stats(
    @SerialName("total") val total: Int = 0,
    @SerialName("active") val active: Int = 0,
    @SerialName("completed") val completed: Int = 0,
    @SerialName("cancelled") val cancelled: Int = 0,
    @SerialName("value") val value: Int = 0,
    @SerialName("daily") val daily: Map<String, Int> = emptyMap(),
    @SerialName("top_merchants") val topMerchants: List<TopMerchant> = emptyList(),
    @SerialName("avg_eta_seconds") val avgEtaSeconds: Long? = null,
    @SerialName("today") val today: String? = null,
    @SerialName("today_count") val todayCount: Int = 0,
)

@Serializable
data class TopMerchant(
    @SerialName("name") val name: String,
    @SerialName("count") val count: Int = 0,
)
