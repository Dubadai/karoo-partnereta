package com.daviddokakis.partnereta

import com.dubadai.karoo.common.*
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.OnMapZoomLevel
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/** Entry point the Karoo system binds to: PartnerETA 1–4 and the people you follow on the map. */
class PartnerEtaExtension : KarooExtension(EXTENSION_ID, "1") {

    private lateinit var karoo: KarooSystemService
    /** One per slot, PartnerETA 1–4; an empty slot just shows "set up". */
    private lateinit var sessions: List<PartnerEtaSession>
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val types: List<DataTypeImpl> by lazy {
        sessions.map { PartnerEtaDataType(extension, it) }
    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        karoo = KarooSystemService(this)
        sessions = (1..People.MAX).map { PartnerEtaSession(this, karoo, it) }
        karoo.connect { connected ->
            Timber.i("Karoo system connected: $connected")
            if (connected) sessions.forEach { it.start() }
        }
        // Not riding: tell the rider when a newer release is on GitHub (see UpdateCheck).
        UpdateCheck(this, karoo, appName = "PartnerETA").start(updateScope)
    }

    /**
     * The partner on the ride map, drawn by [MapOverlay]: her route ahead (hero), what she has
     * ridden (played down), double chevrons and her arrow, in the [MapStyle]. Her lines are cut out
     * wherever they'd cover your own route, and simplified per zoom like the Karoo's route.
     */
    override fun startMap(emitter: Emitter<MapEffect>) {
        val scope = CoroutineScope(Dispatchers.Default)
        val zoom = AtomicReference(12.0)
        val myRoute = AtomicReference<Pair<String, MapOverlay.Mine>?>(null)
        scope.launch {
            karoo.consumerFlow<OnMapZoomLevel>().collect {
                zoom.set(it.zoomLevel)
                Timber.d("Map zoom %.3f", it.zoomLevel) // for calibrating MapOverlay.screenFactor
            }
        }
        scope.launch {
            karoo.consumerFlow<OnNavigationState>().collect { nav ->
                val polyline = (nav.state as? OnNavigationState.NavigationState.NavigatingRoute)?.routePolyline
                if (polyline != myRoute.get()?.first) {
                    myRoute.set(polyline?.let { it to MapOverlay.Mine(Geo.decode(it)) })
                }
            }
        }
        // The Karoo keeps every line and icon an extension ever showed until it's hidden, also
        // across restarts and app updates: clear anything an earlier run or version left behind.
        (LEGACY_LINES + (1..People.MAX).flatMap { slot ->
            (0 until MapOverlay.MAX_LINE_IDS).flatMap { i ->
                listOf(MapOverlay.TRACK, MapOverlay.AHEAD_CASING, MapOverlay.AHEAD, MapOverlay.RAIL, MapOverlay.PATCH_CASING, MapOverlay.PATCH, MapOverlay.CHEV_LINE, MapOverlay.LEAD_CASING, MapOverlay.LEAD)
                    .flatMap { listOf(id(slot, "$it$i"), id(slot, "$it$i~a"), id(slot, "$it$i~b")) }
            }
        }).forEach { emitter.onNext(HidePolyline(it)) }
        emitter.onNext(HideSymbols((1..People.MAX).flatMap { slot ->
            listOf(id(slot, SYMBOL), id(slot, FINISH)) + (0 until MapOverlay.MAX_CHEVRON_IDS).map { id(slot, "${MapOverlay.CHEVRON}$it") }
        }))

        // One layer per person, each with its own map ids, drawn in turn from one loop (the map
        // gets its calls in order): person 1 last, so on top.
        scope.launch {
            val layers = sessions.reversed().map { it to PersonLayer(it.slot) }
            while (isActive) {
                for ((session, layer) in layers) layer.tick(emitter, session, zoom.get(), myRoute.get())
                delay(MAP_TICK_MS)
            }
        }
        emitter.setCancellable { scope.cancel() }
    }

