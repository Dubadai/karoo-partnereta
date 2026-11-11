package com.daviddokakis.partnereta

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.InRideAlert
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.util.Locale

/**
 * "Alex started riding": a Karoo in-ride alert, once per ride, when someone you follow starts while
 * you're recording (design: docs/design/start-alert).
 */
object StartAlert {
    const val SHOW_MS = 15_000L
    /** Without an answer by then, the alert goes without the street address. */
    const val ADDRESS_WAIT_MS = 10_000L

    /** Debug builds fire the alert for a slot over adb (DebugAlertReceiver), to see the real one. */
    val debugFire = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    suspend fun send(context: Context, karoo: KarooSystemService, person: Person, view: PartnerView) {
        val route = view.route?.let { Path(Geo.decode(it)) }?.takeIf { it.points.size >= 2 }
        val address = route?.points?.last()?.let { finish ->
            withTimeoutOrNull(ADDRESS_WAIT_MS) {
                runCatching {
                    val res = karoo.karooHttpGet(FinishAddress.url(finish, Locale.getDefault().language), FinishAddress.headers(context))
                    if (res.statusCode == 200) FinishAddress.parse(res.body) else null
                }.onFailure { Timber.w("Finish address lookup failed: ${it.message}") }.getOrNull()
            }
        }
        val (bg, icon) = look(person.colour)
        karoo.dispatch(
            InRideAlert(
                id = "partnereta-start-${person.slot}",
                icon = icon,
                title = context.getString(R.string.alert_started, person.shownName),
                detail = text(route?.length, view.distanceM, address),
                autoDismissMs = SHOW_MS,
                backgroundColor = bg,
                textColor = R.color.alert_text,
            ),
        )
        Timber.i("Start alert for slot ${person.slot} (route ${route != null}, address ${address != null})")
    }

    /** "38.2 km route / Finish: Hornsgatan 12, Södermalm", or with no route, how far away they are. */
    fun text(routeM: Double?, distanceM: Double?, address: String?): String =
        if (routeM != null) listOfNotNull("${km(routeM)} km route", address?.let { "Finish: $it" }).joinToString("\n")
        else listOfNotNull("No route loaded", distanceM?.let { "${km(it)} km away from you" }).joinToString("\n")

    private fun km(m: Double) = String.format(Locale.US, "%.1f", m / 1000)

    /** Their colour's dark tone behind white text, and their flag in their colour. */
    private fun look(colour: String) = when (colour) {
        "cyan" -> R.color.alert_bg_cyan to R.drawable.ic_alert_flag_cyan
        "green" -> R.color.alert_bg_green to R.drawable.ic_alert_flag_green
        "blue" -> R.color.alert_bg_blue to R.drawable.ic_alert_flag_blue
        "magenta" -> R.color.alert_bg_magenta to R.drawable.ic_alert_flag_magenta
        "violet" -> R.color.alert_bg_violet to R.drawable.ic_alert_flag_violet
        else -> R.color.alert_bg_lime to R.drawable.ic_alert_flag_lime
    }
}

/**
 * When to send the start alert, fed on every redraw. Armed when your ride starts and they aren't
 * riding; fires once when they start. If they were already riding when you started (or when the
 * extension came back mid-ride), it stays quiet. Your ride ending resets it for the next one.
 */
class StartAlertRule {
    private var decided = false
    private var armed = false

    /** True exactly once: the moment to send the alert. */
    fun update(recording: Boolean, status: PartnerView.Status): Boolean {
        if (!recording) { decided = false; armed = false; return false }
        val riding = status == PartnerView.Status.LIVE || status == PartnerView.Status.STALE
        if (!decided) {
            // Wait for a real answer from their link before deciding (not while it's still loading).
            if (!riding && status != PartnerView.Status.NOT_RIDING) return false
            decided = true
            armed = !riding
            return false
        }
        if (armed && riding) { armed = false; return true }
        return false
    }
}

/** Street address of a point from OpenStreetMap's Nominatim (one lookup per alert). */
object FinishAddress {
    fun url(p: LatLng, language: String) = String.format(
        Locale.US,
        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.5f&lon=%.5f&zoom=18&addressdetails=1&accept-language=%s",
        p.lat, p.lng, language,
    )

    /** Nominatim asks every app to name itself; no personal details. */
    fun headers(context: Context): Map<String, String> {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"
        return mapOf("User-Agent" to "PartnerETA/$version (Karoo extension; github.com/Dubadai/karoo-partnereta)")
    }

    /** "Hornsgatan 12, Södermalm": street and number, then the area; null when there's no address. */
    fun parse(body: String): String? = runCatching {
        val o = Json.parseToJsonElement(body).jsonObject
        val a = o["address"] as? JsonObject ?: return null
        fun s(k: String) = a[k]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val street = (s("road") ?: s("pedestrian") ?: s("footway") ?: s("cycleway") ?: s("path"))
            ?.let { r -> s("house_number")?.let { "$r $it" } ?: r }
        val area = s("suburb") ?: s("neighbourhood") ?: s("city_district") ?: s("village") ?: s("town") ?: s("city") ?: s("municipality")
        listOfNotNull(street, area).distinct().joinToString(", ").ifEmpty { null }
    }.getOrNull()
}
