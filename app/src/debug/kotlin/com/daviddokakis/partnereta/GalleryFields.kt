package com.daviddokakis.partnereta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import io.hammerhead.karooext.models.ViewConfig
import java.util.concurrent.CountDownLatch

/**
 * DEBUG ONLY: what the field gallery (FieldGallery.kt) draws: the PartnerETA field in each of its
 * states, with the real renderer ([FieldView]). Clock 10:24 as in showcase.json.
 */
object GalleryFields {
    // Each state of the field (design: docs/design/field-states).
    /** A ride for the README GIF (seq-00…): Alex heads for the finish (ETA, km left), then arrives. */
    private val SEQ = listOf("11:05" to "14.2", "11:05" to "12.6", "11:06" to "11.0", "11:06" to "9.3", "11:05" to "7.7", "11:05" to "6.0", "11:04" to "4.4", "11:04" to "2.8", "11:04" to "1.2", "11:03" to null)

    val variants = listOf("eta", "paused", "arrived", "offline", "offline-none", "stale", "away", "notriding", "connecting", "setup") + SEQ.indices.map { "seq-%02d".format(it) }
    val sizes: List<String>? = null
    /** For the height sweep: the field without its small line, with it, and the long-name fallback. */
    val sweepVariants = listOf("eta", "paused", "offline-none", "longname")

    /** The text size the Karoo 3 reports with each field size (from its logs, 2026-11-07). */
    private fun textSize(w: Int, h: Int) = when {
        h >= 300 -> 96
        w < 300 && h > 150 -> 50
        else -> 46
    }

    private fun text(state: String) = if (state.startsWith("seq-")) SEQ[state.removePrefix("seq-").toInt()].let { (eta, left) ->
        if (left == null) FieldText("Alex ETA", "Partner ETA", eta, note = "arrived", noteIcon = NoteIcon.ARRIVED)
        else FieldText("Alex ETA", "Partner ETA", eta, note = "$left km left", noteIsExtra = true)
    } else when (state) {
        "notriding" -> FieldText("Alex ETA", "Partner ETA", "--:--", note = "not riding")
        "connecting" -> FieldText("Alex ETA", "Partner ETA", "--:--", note = "connecting")
        "setup" -> FieldText("PartnerETA 2", "PartnerETA 2", "--", note = "not set up")
        // No route loaded: how far away instead of an ETA.
        "away" -> FieldText("Alex", "Alex", "3.4", note = "km away")
        "longname" -> FieldText("Alexandra-Louise ETA", "Partner ETA", "11:05", note = "14.2 km left", noteIsExtra = true)
        "stale" -> FieldText("Alex ETA", "Partner ETA", "11:05", note = "3 min ago", stale = true)
        "paused" -> FieldText("Alex ETA", "Partner ETA", "11:12", note = "paused", noteIcon = NoteIcon.PAUSED)
        "arrived" -> FieldText("Alex ETA", "Partner ETA", "10:21", note = "arrived", noteIcon = NoteIcon.ARRIVED)
        "offline" -> FieldText("Alex ETA", "Partner ETA", "11:05", note = "no connection", stale = true, noteIcon = NoteIcon.NO_CONNECTION)
        "offline-none" -> FieldText("Alex ETA", "Partner ETA", "--:--", note = "no connection", noteIcon = NoteIcon.NO_CONNECTION)
        else -> FieldText("Alex ETA", "Partner ETA", "11:05", note = "14.2 km left", noteIsExtra = true)
    }


    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val config = ViewConfig(gridSize = 60 to 15, viewSize = w to h, textSize = textSize(w, h))
        var out: Bitmap? = null
        val done = CountDownLatch(1)
        // RemoteViews inflate on the main thread, as on the Karoo.
        Handler(Looper.getMainLooper()).post {
            val view = FieldView.render(context, config, text(variant)).first.apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            done.countDown()
        }
        done.await()
        return out!!
    }
}
