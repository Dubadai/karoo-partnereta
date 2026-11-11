package com.dubadai.karoo.common

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Paces a field's redraw loop: it sleeps until something the field draws from changes, instead of
 * waking every 40–100 ms to look. Every copy of a field runs its own loop, also on pages that aren't
 * on screen, so looking woke an app 10–25 times a second per copy for about one redraw a second.
 *
 * ```
 * val pace = FieldPacer(listOf(session.state, session.activePage))
 * while (isActive) {
 *     pace.mark()                  // before reading the flows
 *     …                            // read them, draw if due
 *     pace.sleep(FieldPacer.nextMs(pending = key != shown, since, MIN_GAP_MS, IDLE_REDRAW_MS))
 * }
 * ```
 *
 * What isn't one of the flows (settings, night mode, a plain var) is seen within [capMs], so it
 * shows no later than one Karoo redraw (~1 s) after it changes.
 */
class FieldPacer(private val flows: List<StateFlow<*>>, private val capMs: Long = CAP_MS) {
    private var seen: List<Any?> = flows.map { it.value }

    /** Notes what the flows hold now. Call it before reading them, so a change while drawing still wakes [sleep]. */
    fun mark() {
        seen = flows.map { it.value }
    }

    /** Sleeps [ms] (at most [capMs]), or until a flow holds something new since [mark]. */
    suspend fun sleep(ms: Long) {
        val wait = ms.coerceAtMost(capMs)
        if (wait <= 0) return
        withTimeoutOrNull(wait) { combine(flows) { it.toList() }.first { it != seen } }
    }

    companion object {
        const val CAP_MS = 1_000L

        /**
         * How long a loop may sleep after a pass: with a change waiting for the Karoo's gap ([pending]),
         * until the gap ends; otherwise until the next redraw anyway (a change wakes [sleep] sooner).
         */
        fun nextMs(pending: Boolean, sinceMs: Long, minGapMs: Long, idleMs: Long): Long =
            if (pending) minGapMs - sinceMs else idleMs - sinceMs
    }
}
