package com.daviddokakis.partnereta

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the map, in degrees. */
data class LatLng(val lat: Double, val lng: Double)

object Geo {
    private const val R = 6_371_000.0

    /** Metres between two points (haversine). */
    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Compass bearing from [a] to [b]: 0 = north, 90 = east, 0 until 360. */
    fun bearing(a: LatLng, b: LatLng): Double {
        val y = sin(rad(b.lng - a.lng)) * cos(rad(b.lat))
        val x = cos(rad(a.lat)) * sin(rad(b.lat)) - sin(rad(a.lat)) * cos(rad(b.lat)) * cos(rad(b.lng - a.lng))
        return (deg(atan2(y, x)) + 360) % 360
    }

    /** Smallest angle between two bearings, 0 until 180. */
    fun angleBetween(a: Double, b: Double): Double {
        val d = abs(((a - b) % 360 + 360) % 360)
        return if (d > 180) 360 - d else d
    }

    /** The point [metres] from [p] heading [bearing] degrees. */
    fun destination(p: LatLng, bearing: Double, metres: Double): LatLng {
        val d = metres / R
        val b = rad(bearing)
        val lat1 = rad(p.lat)
        val lng1 = rad(p.lng)
        val lat2 = kotlin.math.asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b))
        val lng2 = lng1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLng(deg(lat2), deg(lng2))
    }

    /** Point [t] (0 until 1) of the way from [a] to [b]; fine for the short hops between track points. */
    fun lerp(a: LatLng, b: LatLng, t: Double) = LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    /** Google encoded polyline, precision 5 (what the live feed and the Karoo map use). */
    fun decode(encoded: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var i = 0
        var lat = 0
        var lng = 0
        while (i < encoded.length) {
            val dLat = next(encoded, i).also { i = it.second }.first
            if (i >= encoded.length) break
            val dLng = next(encoded, i).also { i = it.second }.first
            lat += dLat
            lng += dLng
            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }

    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var lastLat = 0L
        var lastLng = 0L
        for (p in points) {
            val lat = Math.round(p.lat * 1e5)
            val lng = Math.round(p.lng * 1e5)
            put(sb, lat - lastLat)
            put(sb, lng - lastLng)
            lastLat = lat
            lastLng = lng
        }
        return sb.toString()
    }

    private fun next(s: String, start: Int): Pair<Int, Int> {
        var i = start
        var result = 0
        var shift = 0
        while (i < s.length) {
            val b = s[i++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
            if (b < 0x20) break
        }
        return (if (result and 1 != 0) (result shr 1).inv() else result shr 1) to i
    }

    private fun put(sb: StringBuilder, v: Long) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) {
            sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar())
            x = x shr 5
        }
        sb.append((x + 63).toInt().toChar())
    }

    fun rad(d: Double) = d * PI / 180
    fun deg(r: Double) = r * 180 / PI
}

/**
 * A path (route or ridden track) with distances along it, for "where on the path is this point"
 * and "where will they be after X more metres".
 */
class Path(val points: List<LatLng>) {
    /** Metres from the start to each point. */
    private val cum: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + Geo.distance(points[i - 1], points[i])
    }

    val length: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    /** Where [p] meets the path: [along] metres from the start, [off] metres beside it, heading there. */
    data class Hit(val along: Double, val off: Double, val bearing: Double)

    /**
     * All places [p] is within [maxOff] metres of the path, nearest first. A path can pass the
     * same spot twice (out-and-back), so callers pick the hit that fits their heading.
     */
    fun hits(p: LatLng, maxOff: Double): List<Hit> {
        if (points.size < 2) return emptyList()
        val out = ArrayList<Hit>()
        // Flat-earth projection around p: exact enough for metres-scale matching.
        val kx = 111_320.0 * cos(Geo.rad(p.lat))
        val ky = 110_540.0
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            val ax = (a.lng - p.lng) * kx
            val ay = (a.lat - p.lat) * ky
            val bx = (b.lng - p.lng) * kx
            val by = (b.lat - p.lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val cx = ax + dx * t
            val cy = ay + dy * t
            val off = sqrt(cx * cx + cy * cy)
            if (off <= maxOff) {
                val segLen = cum[i + 1] - cum[i]
                out += Hit(cum[i] + segLen * t, off, Geo.bearing(a, b))
            }
        }
        // Neighbouring segments report the same spot; keep the closest per 20 m stretch.
        return out.sortedBy { it.off }.fold(ArrayList()) { acc, h ->
            if (acc.none { abs(it.along - h.along) < 20 }) acc += h
            acc
        }
    }

    /** Segment index i (from point i to i+1) and fraction t of the way along it, at [along] metres. */
    fun locate(along: Double): Pair<Int, Double> {
        if (points.size < 2) return 0 to 0.0
        val d = along.coerceIn(0.0, length)
        var i = cum.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
        if (i >= points.size) i = points.size - 1
        val segLen = cum[i] - cum[i - 1]
        return (i - 1) to (if (segLen == 0.0) 0.0 else (d - cum[i - 1]) / segLen)
    }

    /** The part of the path from [from] metres to the end, starting exactly at that point. */
    fun from(from: Double): List<LatLng> {
        if (points.size < 2) return points
        val (i, _) = locate(from)
        return listOf(pointAt(from).first) + points.subList(i + 1, points.size)
    }

    /** The part of the path between [a] and [b] metres (either order), in path order. */
    fun between(a: Double, b: Double): List<LatLng> {
        if (points.size < 2) return points
        val lo = minOf(a, b).coerceIn(0.0, length); val hi = maxOf(a, b).coerceIn(0.0, length)
        val (i, _) = locate(lo); val (j, _) = locate(hi)
        val raw = listOf(pointAt(lo).first) + points.subList(i + 1, j + 1) + pointAt(hi).first
        // Cutting exactly at a corner repeats the corner: drop repeats (their direction is undefined).
        return raw.fold(ArrayList<LatLng>()) { acc, p -> if (acc.isEmpty() || Geo.distance(acc.last(), p) > 0.5) acc += p; acc }
    }

    /** Points every [spacing] metres (first at half a spacing) with the heading there, at most [max]. */
    fun every(spacing: Double, max: Int): List<Pair<LatLng, Double>> {
        if (points.size < 2 || spacing <= 0) return emptyList()
        val out = ArrayList<Pair<LatLng, Double>>()
        var d = spacing / 2
        while (d < length && out.size < max) { out += pointAt(d); d += spacing }
        return out
    }

    /** The point [along] metres from the start (clamped to the path) and the heading there. */
    fun pointAt(along: Double): Pair<LatLng, Double> {
        require(points.isNotEmpty())
        if (points.size == 1) return points[0] to 0.0
        val (i, t) = locate(along)
        return Geo.lerp(points[i], points[i + 1], t) to Geo.bearing(points[i], points[i + 1])
    }
}

