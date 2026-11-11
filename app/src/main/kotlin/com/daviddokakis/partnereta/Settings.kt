package com.daviddokakis.partnereta

import android.content.Context
import android.content.SharedPreferences

/**
 * App-private storage on the Karoo. The people you follow and their settings live in [People];
 * this keeps the shared file and reads the one-partner settings of earlier builds once, to move
 * them over.
 */
object Settings {
    private const val FILE = "partnereta"

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The partner of a one-partner build (before 2026-11-08), as person 1, or null if none was set up. */
    fun legacy(context: Context): Person? {
        val p = prefs(context)
        val name = p.getString("name", null).orEmpty()
        val code = p.getString("code", null)?.takeIf { it.isNotBlank() }
        if (name.isBlank() && code == null) return null
        return Person(
            slot = 1,
            name = name,
            code = code,
            colour = p.getString("colour", null) ?: MapStyle.default.key,
            circleMarker = p.getBoolean("circle_marker", true),
            sharedDots = p.getBoolean("shared_dots", false),
            showHistory = p.getBoolean("show_history", false),
            showRoute = p.getBoolean("show_route", true),
            distanceLeft = p.getBoolean("distance_left", true),
        )
    }
}
