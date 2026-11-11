package com.daviddokakis.partnereta

import kotlin.math.cos
import kotlin.math.pow

/**
 * What to draw on the map for the partner, as plain data (unit-tested). Rules, matched to the
 * Karoo's own route on the device:
 * - Her route ahead is the hero: line + outline about as wide as your yellow route, with small
 *   double chevrons ~240 screen px apart, on straight stretches only (as the Karoo places its own).
 * - What she has ridden: only with "Show ride history" on (off by default), a thin calm line.
 * - Your route always wins. The SDK draws extension lines above the Karoo's route with no layer
 *   order, so wherever her route runs on yours her line is left out. Shared roads ([SharedLook]):
 *   DOTS (standard) / ARROWS: her line runs on to your route's centre and a short patch of your
 *   route is drawn back over the join, so your route "cuts" hers; small markers in her colour
 *   (dots, or arrows pointing her way) sit on your route. RAILS: two thin rails instead.
 * - Lines are simplified to about one screen pixel at the current zoom, like the Karoo's route.
 */
object MapOverlay {
    data class Line(val id: String, val encoded: String, val color: Int, val width: Int)
    data class Mark(val id: String, val pos: LatLng, val bearing: Double, val icon: Int)

    /** Your own route, for keeping clear of it and for drawing rails along it. */
    class Mine(points: List<LatLng>) {
        val path = Path(points)
        val index = NearIndex(points)
    }

    const val TRACK = "partner-track-"
    const val AHEAD_CASING = "partner-ahead-casing-"
    const val AHEAD = "partner-ahead-"
    const val RAIL = "partner-rail-"
    const val PATCH_CASING = "partner-patch-casing-"
    const val PATCH = "partner-patch-"
    const val CHEVRON = "partner-chevron-"
    const val CHEV_LINE = "partner-chev-"
    const val LEAD_CASING = "partner-lead-casing-"
    const val LEAD = "partner-lead-"
    /**
     * Her chevrons as lines drawn into her line (true) or as icons (false). Lines looked best in a
     * mockup, but on the device the Karoo reshapes short lines: each "^" became a bar beside her
     * line's centre. Icons until that's solved (docs/design/line-chevrons/).
     */
    const val LINE_CHEVRONS = false
    /** Redraw your route over each join so it looks like it cuts hers (off: it bulged on the device). */
    const val CUT_JOINS = false
    /**
     * Her line's chevrons: half width, depth and gap between the two "^" (screen px), 2 units thick.
     * Her line is ~14 px with its outline; the arms reach into the outline (same navy, so they
     * blend in) but not past it. 3.5 px / 1 unit looked right in a 3x mockup, invisible on device.
     */
    private const val CHEV_HALF_PX = 5.5
    private const val CHEV_DEPTH_PX = 4.5
    private const val CHEV_GAP_PX = 6.5
    private const val CHEV_UNITS = 2
    /**
     * Real metres per screen px vs [metresPerScreenPx], read off the Karoo's scale bar (1.5 at zoom
     * 10 and 12). The other sizes here were tuned by eye around it; these need true pixels.
     */
    private const val CHEV_TRUE_SCALE = 1.5
    /** At most this many on her line (each is two map lines), nearest her first. */
    private const val MAX_LINE_CHEVRONS = 25
    const val LINE_WIDTH = 10
    const val OUTLINE_WIDTH = 16
    const val RIDDEN_WIDTH = 5
    const val RAIL_WIDTH = 3
    /** Highest ids we ever use, so a fresh start can clear what an earlier run left on the map. */
    const val MAX_LINE_IDS = 40
    val MAX_CHEVRON_IDS get() = MAX_CHEVRONS
    const val GREY = 0xFF9A9A9A.toInt()
    const val GREY_DARK = 0xFF6E6E6E.toInt()

