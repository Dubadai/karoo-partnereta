package com.daviddokakis.partnereta

import kotlin.math.min

/** One known position at a known time. */
data class Fix(val timeMs: Long, val pos: LatLng)

/** Gap along the road when you ride the same road the same way. */
data class Gap(
    /** true: she is ahead of you; false: behind. */
    val ahead: Boolean,
    val metres: Double,
    /** Seconds between you passing the same spot; null until we have the timing for it. */
    val seconds: Double?,
)

/** The same-route check: how much of each route runs along the other. */
data class RouteMatch(val myLengthM: Double, val herLengthM: Double, val mineCovered: Double, val hersCovered: Double, val same: Boolean)

/** Everything the fields and the map show about the partner. Rendering only reads this. */
data class PartnerView(
    val status: Status,
    val position: LatLng? = null,
    /** Compass heading of the arrow. */
    val bearing: Double = 0.0,
    /** Encoded polylines from the feed. */
    val trace: String? = null,
    val route: String? = null,
    /** Her route from where she was last seen to its end (encoded): what she has left. */
    val routeAhead: String? = null,
    /**
     * [routeAhead] in two pieces for the map, so a new position only redraws a short one: [routeRest]
     * from a point that moves on every [PartnerTracker.REST_STEP_M], and [routeLead] from her to it
     * (running [PartnerTracker.LEAD_OVERLAP_M] on into the rest, its outline stopping at the join:
     * [leadCasingM] metres along it).
     */
    val routeRest: String? = null,
    val routeLead: String? = null,
    val leadCasingM: Double = 0.0,
    /** Straight-line metres from you, when your position is known. */
    val distanceM: Double? = null,
    /** Direction to her relative to your heading (0 = straight ahead), for the small arrow in the field. */
    val relativeDirection: Double? = null,
    val gap: Gap? = null,
    /** Clock time she reaches the end of her route. */
    val etaMs: Long? = null,
    val remainingM: Double? = null,
    /** Why there's no ETA. */
    val noEta: NoEta? = null,
    val lastUpdateMs: Long? = null,
    /** She has paused her ride: the ETA moves on while she stands. */
    val paused: Boolean = false,
    /** She stopped at (or very near) the end of her route; [etaMs] is when she got there. */
    val arrived: Boolean = false,
    /** Your Karoo can't reach her live feed (set by the session, not the tracker). */
    val offline: Boolean = false,
) {
    enum class Status {
        /** No live link set up yet. */
        NOT_SET,
        /** Link set, nothing heard yet. */
        WAITING,
        /** The link answers, but no ride is recording. */
        NOT_RIDING,
        /** Recording and updated within the last 2 min. */
        LIVE,
        /** Recording, but no update for 2 min: keep the last values, grey on the map. */
        STALE,
    }

    enum class NoEta { NO_ROUTE, NO_SPEED, OFF_ROUTE }

    val isShown get() = status == Status.LIVE || status == Status.STALE
}

/**
 * Turns the feed's snapshots (every ~30 s) and your own positions into what we show: where she is
 * probably now (moved along her route by her recent speed), her ETA at the end of her route, and
 * the gap when you share a road. No Android here, so it's unit-tested.
 */
class PartnerTracker {
    private var snapshot: PartnerFeed.Snapshot? = null
    private var snapshotAtMs = 0L
    private val herFixes = ArrayList<Fix>()
    private val myFixes = ArrayList<Fix>()
    private var myHeading: Double? = null
    private var linkMissing = false

    private var traceKey: String? = null
    private var tracePath: Path? = null
    private var routeKey: String? = null
    private var routePath: Path? = null
    /** Where each of her fixes lies along her route (metres), keyed by fix time; followed fix to fix. */
    private val herAlong = HashMap<Long, Double>()
    /** How far along her route she last was on it (kept when she leaves it), or null if never this ride. */
    private var herLastAlong: Double? = null
    private var aheadKey: Pair<String?, Double>? = null
    private var aheadEncoded: String? = null
    private var restKey: Pair<String?, Double>? = null
    private var restEncoded: String? = null
    private var leadEncoded: String? = null
    private var leadCasing = 0.0
    /** Where her line starts, set when a new position arrives; and where her dot was last drawn. */
    private var cutFix: Pair<String?, Double>? = null
    private var cutFrom = 0.0
    private var lastDotAlong: Double? = null
    /** Her ETA speed while she was last moving: kept through a pause, which would drag it to zero. */
    private var movingEtaSpeed: Double? = null

