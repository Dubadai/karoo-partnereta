package com.daviddokakis.partnereta

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/**
 * The partner's Karoo live tracking feed. Not an official API: it's what the live-link web page
 * (dashboard.hammerhead.io/live/<code>) loads every 5 s, and it needs no login.
 */
object PartnerFeed {
    fun url(code: String) = "https://dashboard.hammerhead.io/v1/shares/tracking/$code"

    /** The code from a pasted live link ("https://dashboard.hammerhead.io/live/AbCd1234"), or the bare code. */
    fun codeFrom(input: String): String? {
        val s = input.trim()
        Regex("""/live/([A-Za-z0-9_-]+)""").find(s)?.let { return it.groupValues[1] }
        return s.takeIf { Regex("""[A-Za-z0-9_-]{4,40}""").matches(it) }
    }

    /** Parse one response. Throws on anything that isn't a tracking object. */
    fun parse(body: String): Snapshot {
        val o = Json.parseToJsonElement(body).jsonObject
        val loc = o["location"] as? JsonObject
        val route = o["route"] as? JsonObject
        return Snapshot(
            state = o.str("state") ?: "idle",
            position = loc?.let { l ->
                val lat = l["lat"]?.jsonPrimitive?.doubleOrNull
                val lng = l["lng"]?.jsonPrimitive?.doubleOrNull
                if (lat != null && lng != null) LatLng(lat, lng) else null
            },
            bearing = o["bearing"]?.jsonPrimitive?.doubleOrNull,
            trace = o.str("userTrace").orEmpty(),
            route = route?.str("routePolyline")?.takeIf { it.isNotEmpty() },
            updatedAtMs = o.str("updatedAt")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            trackingId = o.str("id"),
        )
    }

    private fun JsonObject.str(key: String) = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    data class Snapshot(
        /** "riding", "paused", "idle", … as the web page gets it. */
        val state: String,
        val position: LatLng?,
        val bearing: Double?,
        /** Encoded polyline of the ride so far. */
        val trace: String,
        /** Encoded polyline of the route they navigate, when they navigate one. */
        val route: String?,
        val updatedAtMs: Long?,
        /** Changes with each new ride on the same link. */
        val trackingId: String?,
    ) {
        val isPaused get() = state == "paused"
        /** Recording a ride right now (paused counts: they'll be back). */
        val isRiding get() = state in RIDING_STATES || isPaused
    }

    private val RIDING_STATES = setOf("riding", "active", "moving", "not_moving", "recording")
}