    /**
     * Ground metres per screen pixel = [metresPerPixel] (512 px tiles) × this. Measured on the
     * Karoo 3 from its scale bar: at zoom 10.87, 1 km = 71 screen px (14.1 m/px vs 21.4 m per
     * 512-tile px). See docs/design/partner-map/README.md.
     */
    var screenFactor = 0.66
    private const val CHEVRON_SPACING_PX = 240.0
    /** A chevron needs this many px of straight line either side of it. */
    private const val STRAIGHT_PX = 14.0
    private const val STRAIGHT_DEG = 15.0
    private const val MAX_CHEVRONS = 120
    /**
     * Your route's half width on screen (13 px yellow + 3 px black each side, measured on the
     * device). Her line ends where its round end just touches your outline: at this plus half her
     * own (outlined) width from your route's centre, found exactly (bisection), not at a sample point.
     */
    private const val MY_HALF_PX = 10.0
    /**
     * SDK line widths are display units, not pixels: measured on the device, width 7 draws 12-13 px
     * (and your route is 20 px wide at every zoom from 11.9 to 15.1: 12-13 px #FFFF00 + ~4 px black).
     */
    const val UNIT_PX = 1.8
    /** Your route redrawn over a join: 11 units ≈ 20 px black, 7 units ≈ 12.5 px yellow. */
    private const val MY_CASING_UNITS = 11
    private const val MY_FILL_UNITS = 7
    private const val MY_FILL = 0xFFFFFF00.toInt()
    private const val MY_CASING = 0xFF000000.toInt()
    /** Patch length either side of a join (the yellow runs a bit further, so no black caps show). */
    private const val PATCH_PX = 18.0
    /**
     * Below this zoom the Karoo draws routes thinner than the 20 px everything here is sized for,
     * so the cut patches and shared-road markers come out too big: left out (seen on the device at
     * zoom 10-11). Her chevrons always show.
     */
    const val DETAIL_ZOOM = 11.75

    /** Markers behind her dot within this are ones she has passed (her line's tail is shorter). */
    const val PASSED_M = 600.0

    /** Mini markers on your route where she rides it too. */
    private const val DOT_SPACING_PX = 70.0 // 220 was tried (fewer near-collisions with the Karoo chevrons); we preferred 70
    /** Used where no style is at hand (rails, chevron keep-out): the standard line's join distance. */
    private const val CLEAR_PX = 16.0
    /** Rails sit just outside your route's black outline (~10 px from its centre). */
    private const val RAIL_OFFSET_PX = 13.0
    private const val SIMPLIFY_PX = 1.0
    private const val MIN_SHARED_PX = 20.0

    /** Ground metres per map pixel at [zoom] (512 px tiles, as the Karoo's Mapbox map). */
    fun metresPerPixel(lat: Double, zoom: Double) = 40_075_016.7 * cos(Math.toRadians(lat)) / (512 * 2.0.pow(zoom))

    /** Ground metres per screen pixel on the Karoo. */
    fun metresPerScreenPx(lat: Double, zoom: Double) = metresPerPixel(lat, zoom) * screenFactor

    /**
     * The short piece of her line from her to the long one ([PartnerView.routeLead]): the only piece
     * a new position redraws. Its cyan runs on over the long piece; its outline stops at the join,
     * so it never lies over the long piece's cyan.
     */
    fun leadLines(v: PartnerView, style: MapStyle, zoom: Double, mine: Mine?): List<Line> {
        val r = v.routeLead ?: return emptyList()
        val lat = v.position?.lat ?: return emptyList()
        val px = metresPerScreenPx(lat, zoom)
        val stale = v.status == PartnerView.Status.STALE
        val pts = Geo.decode(r)
        if (pts.size < 2) return emptyList()
        val casing = Path(pts).between(0.0, v.leadCasingM)
        val out = ArrayList<Line>()
        split(casing, px, mine, joinPx(style)).filter { !it.shared }.forEachIndexed { i, p ->
            out += Line("$LEAD_CASING$i", Geo.encode(p.points), style.outline, style.outlineWidth)
        }
        split(pts, px, mine, joinPx(style)).filter { !it.shared }.forEachIndexed { i, p ->
            out += Line("$LEAD$i", Geo.encode(p.points), if (stale) GREY else style.line, style.lineWidth)
        }
        return out
    }

