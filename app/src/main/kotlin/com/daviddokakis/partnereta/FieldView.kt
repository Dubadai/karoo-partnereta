package com.daviddokakis.partnereta

import com.dubadai.karoo.common.*
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import io.hammerhead.karooext.models.ViewConfig
import kotlin.math.roundToInt

/** Draws the PartnerETA field (field_partner.xml) from a [FieldText]: the Karoo, and the debug field gallery. */
object FieldView {

    /** What a render chose, for the debug ride log. */
    data class Drawn(val short: Boolean, val note: Boolean, val valueSp: Float)

    fun render(context: Context, config: ViewConfig, t: FieldText): Pair<RemoteViews, Drawn> {
        val v = RemoteViews(context.packageName, R.layout.field_partner)
        val density = context.resources.displayMetrics.density
        val (w, h) = config.viewSize

        // Title and number on the Karoo's own lines, measured on its tiles (docs/design/eta-align):
        // capitals 20 px in tiles under 150 px, else 23 px, starting 21 px from the top at 142 px and
        // below (126 px on our test Karoo's page: 21 too), 26 px from 168 px up (a full-width row: 12 px); text ends 9 px in from the right. The
        // number's bottom line sits 6 px above the tile's bottom, its right edge under the title's.
        val capsPx = if (h < 150) 20f else 23f
        val capsTop = if (w >= 400 && h < 150) 12f else (21f + (h - 142f) * 5f / 26f).coerceIn(21f, 26f).roundToInt().toFloat()
        // The title as big as the Karoo's; a long name may shrink to the small tiles' 20 px capitals
        // before it gives way to the short form ("PARTNER ETA"), as the Karoo keeps its own titles whole.
        val headerRoom = w - ICON_LEFT_PX - TEXT_RIGHT_PX - (ICON_DP + ICON_GAP_DP + 2) * density
        val fullSize = capsPx / capRatio(headerFace())
        // Widths don't scale exactly with the size (glyphs are rounded per size): measure at the size
        // itself and step down until it fits (2026-11-07: "PARTNER E…" at 32 px, 4 px too wide).
        fun sizeFor(text: String): Float {
            var size = minOf(fullSize, fullSize * headerRoom / textWidth(headerFace(), fullSize, text.uppercase()))
            while (size > 1f && textWidth(headerFace(), size, text.uppercase()) > headerRoom) size -= 0.25f
            return size
        }
        val minSize = 20f / capRatio(headerFace())
        val short = sizeFor(t.header) < minSize
        val title = if (short) t.headerShort else t.header
        v.setTextViewText(R.id.header, title)
        val header = metrics(headerFace(), maxOf(minSize, sizeFor(title)).coerceAtMost(fullSize), "ALEX ETA")
        // Keep the capitals' top on the Karoo's line whatever their size.
        v.setTextViewTextSize(R.id.header, TypedValue.COMPLEX_UNIT_PX, header.size)
        // The header line starts above its capitals by (ascent - cap height).
        val headerTop = (capsTop - (header.ascent - header.cap)).roundToInt()
        v.setViewPadding(R.id.header_row, ICON_LEFT_PX, headerTop, TEXT_RIGHT_PX, 0)
        val headerBottom = headerTop + header.ascent + header.descent

        // Icon: the green flag for the ETA; for the gap the two-person icon (left you, right your
        // partner), the one in front green. Design: docs/design/same-route/ round 3.
        // The ETA: our green flag, with the Karoo's sensor states (hammerhead-legends): green when
        // live, grey while waiting or old, grey with a red × without a connection. The gap keeps its
        // two-person icon.
        if (t.arrow == null) v.setImageViewBitmap(R.id.icon, flagIcon(context, (ICON_DP * density).roundToInt(), capsPx, t))
        else v.setImageViewResource(R.id.icon, when {
            t.stale -> R.drawable.ic_partners_stale
            t.arrow == 0.0 -> R.drawable.ic_partners_ahead
            else -> R.drawable.ic_partners_behind
        })

        // The small line on every tile from ~60 dp up (a one-row tile is 75 dp); the clock gives way
        // to it, never the other way round (smallest-text rule; docs/design/short-tile-note).
        val showNote = h / density >= NOTE_MIN_HEIGHT_DP && t.note.isNotEmpty()
        val top = headerBottom.roundToInt().coerceIn(0, h - 1)
        val (body, valueSp, split) = drawBody(context, config, t, showNote, top)
        // The small line as its own image under the clock, so a row the Karoo shortens shrinks only
        // the clock (fitEnd) and the line keeps its true size.
        if (split != null) {
            val cut = (split - top).coerceIn(1, body.height - 1)
            v.setImageViewBitmap(R.id.body, Bitmap.createBitmap(body, 0, 0, body.width, cut).apply { this.density = body.density })
            v.setImageViewBitmap(R.id.note, Bitmap.createBitmap(body, 0, cut, body.width, body.height - cut).apply { this.density = body.density })
            v.setViewVisibility(R.id.note, android.view.View.VISIBLE)
        } else {
            v.setImageViewBitmap(R.id.body, body)
            v.setViewVisibility(R.id.note, android.view.View.GONE)
        }
        v.setContentDescription(R.id.body, if (showNote) "${t.value} ${t.unit} ${t.note}" else "${t.value} ${t.unit}")
        return v to Drawn(short, showNote, valueSp)
    }

