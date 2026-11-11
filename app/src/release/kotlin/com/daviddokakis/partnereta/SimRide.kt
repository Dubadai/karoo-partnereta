package com.daviddokakis.partnereta

import android.content.Context

/** Release builds: no simulated rider. (The debug version lives in src/debug.) */
object SimRide {
    fun isOn(context: Context) = false
    fun style(context: Context): Int? = null
    fun circle(context: Context): Boolean? = null
    fun snapshot(context: Context, nowMs: Long): PartnerFeed.Snapshot? = null
}
