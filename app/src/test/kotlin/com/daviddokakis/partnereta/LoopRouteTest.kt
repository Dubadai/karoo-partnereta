package com.daviddokakis.partnereta

import org.junit.Assert.assertEquals
import org.junit.Test

/** A made-up 31 km loop with bends (starts and ends at the same point), ridden at 25 km/h with a fix every 30 s. */
class LoopRouteTest {
    private val encoded = javaClass.getResource("/loop-route.polyline")!!.readText().trim()
    private val route = Path(Geo.decode(encoded))
    private val speed = 25 / 3.6
    private val t0 = 1_800_000_000_000L

    private fun snap(t: Long): PartnerFeed.Snapshot {
        val along = speed * (t - t0) / 1000.0
        val (pos, bearing) = route.pointAt(along)
        val (i, _) = route.locate(along)
        return PartnerFeed.Snapshot("riding", pos, bearing, Geo.encode(route.points.subList(0, i + 1) + pos), encoded, t, "sim")
    }

    /** [from]: when we start following (0 = from the start; later = picked up mid-ride). */
    private fun etaAfter(seconds: Int, from: Int = 0): Long {
        val tr = PartnerTracker()
        for (s in from..seconds step 30) tr.onSnapshot(snap(t0 + s * 1000L), t0 + s * 1000L)
        val v = tr.view(t0 + seconds * 1000L, true, null)
        println("after ${seconds}s: remaining=${v.remainingM} eta in ${(v.etaMs!! - t0) / 60000.0} min, noEta=${v.noEta}")
        return v.etaMs!!
    }

    @Test fun etaOnALoopRoute() {
        val expected = t0 + (route.length / speed * 1000).toLong()
        for (s in listOf(60, 90, 600, 1800, 3600)) assertEquals("after $s s", expected.toDouble(), etaAfter(s).toDouble(), 90_000.0)
    }

    @Test fun etaWhenPickedUpMidRide() {
        val expected = t0 + (route.length / speed * 1000).toLong()
        for (start in 0..4200 step 300) {
            assertEquals("picked up at $start s", expected.toDouble(), etaAfter(start + 90, from = start).toDouble(), 90_000.0)
        }
    }
}