    /**
     * [maxSp], or less when the text is wider than [widthDp]. Plex digits are ~0.56 em wide, colons
     * and dots ~0.27; the font is drawn at 90 % width (textScaleX) with -0.05 letter spacing.
     */

    private fun headerFace(): Typeface = Typeface.create(HEADER_FONT, Typeface.NORMAL)

    /**
     * The clock (with its smaller unit) and the small line under it, drawn into one bitmap for the
     * area under the header (tile y from [top] down), in tile pixels. Like the Karoo's own values
     * (KarooType): Relative, −0.04 spacing, ink ending 9 px from the right like the Karoo's. Alone, the clock is sized to the
     * Karoo's value line (tile − 35 px) with its baseline 14 px above the bottom; with the small line
     * it fills the space from 6 px under the header down to 6 px above the line.
     */
    private fun drawBody(context: Context, config: ViewConfig, t: FieldText, showNote: Boolean, top: Int): Triple<Bitmap, Float, Int?> {
        val density = context.resources.displayMetrics.density
        val (w, h) = config.viewSize
        val colour = context.getColor(if (t.stale) R.color.text_secondary else R.color.text)
        val bmp = Bitmap.createBitmap(w, h - top, Bitmap.Config.ARGB_8888).apply { this.density = context.resources.displayMetrics.densityDpi }
        val c = Canvas(bmp)
        c.translate(0f, -top.toFloat())

        // Nothing to show (not riding, connecting, no connection before any ETA, not set up): words
        // like the Karoo's own "No Sensor" (KarooType.emptyPaint / emptyBaseline, shared rule).
        if (t.value.none { it.isDigit() }) {
            val words = t.note.replaceFirstChar { it.uppercase() }.ifEmpty { "--" }
            c.drawText(words, w / 2f, KarooType.emptyBaseline(h), KarooType.emptyPaint(words, w).apply { color = colour })
            return Triple(bmp, 0f, null)
        }
        val value = KarooType.valueText(t.value)
        val vp = KarooType.valuePaint().apply { color = colour; textAlign = Paint.Align.LEFT }
        fun widthAt(size: Float): Float {
            vp.textSize = size
            val unitW = if (t.unit.isEmpty()) 0f else { vp.textSize = size * UNIT_SCALE; (vp.measureText(t.unit) + size * UNIT_GAP).also { vp.textSize = size } }
            return vp.measureText(value) + unitW
        }
        val digits = Rect().also { vp.textSize = 100f; vp.getTextBounds(value.ifEmpty { "0" }, 0, value.ifEmpty { "0" }.length, it) }
        val digitUp = -digits.top / 100f        // digit height above the baseline, per px of text size
        val digitDown = digits.bottom / 100f    // ink under the baseline

        // The small line in Relative too, as heavy as the Karoo's header: a thin outline, the font's
        // own spacing, and a normal gap between words (Relative's space is a digit wide). As BigWatts.
        val np = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = KarooType.value; textSize = NOTE_SP * density; color = colour; letterSpacing = 0f
            style = Paint.Style.FILL_AND_STROKE; strokeWidth = textSize * NOTE_STROKE
        }
        fun noteWidth(text: String) = text.split(' ').let { ws -> ws.sumOf { np.measureText(it).toDouble() }.toFloat() + (ws.size - 1) * np.textSize * WORD_GAP }
        fun drawNote(text: String, x: Float, y: Float) {
            var nx2 = x
            text.split(' ').forEach { word -> c.drawText(word, nx2, y, np); nx2 += np.measureText(word) + np.textSize * WORD_GAP }
        }
        val noteBaseline = h - NOTE_MARGIN_DP * density - np.textSize * NOTE_DESCENT
        val noteCapTop = noteBaseline - capRatio(np.typeface) * np.textSize

