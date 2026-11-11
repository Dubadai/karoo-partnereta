package com.daviddokakis.partnereta

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * DEBUG BUILDS ONLY (src/debug): the field gallery. Renders this app's field(s) with the field's own
 * code at the Karoo 3's field sizes, light and dark, as PNGs, so mockups need no Karoo. Run it with
 * `Karoo/design-assets/tools/field-gallery/gallery.sh <app folder>` (Karoo 3 emulator, no device).
 * What to draw is in GalleryFields.kt next to this file. Shared copy: design-assets/tools/field-gallery/android/.
 */
class FieldGalleryActivity : Activity() {
    // An activity, not a broadcast: Android freezes a background app mid-way through a long gallery.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            val out = File(filesDir, "gallery")
            try {
                FieldGallery.run(applicationContext, out)
            } catch (e: Throwable) {
                Log.e("FieldGallery", "Gallery failed", e)
                out.mkdirs()
                File(out, "FAILED").writeText(Log.getStackTraceString(e))
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }
}

object FieldGallery {
    /** Field sizes the Karoo 3 reports to fields (ViewConfig.viewSize), in pixels. */
    val SIZES = listOf(
        "full" to (478 to 721),     // whole page (60 × 60 grid)
        "half" to (478 to 336),     // half page, full width (60 × 30)
        "row" to (478 to 141),      // one row, full width (60 × 12)
        "quarter" to (238 to 168),  // half width, quarter height (30 × 15)
        "small" to (236 to 141),    // half width, one row (30 × 12)
    )

    fun run(context: Context, out: File) {
        out.mkdirs()
        out.listFiles()?.forEach { it.delete() }
        useKarooFonts(context)
        // GalleryFields.sizes: the sizes this app's fields are made for (null: all of them)
        val sizes = GalleryFields.sizes?.let { wanted -> SIZES.filter { it.first in wanted } } ?: SIZES
        for (variant in GalleryFields.variants) for ((size, wh) in sizes) for (night in listOf(false, true)) {
            val (w, h) = wh
            val c = withNight(context, night)
            val field = GalleryFields.render(c, variant, w, h, night)
            val tile = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(tile).apply {
                drawColor(if (night) Color.BLACK else Color.WHITE) // the Karoo's tile behind the field
                drawBitmap(field, 0f, 0f, null)
            }
            val mode = if (night) "dark" else "light"
            FileOutputStream(File(out, "$variant-$size-$mode.png")).use { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(out, "DONE").writeText("${GalleryFields.variants.size * sizes.size * 2} fields")
    }

    /** [base] in light or dark mode, so values-night/ colours resolve like on the Karoo. */
    fun withNight(base: Context, night: Boolean): Context {
        val config = Configuration(base.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(config)
    }

    /**
     * The Karoo's own fonts, set up exactly as on the Karoo: gallery.sh pushes the Karoo 3's
     * /system/etc/fonts.xml and its font files (design-assets/fonts/karoo-system/, pulled from a
     * Karoo) to files/fonts/, and every family and alias in that fonts.xml replaces the emulator's.
     * This matters: the Karoo labels Plex Medium as normal (400), Regular as light (300) and SemiBold
     * as bold (700), and its condensed font as 600 (bold condensed text is drawn fake-bold, as on the Karoo).
     * Needs the emulator's hidden-API check off (gallery.sh does that).
     */
    @Suppress("UNCHECKED_CAST")
    private fun useKarooFonts(context: Context) {
        val dir = File(context.filesDir, "fonts")
        val xml = File(dir, "fonts.xml")
        if (!xml.exists()) {
            Log.w("FieldGallery", "No Karoo fonts.xml pushed; using the emulator's fonts")
            return
        }
        val families = HashMap<String, Typeface>()
        val aliases = mutableListOf<Triple<String, String, Int?>>()   // name, to, weight
        val parser = android.util.Xml.newPullParser().apply { setInput(xml.reader()) }
        var family: String? = null
        val fonts = mutableListOf<android.graphics.fonts.Font>()
        var weight = 400
        var italic = false
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "family" -> { family = parser.getAttributeValue(null, "name"); fonts.clear() }
                    "font" -> {
                        weight = parser.getAttributeValue(null, "weight")?.toIntOrNull() ?: 400
                        italic = parser.getAttributeValue(null, "style") == "italic"
                    }
                    "alias" -> aliases += Triple(
                        parser.getAttributeValue(null, "name"), parser.getAttributeValue(null, "to"),
                        parser.getAttributeValue(null, "weight")?.toIntOrNull(),
                    )
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (family != null) {
                    val file = File(dir, parser.text.trim())
                    if (parser.text.isNotBlank() && file.exists()) runCatching {
                        fonts += android.graphics.fonts.Font.Builder(file).setWeight(weight)
                            .setSlant(if (italic) android.graphics.fonts.FontStyle.FONT_SLANT_ITALIC else android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT)
                            .build()
                    }
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "family") {
                    val name = family
                    if (name != null && fonts.isNotEmpty()) runCatching {
                        val b = android.graphics.fonts.FontFamily.Builder(fonts.first())
                        fonts.drop(1).forEach { b.addFont(it) }
                        // The family's own weight, as the Karoo gives it: its upright font closest to 400.
                        // For the condensed family (only 600) bold then asks for 900 and is drawn
                        // fake-bold, as on the Karoo (measured: Last lap 1 px heavier than unfaked).
                        val base = fonts.filter { it.style.slant == android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT }
                            .minByOrNull { kotlin.math.abs(it.style.weight - 400) }?.style?.weight ?: 400
                        families[name] = Typeface.CustomFallbackBuilder(b.build()).setSystemFallback("sans-serif")
                            .setStyle(android.graphics.fonts.FontStyle(base, android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT)).build()
                    }.onFailure { Log.w("FieldGallery", "Font family $name: $it") }
                    family = null
                }
            }
        }
        // Aliases, in order (some point at other aliases), with their own weight where given
        repeat(2) {
            for ((name, to, w) in aliases) {
                val target = families[to] ?: continue
                families[name] = if (w != null) Typeface.create(target, w, false) else target
            }
        }
        val sans = families["sans-serif"] ?: run {
            Log.w("FieldGallery", "Karoo fonts.xml has no usable sans-serif; using the emulator's fonts")
            return
        }
        runCatching {
            val f = Typeface::class.java.getDeclaredField("sSystemFontMap").apply { isAccessible = true }
            val map = HashMap(f.get(null) as Map<String, Typeface>)
            map.putAll(families)
            f.set(null, map)
        }.onFailure { Log.w("FieldGallery", "Couldn't swap system fonts", it) }
        val bold = Typeface.create(sans, 700, false)
        for ((field, value) in listOf("DEFAULT" to sans, "SANS_SERIF" to sans, "DEFAULT_BOLD" to bold)) {
            runCatching { Typeface::class.java.getDeclaredField(field).apply { isAccessible = true }.set(null, value) }
        }
        Log.i("FieldGallery", "Karoo fonts: ${families.size} families and aliases")
    }
}
