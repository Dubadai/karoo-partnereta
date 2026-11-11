package com.daviddokakis.partnereta

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One person you follow, with their own map and field settings (design: docs/design/people/).
 * [slot] is their field, PartnerETA 1–4, and never changes: removing someone leaves their slot
 * empty, nobody moves up, so the tiles on your ride pages never swap people.
 */
data class Person(
    val slot: Int,
    val name: String,
    /** The live link's code (the part after /live/). */
    val code: String?,
    val source: Source = Source.KAROO,
    /** [MapStyle.key]. */
    val colour: String = MapStyle.default.key,
    val circleMarker: Boolean = true,
    val sharedDots: Boolean = false,
    val showHistory: Boolean = false,
    val showRoute: Boolean = true,
    val distanceLeft: Boolean = true,
    /** A Karoo alert when they start riding during your ride (docs/design/start-alert). */
    val startAlert: Boolean = true,
) {
    /** What the fields call them; "Partner" until a name is set. */
    val shownName get() = name.trim().ifEmpty { "Partner" }

    /** Where they share from: a Karoo live link (Garmin LiveTrack was removed in 0.2.1). */
    enum class Source { KAROO }
}

/** The people you follow, stored on the Karoo (app-private). Pure JSON helpers are unit-tested. */
object People {
    const val MAX = 4
    const val KEY = "people"

    fun all(context: Context): List<Person> {
        val prefs = Settings.prefs(context)
        val json = prefs.getString(KEY, null)
        if (json != null) return decode(json)
        // First run of a multi-person build: the one partner from before becomes person 1.
        val old = Settings.legacy(context) ?: return emptyList()
        save(context, listOf(old))
        return listOf(old)
    }

    fun bySlot(context: Context, slot: Int): Person? = all(context).firstOrNull { it.slot == slot }

    fun put(context: Context, person: Person) =
        save(context, (all(context).filterNot { it.slot == person.slot } + person).sortedBy { it.slot })

    fun remove(context: Context, slot: Int) = save(context, all(context).filterNot { it.slot == slot })

    private fun save(context: Context, people: List<Person>) =
        Settings.prefs(context).edit().putString(KEY, encode(people)).apply()

    /** A new person: the first free slot and the first colour nobody has yet (null when full). */
    fun newPerson(existing: List<Person>): Person? {
        val slot = (1..MAX).firstOrNull { s -> existing.none { it.slot == s } } ?: return null
        val colour = MapStyle.all.firstOrNull { s -> existing.none { it.colour == s.key } } ?: MapStyle.default
        return Person(slot = slot, name = "", code = null, colour = colour.key)
    }

    fun encode(people: List<Person>): String = JsonArray(people.map { p ->
        JsonObject(buildMap {
            put("slot", JsonPrimitive(p.slot))
            put("name", JsonPrimitive(p.name))
            p.code?.let { put("code", JsonPrimitive(it)) }
            put("source", JsonPrimitive(p.source.name))
            put("colour", JsonPrimitive(p.colour))
            put("circle", JsonPrimitive(p.circleMarker))
            put("dots", JsonPrimitive(p.sharedDots))
            put("history", JsonPrimitive(p.showHistory))
            put("route", JsonPrimitive(p.showRoute))
            put("left", JsonPrimitive(p.distanceLeft))
            put("alert", JsonPrimitive(p.startAlert))
        })
    }).toString()

    fun decode(json: String): List<Person> = runCatching {
        (Json.parseToJsonElement(json) as JsonArray).mapNotNull { e ->
            val o = e.jsonObject
            fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull
            fun bool(k: String, d: Boolean) = o[k]?.jsonPrimitive?.booleanOrNull ?: d
            val slot = o["slot"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..MAX } ?: return@mapNotNull null
            // Garmin LiveTrack people (0.2.0) are dropped: that source is gone.
            if (str("source") == "GARMIN") return@mapNotNull null
            Person(
                slot = slot,
                name = str("name").orEmpty(),
                code = str("code")?.takeIf { it.isNotBlank() },
                source = Person.Source.entries.firstOrNull { it.name == str("source") } ?: Person.Source.KAROO,
                colour = str("colour") ?: MapStyle.default.key,
                circleMarker = bool("circle", true),
                sharedDots = bool("dots", false),
                showHistory = bool("history", false),
                showRoute = bool("route", true),
                distanceLeft = bool("left", true),
                startAlert = bool("alert", true),
            )
        }.distinctBy { it.slot }.sortedBy { it.slot }
    }.getOrDefault(emptyList())
}
