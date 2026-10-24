package com.garp.tracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Order sesuai respons backend (snake_case). `id` adalah STRING dari backend.
 * Referensi mapping: frontend/app.js fungsi norm().
 */
@Serializable
data class Order(
    val id: String,
    @SerialName("merchant") val merchant: String? = null,
    @SerialName("booking_code") val bookingCode: String? = null,
    @SerialName("status") val status: String? = null, // prepare | ontheway | completed | cancelled
    @SerialName("eta_seconds") val etaSeconds: Long? = null,
    @SerialName("driver_name") val driverName: String? = null,
    @SerialName("driver_vehicle") val driverVehicle: String? = null,
    @SerialName("driver_plate") val driverPlate: String? = null,
    @SerialName("driver_lat") val driverLat: Double? = null,
    @SerialName("driver_lng") val driverLng: Double? = null,
    @SerialName("dest_lat") val destLat: Double? = null,
    @SerialName("dest_lng") val destLng: Double? = null,
    @SerialName("dest_text") val destText: String? = null,
    @SerialName("link") val link: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("error") val error: String? = null,
)

/** GET /api/stats: { total, active, completed, cancelled, value, daily: {'YYYY-MM-DD': n} } */
@Serializable
data class Stats(
    @SerialName("total") val total: Int = 0,
    @SerialName("active") val active: Int = 0,
    @SerialName("completed") val completed: Int = 0,
    @SerialName("cancelled") val cancelled: Int = 0,
    @SerialName("value") val value: Int = 0,
    @SerialName("daily") val daily: Map<String, Int> = emptyMap(),
)
