package com.daviddokakis.partnereta

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * DEBUG BUILDS ONLY (src/debug): a CSV of what happens on a live ride, so it can be replayed and the
 * app tuned afterwards. Stays in the app's private folder on the Karoo; never commit it (it holds both
 * riders' positions; the live code is not logged).
 *
 *   adb exec-out run-as com.daviddokakis.partnereta cat files/ridelog.csv > ridelog.csv
 *
 * One line per event, the first column says which:
 *   F,now,http,ms,bytes,updatedAt,state,lat,lng,hasRoute     feed answer (ms = response time)
 *   E,now,ms,message                                         feed failed (offline, timeout)
 *   P,now,predLat,predLng,realLat,realLng,errorM             where we had her vs her next real position
 *   M,now,lat,lng,speed,heading                              your own GPS, once a second
 *   V,now,status,gapAhead,gapM,gapS,etaMs,remainingM,distanceM,myLat,myLng,myRoute   field state, every 10 s
 *   R,now,myRouteM,herRouteM,mineCovered,hersCovered,same    same-route check (when either route changes)
 *   H,now,points,lengthM,startLat,startLng,endLat,endLng,hash   her route in the feed changed (a reroute?)
 *   N,now,state                                              your navigation state changed
 *   D,now,widthPx,heightPx,shortHeader,noteShown,valueSp,waitedUpdates   field drawn differently
 *   B,now,batteryPercent,charging                            battery, every 5 min
 */
object RideLog {
    private const val MAX_BYTES = 5_000_000L
    private var lastView = 0L
    private var lastMe = 0L
    private var lastBattery = 0L
    private var lastField: String? = null

    fun fetch(context: Context, nowMs: Long, http: Int, ms: Long, bytes: Int, s: PartnerFeed.Snapshot?) =
        write(context, "F,$nowMs,$http,$ms,$bytes,${s?.updatedAtMs ?: ""},${s?.state ?: ""},${s?.position?.lat ?: ""},${s?.position?.lng ?: ""},${s?.route != null}")

    fun fail(context: Context, nowMs: Long, ms: Long, message: String?) =
        write(context, "E,$nowMs,$ms,${message.orEmpty().replace(',', ';').replace('\n', ' ').take(120)}")

    fun predicted(context: Context, nowMs: Long, pred: LatLng, real: LatLng) =
        write(context, "P,$nowMs,${pred.lat},${pred.lng},${real.lat},${real.lng},${Geo.distance(pred, real).toInt()}")

    fun me(context: Context, nowMs: Long, pos: LatLng, speed: Double?, heading: Double?) {
        if (nowMs - lastMe < 1_000) return
        lastMe = nowMs
        write(context, "M,$nowMs,${pos.lat},${pos.lng},${speed?.let { "%.1f".format(java.util.Locale.US, it) } ?: ""},${heading?.toInt() ?: ""}")
    }

    fun view(context: Context, nowMs: Long, v: PartnerView, me: LatLng?, myRoute: Boolean) {
        if (nowMs - lastBattery >= 5 * 60_000L) { lastBattery = nowMs; battery(context, nowMs) }
        if (nowMs - lastView < 10_000) return
        lastView = nowMs
        val g = v.gap
        write(context, "V,$nowMs,${v.status},${g?.ahead ?: ""},${g?.metres?.toInt() ?: ""},${g?.seconds?.toInt() ?: ""}," +
            "${v.etaMs ?: ""},${v.remainingM?.toInt() ?: ""},${v.distanceM?.toInt() ?: ""},${me?.lat ?: ""},${me?.lng ?: ""},$myRoute")
    }

    /** Her route in the feed changed: tells us whether her Karoo's reroutes reach the feed. */
    fun herRoute(context: Context, nowMs: Long, encoded: String?) {
        val pts = encoded?.let { Geo.decode(it) }.orEmpty()
        val len = if (pts.size >= 2) Path(pts).length.toInt() else 0
        write(context, "H,$nowMs,${pts.size},$len,${pts.firstOrNull()?.lat ?: ""},${pts.firstOrNull()?.lng ?: ""}," +
            "${pts.lastOrNull()?.lat ?: ""},${pts.lastOrNull()?.lng ?: ""},${encoded?.hashCode()?.toUInt()?.toString(16) ?: ""}")
    }

    fun route(context: Context, nowMs: Long, m: RouteMatch) =
        write(context, "R,$nowMs,${m.myLengthM.toInt()},${m.herLengthM.toInt()},${"%.2f".format(java.util.Locale.US, m.mineCovered)},${"%.2f".format(java.util.Locale.US, m.hersCovered)},${m.same}")

    fun nav(context: Context, nowMs: Long, state: String) = write(context, "N,$nowMs,$state")

    fun field(context: Context, nowMs: Long, w: Int, h: Int, shortHeader: Boolean, noteShown: Boolean, valueSp: Float, waited: Int) {
        val key = "$w,$h,$shortHeader,$noteShown,${"%.1f".format(java.util.Locale.US, valueSp)}"
        if (key == lastField) return
        lastField = key
        write(context, "D,$nowMs,$key,$waited")
    }

    private fun battery(context: Context, nowMs: Long) {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        write(context, "B,$nowMs,${level * 100 / scale.coerceAtLeast(1)},$plugged")
    }

    private fun write(context: Context, line: String) {
        try {
            val f = File(context.filesDir, "ridelog.csv")
            if (f.length() > MAX_BYTES) f.renameTo(File(context.filesDir, "ridelog.1.csv"))
            f.appendText(line + "\n")
        } catch (_: Exception) { }
    }
}