        val baseline: Float
        var size: Float
        if (showNote) {
            baseline = noteCapTop - NOTE_GAP_PX // where the digits' ink may end
            size = minOf(
                (baseline - top - HEADER_GAP_PX) / (digitUp + digitDown),
                KarooType.valueSize(KarooType.valuePaint(), value, KarooType.valueW(w), KarooType.valueLineH(h)),
            )
        } else {
            baseline = KarooType.valueBaseline(h)
            size = minOf(
                KarooType.valueSize(KarooType.valuePaint(), value, KarooType.valueW(w), KarooType.valueLineH(h)),
                (baseline - top - HEADER_GAP_PX) / digitUp,
            )
        }
        // Big tiles: no bigger than the clock was before (feedback 2026-11-09: "smaller on half tiles").
        size = minOf(size, MAX_DIGITS_PX / digitUp)
        val room = (w - 2 * VALUE_SIDE_PX).toFloat()
        while (size > 6f && widthAt(size) > room) size -= 0.5f
        // When the clock is smaller than the room above the small line (big tiles), lift it: half the
        // spare room, up to a quarter of its height, goes between the clock and the line, so the
        // corner isn't cramped (feedback 2026-11-09). The line keeps its distance from the border.
        val lift = if (showNote) {
            val spare = baseline - top - HEADER_GAP_PX - (digitUp + digitDown) * size
            minOf(spare / 2f, (digitUp + digitDown) * size / 4f).coerceAtLeast(0f)
        } else 0f
        val clockBaseline = if (showNote) baseline - digitDown * size - lift else baseline

        val total = widthAt(size)
        val x0 = when (config.alignment) {
            ViewConfig.Alignment.LEFT -> VALUE_SIDE_PX.toFloat()
            ViewConfig.Alignment.CENTER -> (w - total) / 2f
            else -> w - VALUE_SIDE_PX - total
        }
        vp.textSize = size
        c.drawText(value, x0, clockBaseline, vp)
        if (t.unit.isNotEmpty()) {
            val vx = x0 + vp.measureText(value)
            vp.textSize = size * UNIT_SCALE
            c.drawText(t.unit, vx + size * UNIT_GAP, clockBaseline, vp)
        }

