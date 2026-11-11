package com.daviddokakis.partnereta

import android.content.Context
import java.io.File

/**
 * DEBUG BUILDS ONLY (src/debug): a made-up partner riding a route, to see the map and the field
 * without a second rider. Release builds use the no-op in src/release.
 *
 * Turn on over adb (files in the app's private folder):
 *   sim_route.txt  encoded polyline (precision 5) of the route
 *   sim.txt        start=<epoch ms>  kmh=<speed, default 25>  offset=<metres along at start, default 0>
 *                  style=<n: map style to try, see MapStyle>  circle=<0|1: marker override>
 * Remove sim.txt to turn it off. Like the real feed, a new position comes every 30 s.
 */
object SimRide {
    private const val STEP_MS = 30_000L
    private var cacheKey: String? = null
    private var path: Path? = null

    fun isOn(context: Context) = File(context.filesDir, "sim.txt").exists()

    fun circle(context: Context): Boolean? = File(context.filesDir, "sim.txt").takeIf { it.exists() }?.readText()
        ?.let { Regex("""circle=([01])""").find(it)?.groupValues?.get(1)?.let { v -> v == "1" } }

    fun style(context: Context): Int? = File(context.filesDir, "sim.txt").takeIf { it.exists() }?.readText()
        ?.let { Regex("""style=(\d+)""").find(it)?.groupValues?.get(1)?.toInt() }

    fun snapshot(context: Context, nowMs: Long): PartnerFeed.Snapshot? {
        val cfg = File(context.filesDir, "sim.txt").takeIf { it.exists() }?.readText() ?: return null
        val routeText = File(context.filesDir, "sim_route.txt").takeIf { it.exists() }?.readText()?.trim() ?: return null
        if (routeText != cacheKey) { cacheKey = routeText; path = Path(Geo.decode(routeText)) }
        val route = path?.takeIf { it.points.size >= 2 } ?: return null

        val values = Regex("""(\w+)=([\d.]+)""").findAll(cfg).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
        val start = values["start"]?.toLong() ?: return null
        val speed = (values["kmh"] ?: 25.0) / 3.6
        val offset = values["offset"] ?: 0.0

        // The feed only moves every 30 s: snap the time to the last step.
        val updated = start + (nowMs - start).coerceAtLeast(0) / STEP_MS * STEP_MS
        val along = (offset + speed * (updated - start) / 1000.0).coerceAtMost(route.length)
        val (pos, bearing) = route.pointAt(along)
        val (i, _) = route.locate(along)
        val ridden = route.points.subList(0, i + 1) + pos
        val done = along >= route.length
        return PartnerFeed.Snapshot(
            state = if (done) "idle" else "riding",
            position = pos,
            bearing = bearing,
            trace = Geo.encode(ridden),
            route = routeText,
            updatedAtMs = updated,
            trackingId = "sim-$start",
        )
    }
}
