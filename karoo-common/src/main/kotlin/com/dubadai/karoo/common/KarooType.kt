package com.dubadai.karoo.common

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * The Karoo 3's own field typography, so our fields match its native ones. Part of karoo-common,
 * shared by every app. Sources: the ride app's resources (style singleNumericDataStyle,
 * dataHeader) and measurements on a Karoo; see design-assets/karoo-type/README.md.
 *
 * - Values: font "relative" (Hammerhead Relative Mono 12, a system font on the Karoo), letterSpacing
 *   −0.04, auto-sized to fill the space under the header, right-aligned.
 * - Labels / headers: "ibm-plex-sans-condensed" (Plex Sans Condensed Medium), sized by cap height.
 *
 * Relative is Hammerhead's font: load it from the Karoo at runtime, never bundle it in an app.
 * All sizes are Karoo 3 px (480 × 800 screen, 300 dpi). On another density multiply by density / 1.875.
 */
object KarooType {
    /** The Karoo's value font, from the system (falls back to monospace off a Karoo). */
    val value: Typeface by lazy { Typeface.create("relative", Typeface.NORMAL) }

    /** The Karoo's label font (header text). */
    val label: Typeface by lazy { Typeface.create("ibm-plex-sans-condensed", Typeface.NORMAL) }

    /** singleNumericDataStyle letterSpacing (em). */
    const val VALUE_LETTER_SPACING = -0.04f

    /** Space around the value inside its tile (numeric_data_margin, 4 dp). */
    const val VALUE_MARGIN_PX = 8

    /**
     * The value's line area in a tile [tileH] px tall: tileH − 35 px. Calibrated against a native
     * 236 × 140 tile (BetterIntervals, field gallery 1:1): 64 px digits, 24 px under the label caps,
     * the digits' ink 12–13 px above the tile bottom. The 22 dp header + 2 × 4 dp margins from the
     * ride app's layout alone gave 51 px digits (too small). Relative's line ≈ 1.65 × its digit height.
     * Not yet checked against native on tiles much shorter than 140 px.
     */
    fun valueLineH(tileH: Int): Int = tileH - 35

    /** The value's width in a tile [tileW] px wide: tileW − 2 × 4 dp. */
    fun valueW(tileW: Int): Int = tileW - 2 * VALUE_MARGIN_PX

    /** The digits' ink sits about 2 px under Relative's baseline: place the baseline so the ink ends 12 px above the tile bottom. */
    fun valueBaseline(tileH: Int): Float = tileH - 14f

    /**
     * Text for a value in Relative: it has no "−" (U+2212) or "–" (en dash); they fall back to another
     * font with a wide gap. Use a plain hyphen, like the Karoo ("-1:33", "273-287", "162-162").
     */
    /**
     * The text to size a value by, so it doesn't jump as it changes: digits as "8", padded to at
     * least [minDigits] (BigWatts watts: 3, so 99 → 100 W keeps its size; only 1000+ shrinks).
     * Pass the result to [valueSize] (or measure it) instead of the live text.
     */
    fun steadyProbe(text: String, minDigits: Int): String {
        val digits = text.map { if (it.isDigit()) '8' else it }.joinToString("")
        return if (text.isNotEmpty() && text.all { it.isDigit() }) digits.padStart(minDigits, '8') else digits
    }

    fun valueText(s: String): String = s.replace('−', '-').replace('–', '-')