    // Your loaded route, and whether it's the same as hers (then the gap is measured along it).
    private var myRouteKey: String? = null
    private var myRoutePath: Path? = null
    private var sameKey: Pair<String?, String?>? = null
    private var sameRoute = false
    /** The last same-route check, for the debug ride log. */
    var routeMatch: RouteMatch? = null
        private set
    /** Where you were along her route (metres), with the time: for "she is behind you" timing. */
    private val myAlongFixes = ArrayList<Pair<Long, Double>>()

    fun reset() {
        snapshot = null
        linkMissing = false
        herFixes.clear()
        herAlong.clear()
        herLastAlong = null
        lastDotAlong = null; cutFix = null
        traceKey = null; tracePath = null; routeKey = null; routePath = null
    }

    /** The feed answered 404: wrong code, or the link was removed. */
    fun onLinkMissing() { reset(); linkMissing = true }

    fun onSnapshot(s: PartnerFeed.Snapshot, nowMs: Long) {
        linkMissing = false
        if (s.trackingId != snapshot?.trackingId) { herFixes.clear(); herAlong.clear(); herLastAlong = null; lastDotAlong = null; cutFix = null; movingEtaSpeed = null } // a new ride on the same link
        snapshot = s
        snapshotAtMs = nowMs
        val t = s.updatedAtMs ?: nowMs
        val pos = s.position
        if (s.trace != traceKey) { traceKey = s.trace; tracePath = Path(Geo.decode(s.trace)).takeIf { it.points.size >= 2 } }
        if (s.route != routeKey) {
            routeKey = s.route
            routePath = s.route?.let { Path(Geo.decode(it)) }?.takeIf { it.points.size >= 2 }
            herAlong.clear()
            herLastAlong = null
            var prev: Fix? = null
            for (f in herFixes) {
                projectAlong(f.pos, null, prev?.let { herAlong[it.timeMs] }, prev?.let { (f.timeMs - it.timeMs) / 1000.0 })
                    ?.let { herAlong[f.timeMs] = it; herLastAlong = it; prev = f }
            }
        }
        if (pos != null && (herFixes.isEmpty() || t > herFixes.last().timeMs)) {
            val last = herFixes.lastOrNull { herAlong.containsKey(it.timeMs) }
            herFixes += Fix(t, pos)
            projectAlong(pos, s.bearing, last?.let { herAlong[it.timeMs] }, last?.let { (t - it.timeMs) / 1000.0 })?.let { herAlong[t] = it; herLastAlong = it }
            herFixes.removeAll { it.timeMs < t - HISTORY_MS }
            if (!s.isPaused) speed(ETA_WINDOW_MS)?.let { movingEtaSpeed = it }
            herAlong.keys.removeAll { it < t - HISTORY_MS }
        }
    }

    /**
     * Where [pos] lies along her route. A route can pass the same spot twice (a loop that ends at
     * the door it starts from, an out-and-back, a road that doubles back), so follow on from the
     * previous fix ([prev] metres, [dtSec] ago): only matches she could have reached since then
     * count, and of those the one nearest her position. With no previous fix (picked up mid-ride),
     * the match closest to how far she has ridden (her track's length), else the earliest.
     */
    private fun projectAlong(pos: LatLng, bearing: Double?, prev: Double?, dtSec: Double?): Double? {
        val route = routePath ?: return null
        val hits = route.hits(pos, ON_ROUTE_M)
        if (hits.isEmpty()) return null
        val aligned = hits.filter { bearing == null || Geo.angleBetween(it.bearing, bearing) < 60 }.ifEmpty { hits }
        if (prev == null || dtSec == null) {
            val ridden = tracePath?.length ?: return aligned.minOf { it.along }
            return aligned.minBy { kotlin.math.abs(it.along - ridden) }.along
        }
        // Reachable since the last fix: heading her way first, then any heading (she turned around).
        // Nothing reachable: stay nearest where she was. Never jump to a far pass of the same road
        // just because it runs her new way (a U-turn on a loop once put her 42 km back).
        val reach = prev + dtSec * MAX_SPEED + ON_ROUTE_M
        val reachable = { h: Path.Hit -> h.along >= prev - ON_ROUTE_M && h.along <= reach }
        return aligned.filter(reachable).ifEmpty { hits.filter(reachable) }.minByOrNull { it.off }?.along
            ?: hits.minBy { kotlin.math.abs(it.along - prev) }.along
    }