    fun lines(v: PartnerView, style: MapStyle, zoom: Double, mine: Mine?, showHistory: Boolean = false): List<Line> {
        val lat = v.position?.lat ?: return emptyList()
        val px = metresPerScreenPx(lat, zoom)
        val stale = v.status == PartnerView.Status.STALE
        val lineColor = if (stale) GREY else style.line
        val out = ArrayList<Line>()
        if (showHistory) v.trace?.let { t ->
            // Optionally only a short tail of what she has ridden: enough to see where she came from.
            val ridden = Geo.decode(t).let { pts -> style.trailM?.let { tail(pts, it) } ?: pts }
            split(ridden, px, mine, MY_HALF_PX + style.riddenWidth * UNIT_PX / 2).filter { !it.shared }.forEachIndexed { i, p ->
                out += Line("$TRACK$i", Geo.encode(p.points), if (stale) GREY_DARK else style.ridden, style.riddenWidth)
            }
        }
        // The long piece of her line (the short piece from her to it: leadLines). Views without the
        // split (previews, tests) just have routeAhead.
        (if (v.routeLead != null) v.routeRest else v.routeRest ?: v.routeAhead)?.let { r ->
            val parts = split(Geo.decode(r), px, mine, joinPx(style))
            // The cut (your route redrawn over the join) is off: the Karoo draws your route a few px
            // from its points, so the redrawn bit bulged beside it (seen on the device). Her line
            // ends at your route's edge instead.
            val cut = CUT_JOINS && style.shared != SharedLook.RAILS && mine != null && zoom >= DETAIL_ZOOM
            // With the cut, her line runs on to your route's centre wherever it meets yours.
            val joins = ArrayList<Double>()
            val own = parts.filter { !it.shared }.map { part ->
                if (!cut || mine == null) part.points else {
                    var pts = part.points
                    if (part.endsAtMine) centreOf(mine, pts.last(), joinPx(style) * px)?.let { (p, a) -> pts = pts + p; joins += a }
                    if (part.startsAtMine) centreOf(mine, pts.first(), joinPx(style) * px)?.let { (p, a) -> pts = listOf(p) + pts; joins += a }
                    pts
                }
            }
            val shared = parts.filter { it.shared && !it.tiny }
            // Rails first (under her own line where they meet), then outlines, then the line.
            // Rails follow *your* route's line (hers wobbles beside it), just outside its outline.
            var n = 0
            for (s in shared.filter { style.rails && style.shared == SharedLook.RAILS }) {
                val along = mine?.let { m -> railAlong(m, s.points, joinPx(style) * px) } ?: continue
                val railColor = if (stale) GREY_DARK else if (style.railsInOutline) style.outline else style.line
                for (side in listOf(-1.0, 1.0)) {
                    out += Line("$RAIL${n++}", Geo.encode(offset(along, side * RAIL_OFFSET_PX * px)), railColor, RAIL_WIDTH)
                }
            }
            own.forEachIndexed { i, p -> out += Line("$AHEAD_CASING$i", Geo.encode(p), style.outline, style.outlineWidth) }
            own.forEachIndexed { i, p -> out += Line("$AHEAD$i", Geo.encode(p), lineColor, style.lineWidth) }
            if (LINE_CHEVRONS) out += chevronLines(v, zoom, mine, style, px)
            // Your route redrawn over each join, on top: it looks like yours cuts across hers.
            if (cut && mine != null) joins.forEachIndexed { i, a ->
                val l = PATCH_PX * px
                val casing = simplify(mine.path.between(a - l, a + l), SIMPLIFY_PX * px)
                val fill = simplify(mine.path.between(a - l - MY_HALF_PX * px, a + l + MY_HALF_PX * px), SIMPLIFY_PX * px)
                if (casing.size >= 2) out += Line("$PATCH_CASING$i", Geo.encode(casing), MY_CASING, MY_CASING_UNITS)
                if (fill.size >= 2) out += Line("$PATCH$i", Geo.encode(fill), MY_FILL, MY_FILL_UNITS)
            }
        }
        return out
    }

