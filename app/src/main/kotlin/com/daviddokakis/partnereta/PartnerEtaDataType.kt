package com.daviddokakis.partnereta

import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * A PartnerETA field, one per person ([PartnerEtaSession.slot], PartnerETA 1–4): "Alex ETA", or
 * "Alex ahead/behind" with the gap on the same road. Slot 1 keeps the original type id, so the
 * field already on someone's ride page stays.
 */
class PartnerEtaDataType(
    extension: String,
    private val session: PartnerEtaSession,
) : DataTypeImpl(extension, typeId(session.slot)) {

    /** Numeric fallback for places that show the field as a plain number: minutes until she arrives. */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            session.view.collect { v ->
                val value = v.etaMs?.let { (it - System.currentTimeMillis()) / 60_000.0 }
                emitter.onNext(
                    if (!v.isShown || value == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, values = mapOf(DataType.Field.SINGLE to value))),
                )
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val job = CoroutineScope(Dispatchers.Default).launch {
            // The Karoo drops updates closer than ~0.9 s apart: redraw at most once a second, and
            // only when the text changes.
            var shown: FieldText? = null
            var lastRender = 0L
            var waited = 0
            while (isActive) {
                val now = System.currentTimeMillis()
                val v = session.view.value
                val person = session.person
                val text = FieldTexts.eta(v, person?.shownName ?: "PartnerETA ${session.slot}", now, DateFormat.is24HourFormat(context), person?.distanceLeft ?: true)
                val since = SystemClock.elapsedRealtime() - lastRender
                if (text != shown && since < MIN_GAP_MS) waited++
                if (text != shown && since >= MIN_GAP_MS) {
                    try {
                        val (views, drawn) = FieldView.render(context, config, text)
                        emitter.updateView(views)
                        shown = text
                        lastRender = SystemClock.elapsedRealtime()
                        if (session.slot == 1) RideLog.field(context, now, config.viewSize.first, config.viewSize.second, drawn.short, drawn.note, drawn.valueSp, waited) // debug builds only
                    } catch (e: Exception) {
                        Timber.e(e, "Render failed")
                    }
                }
                delay(POLL_MS)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    companion object {
        const val TYPE_ID = "partner-eta"
        fun typeId(slot: Int) = if (slot == 1) TYPE_ID else "$TYPE_ID-$slot"
        private const val MIN_GAP_MS = 920L
        private const val POLL_MS = 200L
    }
}
