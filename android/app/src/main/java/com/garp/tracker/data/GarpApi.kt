package com.garp.tracker.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Klien API backend GARP (FastAPI, port 8000).
 * baseUrl diambil dari SharedPreferences (Server URL dialog), BUKAN hardcode.
 * Endpoint: POST /api/orders {link}, GET /api/orders, GET /api/orders/{id},
 *           GET /api/stats, DELETE /api/orders/{id}, SSE /api/orders/{id}/stream
 */
class GarpApi(private val baseUrl: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        expectSuccess = false
    }

    suspend fun orders(): List<Order> =
        client.get("$baseUrl/api/orders").body<List<Order>>()

    suspend fun order(id: String): Order =
        client.get("$baseUrl/api/orders/$id").body<Order>()

    suspend fun stats(): Stats =
        client.get("$baseUrl/api/stats").body<Stats>()

    suspend fun addOrder(link: String): Order =
        client.post("$baseUrl/api/orders") {
            contentType(ContentType.Application.Json)
            setBody("""{"link": ${Json.encodeToString(link)}}""")
        }.body<Order>()

    suspend fun deleteOrder(id: String): Boolean {
        val resp = client.delete("$baseUrl/api/orders/$id")
        return resp.status.isSuccess()
    }

    suspend fun refresh(id: String): Order =
        client.get("$baseUrl/api/orders/$id").body<Order>()

    fun close() = client.close()

    companion object {
        const val DEFAULT_BASE_URL: String = "http://10.0.2.2:8000"
    }
}