    /**
     * Small double chevrons on her route ahead: Karoo spacing, straight stretches only. Off your
     * route in her outline colour; over your route (where she rides it too) in her own colour,
     * pointing her way, when the style has [MapStyle.sharedChevron].
     */
    /**
     * Where the chevrons on her own line go (point + heading): Karoo spacing, straight stretches
     * only, not where she runs on your route.
     */
    fun chevronSpots(v: PartnerView, zoom: Double, mine: Mine?, style: MapStyle): List<Pair<LatLng, Double>> {
        val r = v.routeAhead ?: return emptyList()
        val lat = v.position?.lat ?: return emptyList()
        val px = metresPerScreenPx(lat, zoom)
        val clear = joinPx(style) * px
        val out = ArrayList<Pair<LatLng, Double>>()
        place(Path(simplify(Geo.decode(r), SIMPLIFY_PX * px)), px) { p, b ->
            if (out.size < (if (LINE_CHEVRONS) MAX_LINE_CHEVRONS else MAX_CHEVRONS) && mine?.index?.isNear(p, clear) != true) out += p to b
        }
        return out
    }

    /**
     * Her chevrons drawn into her line: two small navy "^" per spot, each a 3-point line, sized to
     * stay inside the cyan. Drawn by the map like her line, so they sit exactly on it at every zoom
     * (icons drifted off it on bends, seen at zoom 10-11).
     */
    private fun chevronLines(v: PartnerView, zoom: Double, mine: Mine?, style: MapStyle, px: Double): List<Line> {
        val m = px * CHEV_TRUE_SCALE
        val w = CHEV_HALF_PX * m; val depth = CHEV_DEPTH_PX * m; val gap = CHEV_GAP_PX * m
        val out = ArrayList<Line>()
        chevronSpots(v, zoom, mine, style).forEach { (p, b) ->
            for (o in listOf(-gap / 2, gap / 2)) {
                val tip = Geo.destination(p, b, o)
                val back = Geo.destination(tip, b + 180, depth)
                val pts = listOf(Geo.destination(back, b - 90, w), tip, Geo.destination(back, b + 90, w))
                out += Line("$CHEV_LINE${out.size}", Geo.encode(pts), style.outline, CHEV_UNITS)
            }
        }
        return out
    }

    fun chevrons(v: PartnerView, style: MapStyle, zoom: Double, mine: Mine?, dots: Boolean = false): List<Mark> {
        val r = v.routeAhead ?: return emptyList()
        val lat = v.position?.lat ?: return emptyList()
        val px = metresPerScreenPx(lat, zoom)
        val clear = joinPx(style) * px
        val out = ArrayList<Mark>()
        if (!LINE_CHEVRONS) chevronSpots(v, zoom, mine, style).forEach { (p, b) -> out += Mark("$CHEVRON${out.size}", p, b, style.chevron) }
        // Shared-road markers: arrows (pointing her way) unless the rider picked dots (or the style is DOTS).
        val useDots = dots || style.shared == SharedLook.DOTS
        val dot = if (useDots) style.dot else style.miniArrow
        if (mine != null && style.shared != SharedLook.RAILS && dot != null && zoom >= DETAIL_ZOOM) {
            for (part in split(Geo.decode(r), px, mine, joinPx(style)).filter { it.shared && !it.tiny }) {
                val along = railAlong(mine, part.points, clear, keepDirection = true) ?: continue
                Path(along).every(DOT_SPACING_PX * px, MAX_CHEVRONS).forEach { (p, b) -> out += Mark("$CHEVRON${out.size}", p, if (useDots) 0.0 else b, dot) }
            }
        }
        val shared = style.sharedChevron
        if (mine != null && shared != null && style.shared == SharedLook.RAILS && zoom >= DETAIL_ZOOM) {
            for (part in split(Geo.decode(r), px, mine, joinPx(style)).filter { it.shared && !it.tiny }) {
                val along = railAlong(mine, part.points, clear, keepDirection = true) ?: continue
                place(Path(along), px) { p, b -> out += Mark("$CHEVRON${out.size}", p, b, shared) }
            }
        }
        return out.take(MAX_CHEVRONS)
    }

