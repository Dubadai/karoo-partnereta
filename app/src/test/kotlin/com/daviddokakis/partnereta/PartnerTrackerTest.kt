package com.daviddokakis.partnereta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerTrackerTest {

    // A straight road due north: 1 km ≈ 0.008993° of latitude.
    private val start = LatLng(59.0, 18.0)
    private fun north(m: Double) = Geo.destination(start, 0.0, m)
    private val route = Geo.encode((0..100).map { north(it * 100.0) }) // 10 km
    private val t0 = 1_800_000_000_000L
    private val speed = 8.0 // m/s

    /** She rides north at 8 m/s; a snapshot every 30 s for [n] snapshots. */
    private fun ride(tracker: PartnerTracker, n: Int, state: String = "riding", withRoute: Boolean = true) {
        for (i in 0 until n) {
            val t = t0 + i * 30_000L
            val traveled = (0..i).map { north(it * 30 * speed) }
            tracker.onSnapshot(
                PartnerFeed.Snapshot(
                    state = state, position = traveled.last(), bearing = 0.0,
                    trace = Geo.encode(traveled), route = if (withRoute) route else null,
                    updatedAtMs = t, trackingId = "ride-1",
                ),
                t + 1_000,
            )
        }
    }

    @Test fun polylineRoundTrip() {
        // Google's documented example.
        val pts = Geo.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5); assertEquals(-120.2, pts[0].lng, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5); assertEquals(-126.453, pts[2].lng, 1e-5)
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Geo.encode(pts))
    }

    @Test fun parsesTheRealFeed() {
        val body = """{"state":"idle","location":{"lat":59.28877,"lng":18.283},"bearing":5.2,"activityInfo":[],
            "units":"METRIC","userTrace":"ezzhJ{{qnBD@?JC@D?DGFAECDJEE","id":"149699.tracking.d549","riderName":" ",
            "createdAt":"2025-09-12T17:35:49.824Z","updatedAt":"2026-11-06T11:16:20.455Z"}"""
        val s = PartnerFeed.parse(body)
        assertEquals("idle", s.state)
        assertEquals(59.28877, s.position!!.lat, 1e-9)
        assertTrue(!s.isRiding)
        assertNull(s.route)
        assertTrue(Geo.decode(s.trace).first().lat in 59.2..59.3)
    }

    @Test fun codeFromLink() {
        assertEquals("AbCd1234", PartnerFeed.codeFrom("https://dashboard.hammerhead.io/live/AbCd1234"))
        assertEquals("AbCd1234", PartnerFeed.codeFrom("…/live/AbCd1234"))
        assertEquals("AbCd1234", PartnerFeed.codeFrom(" AbCd1234 "))
        assertNull(PartnerFeed.codeFrom("hello world"))
    }

    @Test fun etaAtTheEndOfHerRoute() {
        val tr = PartnerTracker()
        ride(tr, 11) // last fix at 300 s: 2400 m along, 7600 m to go
        val updated = t0 + 10 * 30_000L
        val v = tr.view(updated + 5_000, configured = true, mySpeed = null)
        assertEquals(PartnerView.Status.LIVE, v.status)
        val expected = updated + (7600 / speed * 1000).toLong()
        assertEquals(expected.toDouble(), v.etaMs!!.toDouble(), 15_000.0)
        // The arrow has moved on ~40 m in the 5 s since the update.
        assertEquals(2440.0, Geo.distance(start, v.position!!), 5.0)
        // Her line starts just behind her last real position (under the dot, which glides on).
        assertEquals(2370.0, Geo.distance(start, Geo.decode(v.routeAhead!!).first()), 5.0)
        // For the map: a long piece from the next 2 km mark, and a short one from her to 300 m past it.
        assertEquals(4000.0, Geo.distance(start, Geo.decode(v.routeRest!!).first()), 5.0)
        assertEquals(2370.0, Geo.distance(start, Geo.decode(v.routeLead!!).first()), 5.0)
        assertEquals(4300.0, Geo.distance(start, Geo.decode(v.routeLead!!).last()), 5.0)
        assertEquals(1630.0, v.leadCasingM, 5.0)
    }

    @Test fun noRouteMeansNoEta() {
        val tr = PartnerTracker()
        ride(tr, 5, withRoute = false)
        val v = tr.view(t0 + 4 * 30_000L, configured = true, mySpeed = null)
        assertNull(v.etaMs)
        assertEquals(PartnerView.NoEta.NO_ROUTE, v.noEta)
    }

    private fun fix(tr: PartnerTracker, t: Long, p: LatLng, bearing: Double, trace: List<LatLng>) =
        tr.onSnapshot(PartnerFeed.Snapshot(state = "riding", position = p, bearing = bearing,
            trace = Geo.encode(trace), route = route, updatedAtMs = t, trackingId = "ride-1"), t + 1_000)

    @Test fun onHerWayToTheStartTheEtaCountsTheWayThere() {
        // 2 km east of the start, riding west to it at 8 m/s: her Karoo guides her to the start.
        val tr = PartnerTracker()
        val trace = ArrayList<LatLng>()
        for (i in 0..10) {
            val p = Geo.destination(start, 90.0, 2000.0 - i * 30 * speed)
            trace += p
            fix(tr, t0 + i * 30_000L, p, 270.0, trace)
        }
        val updated = t0 + 10 * 30_000L
        val v = tr.view(updated + 1_000, configured = true, mySpeed = null)
        assertNull(v.noEta)
        val left = 400 * PartnerTracker.DETOUR_FACTOR + 10_000 // 400 m to go to the start, then all of it
        assertEquals((updated + (left / speed * 1000).toLong()).toDouble(), v.etaMs!!.toDouble(), 15_000.0)
        assertEquals(left, v.remainingM!!, 50.0)
        assertEquals(route, v.routeAhead) // all of it
    }

    @Test fun offHerRouteMidRideTheEtaCountsTheWayBack() {
        // On it to 2400 m, then a detour east: she's 300 m beside the route at 2640 m.
        val tr = PartnerTracker()
        ride(tr, 11)
        val trace = (0..10).map { north(it * 30 * speed) }.toMutableList()
        val side = Geo.destination(north(2640.0), 90.0, 300.0)
        trace += side
        val updated = t0 + 11 * 30_000L
        fix(tr, updated, side, 90.0, trace)
        val v = tr.view(updated + 1_000, configured = true, mySpeed = null)
        assertNull(v.noEta)
        val left = 300 * PartnerTracker.DETOUR_FACTOR + (10_000 - 2640)
        assertEquals(left, v.remainingM!!, 60.0)
        // Her line starts where she'll rejoin it, not at the route start.
        assertEquals(2640.0, Geo.distance(start, Geo.decode(v.routeAhead!!).first()), 20.0)
    }

    @Test fun aUTurnKeepsHerWhereSheWasOnALoop() {
        // A loop that rides the same road twice: west on it first (0-3000 m), round a block, then
        // east on it (7000-10000 m) and on 1 km past the start. She rides the second, eastbound pass,
        // then turns back (to meet someone): the westbound first pass matches her new heading,
        // 7 km back. She must stay near where she was, not jump back (seen 2026-11-07: "46.6 km left").
        fun at(x: Double, y: Double) = Geo.destination(Geo.destination(start, 90.0, x), 0.0, y)
        val corners = listOf(at(0.0, 0.0), at(-3000.0, 0.0), at(-3000.0, -1000.0), at(-4000.0, -1000.0),
            at(-4000.0, 0.0), at(1000.0, 0.0))
        val pts = corners.zipWithNext().flatMap { (a, b) ->
            val n = (Geo.distance(a, b) / 100).toInt().coerceAtLeast(1)
            (0 until n).map { i -> LatLng(a.lat + (b.lat - a.lat) * i / n, a.lng + (b.lng - a.lng) * i / n) }
        } + corners.last()
        val loop = Geo.encode(pts)
        val tr = PartnerTracker()
        val trace = ArrayList<LatLng>()
        fun snap(i: Int, p: LatLng, bearing: Double) {
            trace += p
            val t = t0 + i * 65_000L
            tr.onSnapshot(PartnerFeed.Snapshot(state = "riding", position = p, bearing = bearing,
                trace = Geo.encode(trace), route = loop, updatedAtMs = t, trackingId = "ride-1"), t + 1_000)
        }
        // The first pass and the block (as her trace), then eastbound on the second pass.
        trace += pts.take(60)
        for (i in 0..4) snap(i, at(-3800.0 + i * 520.0, 0.0), 90.0)   // to x = -1720, along ≈ 8280
        val before = tr.view(t0 + 4 * 65_000L + 1_000, true, null).remainingM!!
        snap(5, at(-1900.0, 0.0), 270.0) // turned back 180 m
        val after = tr.view(t0 + 5 * 65_000L + 1_000, true, null).remainingM!!
        assertEquals(before + 180, after, 120.0)
    }

    @Test fun staleAfterTwoMinutesKeepsTheEta() {
        val tr = PartnerTracker()
        ride(tr, 11)
        val updated = t0 + 10 * 30_000L
        val live = tr.view(updated + 1_000, true, null)
        val stale = tr.view(updated + 3 * 60_000L, true, null)
        assertEquals(PartnerView.Status.STALE, stale.status)
        assertEquals(live.etaMs, stale.etaMs)
    }

    @Test fun pausedIsNotStaleBetweenItsSlowerUpdates() {
        val tr = PartnerTracker()
        ride(tr, 11, state = "paused")
        val updated = t0 + 10 * 30_000L
        assertEquals(PartnerView.Status.LIVE, tr.view(updated + 3 * 60_000L, true, null).status)
        assertEquals(PartnerView.Status.STALE, tr.view(updated + 7 * 60_000L, true, null).status)
    }

    @Test fun notRidingWhenIdle() {
        val tr = PartnerTracker()
        ride(tr, 3, state = "idle")
        assertEquals(PartnerView.Status.NOT_RIDING, tr.view(t0 + 60_000, true, null).status)
    }

    @Test fun noGapOnHerTrackWithoutTheSameRoute() {
        // Standing at home on the road she rode out on, no route loaded: not "she's ahead".
        val tr = PartnerTracker()
        ride(tr, 11)
        val now = t0 + 10 * 30_000L
        tr.onMyLocation(north(240.0), 0.0, now)
        assertNull(tr.view(now, true, mySpeed = 0.0).gap)
    }

    @Test fun noGapOnDifferentRoads() {
        val tr = PartnerTracker()
        ride(tr, 11)
        val now = t0 + 10 * 30_000L
        tr.onMyLocation(Geo.destination(north(2000.0), 90.0, 500.0), 0.0, now) // 500 m east
        val v = tr.view(now, true, 8.0)
        assertNull(v.gap)
        assertEquals(640.0, v.distanceM!!, 30.0)
    }

    @Test fun sameRouteGapWhenSheIsAhead() {
        val tr = PartnerTracker()
        tr.onMyRoute(route)
        ride(tr, 11) // she's at 2400 m
        val now = t0 + 10 * 30_000L
        tr.onMyLocation(north(1000.0), 0.0, now)
        val gap = tr.view(now, true, 8.0).gap!!
        assertTrue(gap.ahead)
        assertEquals(1400.0, gap.metres, 30.0)
        // She passed 1000 m at t0 + 125 s, so 175 s before now.
        assertEquals(175.0, gap.seconds!!, 10.0)
    }

    @Test fun sameRouteGapWhenSheIsBehindFarAway() {
        val tr = PartnerTracker()
        tr.onMyRoute(route)
        ride(tr, 11) // she's at 2400 m
        val now = t0 + 10 * 30_000L
        tr.onMyLocation(north(8000.0), 0.0, now) // far ahead, nowhere near each other
        val gap = tr.view(now, true, 8.0).gap!!
        assertTrue(!gap.ahead)
        assertEquals(5600.0, gap.metres, 30.0)
        assertEquals(700.0, gap.seconds!!, 30.0) // at her 8 m/s
    }

    @Test fun noRouteGapOnADifferentRoute() {
        val tr = PartnerTracker()
        tr.onMyRoute(Geo.encode((0..100).map { Geo.destination(start, 90.0, it * 100.0) })) // due east
        ride(tr, 11)
        val now = t0 + 10 * 30_000L
        tr.onMyLocation(north(8000.0), 0.0, now)
        assertNull(tr.view(now, true, 8.0).gap)
    }

    @Test fun aShortRouteInsideALongOneIsNotTheSame() {
        val long = Path(Geo.decode(route))
        val short = Path((0..20).map { north(it * 100.0) }) // the first 2 km
        assertTrue(PartnerTracker.covers(short, long))
        assertTrue(!PartnerTracker.covers(long, short))
        // The same road the other way round isn't the same route either.
        assertTrue(!PartnerTracker.covers(Path(long.points.reversed()), long))
    }

    @Test fun fieldTexts() {
        val now = t0
        val v = PartnerView(PartnerView.Status.LIVE, distanceM = 1400.0, etaMs = now + 35 * 60_000L, remainingM = 14_200.0, lastUpdateMs = now)
        // Different roads: the ETA with the flag.
        FieldTexts.eta(v, "Robin", now, true).let {
            assertEquals("Robin ETA", it.header); assertEquals("Partner ETA", it.headerShort); assertEquals("14.2 km left", it.note); assertNull(it.arrow)
        }
        // Same road: the gap, with the pink arrow up (ahead) or down (behind).
        FieldTexts.eta(v.copy(gap = Gap(true, 200.0, 30.0)), "Robin", now, true).let {
            assertEquals("Robin ahead", it.header); assertEquals("ahead", it.headerShort); assertEquals("0:30", it.value); assertTrue(it.note.startsWith("ETA ")); assertEquals(0.0, it.arrow!!, 0.0)
        }
        // Gap without an ETA: the distance under it.
        FieldTexts.eta(v.copy(gap = Gap(true, 200.0, 30.0), etaMs = null), "Robin", now, true).let { assertEquals("200 m", it.note) }
        // "Distance left" off: just the clock.
        FieldTexts.eta(v, "Robin", now, true, distanceLeft = false).let { assertEquals("", it.note) }
        FieldTexts.eta(v.copy(gap = Gap(true, 200.0, 30.0)), "Robin", now, true, distanceLeft = false).let {
            assertTrue(it.note.startsWith("ETA "))
        }
        FieldTexts.eta(v.copy(gap = Gap(false, 350.0, null)), "Robin", now, true).let {
            assertEquals("Robin behind", it.header); assertEquals("behind", it.headerShort); assertEquals("350", it.value); assertEquals("m", it.unit); assertEquals(180.0, it.arrow!!, 0.0)
        }
        FieldTexts.eta(v.copy(status = PartnerView.Status.STALE, lastUpdateMs = now - 2 * 60_000L), "Robin", now, true)
            .let { assertEquals("2 min ago", it.note); assertTrue(it.value != "--:--"); assertTrue(it.stale) }
        FieldTexts.eta(PartnerView(PartnerView.Status.NOT_RIDING), "Robin", now, true).let { assertEquals("--:--", it.value); assertEquals("not riding", it.note) }
        FieldTexts.eta(v.copy(etaMs = null, noEta = PartnerView.NoEta.NO_ROUTE, distanceM = null), "Robin", now, true).let { assertEquals("--:--", it.value); assertEquals("no route", it.note) }
        // No route but we know where they are (no route loaded): how far away.
        FieldTexts.eta(v.copy(etaMs = null, noEta = PartnerView.NoEta.NO_ROUTE, distanceM = 3400.0), "Robin", now, true).let {
            assertEquals("Robin", it.header); assertEquals("3.4", it.value); assertEquals("", it.unit); assertEquals("km away", it.note)
        }
        // The km line is extra (left out on short tiles); state words are not, and carry their icon.
        FieldTexts.eta(v.copy(paused = true), "Robin", now, true).let {
            assertEquals("paused", it.note); assertEquals(NoteIcon.PAUSED, it.noteIcon); assertTrue(!it.stale)
        }
        FieldTexts.eta(v.copy(paused = true, arrived = true), "Robin", now, true).let { assertEquals("arrived", it.note); assertEquals(NoteIcon.ARRIVED, it.noteIcon) }
        FieldTexts.eta(v.copy(offline = true), "Robin", now, true).let {
            assertEquals("no connection", it.note); assertEquals(NoteIcon.NO_CONNECTION, it.noteIcon); assertTrue(it.stale); assertTrue(it.value != "--:--")
        }
        // Offline we can't know she isn't riding.
        FieldTexts.eta(PartnerView(PartnerView.Status.NOT_RIDING, offline = true), "Robin", now, true).let {
            assertEquals("--:--", it.value); assertEquals("no connection", it.note); assertEquals(NoteIcon.NO_CONNECTION, it.noteIcon)
        }
        FieldTexts.eta(PartnerView(PartnerView.Status.WAITING, offline = true), "Robin", now, true).let { assertEquals("no connection", it.note) }
        // Gap mode keeps the state words too.
        FieldTexts.eta(v.copy(gap = Gap(true, 200.0, 30.0), paused = true), "Robin", now, true).let { assertEquals("paused", it.note); assertEquals(NoteIcon.PAUSED, it.noteIcon) }
    }

    @Test fun pausedTheEtaMovesOnAtHerMovingSpeed() {
        val tr = PartnerTracker()
        ride(tr, 11) // to 2400 m of 10 km at 8 m/s
        val updated = t0 + 11 * 30_000L
        val pos = north(2400.0)
        tr.onSnapshot(PartnerFeed.Snapshot(state = "paused", position = pos, bearing = 0.0, trace = Geo.encode(listOf(start, pos)),
            route = route, updatedAtMs = updated, trackingId = "ride-1"), updated + 1_000)
        val left = 10_000 - 2400.0
        listOf(1, 4).forEach { min ->
            val now = updated + min * 60_000L
            val v = tr.view(now, true, null)
            assertTrue(v.paused); assertTrue(!v.arrived); assertEquals(PartnerView.Status.LIVE, v.status)
            assertEquals((now + (left / speed * 1000).toLong()).toDouble(), v.etaMs!!.toDouble(), 20_000.0)
        }
    }

    @Test fun pausedNearTheEndIsArrived() {
        val tr = PartnerTracker()
        ride(tr, 41) // 9600 m of 10 km
        val updated = t0 + 41 * 30_000L
        val pos = north(9700.0)
        tr.onSnapshot(PartnerFeed.Snapshot(state = "paused", position = pos, bearing = 0.0, trace = Geo.encode(listOf(start, pos)),
            route = route, updatedAtMs = updated, trackingId = "ride-1"), updated + 1_000)
        val v = tr.view(updated + 5 * 60_000L, true, null)
        assertTrue(v.arrived)
        // When she first got within 500 m of the end: 9520 m at 8 m/s, the fix at 9600 m (40 × 30 s).
        assertEquals((t0 + 40 * 30_000L).toDouble(), v.etaMs!!.toDouble(), 31_000.0)
    }
}