    /**
     * Off her route: where she'll rejoin it (metres along) and how far away that is (straight line).
     * Not on it yet this ride: the start. Left it: the nearest point at or ahead of where she left.
     */
    private fun rejoin(route: Path, pos: LatLng): Pair<Double, Double>? {
        val left = herLastAlong ?: return 0.0 to Geo.distance(pos, route.points.first())
        val hit = route.hits(pos, Double.MAX_VALUE).filter { it.along >= left }.minByOrNull { it.off }
            ?: return null
        return hit.along to hit.off
    }

    /** The route you navigate (encoded polyline), or null when none is loaded. */
    fun onMyRoute(polyline: String?) {
        if (polyline == myRouteKey) return
        myRouteKey = polyline
        myRoutePath = polyline?.let { Path(Geo.decode(it)) }?.takeIf { it.points.size >= 2 }
        myAlongFixes.clear()
    }

    /** True when your route and hers are the same route (e.g. the same RideWithGPS route on both Karoos). */
    private fun isSameRoute(): Boolean {
        val key = myRouteKey to routeKey
        if (key != sameKey) {
            sameKey = key
            val a = myRoutePath; val b = routePath
            if (a != null && b != null) {
                val mine = coverage(a, b); val hers = coverage(b, a)
                sameRoute = mine >= SAME_ROUTE_SHARE && hers >= SAME_ROUTE_SHARE
                routeMatch = RouteMatch(a.length, b.length, mine, hers, sameRoute)
            } else sameRoute = false
            myAlongFixes.clear()
        }
        return sameRoute
    }

    /** Where you are along her route now, following on from where you were (loops pass a spot twice). */
    private fun myAlongOnRoute(route: Path, me: LatLng, nowMs: Long): Double? {
        val hits = route.hits(me, ON_ROUTE_M)
        if (hits.isEmpty()) return null
        val heading = myHeading
        val aligned = hits.filter { heading == null || Geo.angleBetween(it.bearing, heading) < 60 }.ifEmpty { hits }
        val prev = myAlongFixes.lastOrNull()
        val along = if (prev == null) {
            val ridden = if (myFixes.size >= 2) Path(myFixes.map { it.pos }).length else 0.0
            aligned.minBy { kotlin.math.abs(it.along - ridden) }.along
        } else {
            val reach = prev.second + (nowMs - prev.first) / 1000.0 * MAX_SPEED + ON_ROUTE_M
            (aligned.filter { it.along >= prev.second - ON_ROUTE_M && it.along <= reach }.ifEmpty { aligned })
                .minBy { it.off }.along
        }
        if (prev == null || kotlin.math.abs(along - prev.second) >= 10 || nowMs - prev.first >= 10_000) {
            myAlongFixes += nowMs to along
            myAlongFixes.removeAll { it.first < nowMs - HISTORY_MS }
        }
        return along
    }

    /**
     * The gap along the shared route: positive metres when she's ahead. Time = how long ago the one
     * in front was where the other is now (race timing), from her timed fixes or yours.
     */
    private fun routeGap(nowMs: Long, route: Path, me: LatLng, herNow: Double, herSpeed: Double?, mySpeed: Double?): Gap? {
        val mine = myAlongOnRoute(route, me, nowMs) ?: return null
        val metres = herNow - mine
        return if (metres >= 0) {
            val herTimes = herFixes.mapNotNull { f -> herAlong[f.timeMs]?.let { f.timeMs to it } }
            val seconds = timeAtAlong(herTimes, mine)?.let { (nowMs - it) / 1000.0 }
                ?: mySpeed?.takeIf { it > 2 }?.let { metres / it }
            Gap(ahead = true, metres = metres, seconds = seconds)
        } else {
            val seconds = timeAtAlong(myAlongFixes, herNow)?.let { (nowMs - it) / 1000.0 }
                ?: herSpeed?.takeIf { it > 2 }?.let { -metres / it }
            Gap(ahead = false, metres = -metres, seconds = seconds)
        }
    }

