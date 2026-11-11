package com.daviddokakis.partnereta

import com.dubadai.karoo.common.*
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Setup, opened from the Karoo's app list, built from the shared settings kit (SettingsKit.kt;
 * design: karoo-starter/docs/design/settings-kit, PartnerETA pages; docs/design/people/).
 * - Without extras: the people you follow (list cards), "+ Add person" and the app rows.
 * - With [EXTRA_SLOT]: one person's page: the Example card with their field, name and link (checked
 *   live), Save, their field and map settings, Remove. With [EXTRA_NEW] it adds a new person in that
 *   slot; nothing is stored until "Add <name>".
 */
class SetupActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var karoo: KarooSystemService
    private var connected = false
    private var checkJob: Job? = null

    private var slot = 0
    private var isNew = false
    /** The person on this page: saved at once when they exist, held until "Add" when new. */
    private var person: Person? = null

    private lateinit var form: LinearLayout
    private lateinit var title: TextView
    private lateinit var example: ExampleCard
    private lateinit var layout: SettingsLayout
    private var nameInput: EditText? = null
    private var linkInput: EditText? = null
    private var nameRow: View? = null
    private var linkRow: View? = null
    /** Holds the link check's line (settingsStatus), right above Save. */
    private var status: LinearLayout? = null
    private var saveButton: TextView? = null
    private var removeArmed = false
    /** Status per slot on the list ("Riding now"), filled in as the checks come back. */
    private val listStatus = mutableMapOf<Int, Pair<String, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        slot = intent.getIntExtra(EXTRA_SLOT, 0)
        isNew = intent.getBooleanExtra(EXTRA_NEW, false)
        person = when {
            slot == 0 -> null
            isNew -> People.newPerson(People.all(this))?.copy(slot = slot) ?: Person(slot, "", null)
            else -> People.bySlot(this, slot)
        }
        if (slot != 0 && person == null) { finish(); return }
        karoo = KarooSystemService(this)
        karoo.connect { ok ->
            connected = ok
            if (ok) runOnUiThread { if (slot == 0) checkAll() else person?.code?.let { check(it) } }
        }

        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), 0, px(12), px(24))
        }
        title = settingsTitle(getString(R.string.app_short_name))
        example = ExampleCard(this)
        layout = SettingsLayout(
                this,
                back = settingsBack(getString(R.string.back)) { finish() },
                barTitle = getString(R.string.settings),
                title = title,
                card = if (slot == 0) null else example,
                form = form,
                pageColor = getColor(R.color.settings_bg),
                textColor = getColor(R.color.settings_title),
            )
        setContentView(layout)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (slot == 0 && ::form.isInitialized) { render(); if (connected) checkAll() }
    }

    private fun render() = when {
        slot == 0 -> renderList()
        else -> renderPerson()
    }

    // ---- The list -------------------------------------------------------------------------

    private fun renderList() {
        title.text = getString(R.string.app_short_name)
        form.removeAllViews()
        val people = People.all(this)
        form.addView(settingsHeading(R.drawable.ic_settings_person, getString(R.string.people_title)))
        if (people.isEmpty()) form.addView(settingsHelp(getString(R.string.people_empty)))
        people.forEach { p ->
            val (st, colour) = listStatus[p.slot] ?: ("" to R.color.settings_help)
            form.addView(
                settingsListCard(
                    leading = settingsSwatch(MapStyle.byKey(p.colour).line),
                    title = p.shownName,
                    status = listOfNotNull(sourceLine(p), st.takeIf { it.isNotEmpty() }),
                    // "Karoo · PartnerETA 1" stays grey; the link check's words in their colour.
                    statusColors = listOf(R.color.settings_help, colour),
                ) { open(p.slot, new = false) },
            )
        }
        val next = People.newPerson(people)
        if (next != null) form.addView(settingsButton(SettingsButton.ADD, getString(R.string.people_add)) { open(next.slot, new = true) })
        else form.addView(settingsHelp(getString(R.string.people_full)))
        if (people.isNotEmpty()) form.addView(settingsHelp(getString(R.string.people_fields,
            people.joinToString(", ") { "PartnerETA ${it.slot} is ${it.shownName}" })))
        settingsAppRows(form, repo = "karoo-partnereta") { render() }
    }

    private fun open(slot: Int, new: Boolean) =
        startActivity(Intent(this, SetupActivity::class.java).putExtra(EXTRA_SLOT, slot).putExtra(EXTRA_NEW, new))

    /** Check each person's link once, for the list. */
    private fun checkAll() {
        People.all(this).forEach { p ->
            val code = p.code ?: return@forEach
            scope.launch {
                listStatus[p.slot] = shortStatus(linkStatus(code))
                if (slot == 0) renderList()
            }
        }
    }

    /** The list's short words for a link check. */
    private fun shortStatus(full: Pair<String, Int>): Pair<String, Int> {
        val (text, colour) = full
        val short = when (text) {
            getString(R.string.setup_not_riding) -> getString(R.string.short_not_riding)
            getString(R.string.setup_offline) -> getString(R.string.short_offline)
            else -> if (text.startsWith(getString(R.string.short_riding))) getString(R.string.short_riding)
                else if (text.startsWith(getString(R.string.setup_not_found))) getString(R.string.short_not_found) else text
        }
        return short to colour
    }

    /** "Karoo · PartnerETA 1". */
    private fun sourceLine(p: Person) = getString(R.string.person_source, p.slot)

    // ---- One person ------------------------------------------------------------------------

    /** The stored link as the field shows it. */
    private fun shownLink(p: Person) = p.code?.let { "…/live/$it" }.orEmpty()

    /** Move a view that lives across re-renders into the form. */
    private fun <V : View> keep(v: V): V { (v.parent as? LinearLayout)?.removeView(v); form.addView(v); return v }

    private fun renderPerson() {
        val p = person ?: return
        title.text = if (isNew) getString(R.string.person_add_title) else p.shownName
        showExample(p)
        form.removeAllViews()

        // Name and link keep what was typed across re-renders.
        nameRow = keep(nameRow ?: settingsInput(getString(R.string.setup_name), p.name, getString(R.string.setup_name_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS) {
            nameInput = this
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { if (isNew) showSaveButton(); showExample(person ?: return) }
            })
        })
        linkRow = keep(linkRow ?: settingsInput(
            getString(R.string.setup_link), shownLink(p),
            getString(R.string.setup_link_hint),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
        ) {
            linkInput = this
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, _, _ -> save(); true }
        })
        form.addView(settingsHelp(getString(R.string.setup_link_help)))
        // The link check's line, right above Save, so it shows where the rider just tapped.
        status = keep(status ?: LinearLayout(this).apply { orientation = LinearLayout.VERTICAL })
        saveButton = keep(saveButton ?: settingsButton(SettingsButton.MAIN, getString(R.string.setup_save)) { save() })
        showSaveButton()
        form.addView(settingsDivider())

        // Their field
        form.addView(settingsSwitchRow(R.drawable.ic_settings_distance, getString(R.string.setting_distance_left), p.distanceLeft) { on ->
            update { it.copy(distanceLeft = on) }
        })
        form.addView(settingsHelp(getString(R.string.setting_distance_left_help) + " " + getString(R.string.setting_same_route_help)))
        form.addView(settingsDivider())

        form.addView(settingsSwitchRow(R.drawable.ic_settings_update, getString(R.string.setting_start_alert), p.startAlert) { on ->
            update { it.copy(startAlert = on) }
        })
        form.addView(settingsHelp(getString(R.string.setting_start_alert_help, p.shownName)))
        form.addView(settingsDivider())

        // Their map
        val style = MapStyle.byKey(p.colour)
        form.addView(settingsHeading(R.drawable.ic_settings_target, getString(R.string.setting_colour)))
        form.addView(settingsSwatchRow(MapStyle.all.map { it.line to it.key }, style.key) { key -> update { it.copy(colour = key) } })
        val note = when (style.key) { "cyan" -> getString(R.string.colour_standard); in MapStyle.recommended -> getString(R.string.colour_recommended); else -> "" }
        form.addView(settingsHelp((if (note.isEmpty()) style.label else "${style.label} · $note") + ". " + getString(R.string.map_recommend)))
        form.addView(settingsDivider())

        form.addView(settingsHeading(R.drawable.ic_settings_marker, getString(R.string.setting_marker)))
        form.addView(settingsSegmented(listOf(getString(R.string.marker_circle) to true, getString(R.string.marker_arrow) to false), p.circleMarker) { on ->
            update { it.copy(circleMarker = on) }
        })
        form.addView(settingsHelp(getString(R.string.setting_marker_help)))
        form.addView(settingsDivider())

        // Their route: Show / Hide. Hidden, "On your route" has nothing to mark, so it goes too.
        form.addView(settingsHeading(R.drawable.ic_settings_flag, getString(R.string.setting_route)))
        form.addView(settingsSegmented(listOf(getString(R.string.route_show) to true, getString(R.string.route_hide) to false), p.showRoute) { on ->
            update { it.copy(showRoute = on) }
        })
        form.addView(settingsHelp(getString(if (p.showRoute) R.string.setting_route_help else R.string.setting_route_hidden_help)))
        form.addView(settingsDivider())

        if (p.showRoute) {
            form.addView(settingsHeading(R.drawable.ic_settings_lap, getString(R.string.setting_shared)))
            form.addView(settingsSegmented(listOf(getString(R.string.shared_arrows) to false, getString(R.string.shared_dots) to true), p.sharedDots) { on ->
                update { it.copy(sharedDots = on) }
            })
            form.addView(settingsHelp(getString(R.string.setting_shared_help)))
            form.addView(settingsDivider())
        }

        form.addView(settingsSwitchRow(R.drawable.ic_settings_clock, getString(R.string.setting_history), p.showHistory) { on ->
            update { it.copy(showHistory = on) }
        })
        form.addView(settingsHelp(getString(R.string.setting_history_help)))
        form.addView(settingsPicture(mapPreview(p, style), getString(R.string.map_preview_description)))

        if (!isNew) {
            form.addView(settingsDivider())
            // Remove asks once more, so it can't happen by a slip.
            form.addView(settingsButton(SettingsButton.REMOVE,
                if (removeArmed) getString(R.string.person_remove_confirm) else getString(R.string.person_remove, p.shownName)) {
                if (removeArmed) { People.remove(this, p.slot); finish() } else { removeArmed = true; renderPerson() }
            })
            form.addView(settingsHelp(getString(R.string.person_remove_help, p.slot)))
        }
    }

    /** Their ETA field as the settings make it, drawn by its own renderer (a quarter page). */
    private fun showExample(p: Person) {
        layout.showCard(true)
        val name = nameInput?.text?.toString()?.trim().orEmpty().ifEmpty { p.shownName }
        val text = FieldText("$name ETA", "Partner ETA", "11:05", note = if (p.distanceLeft) "14.2 km left" else "", noteIsExtra = true)
        val config = ViewConfig(gridSize = 30 to 15, viewSize = QUARTER_W to QUARTER_H, textSize = 46)
        val view = FieldView.render(this, config, text).first.apply(this, FrameLayout(this))
        view.measure(View.MeasureSpec.makeMeasureSpec(QUARTER_W, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(QUARTER_H, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, QUARTER_W, QUARTER_H)
        val full = Bitmap.createBitmap(QUARTER_W, QUARTER_H, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        val h = px(110)
        example.show(Bitmap.createScaledBitmap(full, h * QUARTER_W / QUARTER_H, h, true), getString(R.string.field_eta_description))
    }

    /** Change this person's settings: stored at once when they exist, held for "Add" when new. */
    private fun update(change: (Person) -> Person) {
        val p = person ?: return
        val next = change(p)
        person = next
        if (!isNew) People.put(this, next)
        removeArmed = false
        renderPerson()
    }

    private fun save() {
        val p = person ?: return
        val name = nameInput?.text?.toString().orEmpty().trim()
        // Keep the stored code when the field still shows it ("…/live/CODE").
        val typed = linkInput?.text?.toString().orEmpty()
        val code = PartnerFeed.codeFrom(typed)
        if (code == null) {
            showStatus(getString(R.string.setup_bad_link), R.color.settings_warn)
            return
        }
        if (isNew && name.isEmpty()) {
            showStatus(getString(R.string.person_needs_name), R.color.settings_warn)
            return
        }
        val saved = p.copy(name = name, code = code)
        People.put(this, saved)
        person = saved
        linkInput?.setText(shownLink(saved))
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(linkInput?.windowToken, 0)
        if (isNew) { finish(); return }
        title.text = saved.shownName
        saveButton?.settingsFlashSaved(getString(R.string.setup_saved))
        check(code)
    }

    /** Ask the feed once and say what we found. */
    private fun check(code: String) {
        if (!connected) return
        checkJob?.cancel()
        showStatus(getString(R.string.setup_checking), R.color.settings_help)
        checkJob = scope.launch {
            val (text, color) = linkStatus(code)
            showStatus(text, color)
        }
    }

    /** What a live link says right now: riding, not riding, not found, or offline. */
    private suspend fun linkStatus(code: String): Pair<String, Int> = karooStatus(code)

    private suspend fun karooStatus(code: String): Pair<String, Int> = try {
        val res = karoo.karooHttpGet(PartnerFeed.url(code))
        when (res.statusCode) {
            200 -> {
                val s = PartnerFeed.parse(res.body)
                val age = s.updatedAtMs?.let { System.currentTimeMillis() - it }
                if (s.isRiding && age != null && age < PartnerTracker.ENDED_MS) getString(R.string.setup_riding, ago(age)) to R.color.settings_ok
                else getString(R.string.setup_not_riding) to R.color.settings_ok
            }
            404 -> getString(R.string.setup_not_found) to R.color.settings_warn
            else -> "${getString(R.string.setup_not_found)} (${res.statusCode})" to R.color.settings_warn
        }
    } catch (e: Exception) {
        getString(R.string.setup_offline) to R.color.settings_warn
    }

    private fun ago(ms: Long): String {
        val s = ms / 1000
        return if (s < 90) "$s s ago" else "${s / 60} min ago"
    }

    private fun showStatus(text: String, colorRes: Int) {
        val tone = when (colorRes) { R.color.settings_ok -> SettingsTone.OK; R.color.settings_warn -> SettingsTone.WARN; else -> SettingsTone.NEUTRAL }
        status?.apply { removeAllViews(); addView(settingsStatus(text, tone)) }
    }

    /** Save, or "Add Alex" while adding someone. */
    private fun showSaveButton() {
        val b = saveButton ?: return
        val name = nameInput?.text?.toString().orEmpty().trim()
        b.text = when {
            isNew -> if (name.isEmpty()) getString(R.string.person_add_unnamed) else getString(R.string.person_add, name)
            else -> getString(R.string.setup_save)
        }
    }

    /** The map preview (MapPreview) as a picture for the kit's settingsPicture, the form's width. */
    private fun mapPreview(p: Person, style: MapStyle): Bitmap {
        val w = resources.displayMetrics.widthPixels - px(24)
        val h = px(150)
        val view = MapPreview(this, style, p.circleMarker, p.sharedDots, p.showHistory, p.showRoute)
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    override fun onDestroy() {
        scope.cancel()
        if (::karoo.isInitialized) karoo.disconnect()
        super.onDestroy()
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_SLOT = "slot"
        const val EXTRA_NEW = "new"
        /** A quarter-page field on the Karoo 3 (pixels), for the Example card. */
        private const val QUARTER_W = 238
        private const val QUARTER_H = 168
    }
}
