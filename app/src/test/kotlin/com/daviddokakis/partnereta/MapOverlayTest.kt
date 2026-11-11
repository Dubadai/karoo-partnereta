package com.daviddokakis.partnereta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayTest {
    private val start = LatLng(59.0, 18.0)
    private fun north(m: Double, east: Double = 0.0) = Geo.destination(Geo.destination(start, 0.0, m), 90.0, east)
    private val style = MapStyle("t", "t", 1, 2, 3, 4, 5, 6, null, null, null, lineWidth = 10, outlineWidth = 16, riddenWidth = 5, shared = SharedLook.RAILS)

    private fun view(ahead: List<LatLng>) =
        PartnerView(PartnerView.Status.LIVE, position = ahead.first(), routeAhead = Geo.encode(ahead))

    @Test fun cutsOutWhereSheRidesOnMyRoute() {
        // Her route: 3 km north. My route shares the middle kilometre, then turns away east.
        val hers = (0..30).map { north(it * 100.0) }
        val mine = listOf(north(500.0, 2000.0), north(1000.0), north(2000.0), north(2500.0, 2000.0))
        val lines = MapOverlay.lines(view(hers), style, 15.0, MapOverlay.Mine(mine))
        val ahead = lines.filter { it.id.startsWith(MapOverlay.AHEAD) && !it.id.startsWith(MapOverlay.AHEAD_CASING) }
        assertEquals(2, ahead.size) // before and after the shared kilometre
        val px15 = MapOverlay.metresPerScreenPx(59.0, 15.0)
        for (l in ahead) for (p in Geo.decode(l.encoded)) assertTrue("covers my route at $p", NearIndex(mine).isNear(p, 17 * px15).not())
        // The shared kilometre gets two rails, one each side of my route, not on it.
        val rails = lines.filter { it.id.startsWith(MapOverlay.RAIL) }
        assertEquals(2, rails.size)
        val px = MapOverlay.metresPerScreenPx(59.0, 15.0)
        for (r in rails) {
            val pts = Geo.decode(r.encoded)
            for (p in pts) assertTrue("rail on my route at $p", NearIndex(mine).isNear(p, 10 * px).not())
            // Away from the ends (where my route turns off), the rail runs right beside it.
            for (p in pts.drop(1).dropLast(1)) assertTrue("rail far from my route at $p", NearIndex(mine).isNear(p, 34 * px)) // 13 px, mitred up to 2.5x at corners
        }
    }

    @Test fun chevronsOnlyOnStraights() {
        // 2 km north, a tight zig-zag (no straight bit long enough), 2 km north again.
        val zig = (0..20).map { north(it * 100.0) } +
            (1..10).map { north(2000.0 + it * 10.0, if (it % 2 == 1) 30.0 else 0.0) } +
            (1..20).map { north(2100.0 + it * 100.0) }
        val spots = MapOverlay.chevronSpots(view(zig), 14.0, null, style)
        assertTrue(spots.size >= 4)
        for ((p, _) in spots) assertTrue("chevron on the bend at $p", Geo.distance(p, north(2050.0)) > 60)
        for ((_, b) in spots) assertEquals(0.0, Geo.angleBetween(b, 0.0), 1.0)
        if (MapOverlay.LINE_CHEVRONS) {
            // Drawn into her line: two "^" lines per spot, in the outline colour.
            val chev = MapOverlay.lines(view(zig), style, 14.0, null).filter { it.id.startsWith(MapOverlay.CHEV_LINE) }
            assertEquals(spots.size * 2, chev.size)
            assertTrue(chev.all { it.color == style.outline && Geo.decode(it.encoded).size == 3 })
        } else assertEquals(spots.size, MapOverlay.chevrons(view(zig), style, 14.0, null).size)
    }

    @Test fun herLineEndsRightAtMyOutline() {
        // She rides due north and meets my east-west route at 1 km: her line must end where its
        // round end just touches my outline (10 px + half her 16-unit outline: 16 × 1.8 / 2 = 14.4 px), not short of it.
        val hers = (0..10).map { north(it * 100.0) }
        val mine = listOf(north(1000.0, -300.0), north(1000.0, 300.0)) // short: no great-circle bend
        val px = MapOverlay.metresPerScreenPx(59.0, 15.0)
        val ahead = MapOverlay.lines(view(hers), style, 15.0, MapOverlay.Mine(mine))
            .first { it.id == "${MapOverlay.AHEAD}0" }
        val end = Geo.decode(ahead.encoded).last()
        val gapPx = Geo.distance(end, north(1000.0)) / px
        assertEquals(24.4, gapPx, 1.0) // the encoded polyline rounds to ~1 m (~0.7 px here)
    }

    @Test fun sharedRoadCutAndMiniMarkers() {
        // She rides north 1 km, then on along my route (east-west) for 1 km.
        val hers = (0..10).map { north(it * 100.0) } + (1..10).map { north(1000.0, it * 100.0) }
        val mine = listOf(north(1000.0, -300.0), north(1000.0, 1500.0))
        val cut = style.copy(shared = SharedLook.ARROWS, miniArrow = 7, dot = 8, outlineWidth = 9, lineWidth = 5)
        val lines = MapOverlay.lines(view(hers), cut, 15.0, MapOverlay.Mine(mine))
        val px = MapOverlay.metresPerScreenPx(59.0, 15.0)
        val ahead = lines.first { it.id == "${MapOverlay.AHEAD}0" }
        if (MapOverlay.CUT_JOINS) {
            // Her own line runs on to my route's centre...
            assertEquals(0.0, Geo.distance(Geo.decode(ahead.encoded).last(), north(1000.0)) / px, 2.0)
            // ...and a patch of my route is drawn after (above) it: black, then yellow.
            val ids = lines.map { it.id }
            assertTrue(ids.indexOf("${MapOverlay.PATCH_CASING}0") > ids.indexOf("${MapOverlay.AHEAD}0"))
            assertTrue(ids.indexOf("${MapOverlay.PATCH}0") > ids.indexOf("${MapOverlay.PATCH_CASING}0"))
            assertEquals(0xFFFFFF00.toInt(), lines.first { it.id == "${MapOverlay.PATCH}0" }.color)
        } else {
            // Her line stops at my route's edge, with no patch over the join.
            assertTrue(Geo.distance(Geo.decode(ahead.encoded).last(), north(1000.0)) / px > 8)
            assertTrue(lines.none { it.id.startsWith(MapOverlay.PATCH) })
        }
        // No edge or rails beside my route: just the markers.
        assertTrue(lines.none { it.id.startsWith(MapOverlay.RAIL) })
        // Mini markers on my route along the shared stretch, pointing her way (east).
        val dots = MapOverlay.chevrons(view(hers), cut, 15.0, MapOverlay.Mine(mine)).filter { it.icon == 7 }
        assertTrue(dots.size >= 2)
        for (d in dots) { assertTrue(NearIndex(mine).isNear(d.pos, 2 * px)); assertEquals(90.0, d.bearing, 3.0) }
    }

    @Test fun keepsTheWholeLineWhenRoutesDontMeet() {
        val hers = (0..30).map { north(it * 100.0) }
        val mine = (0..30).map { north(it * 100.0, 3000.0) }
        val lines = MapOverlay.lines(view(hers), style, 15.0, MapOverlay.Mine(mine))
        assertEquals(1, lines.count { it.id.startsWith(MapOverlay.AHEAD) && !it.id.startsWith(MapOverlay.AHEAD_CASING) })
    }

    @Test fun simplifiesMoreWhenZoomedOut() {
        // A wiggly 3 km line: far fewer points at zoom 10 than at zoom 16.
        val hers = (0..300).map { north(it * 10.0, if (it % 2 == 0) 0.0 else 4.0) }
        fun points(z: Double) = Geo.decode(MapOverlay.lines(view(hers), style, z, null).first { it.id.startsWith(MapOverlay.AHEAD) }.encoded).size
        assertTrue(points(10.0) < 10)
        assertTrue(points(16.0) > 100)
    }

    @Test fun rideHistoryOnlyWhenSwitchedOn() {
        val hers = (0..30).map { north(it * 100.0) }
        val v = view(hers).copy(trace = Geo.encode((0..10).map { north(it * -100.0) }))
        assertTrue(MapOverlay.lines(v, style, 15.0, null).none { it.id.startsWith(MapOverlay.TRACK) })
        assertTrue(MapOverlay.lines(v, style, 15.0, null, showHistory = true).any { it.id.startsWith(MapOverlay.TRACK) })
    }

    @Test fun routeHiddenLeavesOnlyHistoryAndFinish() {
        val hers = (0..30).map { north(it * 100.0) }
        val v = view(hers).copy(trace = Geo.encode((0..10).map { north(it * -100.0) }))
        val hidden = MapOverlay.withoutRoute(v)
        val lines = MapOverlay.lines(hidden, style, 15.0, null, showHistory = true)
        assertTrue(lines.isNotEmpty() && lines.all { it.id.startsWith(MapOverlay.TRACK) })
        assertTrue(MapOverlay.leadLines(hidden, style, 15.0, null).isEmpty())
        assertEquals(0, MapOverlay.chevrons(hidden, style, 14.0, null).size)
    }

    @Test fun noChevronsOnMyRoute() {
        val hers = (0..30).map { north(it * 100.0) }
        val mine = listOf(north(0.0), north(3000.0)) // the same road all the way
        assertEquals(0, MapOverlay.chevronSpots(view(hers), 14.0, MapOverlay.Mine(mine), style).size)
        assertEquals(0, MapOverlay.chevrons(view(hers), style, 14.0, MapOverlay.Mine(mine)).size)
        // With shared chevrons on: they sit on my route, in her colour, pointing her way (north).
        val withShared = style.copy(sharedChevron = 99)
        val marks = MapOverlay.chevrons(view(hers), withShared, 14.0, MapOverlay.Mine(mine))
        assertTrue(marks.isNotEmpty())
        for (m in marks) { assertEquals(99, m.icon); assertEquals(0.0, Geo.angleBetween(m.bearing, 0.0), 2.0) }
        // ...also when my route runs the other way.
        val back = MapOverlay.chevrons(view(hers), withShared, 14.0, MapOverlay.Mine(mine.reversed()))
        for (m in back) assertEquals(0.0, Geo.angleBetween(m.bearing, 0.0), 2.0)
        assertTrue(MapOverlay.chevronSpots(view(hers), 14.0, null, style).isNotEmpty())
    }
}