    /** Your own position: kept (every 10 m or 10 s) for "she is behind you" timing. */
    fun onMyLocation(pos: LatLng, heading: Double?, nowMs: Long) {
        if (heading != null) myHeading = heading
        val last = myFixes.lastOrNull()
        if (last == null || Geo.distance(last.pos, pos) >= 10 || nowMs - last.timeMs >= 10_000) {
            myFixes += Fix(nowMs, pos)
            if (myFixes.size > 4000) myFixes.subList(0, 1000).clear()
            myFixes.removeAll { it.timeMs < nowMs - HISTORY_MS }
        }
    }

    fun view(nowMs: Long, configured: Boolean, mySpeed: Double?): PartnerView {
        if (!configured) return PartnerView(PartnerView.Status.NOT_SET)
        val s = snapshot ?: return PartnerView(if (linkMissing) PartnerView.Status.NOT_RIDING else PartnerView.Status.WAITING)
        val updated = s.updatedAtMs ?: snapshotAtMs
        val base = herFixes.lastOrNull()?.pos ?: s.position
        if (!s.isRiding || base == null || nowMs - updated > ENDED_MS) {
            return PartnerView(PartnerView.Status.NOT_RIDING, lastUpdateMs = updated)
        }
        // Paused, her Karoo sends a position only every 2-3 min (seen 2026-11-07), so allow longer.
        val stale = nowMs - updated > if (s.isPaused) PAUSED_STALE_MS else STALE_MS
        val speed = if (s.isPaused) 0.0 else speed(ARROW_WINDOW_MS) ?: 0.0
        val dt = if (stale) 0.0 else ((nowMs - updated) / 1000.0).coerceIn(0.0, MAX_GLIDE_S)

        // Where she probably is now: along her route when she follows it, else straight ahead briefly.
        val route = routePath
        val onRoute = herFixes.lastOrNull()?.let { herAlong[it.timeMs] }
        val (pos, bearing) = when {
            route != null && onRoute != null -> route.pointAt(onRoute + speed * dt)
            else -> Geo.destination(base, s.bearing ?: 0.0, speed * min(dt, MAX_STRAIGHT_S)) to (s.bearing ?: 0.0)
        }

        // ETA: when she reaches the end of her route at her recent moving speed. A clock time, so it
        // only changes when a new position arrives, or, paused, as the minutes pass (she'll set off
        // from now, at the speed she had before the pause).
        val etaSpeed = if (s.isPaused) movingEtaSpeed ?: speed(ETA_WINDOW_MS) else speed(ETA_WINDOW_MS)
        var arrived = false
        var etaMs: Long? = null
        var remaining: Double? = null
        // Off her route her Karoo reroutes her back to it; the feed doesn't send that reroute, so
        // estimate it: to the route start if she hasn't been on it yet, else to the nearest point
        // ahead of where she left it, by road (straight line × DETOUR_FACTOR), then the rest of it.
        val rejoin = if (route != null && onRoute == null) rejoin(route, base) else null
        val noEta = when {
            route == null -> PartnerView.NoEta.NO_ROUTE
            onRoute == null && rejoin == null -> PartnerView.NoEta.OFF_ROUTE
            else -> {
                val left = if (onRoute != null) (route.length - onRoute).coerceAtLeast(0.0)
                    else rejoin!!.second * DETOUR_FACTOR + (route.length - rejoin.first).coerceAtLeast(0.0)
                remaining = (left - speed * dt).coerceAtLeast(0.0)
                when {
                    // At the end, or stopped close to it (2026-11-07 she paused 287 m short of it):
                    // arrived, at the time she first got that close.
                    left < ARRIVED_M || (s.isPaused && onRoute != null && left < ARRIVED_NEAR_M) -> {
                        arrived = true; etaMs = arrivedAt(route) ?: updated; null
                    }
                    etaSpeed == null || etaSpeed < MIN_ETA_SPEED -> PartnerView.NoEta.NO_SPEED
                    else -> { etaMs = (if (s.isPaused) maxOf(nowMs, updated) else updated) + (left / etaSpeed * 1000).toLong(); null }
                }
            }
        }

        // What she has left: her route from her last real position, cut afresh only when a new one
        // arrives (re-sending a long route to the map often makes the Karoo drop pieces of it).
        // It starts where her dot was just before that update (minus LINE_BEHIND_M), so the dot,
        // which glides on from there, always sits on the line. Off her route: from where she'll
        // rejoin it (all of it on her way to the start).
        val routeAhead = when {
            route == null -> null
            onRoute == null && (rejoin == null || rejoin.first <= 0.0) -> s.route
            else -> {
                val from = if (onRoute != null) {
                    val fix = routeKey to onRoute
                    if (fix != cutFix) { cutFix = fix; cutFrom = (minOf(onRoute, lastDotAlong?.takeIf { onRoute - it < MAX_TAIL_M } ?: onRoute) - LINE_BEHIND_M).coerceAtLeast(0.0) }
                    cutFrom
                } else rejoin!!.first
                val key = routeKey to from
                if (key != aheadKey) {
                    aheadKey = key; aheadEncoded = Geo.encode(route.from(from))
                    val restFrom = minOf(route.length, (kotlin.math.floor(from / REST_STEP_M) + 1) * REST_STEP_M)
                    if (routeKey to restFrom != restKey) { restKey = routeKey to restFrom; restEncoded = if (restFrom < route.length) Geo.encode(route.from(restFrom)) else null }
                    leadEncoded = Geo.encode(route.between(from, minOf(route.length, restFrom + LEAD_OVERLAP_M)))
                    leadCasing = restFrom - from
                }
                aheadEncoded
            }
        }
        val split = route != null && routeAhead != null && routeAhead !== s.route

        lastDotAlong = if (route != null && onRoute != null) (onRoute + speed * dt).coerceAtMost(route.length) else null
        val me = myFixes.lastOrNull()?.pos
        return PartnerView(
            status = if (stale) PartnerView.Status.STALE else PartnerView.Status.LIVE,
            position = pos,
            bearing = bearing,
            trace = s.trace.takeIf { it.isNotEmpty() },
            route = s.route,
            routeAhead = routeAhead,
            routeRest = if (split) restEncoded else routeAhead,
            routeLead = if (split) leadEncoded else null,
            leadCasingM = if (split) leadCasing else 0.0,
            distanceM = me?.let { Geo.distance(it, pos) },
            relativeDirection = me?.let { m -> myHeading?.let { h -> (Geo.bearing(m, pos) - h + 360) % 360 } },
            // Ahead/behind only on the same route (the gap along it, wherever you both are). Not on a
            // merely shared road: standing at home on the road she rode out on isn't "she's ahead".
            gap = me?.let { m ->
                if (route != null && onRoute != null && isSameRoute())
                    routeGap(nowMs, route, m, (onRoute + speed * dt).coerceAtMost(route.length), etaSpeed, mySpeed) else null
            },
            etaMs = etaMs,
            remainingM = remaining,
            noEta = noEta,
            lastUpdateMs = updated,
            paused = s.isPaused,
            arrived = arrived,
        )
    }