/** Douglas–Peucker: drop points closer than [toleranceM] to the line through their neighbours. */
fun simplify(points: List<LatLng>, toleranceM: Double): List<LatLng> {
    if (points.size < 3 || toleranceM <= 0) return points
    val keep = BooleanArray(points.size).also { it[0] = true; it[points.size - 1] = true }
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.size - 1) }
    val kx = 111_320.0 * kotlin.math.cos(Geo.rad(points[0].lat))
    val ky = 110_540.0
    while (stack.isNotEmpty()) {
        val (a, b) = stack.removeLast()
        if (b <= a + 1) continue
        val ax = points[a].lng * kx; val ay = points[a].lat * ky
        val dx = points[b].lng * kx - ax; val dy = points[b].lat * ky - ay
        val len2 = dx * dx + dy * dy
        var worst = -1.0; var at = -1
        for (i in a + 1 until b) {
            val px = points[i].lng * kx - ax; val py = points[i].lat * ky - ay
            val t = if (len2 == 0.0) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
            val ex = px - dx * t; val ey = py - dy * t
            val d = ex * ex + ey * ey
            if (d > worst) { worst = d; at = i }
        }
        if (worst > toleranceM * toleranceM) { keep[at] = true; stack.add(a to at); stack.add(at to b) }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

/** Fast "is this point within d metres of the path" via a grid of its segments. */
class NearIndex(private val points: List<LatLng>, private val cellM: Double = 100.0) {
    private val kx = 111_320.0 * kotlin.math.cos(Geo.rad(points.firstOrNull()?.lat ?: 0.0))
    private val ky = 110_540.0
    private val cells = HashMap<Long, MutableList<Int>>()

    init {
        for (i in 0 until points.size - 1) {
            val (x0, y0) = xy(points[i]); val (x1, y1) = xy(points[i + 1])
            for (cx in cell(minOf(x0, x1))..cell(maxOf(x0, x1))) for (cy in cell(minOf(y0, y1))..cell(maxOf(y0, y1))) {
                cells.getOrPut(key(cx, cy)) { ArrayList() } += i
            }
        }
    }

    private fun xy(p: LatLng) = p.lng * kx to p.lat * ky
    private fun cell(v: Double) = kotlin.math.floor(v / cellM).toLong()
    private fun key(cx: Long, cy: Long) = cx * 1_000_003L + cy

    fun isNear(p: LatLng, d: Double): Boolean {
        if (points.size < 2) return false
        val (px, py) = xy(p)
        val r = kotlin.math.ceil(d / cellM).toLong()
        val c0 = cell(px); val c1 = cell(py)
        for (cx in c0 - r..c0 + r) for (cy in c1 - r..c1 + r) {
            for (i in cells[key(cx, cy)] ?: continue) {
                val (ax, ay) = xy(points[i]); val (bx, by) = xy(points[i + 1])
                val dx = bx - ax; val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
                val ex = px - ax - dx * t; val ey = py - ay - dy * t
                if (ex * ex + ey * ey <= d * d) return true
            }
        }
        return false
    }
}
