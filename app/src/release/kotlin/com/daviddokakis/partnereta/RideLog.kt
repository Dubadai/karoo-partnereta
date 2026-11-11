package com.daviddokakis.partnereta

import android.content.Context

/** Release builds: no ride log. (The debug version lives in src/debug.) */
object RideLog {
    fun fetch(context: Context, nowMs: Long, http: Int, ms: Long, bytes: Int, s: PartnerFeed.Snapshot?) = Unit
    fun fail(context: Context, nowMs: Long, ms: Long, message: String?) = Unit
    fun predicted(context: Context, nowMs: Long, pred: LatLng, real: LatLng) = Unit
    fun me(context: Context, nowMs: Long, pos: LatLng, speed: Double?, heading: Double?) = Unit
    fun view(context: Context, nowMs: Long, v: PartnerView, me: LatLng?, myRoute: Boolean) = Unit
    fun route(context: Context, nowMs: Long, m: RouteMatch) = Unit
    fun herRoute(context: Context, nowMs: Long, encoded: String?) = Unit
    fun nav(context: Context, nowMs: Long, state: String) = Unit
    fun field(context: Context, nowMs: Long, w: Int, h: Int, shortHeader: Boolean, noteShown: Boolean, valueSp: Float, waited: Int) = Unit
}