    /** A paint for values: Relative, the Karoo's spacing, right-aligned. Set color and textSize. */
    fun valuePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = value
        letterSpacing = VALUE_LETTER_SPACING
        textAlign = Paint.Align.RIGHT
    }

    /**
     * The value's text size, like the Karoo's uniform auto-size: the largest size at which [text]
     * fits [w] × [h] px. For a tile use `valueSize(p, text, valueW(tileW), valueLineH(tileH))`.
     * Digits are measured as "8" so the size doesn't jump between values; pass the widest value the
     * field can show. Right edge: with Relative and −0.04 spacing the ink ends ~5 px further in than
     * Plex did; native ink ends 8–10 px from the tile edge (labels 7–8), so check it in the gallery.
     */
    fun valueSize(paint: Paint, text: String, w: Int, h: Int, maxPx: Float = 400f): Float {
        val probe = text.map { if (it.isDigit()) '8' else it }.joinToString("")
        var lo = 6f
        var hi = maxPx
        repeat(18) {
            val mid = (lo + hi) / 2
            paint.textSize = mid
            val fm = paint.fontMetrics
            // Like TextView auto-size with includeFontPadding=false: the whole line must fit
            val fits = paint.measureText(probe) <= w && (fm.descent - fm.ascent) <= h
            if (fits) lo = mid else hi = mid
        }
        return lo
    }

    /**
     * Is a view of this field on the ride page on screen? The page-rows rule (README "The rule to use:
     * page rows"): a page is 60 rows; the Karoo stretches the last element into the free rows but
     * reports its set size. [pageGrids] = gridSize of every element on the active page (ActiveRidePage),
     * [fieldGrids] = those of this field's elements, [view] = the view's ViewConfig.gridSize.
     */
    fun onPage(pageGrids: List<Pair<Int, Int>>, fieldGrids: List<Pair<Int, Int>>, view: Pair<Int, Int>): Boolean {
        val full = pageGrids.filter { it.first >= 60 }.sumOf { it.second }
        val half = pageGrids.filter { it.first < 60 }.sumOf { it.second }
        val free = 60 - (full + (half + 1) / 2)
        return fieldGrids.any { it == view || (it.first == view.first && it.second + free == view.second) }
    }

    // ---- Header (measured 1:1 against native fields; SmallerFields TileRenderer) ----

    /** Label cap height: 20 px on tiles under 150 px tall, else 23 px. */
    fun headerCapsPx(tileH: Int): Int = if (tileH < 150) 20 else 23

    /** Top of the label's caps from the tile top. Full-width rows under 150 px: 12. */
    fun headerCapsTop(tileH: Int, fullWidth: Boolean): Int =
        if (fullWidth && tileH < 150) 12 else (21 + (tileH - 142) * 5f / 26f).coerceIn(18f, 26f).roundToInt()

    /** Label text is right-aligned at w − 7 (glyphs end 9 px from the edge). */
    const val HEADER_RIGHT_INSET_PX = 7

    /** Icon box: 12 px from the left, 25 × caps/23 wide, bottom on the label baseline. */
    const val HEADER_ICON_LEFT_PX = 12
    fun headerIconBoxPx(capsPx: Int): Int = (25f * capsPx / 23f).roundToInt()

    /**
     * A header label the Karoo's way: it **never shrinks** a long label, it wraps onto two lines at
     * full size (measured on native LAP AVG / POWER and LAP AVG / SPEED). The first line is filled
     * first. One line: baseline as usual. Two lines: the first 14 px above the one-line baseline, the
     * second 15 px below it, both right-aligned. Returns (text, baseline) pairs for [oneLineBaseline].
     */
    fun headerLines(label: String, paint: Paint, room: Float, oneLineBaseline: Float): List<Pair<String, Float>> {
        val words = label.split(" ")
        // Fits, or a single word (nothing to wrap): one line at the usual baseline; the caller decides
        if (paint.measureText(label) <= room || words.size < 2) return listOf(label to oneLineBaseline)
        var first = words.first()
        var i = 1
        while (i < words.size - 1 && paint.measureText(first + " " + words[i]) <= room) { first += " " + words[i]; i++ }
        val second = words.drop(i).joinToString(" ")
        return listOf(first to oneLineBaseline - 14f, second to oneLineBaseline + 15f)
    }

    /** A paint for header labels with [capsPx] cap height. */
    /** [outline]: the same as [withLabelOutline] (apps that measured their labels thin use it). */
    fun labelPaint(capsPx: Int, outline: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = label
        textAlign = Paint.Align.RIGHT
        textSize = 100f
        val b = Rect(); getTextBounds("H", 0, 1, b)
        textSize = 100f * capsPx / b.height()
        if (outline) withLabelOutline(this)
    }

    /**
     * Opt-in outline for header labels that measure thinner than the Karoo's own on the device.
     * ClearWind's did (~5 %; a 0.002 em FILL_AND_STROKE closes it); Mountaineer's and SmallerFields'
     * plain labels already match native. Measure first; use `withLabelOutline(labelPaint(caps))` only if thin.
     */
    const val LABEL_OUTLINE_EM = 0.002f
    fun withLabelOutline(p: Paint): Paint = p.apply { style = Paint.Style.FILL_AND_STROKE; strokeWidth = LABEL_OUTLINE_EM * textSize }

    // ---- Heart for heart-rate values (our pick, option D, BigWatts 2026-11-08) ----

    /** The Karoo's own heart (as on its HR field), 24-unit path. Filled in [HEART_RED], light and dark. */
    const val HEART_PATH = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    const val HEART_RED = 0xFFD60404.toInt()   // zone colour climb6

    /** Heart next to a value of text size [size]: square box 1.05 × size, top at baseline − 0.9 × size, gap 0.3 × size before the number. */
    fun heartBox(size: Float): Float = 1.05f * size
    fun heartTop(baseline: Float, size: Float): Float = baseline - 0.9f * size
    fun heartGap(size: Float): Float = 0.3f * size

    // ---- Zone colours (the ride app's table; power and heart rate; light and dark; black text) ----

    val ZONE = intArrayOf(0xFF60EEB2.toInt(), 0xFF00B988.toInt(), 0xFFFFF500.toInt(), 0xFFFB8C65.toInt(),
        0xFFFE581F.toInt(), 0xFFD60404.toInt(), 0xFFB700A2.toInt())

    /** Deeper versions for a zone number drawn on white. */
    val ZONE_ON_WHITE = intArrayOf(0xFF20AA78.toInt(), 0xFF007A5A.toInt(), 0xFFB9A300.toInt(), 0xFFE2683A.toInt(),
        0xFFD9480F.toInt(), 0xFFC40404.toInt(), 0xFFA3008F.toInt())

    /** Which of the 7 colours a profile with [count] zones uses (0-based). */
    fun zoneColours(count: Int): List<Int> = when (count) {
        1 -> listOf(2); 2 -> listOf(1, 5); 3 -> listOf(1, 2, 5); 4 -> listOf(1, 2, 3, 5)
        5 -> listOf(0, 1, 2, 3, 5); 6 -> listOf(0, 1, 2, 3, 5, 6); else -> (0..6).toList()
    }.map { ZONE[it] }

    /**
     * The Karoo's own field icons, from its ride app's vector drawables (ic_power, ic_heart outline,
     * ic_speed, ic_cadence, ic_distance, ic_time, ic_lap, ic_ascent, ic_grade, ic_target, ic_timer). Filled, 38 × 38
     * viewport, glyph from about 4 to 34: draw with [drawGlyph]. Speed and distance are in [GLYPH_GREEN]
     * (night [GLYPH_GREEN_NIGHT]) like on the Karoo's fields, the rest in the text colour.
     */
    object Glyph {
        const val BOLT = "M28,16.002L19.002,16.002 19.002,2 10,22 18.999,22 18.999,36z"
        const val HEART = "M29.55,16.936l-10.568,12.43 -10.568,-12.43 -0.007,0.006c-2.141,-2.518 -1.827,-6.301 0.7,-8.433 2.355,-1.99 5.814,-1.852 8.005,0.216 0.16,0.15 0.312,0.31 0.457,0.48 0.882,1.04 1.348,2.293 1.413,3.558 0.064,-1.265 0.53,-2.518 1.412,-3.557 0.146,-0.171 0.297,-0.332 0.457,-0.48 2.191,-2.07 5.65,-2.206 8.005,-0.217 2.527,2.132 2.841,5.915 0.7,8.433l-0.006,-0.006zM30.788,6.236c-3.469,-2.927 -8.515,-2.785 -11.806,0.156 -3.292,-2.94 -8.338,-3.083 -11.807,-0.156 -3.79,3.2 -4.258,8.854 -1.048,12.63l12.855,15.12 12.854,-15.12c3.21,-3.776 2.741,-9.43 -1.048,-12.63z"
        const val SPEED = "M18.988,7C23.979,7 28.401,9.431 31.123,13.172C32.917,15.633 33.974,18.661 33.974,21.936C33.974,25.211 32.917,28.24 31.123,30.7L28.693,28.945C30.128,26.977 30.973,24.555 30.973,21.936C30.973,19.317 30.127,16.896 28.692,14.927C26.516,11.936 22.979,9.992 18.988,9.992C14.996,9.992 11.46,11.936 9.282,14.927C7.847,16.896 7.001,19.317 7.001,21.936C7.001,24.555 7.846,26.977 9.282,28.945L6.851,30.701C5.057,28.241 4,25.211 4,21.936C4,18.661 5.057,15.633 6.851,13.172C9.574,9.431 13.997,7 18.988,7ZM24.691,14.706L20.638,26.667C19.952,27.851 18.426,28.263 17.238,27.58C16.05,26.896 15.636,25.375 16.322,24.191L24.691,14.706Z"
        const val CADENCE = "M36.212,4.113l-7.244,7.22c3.995,4.695 3.769,11.737 -0.676,16.165 -4.445,4.43 -11.51,4.654 -16.22,0.674l-5.833,5.812L2,33.984l8.04,-8.012C6.704,21.306 7.137,14.786 11.336,10.6c4.201,-4.187 10.742,-4.618 15.424,-1.292l5.214,-5.196h4.238zM24.607,11.455c-3.476,-2.181 -8.134,-1.763 -11.159,1.25 -3.024,3.014 -3.442,7.657 -1.255,11.121 0.351,0.556 0.77,1.083 1.255,1.567 0.239,0.238 0.487,0.458 0.745,0.665 3.531,2.817 8.716,2.595 11.987,-0.665 3.272,-3.26 3.494,-8.426 0.665,-11.946l-6.326,6.306 -0.292,0.291h-4.24L18.4,17.64z"
        const val DISTANCE = "M28.5,24c3.038,0 5.5,2.462 5.5,5.5S31.538,35 28.5,35 23,32.538 23,29.5s2.462,-5.5 5.5,-5.5zM27.228,6.987c2.338,0 4.398,1.186 5.609,2.988 0.718,1.067 1.136,2.351 1.136,3.733 0,1.382 -0.418,2.665 -1.136,3.733 -1.21,1.803 -3.271,2.988 -5.609,2.988H10.744c-2.069,0 -3.746,1.672 -3.746,3.734 0,2.063 1.677,3.734 3.746,3.734h9.24v2.988l-9.24,-0.001c-2.339,0 -4.399,-1.186 -5.608,-2.987C4.418,26.83 4,25.546 4,24.163c0,-1.382 0.418,-2.667 1.136,-3.734 1.21,-1.802 3.27,-2.988 5.608,-2.988h16.484c2.07,0 3.747,-1.671 3.747,-3.733 0,-2.062 -1.678,-3.733 -3.747,-3.733h-9.242V6.987zM9.5,3C12.538,3 15,5.462 15,8.5S12.538,14 9.5,14 4,11.538 4,8.5 6.462,3 9.5,3z"
        const val CLOCK = "M18.986,4c8.278,0 14.988,6.687 14.988,14.936 0,8.25 -6.71,14.935 -14.988,14.935C10.71,33.871 4,27.185 4,18.936S10.709,4 18.986,4zM18.986,6.992c-6.62,0 -11.985,5.347 -11.985,11.944s5.365,11.945 11.985,11.945c6.62,0 11.987,-5.348 11.987,-11.945S25.606,6.992 18.986,6.992zM20.486,9.975v7.965h4.493v2.987h-7.494L17.485,17.94h0.003L17.488,9.975h2.998z"
        const val LAP = "M28.885,9.113C26.346,6.575 22.861,5 18.991,5C11.251,5 5,11.265 5,19C5,26.735 11.251,33 18.991,33C25.523,33 30.969,28.538 32.527,22.5H28.885C27.449,26.577 23.562,29.5 18.991,29.5C13.195,29.5 8.485,24.792 8.485,19C8.485,13.208 13.195,8.5 18.991,8.5C21.898,8.5 24.49,9.708 26.381,11.615L20.742,17.25H33V5L28.885,9.113Z"
        const val ASCENT = "M33.9703,18.9742L33.9703,31.9185L4,31.9185L4,28.9314L17.9862,21.9613L24.9793,25.9443L33.9703,18.9742ZM28.9019,8.1416L28.9019,11.1287L4,23.2486L4,20.2613L28.9019,8.1416ZM33.0385,7.6397l-11.2016,-3.9937l-0.8652,2.6041l6.7924,2.4216l-12.4096,6.0526l1.2159,2.493l12.4056,-6.0506l-2.1771,7.1376l4.1113,-2.9426z"
        const val GRADE = "M33.97,16.974L33.97,29.92L4,29.92L4,26.93l13.986,-6.97 6.993,3.983 8.991,-6.97zM33.97,11v2.987l-8.99,6.97 -6.994,-3.983L4,23.944v-2.987l13.986,-6.97 6.993,3.983L33.97,11z"
        /** ic_target (its group's 3.5, 4 offset folded in): a workout's target. */
        const val TARGET = "M19.4813 4.1108C27.7587 4.1108 34.4685 10.7978 34.4685 19.0465C34.4685 27.2953 27.7587 33.9822 19.4813 33.9822C11.2042 33.9822 4.4941 27.2953 4.4941 19.0465C4.4941 10.7978 11.2042 4.1108 19.4813 4.1108ZM19.4813 7.0882C12.8647 7.0882 7.4819 12.4527 7.4819 19.0465C7.4819 25.6404 12.8647 31.0049 19.4813 31.0049C26.0979 31.0049 31.481 25.6404 31.481 19.0465C31.481 12.4527 26.0979 7.0882 19.4813 7.0882ZM19.4813 10.0852C24.4477 10.0852 28.4737 14.0974 28.4737 19.0465C28.4737 23.9958 24.4477 28.008 19.4813 28.008C14.5151 28.008 10.4891 23.9958 10.4891 19.0465C10.4891 14.0974 14.5151 10.0852 19.4813 10.0852ZM19.4813 13.0625C16.1704 13.0625 13.4767 15.7468 13.4767 19.0465C13.4767 22.3463 16.1704 25.0306 19.4813 25.0306C22.7924 25.0306 25.4861 22.3463 25.4861 19.0465C25.4861 15.7468 22.7924 13.0625 19.4813 13.0625ZM19.4813 16.0619C21.1355 16.0619 22.4763 17.3981 22.4763 19.0465C22.4763 20.6948 21.1355 22.0312 19.4813 22.0312C17.8273 22.0312 16.4864 20.6948 16.4864 19.0465C16.4864 17.3981 17.8273 16.0619 19.4813 16.0619Z"
        /** ic_timer: the stopwatch on LAP TIME / TIME. */
        const val TIMER = "M25.353,28.26c-1.7,1.694 -3.96,2.627 -6.365,2.627 -2.404,0 -4.664,-0.933 -6.364,-2.627 -3.51,-3.497 -3.51,-9.188 0,-12.685 1.7,-1.694 3.96,-2.628 6.364,-2.628s4.665,0.934 6.365,2.628c3.51,3.497 3.51,9.188 0,12.685m3.102,-13.668l2.188,-2.18 -2.117,-2.11 -2.187,2.18c-1.742,-1.35 -3.77,-2.156 -5.854,-2.416V6.983h4.497V4H12.994v2.983h4.498v3.083c-2.552,0.319 -5.021,1.45 -6.98,3.403 -4.682,4.666 -4.682,12.23 0,16.896 2.34,2.333 5.408,3.5 8.476,3.5 3.068,0 6.136,-1.167 8.477,-3.5 4.323,-4.308 4.651,-11.086 0.99,-15.773"

        /**
         * ic_climber / ic_climb_tab: the ClimbER mountain (ClimbER drawer tab, climb headers). Its own
         * 54 × 54 viewport (not 38): draw with [drawGlyphFit]. White in the drawer. (Mountaineer)
         */
        const val CLIMBER = "M21.1809,37.5039L33.8665,9.9474H38.05L54.1094,45.4737H48.1715L35.8907,17.9172L23.2052,45.4737H19.0217L13.0512,31.7473L6.9665,45.4737H1.2753L4.7455,37.4302L10.1591,25.4332H15.8502L15.7688,25.6168H15.9177L21.1809,37.5039Z"
        /**
         * ic_elevation: peaks, the ClimbER drawer's "height left" icon (#A0B4BE there). Its own 17 × 17
         * viewport: draw with [drawGlyphFit]. (Mountaineer)
         */
        const val ELEVATION = "M8.676,1.476l2.021,4.597 2.204,-3.352 3.727,6.467 -1.63,-0.074 -2.15,-3.584 -1.432,2.178 3.19,7.22h-0.08l-1.616,-0.02 -4.27,-9.71 -4.27,9.71 -1.615,0.02h-0.079l3.189,-7.22 -1.433,-2.178L2.284,9.115 0.653,9.188l3.727,-6.467L6.583,6.073l2.02,-4.597 0.036,0.082 0.036,-0.082z"
        /**
         * ic_pr: the Strava Live Segments "Personal Record" rosette (white in the ride app; the legend
         * shows it white on a black disc). Its own 41 × 41 viewport: draw with [drawGlyphFit]. The ride
         * app names the PR fields "PR" and "Time to PR" ("Time against PR"). (Mountaineer)
         */
        const val PR = "M24.4903,33.3234C25.3046,35.7368 26.3614,38.6789 26.8245,38.842C27.5674,39.1036 29.1398,34.8839 29.1398,34.8839C29.1398,34.8839 32.9066,36.517 33.4237,35.9984C33.9407,35.4798 30.9008,28.449 30.3722,27.3432C29.8437,26.2375 29.2867,28.9218 27.4443,29.5677C26.8963,29.7597 26.2915,29.8181 25.7213,29.8292L24.4903,33.3234ZM15.0381,12.0115C14.1743,13.2445 15.0186,13.6975 14.5065,15.2636C13.9945,16.8297 13.022,16.8035 13.0002,18.2446C12.9785,19.6857 14.4179,21.1353 14.4179,21.1353C14.4179,21.1353 14.5149,23.6973 15.2154,24.7486C15.9158,25.7999 16.2391,25.1734 17.7849,26.2843C19.3307,27.3952 18.8305,27.9605 20.1772,28.4523C21.5239,28.9441 21.7744,28.091 23.5442,28.091C25.3139,28.091 25.5007,28.9016 26.8226,28.4523C28.1444,28.0031 27.8228,27.3335 29.1263,26.2843C30.4298,25.2352 31.2733,25.7372 32.1388,24.568C33.0044,23.3987 32.1456,23.0117 32.6705,21.4063C33.1954,19.8008 33.9775,19.8624 33.9995,18.3349C34.0216,16.8075 33.264,16.8302 32.7591,15.2636C32.2542,13.6969 32.9591,13.119 32.0502,11.9212C31.1413,10.7234 30.5759,11.3544 29.1263,10.2952C27.6767,9.236 27.9699,8.5411 26.8226,8.1272C25.6752,7.7133 25.4374,8.4378 23.7214,8.4885C22.0054,8.5392 21.5947,7.725 20.2658,8.1272C18.937,8.5294 19.4499,9.1652 18.0507,10.2049C16.6515,11.2445 15.902,10.7785 15.0381,12.0115ZM23.5,25.8779C27.612,25.8779 30.9455,22.4863 30.9455,18.3025C30.9455,14.1188 27.612,10.7271 23.5,10.7271C19.388,10.7271 16.0545,14.1188 16.0545,18.3025C16.0545,22.4863 19.388,25.8779 23.5,25.8779ZM23.5,24.6659C26.9794,24.6659 29.8,21.8169 29.8,18.3025C29.8,14.7882 26.9794,11.9392 23.5,11.9392C20.0206,11.9392 17.2,14.7882 17.2,18.3025C17.2,21.8169 20.0206,24.6659 23.5,24.6659ZM17.0044,27.6738C16.6569,28.5799 13.4423,35.5568 13.7899,36.2817C14.1374,37.0066 17.9601,35.1038 17.9601,35.1038C17.9601,35.1038 19.524,39 20.219,39C20.9141,39 23.6942,31.117 24.0417,29.939C24.3893,28.7611 21.7344,30.4828 20.0452,29.939C18.3561,29.3953 17.352,26.7677 17.0044,27.6738Z"
    }
    const val GLYPH_GREEN = 0xFF00A772.toInt()
    const val GLYPH_GREEN_NIGHT = 0xFF33E2AA.toInt()

    /**
     * The red Karoo heart (2026-11-09): the Karoo's own heart shape ([Glyph.HEART]) **filled**
     * in [HEART_RED] (#D60404, zone colour climb6), the same in light and dark. Glyph.HEART is a ring
     * (inner cut-out, then outer outline); the filled heart is its outer outline alone. An allowed
     * alternative to the text-colour outline heart wherever an app shows heart rate; which one an app
     * uses is our pick. (The Material [HEART_PATH] stays BigWatts' heart next to a big value.)
     */
    val HEART_FILLED: String by lazy { Glyph.HEART.substring(Glyph.HEART.indexOf('M', 1)) }

    /**
     * Box for [drawRedHeart] next to a value of text size [size] (BigWatts' line under the watts):
     * the Karoo heart's ink is ~0.93 × its box tall, so 0.86 × size makes it as tall as the digits'
     * line. Centre it in the [heartBox] slot so the gap to the number stays [heartGap].
     */
    fun redHeartBox(size: Float): Float = 0.86f * size

    fun drawRedHeart(c: android.graphics.Canvas, x: Float, baseline: Float, box: Float, paint: Paint) {
        val old = paint.color
        val oldStyle = paint.style
        paint.color = HEART_RED
        paint.style = Paint.Style.FILL
        drawGlyph(c, HEART_FILLED, x, baseline, box, paint)
        paint.color = old
        paint.style = oldStyle
    }

    /** Draw a [Glyph] path so the glyph fills a [box] px header icon box at [x], its bottom on [baseline]. */
    fun drawGlyph(c: android.graphics.Canvas, pathData: String, x: Float, baseline: Float, box: Float, paint: Paint) {
        val k = box / 30f
        val p = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
        p.transform(android.graphics.Matrix().apply { setScale(k, k); postTranslate(x - 4f * k, baseline - 34f * k) })
        c.drawPath(p, paint)
    }

    /**
     * Draw any glyph path, whatever its viewport (e.g. [Glyph.CLIMBER], [Glyph.ELEVATION]): its ink is
     * scaled to fit a [box] px square, left at [x], bottom on [baseline]. (Mountaineer)
     */
    fun drawGlyphFit(c: android.graphics.Canvas, pathData: String, x: Float, baseline: Float, box: Float, paint: Paint) {
        val p = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
        val b = android.graphics.RectF(); p.computeBounds(b, true)
        val k = box / maxOf(b.width(), b.height())
        p.transform(android.graphics.Matrix().apply { setTranslate(-b.left, -b.bottom); postScale(k, k); postTranslate(x, baseline) })
        c.drawPath(p, paint)
    }

    // ---- Empty states (2026-11-09): a tile with no value, like the Karoo's "No Sensor" ----

    /**
     * Empty-state text ("No Sensor", "No Route", "Searching…", "Not riding", "No connection") the way
     * the Karoo draws it: label font (Plex Sans Condensed Medium), **25 px caps** (~35 px text size),
     * centred, its ink bottom **39 px above the tile bottom**. Measured by PartnerETA on our test Karoo,
     * 238 × 167 tiles with two-line headers (2026-11-09). Not yet measured on 141 px tiles or rows.
     */
    const val EMPTY_CAPS_PX = 25
    const val EMPTY_BOTTOM_PX = 39

    /** Paint for empty-state text: [labelPaint] at 25 px caps, centred, shrunk to fit [tileW] − 2 × 8 px. */
    fun emptyPaint(text: String, tileW: Int): Paint = labelPaint(EMPTY_CAPS_PX).apply {
        textAlign = Paint.Align.CENTER
        val room = (tileW - 2 * VALUE_MARGIN_PX).toFloat()
        val w = measureText(text)
        if (w > room) textSize *= room / w
    }

    /** Baseline for empty-state text (the ink bottom), centred horizontally at tileW / 2. */
    fun emptyBaseline(tileH: Int): Float = (tileH - EMPTY_BOTTOM_PX).toFloat()

    // ---- Our green flag (PartnerETA; approved for the library, 2026-11-09) ----

    /**
     * The finish flag: Tabler "flag" (MIT licence), a **stroked** outline on a 24-unit grid, stroke
     * 2.2, round caps and joins. The Karoo has no flag of its own; this one is ours, in the Karoo green.
     */
    const val FLAG_PATH = "M5,21V4 M5,4C8,2 11,2 13,4C15,6 18,6 19,5V14C18,15 15,15 13,13C11,11 8,11 5,13"
    const val FLAG_STROKE = 2.2f
    const val FLAG_GREEN = 0xFF129A5E.toInt()        // karoo_green, light
    const val FLAG_GREEN_NIGHT = 0xFF1DB872.toInt()  // karoo_green, night
    const val STATE_GREY = 0xFF7A7A78.toInt()        // waiting, old, not riding
    const val STATE_RED = 0xFFE5484D.toInt()         // the red × for "no connection"

    /** States follow the Karoo's sensor legend: live green, waiting grey, failed grey with a red ×. */
    enum class GlyphState { LIVE, WAITING, NO_CONNECTION }

    /**
     * Draws the flag sized like the Karoo's glyphs: its ink (stroke included, units 3.9…20.6 tall)
     * fills the header icon [box] ([headerIconBoxPx]) with its bottom on [baseline], so it lines up with
     * every other header icon at every tile size. LIVE is green ([night] picks the night green); WAITING
     * and NO_CONNECTION are grey, **always full size**. Rule "× only if needed" (2026-11-09): when the
     * tile already says it in words ("No connection"), pass [cross] = false; only a tile without such
     * text (e.g. an old value still showing) passes [cross] = true, and the red × goes to the right of
     * the icon ([drawStateCross]). Leave room for it: [STATE_CROSS_ROOM] × box after the icon box.
     */
    fun drawFlag(c: android.graphics.Canvas, x: Float, baseline: Float, box: Float, state: GlyphState = GlyphState.LIVE, night: Boolean = false, cross: Boolean = false) {
        val k = box / (21f + FLAG_STROKE / 2 - (2.5f - FLAG_STROKE / 2))   // ink height in units
        val p = androidx.core.graphics.PathParser.createPathFromPathData(FLAG_PATH)
        p.transform(android.graphics.Matrix().apply {
            setTranslate(-(5f - FLAG_STROKE / 2), -(21f + FLAG_STROKE / 2)); postScale(k, k); postTranslate(x, baseline)
        })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = FLAG_STROKE * k
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            color = if (state == GlyphState.LIVE) (if (night) FLAG_GREEN_NIGHT else FLAG_GREEN) else STATE_GREY
        }
        c.drawPath(p, paint)
        if (state == GlyphState.NO_CONNECTION && cross) drawStateCross(c, x, baseline, box)
    }

    /** Room the red × needs to the right of an icon box, as a share of the box. */
    const val STATE_CROSS_ROOM = 0.6f

    /**
     * The red × of a failed state, **to the right of** a full-size icon whose box starts at [x]: centre
     * (x + 1.22 × box, baseline − 0.55 × box), arm 0.16 × box, stroke 0.11 × box. Works with any glyph
     * (flag, Karoo field icons). Only draw it when the tile doesn't already say the state in words.
     */
    fun drawStateCross(c: android.graphics.Canvas, x: Float, baseline: Float, box: Float) {
        val arm = 0.16f * box
        val stroke = 0.11f * box
        val cx = x + box + 0.22f * box
        val cy = baseline - 0.55f * box
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND; color = STATE_RED
        }
        c.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, paint)
        c.drawLine(cx - arm, cy + arm, cx + arm, cy - arm, paint)
    }
}
