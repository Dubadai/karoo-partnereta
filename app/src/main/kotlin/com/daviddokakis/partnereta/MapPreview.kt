package com.daviddokakis.partnereta

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as GPath
import android.view.View

/**
 * A bit of map for the setup screen: your yellow route and the partner's line, outline, marker,
 * finish pin and shared-road markers in the chosen style. The Karoo's map looks the same in light
 * and dark mode, so this does too. Drawn by hand: it only has to look like the real thing.
 */
class MapPreview(
    context: Context,
    private val style: MapStyle,
    private val circle: Boolean,
    private val dots: Boolean,
    private val history: Boolean,
    /** "Their route" shown; hidden: only their marker (and ride history, if on). */
    private val route: Boolean = true,
) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val d = resources.displayMetrics.density

    private fun line(c: Canvas, pts: List<Pair<Float, Float>>, color: Int, w: Float) {
        val path = GPath(); pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = w; c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        fun pt(x: Float, y: Float) = x * w to y * h
        p.style = Paint.Style.FILL; p.color = LAND
        c.drawRoundRect(0f, 0f, w, h, 10 * d, 10 * d, p)
        c.save(); c.clipRect(0f, 0f, w, h)

        // Close-up street map like the Karoo's navigation view: a grid of white streets between
        // grey blocks. You ride up a street and your route turns right ahead; he comes along a
        // cross street from the left and joins your route just ahead of you.
        val cols = listOf(0.12f, 0.33f, 0.56f, 0.8f)
        val rows = listOf(0.22f, 0.5f, 0.82f)
        for (i in 0..cols.size) for (j in 0..rows.size) {
            val x0 = (if (i == 0) -0.1f else cols[i - 1]) + 0.035f; val x1 = (if (i == cols.size) 1.1f else cols[i]) - 0.035f
            val y0 = (if (j == 0) -0.1f else rows[j - 1]) + 0.06f; val y1 = (if (j == rows.size) 1.1f else rows[j]) - 0.06f
            if (x1 > x0 && y1 > y0) fill(c, listOf(pt(x0, y0), pt(x1, y0), pt(x1, y1), pt(x0, y1)), if ((i + j) % 4 == 1) PARK else BLOCK)
        }
        val streets = cols.map { x -> listOf(pt(x, -0.05f), pt(x, 1.05f)) } + rows.map { y -> listOf(pt(-0.05f, y), pt(1.05f, y)) }
        streets.forEach { line(c, it, CASING, 7 * d) }
        streets.forEach { line(c, it, ROAD, 5 * d) }

        val myX = 0.33f
        val mine = listOf(pt(myX, 1.1f), pt(myX, 0.22f), pt(1.1f, 0.22f))
        val join = pt(myX, 0.5f)
        // Ride history (only with it on): he came up the street on the left, then turned right.
        if (history) line(c, listOf(pt(0.12f, 1.1f), pt(0.12f, 0.5f)), style.ridden, 3 * d)
        // Where he's going: along the cross street to your route.
        val ahead = listOf(pt(0.12f, 0.5f), join)
        if (route) { line(c, ahead, style.outline, 9 * d); line(c, ahead, style.line, 5 * d) }
        // Your route on top (it cuts his line where he joins), with the Karoo's chevrons behind you.
        line(c, mine, 0xFF000000.toInt(), 12 * d); line(c, mine, 0xFFFFFF00.toInt(), 8 * d)
        chevron(c, myX * w, 0.95f * h, -90f, 0xFF000000.toInt())
        // Where he'll ride your route too: up from where he joins, then right with your route.
        val marks = listOf(Triple(myX, 0.36f, -90f), Triple(0.5f, 0.22f, 0f), Triple(0.72f, 0.22f, 0f), Triple(0.94f, 0.22f, 0f))
        for ((x, y, deg) in marks.takeIf { route }.orEmpty()) {
            val cx = w * x; val cy = h * y
            if (dots) {
                p.style = Paint.Style.FILL; p.color = style.line; c.drawCircle(cx, cy, 3.5f * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = style.outline; c.drawCircle(cx, cy, 3.5f * d, p)
            } else {
                c.save(); c.translate(cx, cy); c.rotate(deg)
                val a = GPath().apply { moveTo(5 * d, 0f); lineTo(-4 * d, -4 * d); lineTo(-2 * d, 0f); lineTo(-4 * d, 4 * d); close() }
                p.style = Paint.Style.FILL; p.color = style.line; c.drawPath(a, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * d; p.color = style.outline; c.drawPath(a, p)
                c.restore()
            }
        }
        // You: the Karoo's position arrow, yellow with a black outline, bigger than the route is wide.
        c.save(); c.translate(myX * w, 0.7f * h)
        val me = GPath().apply { moveTo(0f, -15 * d); lineTo(11 * d, 11 * d); lineTo(0f, 7 * d); lineTo(-11 * d, 11 * d); close() }
        p.style = Paint.Style.FILL; p.color = 0xFFFFE600.toInt(); c.drawPath(me, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0xFF000000.toInt(); c.drawPath(me, p)
        c.restore()
        // His marker on the cross street, heading right towards your route.
        c.save(); c.translate(0.12f * w, 0.5f * h); c.rotate(90f)
        if (circle) {
            p.style = Paint.Style.FILL; p.color = style.line; c.drawCircle(0f, 0f, 10 * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = style.outline; c.drawCircle(0f, 0f, 10 * d, p)
            val a = GPath().apply { moveTo(0f, -6.5f * d); lineTo(4.5f * d, 3.5f * d); lineTo(0f, 1.2f * d); lineTo(-4.5f * d, 3.5f * d); close() }
            p.style = Paint.Style.FILL; p.color = style.outline; c.drawPath(a, p)
        } else {
            val a = GPath().apply { moveTo(0f, -11 * d); lineTo(8 * d, 9 * d); lineTo(0f, 4 * d); lineTo(-8 * d, 9 * d); close() }
            p.style = Paint.Style.FILL; p.color = style.line; c.drawPath(a, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = style.outline; c.drawPath(a, p)
        }
        c.restore()
        c.restore()
    }

    private fun fill(c: Canvas, pts: List<Pair<Float, Float>>, color: Int) {
        val path = GPath(); pts.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }; path.close()
        p.style = Paint.Style.FILL; p.color = color; c.drawPath(path, p)
    }

    /** The Karoo's double chevron on a route, pointing [deg] (0 = right). */
    private fun chevron(c: Canvas, x: Float, y: Float, deg: Float, color: Int) {
        c.save(); c.translate(x, y); c.rotate(deg)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = color; p.strokeCap = Paint.Cap.SQUARE
        for (o in listOf(-1.8f, 1.8f)) {
            val a = GPath().apply { moveTo(o * d - 2 * d, -3.2f * d); lineTo(o * d + 1.5f * d, 0f); lineTo(o * d - 2 * d, 3.2f * d) }
            c.drawPath(a, p)
        }
        p.strokeCap = Paint.Cap.ROUND
        c.restore()
    }

    companion object {
        // Sampled from the Karoo's map on the device.
        private const val LAND = 0xFFE2DFD8.toInt()
        private const val PARK = 0xFFA8BC9A.toInt()
        private const val WATER = 0xFF6BA8B5.toInt()
        private const val ROAD = 0xFFFFFFFF.toInt()
        private const val CASING = 0xFFC4C0B8.toInt()
        private const val BLOCK = 0xFFD3D0CA.toInt()
    }
}
