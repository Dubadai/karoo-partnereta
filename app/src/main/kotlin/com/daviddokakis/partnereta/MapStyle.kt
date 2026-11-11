package com.daviddokakis.partnereta

import android.content.Context

/** Shared roads: your route cuts her line at the join, plus small markers in her colour on yours. */
enum class SharedLook { RAILS, DOTS, ARROWS }

/**
 * How the partner looks on the map, in one colour: her route ahead ([line] with a dark tinted
 * [outline] and small chevrons), her marker (circle standard, arrow optional), the finish pin where
 * her route ends, what she has ridden ([ridden], only with "Ride history" on), and the mini arrows
 * (12 dp) or dots (10 dp) on your route where she rides it too.
 * Design: docs/design/partner-map/ (rounds 4-5 and the shared-road tries).
 */
data class MapStyle(
    val key: String,
    val label: String,
    val line: Int,
    val outline: Int,
    val ridden: Int,
    val chevron: Int,
    val marker: Int,
    val circle: Int,
    val finish: Int?,
    val miniArrow: Int?,
    val dot: Int?,
    /** SDK line widths are display units (~1.8 px): 9/5 = ~16/9 px, thinner than your 20 px route. */
    val lineWidth: Int = 5,
    val outlineWidth: Int = 9,
    val riddenWidth: Int = 3,
    val trailM: Double? = null,
    val shared: SharedLook = SharedLook.ARROWS,
    val sharedChevron: Int? = null,
    val railsInOutline: Boolean = false,
    val rails: Boolean = true,
    /** Markers when no update for 2 min. */
    val markerStale: Int = R.drawable.ic_partner_arrow_grey,
    val circleStale: Int = R.drawable.ic_partner_puck_grey,
) {
    companion object {
        private fun c(hex: Long) = hex.toInt()

        /** The colours on offer, standard first. Cyan and green are the recommended ones. */
        val all = listOf(
            MapStyle(
                "cyan", "Cyan", c(0xFF00E5FF), c(0xFF0B2E4A), c(0xFF7FC4CC),
                chevron = R.drawable.ic_chevron_small_cyan, marker = R.drawable.ic_partner_arrow_cyan,
                circle = R.drawable.ic_partner_puck_cyan, finish = R.drawable.ic_partner_finish_cyan,
                miniArrow = R.drawable.ic_partner_mini_arrow_cyan, dot = R.drawable.ic_partner_dot_cyan,
            ),
            MapStyle(
                "green", "Green", c(0xFF00D35A), c(0xFF0B3D1E), c(0xFF86CFA0),
                chevron = R.drawable.ic_chevron_small_green, marker = R.drawable.ic_partner_arrow_green,
                circle = R.drawable.ic_partner_puck_green, finish = R.drawable.ic_partner_finish_green,
                miniArrow = R.drawable.ic_partner_mini_arrow_green, dot = R.drawable.ic_partner_dot_green,
            ),
            MapStyle(
                "blue", "Blue", c(0xFF1E7BFF), c(0xFF0A2A5C), c(0xFF8FB4E8),
                chevron = R.drawable.ic_chevron_small_blue, marker = R.drawable.ic_partner_arrow_blue,
                circle = R.drawable.ic_partner_puck_blue, finish = R.drawable.ic_partner_finish_blue,
                miniArrow = R.drawable.ic_partner_mini_arrow_blue, dot = R.drawable.ic_partner_dot_blue,
            ),
            MapStyle(
                "magenta", "Magenta", c(0xFFFF00E6), c(0xFF3A0035), c(0xFFE59ADB),
                chevron = R.drawable.ic_chevron_small_magenta, marker = R.drawable.ic_partner_arrow_magenta,
                circle = R.drawable.ic_partner_puck_magenta, finish = R.drawable.ic_partner_finish_magenta,
                miniArrow = R.drawable.ic_partner_mini_arrow_magenta, dot = R.drawable.ic_partner_dot_magenta,
            ),
            MapStyle(
                "violet", "Violet", c(0xFF8A2BE2), c(0xFF2A0B4A), c(0xFFB79AD8),
                chevron = R.drawable.ic_chevron_small_violet, marker = R.drawable.ic_partner_arrow_violet,
                circle = R.drawable.ic_partner_puck_violet, finish = R.drawable.ic_partner_finish_violet,
                miniArrow = R.drawable.ic_partner_mini_arrow_violet, dot = R.drawable.ic_partner_dot_violet,
            ),
            MapStyle(
                "lime", "Lime", c(0xFFB6FF00), c(0xFF1E4A00), c(0xFFC9E68A),
                chevron = R.drawable.ic_chevron_small_lime, marker = R.drawable.ic_partner_arrow_lime,
                circle = R.drawable.ic_partner_puck_lime, finish = R.drawable.ic_partner_finish_lime,
                miniArrow = R.drawable.ic_partner_mini_arrow_lime, dot = R.drawable.ic_partner_dot_lime,
            ),
        )
        val default = all[0]
        val recommended = setOf("cyan", "green")

        fun byKey(key: String?) = all.firstOrNull { it.key == key } ?: default

        /** A person's colour; a debug build can try another for person 1 over adb (sim.txt style=N). */
        fun of(context: Context, person: Person) =
            SimRide.style(context)?.takeIf { person.slot == 1 }?.let { all.getOrNull(it - 1) } ?: byKey(person.colour)
    }
}