    /**
     * One person on the map: their lines, chevrons, marker and finish flag, under their own ids
     * ([id]). Redraws only what changed, as before (one person per layer).
     */
    private inner class PersonLayer(private val slot: Int) {
        private var drawnKey: Any? = null
        private var drawnChevKey: Any? = null
        private var lines = emptyList<MapOverlay.Line>()
        private var gen = "a"
        private var leadLines = emptyList<MapOverlay.Line>()
        private var leadGen = "a"
        private var marks = emptySet<String>()
        private var chevAll = emptyList<MapOverlay.Mark>()
        private var shownChev = emptyList<MapOverlay.Mark>()
        private var arrow: Triple<LatLng, Int, Int>? = null

        suspend fun tick(emitter: Emitter<MapEffect>, session: PartnerEtaSession, zoomNow: Double, mine: Pair<String, MapOverlay.Mine>?) {
            val v = session.view.value
            val pos = v.position
            val person = session.person
            if (person != null && v.isShown && pos != null) {
                val style = MapStyle.of(this@PartnerEtaExtension, person)
                val stale = v.status == PartnerView.Status.STALE
                val zoomStep = Math.round(zoomNow * 2) / 2.0
                val history = person.showHistory
                val dots = person.sharedDots
                // "Their route" hidden: draw from a view without it (marker and finish flag stay).
                val showRoute = person.showRoute
                val drawn = if (showRoute) v else MapOverlay.withoutRoute(v)
                // Lines are drawn once per position at a fixed zoom and the map scales them, like
                // the Karoo's own route: no redraw (and no flashing) while you zoom. Only the
                // chevron and dot icons are re-placed per zoom, for their spacing.
                val key = listOf(v.trace.takeIf { history }, v.routeAhead, stale, style, mine?.first, history, showRoute)
                val chevKey = listOf(v.routeAhead, style, zoomStep, mine?.first, dots, showRoute)
                if (chevKey != drawnChevKey) {
                    chevAll = MapOverlay.chevrons(drawn, style, zoomStep, mine?.second, dots).map { it.copy(id = id(slot, it.id)) }
                    drawnChevKey = chevKey
                }
                if (key != drawnKey) {
                    // Two groups: the long piece of the line (re-cut every few km) and the short
                    // piece from them to it (every new position). Only what changed is redrawn,
                    // the short piece last so it lies on top.
                    val wantRest = MapOverlay.lines(drawn, style, LINE_ZOOM, mine?.second, history).map { it.copy(id = id(slot, it.id)) }
                    val wantLead = MapOverlay.leadLines(drawn, style, LINE_ZOOM, mine?.second).map { it.copy(id = id(slot, it.id)) }
                    val restChanged = wantRest != lines
                    if (restChanged) { gen = redraw(emitter, lines, wantRest, gen); lines = wantRest }
                    if (restChanged || wantLead != leadLines) { leadGen = redraw(emitter, leadLines, wantLead, leadGen); leadLines = wantLead }
                    // Finish flag where their route ends (no rotation: a flag stands upright).
                    val end = MapOverlay.finish(v)
                    val flag = style.finish
                    if (end != null && flag != null) emitter.onNext(ShowSymbols(listOf(Symbol.Icon(id(slot, FINISH), end.lat, end.lng, flag, 0f))))
                    else emitter.onNext(HideSymbols(listOf(id(slot, FINISH))))
                    drawnKey = key
                    arrow = null // re-send so the marker stays on top of the new lines
                }
                val circle = SimRide.circle(this@PartnerEtaExtension)?.takeIf { slot == 1 } ?: person.circleMarker
                val icon = when {
                    circle && stale -> style.circleStale
                    circle -> style.circle
                    stale -> style.markerStale
                    else -> style.marker
                }
                // The line starts where they were at the last update and the dot glides on from
                // there, so drop the markers it has passed (just behind it, their way).
                val chev = chevAll.filterNot { MapOverlay.isBehind(it.pos, pos, v.bearing) }
                val ids = chev.map { it.id }.toSet()
                if (chev != shownChev) {
                    val fresh = chev - shownChev.toSet()
                    if (fresh.isNotEmpty()) emitter.onNext(ShowSymbols(fresh.map { Symbol.Icon(it.id, it.pos.lat, it.pos.lng, it.icon, it.bearing.toFloat()) }))
                    val gone = marks - ids
                    if (gone.isNotEmpty()) emitter.onNext(HideSymbols(gone.toList()))
                    marks = ids; shownChev = chev
                    arrow = null // keep the marker on top
                }
                val wantArrow = Triple(pos, v.bearing.toInt(), icon)
                if (wantArrow != arrow) {
                    emitter.onNext(ShowSymbols(listOf(Symbol.Icon(id(slot, SYMBOL), pos.lat, pos.lng, icon, v.bearing.toFloat()))))
                    arrow = wantArrow
                }
            } else if (drawnKey != null || arrow != null) {
                emitter.onNext(HideSymbols(listOf(id(slot, SYMBOL), id(slot, FINISH)) + marks))
                lines.forEach { emitter.onNext(HidePolyline("${it.id}~$gen")) }
                leadLines.forEach { emitter.onNext(HidePolyline("${it.id}~$leadGen")) }
                lines = emptyList(); leadLines = emptyList(); marks = emptySet(); chevAll = emptyList(); shownChev = emptyList(); drawnKey = null; drawnChevKey = null; arrow = null
            }
        }
    }

    /**
     * Swap a set of map lines for [want]. The Karoo handles calls sent together in no fixed order
     * (its log: chev-1 added before chev-0), and an updated line keeps its old place in the stack;
     * removing a line and adding it back under the same name could end with it removed. So the new
     * set goes in under fresh names (alternating ~a / ~b), layer by layer (outlines, then her line)
     * with a short pause between, then the old set is removed. Returns the new generation.
     */
    private suspend fun redraw(emitter: Emitter<MapEffect>, old: List<MapOverlay.Line>, want: List<MapOverlay.Line>, gen: String): String {
        val next = if (gen == "a") "b" else "a"
        var layer: String? = null
        for (l in want) {
            val kind = l.id.trimEnd { it.isDigit() }
            if (layer != null && kind != layer) delay(LAYER_PAUSE_MS)
            layer = kind
            emitter.onNext(ShowPolyline("${l.id}~$next", l.encoded, l.color, l.width))
        }
        delay(LAYER_PAUSE_MS)
        old.forEach { emitter.onNext(HidePolyline("${it.id}~$gen")) }
        return next
    }

    override fun onDestroy() {
        sessions.forEach { it.stop() }
        updateScope.cancel()
        karoo.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "partnereta"
        private const val MAP_TICK_MS = 2_000L
        /** Pause between map line layers, so the Karoo stacks them in order. Short: while it runs the new navy outline covers the old cyan line (a dark flash at 250 ms). */
        private const val LAYER_PAUSE_MS = 40L
        /**
         * The zoom her lines are worked out for (simplifying, and where they stop short of your
         * route). Fixed, so zooming doesn't redraw them; mid-range, so they hold up both ways.
         */
        private const val LINE_ZOOM = 13.5
        private const val SYMBOL = "partner"
        /** A map id for [slot]'s person: person 1 keeps the ids of the one-partner builds. */
        fun id(slot: Int, base: String) = if (slot == 1) base else "p$slot-$base"
        private const val FINISH = "partner-finish"
        private val LEGACY_LINES = listOf(
            "partner-route", "partner-route-casing", "partner-track-casing", "partner-track", "partner-ahead", "partner-ahead-casing",
        )
    }
}
