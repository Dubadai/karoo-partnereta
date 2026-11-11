package com.daviddokakis.partnereta

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** What one field shows: header, big value (+ small unit), the line under it, and the arrow. */
data class FieldText(
    val header: String,
    /** The header when [header] doesn't fit the tile: "Partner ETA", or just "ahead" next to the partner icon. */
    val headerShort: String = header,
    val value: String,
    val unit: String = "",
    val note: String = "",
    /** Gap mode: the two-person icon instead of the flag; 0 = she's ahead, 180 = behind. */
    val arrow: Double? = null,
    /** Grey arrow: no update for 2 min. */
    val stale: Boolean = false,
    /** A small icon before the small line, in its colour. */
    val noteIcon: NoteIcon? = null,
)

/** Icons before the field's small line (design: docs/design/field-states round 3). */
enum class NoteIcon { PAUSED, ARRIVED, NO_CONNECTION }

/** The words and numbers for the field, from a [PartnerView]. Pure, so it's unit-tested. */
object FieldTexts {

    /**
     * The one PartnerETA field. Normally "Alex ETA": clock time at the end of her route. When you
     * ride the same road the same way it becomes "Alex ahead" / "Alex behind" with the gap as
     * time (or distance until the timing is known).
     */
    fun eta(v: PartnerView, name: String, nowMs: Long, is24h: Boolean, distanceLeft: Boolean = true): FieldText {
        val header = "$name ETA"
        val short = "Partner ETA"
        common(v, name)?.let { return it.copy(header = header, headerShort = short, value = "--:--") }
        // Grey when old, or when we can't reach her feed (then the last values stay up).
        val stale = v.status == PartnerView.Status.STALE || v.offline
        // The small line's words that matter more than the distance, with their icon (design:
        // docs/design/field-states): no connection, arrived, how old, paused.
        val state: Pair<String, NoteIcon?>? = when {
            v.offline -> "no connection" to NoteIcon.NO_CONNECTION
            v.arrived -> "arrived" to NoteIcon.ARRIVED
            v.status == PartnerView.Status.STALE -> updatedAgo(v, nowMs) to null
            v.paused -> "paused" to NoteIcon.PAUSED
            else -> null
        }
        v.gap?.let { gap ->
            // The gap is the big number; her ETA (or, without one, the distance) is the small line.
            val note = state?.first ?: v.etaMs?.let { "ETA ${timeOfDay(it, is24h)}" }
            return gap(gap, name, stale, note).copy(noteIcon = state?.second)
        }
        val eta = v.etaMs
        if (eta == null) {
            // No route to time (a Karoo rider without a route):
            // how far away they are instead (design: docs/design/people, Robin's tile).
            val away = v.distanceM
            if (v.noEta == PartnerView.NoEta.NO_ROUTE && away != null) {
                val (value, unit) = distance(away)
                // "3.4" big and "km away" under it; with a state to say ("3 min ago") the unit stays by the number.
                return if (state == null) FieldText(name, name, value, note = "$unit away", stale = stale)
                else FieldText(name, name, value, unit, note = state.first, stale = stale, noteIcon = state.second)
            }
            val why = when (v.noEta) {
                PartnerView.NoEta.NO_ROUTE -> "no route"
                PartnerView.NoEta.OFF_ROUTE -> "off route"
                else -> "getting speed"
            }
            return FieldText(header, short, "--:--", note = state?.first ?: why, stale = stale, noteIcon = state?.second)
        }
        if (state != null) return FieldText(header, short, timeOfDay(eta, is24h), note = state.first, stale = stale, noteIcon = state.second)
        val note = if (distanceLeft) distance(v.remainingM ?: 0.0).let { "${it.first} ${it.second} left" } else ""
        return FieldText(header, short, timeOfDay(eta, is24h), note = note)
    }

    private fun gap(gap: Gap, name: String, stale: Boolean, note: String?): FieldText {
        val word = if (gap.ahead) "ahead" else "behind"
        val header = "$name $word"
        val arrow = if (gap.ahead) 0.0 else 180.0
        val secs = gap.seconds
        return if (secs != null) {
            FieldText(header, word, clock(secs), note = note ?: distance(gap.metres).let { "${it.first} ${it.second}" }, arrow = arrow, stale = stale)
        } else {
            val (value, unit) = distance(gap.metres)
            FieldText(header, word, value, unit, note.orEmpty(), arrow = arrow, stale = stale)
        }
    }

    /** States without a ride to show; null when she's riding. Offline we can't know she isn't riding. */
    private fun common(v: PartnerView, name: String): FieldText? = when {
        v.status == PartnerView.Status.NOT_SET -> FieldText(name, value = "--", note = "not set up")
        v.isShown -> null
        v.offline -> FieldText(name, value = "--", note = "no connection", noteIcon = NoteIcon.NO_CONNECTION)
        v.status == PartnerView.Status.WAITING -> FieldText(name, value = "--", note = "connecting")
        else -> FieldText(name, value = "--", note = "not riding")
    }

    private fun updatedAgo(v: PartnerView, nowMs: Long): String {
        val mins = ((nowMs - (v.lastUpdateMs ?: nowMs)) / 60_000.0).roundToInt().coerceAtLeast(1)
        // Short enough for a half-width tile; the grey value already says it's old.
        return if (mins < 60) "$mins min ago" else "${mins / 60} h ago"
    }

    /** "350" m below 1 km, "1.4" km below 100 km, then whole km. */
    fun distance(m: Double): Pair<String, String> = when {
        m < 1000 -> (((m / 10).roundToInt() * 10).toString()) to "m"
        m < 100_000 -> String.format(java.util.Locale.US, "%.1f", m / 1000) to "km"
        else -> (m / 1000).roundToInt().toString() to "km"
    }

    /** Gap as m:ss, or h:mm:ss from an hour. */
    fun clock(seconds: Double): String {
        val s = seconds.roundToLong().coerceAtLeast(0)
        return if (s < 3600) "%d:%02d".format(s / 60, s % 60) else "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    }

    fun duration(seconds: Double): String {
        val mins = (seconds / 60).roundToInt()
        return if (mins < 60) "$mins min" else "${mins / 60} h ${mins % 60} min"
    }

    fun timeOfDay(ms: Long, is24h: Boolean): String {
        val t = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return if (is24h) "%d:%02d".format(t.hour, t.minute)
        else "%d:%02d".format(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute)
    }
}