    /** When she first got within [ARRIVED_NEAR_M] of her route's end and stayed there. */
    private fun arrivedAt(route: Path): Long? {
        var first: Long? = null
        for (f in herFixes) {
            val along = herAlong[f.timeMs] ?: continue
            if (along >= route.length - ARRIVED_NEAR_M) { if (first == null) first = f.timeMs } else first = null
        }
        return first
    }

    /**
     * Her moving speed (m/s) over the last [windowMs], from our own timed fixes: distance along her
     * route when she follows it (true road distance), else straight lines between fixes (which cut
     * corners, so a little low on twisty roads).
     */
    private fun speed(windowMs: Long): Double? {
        if (herFixes.size < 2) return null
        val end = herFixes.last().timeMs
        val recent = herFixes.filter { it.timeMs >= end - windowMs }
        if (recent.size < 2) return null
        val secs = (recent.last().timeMs - recent.first().timeMs) / 1000.0
        if (secs < 20) return null
        val a0 = herAlong[recent.first().timeMs]
        val a1 = herAlong[recent.last().timeMs]
        if (a0 != null && a1 != null && a1 >= a0) return (a1 - a0) / secs
        val metres = recent.zipWithNext { a, b -> Geo.distance(a.pos, b.pos) }.sum()
        return metres / secs
    }