    /** Spots ~240 px apart on straight stretches (sliding forward to the next straight bit, like Mapbox). */
    private fun place(path: Path, px: Double, add: (LatLng, Double) -> Unit) {
        val spacing = CHEVRON_SPACING_PX * px
        val reach = STRAIGHT_PX * px
        var d = spacing / 2
        var n = 0
        while (d < path.length - reach && n < MAX_CHEVRONS) {
            var placed = false
            var t = d
            while (t < d + spacing / 2 && t < path.length - reach) {
                val (p, b) = path.pointAt(t)
                val before = path.pointAt(t - reach).second
                val after = path.pointAt(t + reach).second
                if (Geo.angleBetween(before, b) < STRAIGHT_DEG && Geo.angleBetween(b, after) < STRAIGHT_DEG) {
                    add(p, b); n++
                    placed = true
                    break
                }
                t += reach
            }
            d = if (placed) t + spacing else d + spacing
        }
    }

    /** The last [metres] of a ridden track. */
    fun tail(points: List<LatLng>, metres: Double): List<LatLng> {
        val path = Path(points)
        return if (path.length <= metres) points else path.from(path.length - metres)
    }

    /** True when [p] is just behind her dot at [dot] going [bearing]: a marker she has passed. */
    fun isBehind(p: LatLng, dot: LatLng, bearing: Double): Boolean {
        val d = Geo.distance(dot, p)
        return d < PASSED_M && (d < 1 || Geo.angleBetween(Geo.bearing(dot, p), bearing) > 90)
    }

    /**
     * What the map draws with "Their route" hidden: no route line, chevrons or shared-road markers,
     * but ride history (its own setting), the marker and the finish flag ([finish] reads the full view).
     */
    fun withoutRoute(v: PartnerView) = v.copy(routeAhead = null, routeRest = null, routeLead = null)

    /** Where her route ends, for the finish flag. */
    fun finish(v: PartnerView): LatLng? = v.routeAhead?.let { Geo.decode(it).lastOrNull() }

    /**
     * A stretch of her route: on your route ([shared]) or off it. [startsAtMine]/[endsAtMine]: this
     * end meets your route. [tiny]: a shared bit too short to be "riding the same road" (a crossing).
     */
    private data class Part(
        val points: List<LatLng>,
        val shared: Boolean,
        val startsAtMine: Boolean = false,
        val endsAtMine: Boolean = false,
        val tiny: Boolean = false,
    )

    /** The point on your route's centre line nearest [p] (within [reach]), and how far along it is. */
    private fun centreOf(mine: Mine, p: LatLng, reach: Double): Pair<LatLng, Double>? =
        mine.path.hits(p, reach * 1.5).firstOrNull()?.let { mine.path.pointAt(it.along).first to it.along }

    /** Simplify for this zoom, then split into stretches on your route (shared) and off it. */
    /** The stretch of your route that her shared piece [points] runs along. */
    private fun railAlong(mine: Mine, points: List<LatLng>, clear: Double, keepDirection: Boolean = false): List<LatLng>? {
        fun at(p: LatLng) = mine.path.hits(p, clear * 1.5).firstOrNull()?.along
        val a = at(points.first()) ?: return null
        val b = at(points.last()) ?: return null
        // Start and end a route-width in, so the rails don't poke out where she joins or leaves.
        val trim = clear * 1.5
        if (kotlin.math.abs(b - a) <= 3 * trim) return null
        val (lo, hi) = if (a < b) a + trim to b - trim else a - trim to b + trim
        // Simplified like the Karoo draws your route at this zoom, so the rails don't wiggle.
        val stretch = simplify(mine.path.between(lo, hi), clear / CLEAR_PX * SIMPLIFY_PX)
        // between() runs in your route's direction; flip it when she rides it the other way.
        val inHerDirection = if (keepDirection && a > b) stretch.reversed() else stretch
        return inHerDirection.takeIf { it.size >= 2 }
    }

