package com.dubadai.karoo.common

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin

/** Forecast or measured wind: [speedMs] from [fromDeg] (0 north, 90 east). */
data class Wind(val speedMs: Double, val fromDeg: Double)

/**
 * Cycling physics (from Mountaineer, shared since kit 1.1.0): speed from power on a grade (gravity,
 * rolling, air with the head- or tailwind), and power from speed. Good on climbs, where power sets the
 * speed; on the flat and downhill a bunch, braking and corners decide more than watts, so an app
 * should prefer the rider's own measured speed there (UpAhead's ETA does).
 */
object Physics {
    private const val G = 9.81
    private const val CRR = 0.005
    private const val CDA = 0.36
    private const val RHO = 1.2
    /** The bike weight when the rider hasn't set one. */
    const val BIKE_KG = 8.0
    /** Forecast wind is 10 m up and in the open; a rider gets less of it. */
    const val WIND_AT_RIDER = 0.6

    /** Power for [v] (m/s) on [gradePct] with [headwindMs] against you (negative: tailwind). */
    fun power(v: Double, gradePct: Double, massKg: Double, headwindMs: Double = 0.0): Double {
        val th = atan(gradePct / 100)
        val air = v + headwindMs
        return v * (massKg * G * (sin(th) + CRR * cos(th)) + 0.5 * RHO * CDA * air * abs(air))
    }

    /** Speed (m/s) for [watts] on [gradePct] with [headwindMs]. */
    fun speed(watts: Double, gradePct: Double, massKg: Double, headwindMs: Double = 0.0): Double {
        var lo = 0.3; var hi = 25.0
        repeat(40) { val mid = (lo + hi) / 2; if (power(mid, gradePct, massKg, headwindMs) > watts) hi = mid else lo = mid }
        return (lo + hi) / 2
    }

    /** The part of [w] against a rider heading [headingDeg], as felt on the road (negative: tailwind). */
    fun headwind(w: Wind?, headingDeg: Double?): Double {
        if (w == null || headingDeg == null) return 0.0
        return w.speedMs * WIND_AT_RIDER * cos(Math.toRadians(w.fromDeg - headingDeg))
    }
}