    companion object {
        /**
         * True when most of [a] (sampled every [SAMPLE_M]) runs within [SAME_ROUTE_M] of [b], the
         * same way. Checked both ways, so a short route inside a long one doesn't count.
         */
        fun covers(a: Path, b: Path): Boolean = coverage(a, b) >= SAME_ROUTE_SHARE

        /** The share (0–1) of [a], sampled every [SAMPLE_M], that runs within [SAME_ROUTE_M] of [b] the same way. */
        fun coverage(a: Path, b: Path): Double {
            var n = 0; var hit = 0; var d = 0.0
            while (d <= a.length) {
                val (p, bearing) = a.pointAt(d)
                n++
                if (b.hits(p, SAME_ROUTE_M).any { Geo.angleBetween(it.bearing, bearing) < SAME_WAY_DEG }) hit++
                d += SAMPLE_M
            }
            return if (n == 0) 0.0 else hit.toDouble() / n
        }

        /** When the rider of [fixes] (time, metres along) was at [along]: interpolated. */
        fun timeAtAlong(fixes: List<Pair<Long, Double>>, along: Double): Long? {
            for (i in fixes.size - 1 downTo 1) {
                val (t0, a0) = fixes[i - 1]; val (t1, a1) = fixes[i]
                if (along in a0..a1 && a1 > a0) return t0 + ((t1 - t0) * (along - a0) / (a1 - a0)).toLong()
            }
            return null
        }

        const val SAME_ROUTE_M = 30.0
        const val SAMPLE_M = 200.0
        const val SAME_ROUTE_SHARE = 0.8
        const val STALE_MS = 120_000L
        /** Stale while she's paused: her Karoo sends a position about every 2.5 min then. */
        const val PAUSED_STALE_MS = 6 * 60_000L
        /** No update for this long: treat the ride as over. */
        const val ENDED_MS = 30 * 60_000L
        const val HISTORY_MS = 4 * 3_600_000L
        const val ARROW_WINDOW_MS = 3 * 60_000L
        const val ETA_WINDOW_MS = 10 * 60_000L
        /** Move the arrow along her route for up to this long after an update (then it's stale anyway). */
        const val MAX_GLIDE_S = 120.0
        /** Without a route, guess straight ahead for at most this long. */
        const val MAX_STRAIGHT_S = 30.0
        const val ON_ROUTE_M = 60.0
        /** Her timed fixes are ~30 s apart, so chords cut corners: allow more room. */
        const val SAME_WAY_DEG = 45.0
        const val ARRIVED_M = 50.0
        /** Paused this close to her route's end counts as arrived. */
        const val ARRIVED_NEAR_M = 500.0
        /** Her line starts this far behind her dot (under it). */
        const val LINE_BEHIND_M = 30.0
        /** Her map line's long piece is re-cut only every this far, so updates redraw a short piece. */
        const val REST_STEP_M = 2000.0
        /** The short piece runs this far on into the long one, so their outlines don't show at the join. */
        const val LEAD_OVERLAP_M = 300.0
        /** A dot further behind than this was from something else (a jump, another ride): ignored. */
        const val MAX_TAIL_M = 500.0
        /** Road distance back to the route ≈ straight line × this (roads aren't straight). Tune from ride logs. */
        const val DETOUR_FACTOR = 1.3
        const val MIN_ETA_SPEED = 1.5
        /** Fastest believable bike speed (m/s), for following her along the route. */
        const val MAX_SPEED = 25.0
    }
}