        var split: Int? = null
        if (showNote) {
            val icon = when (t.noteIcon) {
                NoteIcon.PAUSED -> R.drawable.ic_paused
                NoteIcon.ARRIVED -> R.drawable.ic_arrived
                NoteIcon.NO_CONNECTION -> 0 // the header's clock carries the red ×
                null -> 0
            }
            val iconPx = if (icon != 0) (ICON_DP * density).roundToInt() else 0
            val gapPx = if (icon != 0) (NOTE_ICON_GAP_DP * density) else 0f
            var note = t.note
            val maxNote = room - iconPx - gapPx
            while (note.length > 1 && noteWidth(note) > maxNote) note = note.dropLast(2) + "…"
            val noteW = noteWidth(note) + iconPx + gapPx
            val nx = when (config.alignment) {
                ViewConfig.Alignment.LEFT -> VALUE_SIDE_PX.toFloat()
                ViewConfig.Alignment.CENTER -> (w - noteW) / 2f
                else -> w - VALUE_SIDE_PX - noteW
            }
            // Cap height to baseline, like the words next to it.
            val iconTop = ((noteCapTop + noteBaseline) / 2f - iconPx / 2f).roundToInt()
            // Where the line's image starts: just above its capitals (or its icon), under the clock's ink.
            split = (minOf(noteCapTop, if (icon != 0) iconTop.toFloat() else noteCapTop) - 1f).toInt()
            if (icon != 0) context.getDrawable(icon)?.mutate()?.let { d ->
                d.setBounds(nx.roundToInt(), iconTop, nx.roundToInt() + iconPx, iconTop + iconPx)
                d.setTint(colour); d.draw(c)
            }
            drawNote(note, nx + iconPx + gapPx, noteBaseline)
        }
        return Triple(bmp, size / density, split)
    }

    /** The flag [px] square, from the shared library (KarooType.drawFlag): sized to the Karoo's icon box, with its states. */
    private fun flagIcon(context: Context, px: Int, capsPx: Float, t: FieldText): Bitmap {
        val night = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val state = when {
            t.noteIcon == NoteIcon.NO_CONNECTION -> KarooType.GlyphState.NO_CONNECTION
            t.stale || t.value.none { it.isDigit() } -> KarooType.GlyphState.WAITING
            else -> KarooType.GlyphState.LIVE
        }
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val box = KarooType.headerIconBoxPx(capsPx.roundToInt()).toFloat().coerceAtMost(px.toFloat())
        KarooType.drawFlag(Canvas(bmp), 0f, px.toFloat(), box, state, night)
        return bmp
    }

    /**
     * The line a TextView without font padding lays out for [text] at [size] px: ascent and descent
     * (px, positive) and the capital height. Laid out for real, because Android makes the line taller
     * than the font's own metrics when fallback fonts are taller (2026-11-07: 6 px on the clock, which
     * then stood at the top of its box instead of the bottom).
     */
    private class Metrics(val size: Float, val ascent: Float, val descent: Float, val cap: Float)

    private fun metrics(face: Typeface, size: Float, text: String, variation: String? = null): Metrics {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face; textSize = size
            if (variation != null) fontVariationSettings = variation
        }
        val sample = text.ifEmpty { "0" }
        val layout = StaticLayout.Builder.obtain(sample, 0, sample.length, paint, 100_000)
            .setIncludePad(false)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUseLineSpacingFromFallbacks(true) }
            .build()
        val baseline = layout.getLineBaseline(0).toFloat()
        return Metrics(size, baseline - layout.getLineTop(0), layout.getLineBottom(0) - baseline, capRatio(face) * size)
    }

    /** Capital height as a share of the text size. */
    private fun capRatio(face: Typeface): Float {
        val bounds = Rect()
        Paint().apply { typeface = face; textSize = 100f }.getTextBounds("H", 0, 1, bounds)
        return bounds.height() / 100f
    }

    private fun textWidth(face: Typeface, size: Float, text: String): Float =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size }.measureText(text)

    private const val UNIT_SCALE = 0.4f
    /** Space between the header and the clock's tallest digits, and between the digits and the small line. */
    private const val HEADER_GAP_PX = 6f
    private const val NOTE_GAP_PX = 6f
    /** Space left and right of the value: the Karoo's ink ends 8–10 px from the tile's edge. */
    private const val VALUE_SIDE_PX = 5
    private const val NOTE_ICON_GAP_DP = 4f
    /** The gap before the unit ("3.4 km"), in text sizes: Relative's own space is a full digit wide. */
    private const val UNIT_GAP = 0.12f
    /** The small line in Relative (KarooType README, BigWatts): outline and word gap, in text sizes. */
    private const val NOTE_STROKE = 0.028f
    private const val WORD_GAP = 0.28f
    /** Room under the small line's baseline (no descenders in "14.2 km left"; "y" and "g" stay in). */
    private const val NOTE_DESCENT = 0.22f
    /** The tallest the clock's digits get (half and full tiles), as before this change. */
    private const val MAX_DIGITS_PX = 97f
    /** Where the icon starts and where text ends (px from the tile's edges), as on the Karoo's tiles. */
    private const val ICON_LEFT_PX = 12
    private const val TEXT_RIGHT_PX = 7 // the capitals' ink ends 2 px further in: 9 px, as the Karoo's
    /** The number's bottom line, px above the tile's bottom (the Karoo's own numbers). */
    // Sizes from field_partner.xml.
    private const val ICON_DP = 14f
    private const val ICON_GAP_DP = 4f
    private const val NOTE_SP = 14f
    private const val NOTE_MARGIN_DP = 4f
    // Fonts as in field_partner.xml (on the Karoo "sans-serif" is Plex Medium).
    private const val HEADER_FONT = "ibm-plex-sans-condensed"
    /** Below this tile height the small line would squeeze the clock too much. */
    private const val NOTE_MIN_HEIGHT_DP = 60f
}
