package com.daviddokakis.partnereta

import com.dubadai.karoo.common.*

import android.content.Context
import android.content.SharedPreferences
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnLocationChanged
import io.hammerhead.karooext.models.OnNavigationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * App logic for one person you follow ([slot], PartnerETA 1–4). One per slot, owned by
 * [PartnerEtaExtension]: fetches their live feed every 30 s (60 s while they're not riding),
 * follows your own position and speed, and recomputes [view] once a second so the arrow moves
 * between fetches. An empty slot shows NOT_SET and fetches nothing.
 */
class PartnerEtaSession(private val context: Context, private val karoo: KarooSystemService, val slot: Int) {

    // One thread for all state changes, so the tracker is never used from two threads at once.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val tracker = PartnerTracker()
    private val _view = MutableStateFlow(PartnerView(PartnerView.Status.NOT_SET))
    val view: StateFlow<PartnerView> = _view

    /** Who this slot follows now (re-read when the settings change). */
    @Volatile var person: Person? = People.bySlot(context, slot)
        private set
    private var code: String? = person?.code
    /** The debug ride simulation feeds person 1 only. */
    private fun sim() = slot == 1 && SimRide.isOn(context)
    private var mySpeed: Double? = null
    private var myPos: LatLng? = null
    private var myRoute = false
    private var loggedMatch: RouteMatch? = null
    private var lastHerUpdate: Long? = null
    private var lastHerRoute: String? = null
    /** Fetches in a row that got no answer at all (no internet, phone link down). */
    private var failedInARow = 0
    private val fetchNow = Channel<Unit>(Channel.CONFLATED)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        scope.launch {
            person = People.bySlot(context, slot)
            val newCode = person?.code
            if (newCode != code) {
                Timber.i("Live link changed")
                code = newCode
                tracker.reset()
                fetchNow.trySend(Unit)
            }
            publish()
        }
    }

    fun start() {
        Settings.prefs(context).registerOnSharedPreferenceChangeListener(prefsListener)
        scope.launch {
            karoo.consumerFlow<OnLocationChanged>().collect { loc ->
                val now = System.currentTimeMillis()
                myPos = LatLng(loc.lat, loc.lng)
                tracker.onMyLocation(myPos!!, loc.orientation, now)
                if (slot == 1) RideLog.me(context, now, myPos!!, mySpeed, loc.orientation) // debug builds only
            }
        }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.SPEED).collect { s -> mySpeed = s.single() }
        }
        scope.launch {
            // Your loaded route: when it's the same as hers, the gap is measured along it.
            karoo.consumerFlow<OnNavigationState>().collect { nav ->
                val polyline = (nav.state as? OnNavigationState.NavigationState.NavigatingRoute)?.routePolyline
                myRoute = polyline != null
                if (slot == 1) RideLog.nav(context, System.currentTimeMillis(), nav.state::class.simpleName.orEmpty()) // debug builds only
                tracker.onMyRoute(polyline)
            }
        }
        scope.launch {
            while (isActive) {
                val c = code
                val sim = if (slot == 1) SimRide.snapshot(context, System.currentTimeMillis()) else null // debug builds only
                if (sim != null) { tracker.onSnapshot(sim, System.currentTimeMillis()); publish() }
                else if (c != null) fetch(c)
                val riding = _view.value.isShown
                withTimeoutOrNull(if (riding) FETCH_MS else FETCH_IDLE_MS) { fetchNow.receive() }
            }
        }
        scope.launch {
            while (isActive) {
                publish()
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        Settings.prefs(context).unregisterOnSharedPreferenceChangeListener(prefsListener)
        scope.cancel()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        // Two failed fetches in a row (30-60 s): say so, rather than "not riding" or just old values.
        val v = tracker.view(now, code != null || sim(), mySpeed)
        _view.value = if (failedInARow >= OFFLINE_AFTER_FAILURES) v.copy(offline = true) else v
        if (slot == 1) RideLog.view(context, now, _view.value, myPos, myRoute) // debug builds only
        tracker.routeMatch?.let { if (it != loggedMatch) { loggedMatch = it; if (slot == 1) RideLog.route(context, now, it) } }
    }

    private suspend fun fetch(c: String) {
        val started = System.currentTimeMillis()
        try {
            val res = karoo.karooHttpGet(PartnerFeed.url(c))
            failedInARow = 0
            val now = System.currentTimeMillis()
            val snap = if (res.statusCode == 200) runCatching { PartnerFeed.parse(res.body) }.getOrNull() else null
            if (slot == 1) RideLog.fetch(context, now, res.statusCode, now - started, res.body.length, snap) // debug builds only
            // How far off our moving marker was when her next real position arrives.
            val shown = _view.value
            if (snap?.position != null && snap.updatedAtMs != lastHerUpdate && shown.isShown && shown.position != null)
                if (slot == 1) RideLog.predicted(context, now, shown.position, snap.position)
            if (snap != null) lastHerUpdate = snap.updatedAtMs
            if (snap != null && snap.route != lastHerRoute) { lastHerRoute = snap.route; if (slot == 1) RideLog.herRoute(context, now, snap.route) }
            when (res.statusCode) {
                200 -> tracker.onSnapshot(snap ?: PartnerFeed.parse(res.body), now)
                404 -> { Timber.w("Live link not found"); tracker.onLinkMissing() }
                else -> Timber.w("Feed answered ${res.statusCode}")
            }
        } catch (e: Exception) {
            // Offline or a bad answer: keep the last values; they turn stale after 2 min.
            failedInARow++
            Timber.w("Fetch failed: ${e.message}")
            if (slot == 1) RideLog.fail(context, System.currentTimeMillis(), System.currentTimeMillis() - started, e.message) // debug builds only
        }
        publish()
    }

    companion object {
        const val FETCH_MS = 30_000L
        const val OFFLINE_AFTER_FAILURES = 2
        const val FETCH_IDLE_MS = 60_000L
        const val TICK_MS = 1_000L
    }
}