    /** How far from your route's centre her line ends: its round end then just touches your outline. */
    private fun joinPx(style: MapStyle) = MY_HALF_PX + style.outlineWidth * UNIT_PX / 2

    /**
     * Simplify for this zoom, then split into stretches on your route (shared, within [clearPx]
     * of it) and off it. Each boundary is found exactly, so her line ends right at your outline.
     */
    private fun split(points: List<LatLng>, px: Double, mine: Mine?, clearPx: Double): List<Part> {
        val simple = simplify(points, SIMPLIFY_PX * px)
        if (mine == null) return listOf(Part(simple, false)).filter { it.points.size >= 2 }
        val clear = clearPx * px
        val dense = densify(simple, clear / 2)
        val out = ArrayList<Part>()
        var cur = ArrayList<LatLng>()
        var curShared: Boolean? = null
        var prev: LatLng? = null
        for (p in dense) {
            val s = mine.index.isNear(p, clear)
            if (curShared != null && s != curShared && prev != null) {
                val edge = boundary(prev, p, mine, clear)
                cur += edge
                out += Part(cur, curShared, startsAtMine = out.isNotEmpty() && !curShared, endsAtMine = !curShared)
                cur = arrayListOf(edge)
            }
            cur += p
            curShared = s
            prev = p
        }
        if (curShared != null && cur.size >= 2) out += Part(cur, curShared, startsAtMine = out.isNotEmpty() && !curShared)
        // Tiny shared bits (crossing your route) aren't "riding the same road": no edge or rails.
        val minShared = MIN_SHARED_PX * px
        return out.mapNotNull { part ->
            val pts = simplify(part.points, SIMPLIFY_PX * px)
            if (pts.size < 2) null
            else part.copy(points = pts, tiny = part.shared && Path(pts).length < minShared)
        }
    }

    /** The point between [a] and [b] where the distance to your route crosses [clear] (bisection). */
    private fun boundary(a: LatLng, b: LatLng, mine: Mine, clear: Double): LatLng {
        val aNear = mine.index.isNear(a, clear)
        var lo = 0.0; var hi = 1.0
        repeat(14) {
            val mid = (lo + hi) / 2
            if (mine.index.isNear(Geo.lerp(a, b, mid), clear) == aNear) lo = mid else hi = mid
        }
        // Take the side that is *off* your route, so her line never reaches into it.
        return Geo.lerp(a, b, if (aNear) hi else lo)
    }

    /** The line moved sideways by [metres] (positive = right of the direction of travel). */
    fun offset(points: List<LatLng>, metres: Double): List<LatLng> {
        if (points.size < 2) return points
        return points.indices.map { i ->
            val inB = if (i > 0) Geo.bearing(points[i - 1], points[i]) else Geo.bearing(points[0], points[1])
            val outB = if (i < points.size - 1) Geo.bearing(points[i], points[i + 1]) else inB
            // Average of the two directions, so corners get a shared normal.
            val x = kotlin.math.sin(Geo.rad(inB)) + kotlin.math.sin(Geo.rad(outB))
            val y = kotlin.math.cos(Geo.rad(inB)) + kotlin.math.cos(Geo.rad(outB))
            val mid = (Geo.deg(kotlin.math.atan2(x, y)) + 360) % 360
            // Mitre: at a corner the offset has to reach further to stay [metres] from both legs.
            val half = Geo.rad(Geo.angleBetween(inB, outB) / 2)
            val mitre = (1 / kotlin.math.cos(half)).coerceAtMost(2.5)
            Geo.destination(points[i], mid + 90, metres * mitre)
        }
    }

    private fun densify(points: List<LatLng>, stepM: Double): List<LatLng> {
        if (points.size < 2) return points
        val out = ArrayList<LatLng>()
        for (i in 0 until points.size - 1) {
            val a = points[i]; val b = points[i + 1]
            val n = (Geo.distance(a, b) / stepM).toInt().coerceIn(1, 200)
            for (k in 0 until n) out += Geo.lerp(a, b, k.toDouble() / n)
        }
        out += points.last()
        return out
    }
}
