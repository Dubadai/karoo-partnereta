package com.dubadai.karoo.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/**
 * The shared settings kit: every app's Settings / Setup pages are built from these pieces, so they
 * look and behave the same (design: karoo-starter/docs/design/settings-kit/). Part of karoo-common
 * with its resources; apps get it with setup/sync-kit.sh, never by copying files.
 *
 * Page: SettingsLayout (ExampleCard.kt) with [settingsTitle] as its title, then sections:
 *   [settingsHeading] or [settingsSwitchRow], then [settingsIntro] (what it does, always before the
 *   choices), then [settingsSegmented] / [settingsChoiceCards], then [settingsDivider].
 * Order: look → what the field shows → data and smoothing → links and accounts → [settingsAppRows].
 *
 * Heading icons ship with the kit as res/drawable/ic_settings_*: the Karoo's own glyphs (speed, power,
 * heart, clock, target, distance, lap) and outline icons (tiles, appearance, person, timer, top_row,
 * plus, flag, intro, info, update, and the glyphs cadence, ascent, grade, timer, climber,
 * elevation, pr). Add a new one here first, then copy it to the apps.
 */

/** A setting that doesn't apply right now (Android's disabled look). Its help says why. */
const val SETTINGS_DISABLED_ALPHA = 0.38f

private fun Context.px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

private fun Context.label(value: String, sp: Float, color: Int, weight: Int = 400) = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(getColor(color))
    typeface = weightFace(weight)
}

/** 400 regular, 500 medium, 600+ bold (Typeface.create with a weight needs API 28; minSdk is 26). */
private fun weightFace(weight: Int): Typeface = when {
    weight >= 600 -> Typeface.DEFAULT_BOLD
    weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.DEFAULT
}

/** The app's name (or the page's, e.g. a person's) under the bar, scrolling away with the page. */
fun Context.settingsTitle(value: String) = label(value, 22f, R.color.settings_title, 700).apply {
    setPadding(px(12), px(4), px(12), px(4))
}

/** The back arrow for SettingsLayout's bar. */
fun Context.settingsBack(description: String, onBack: () -> Unit) = ImageView(this).apply {
    setImageResource(R.drawable.ic_settings_back)
    setColorFilter(getColor(R.color.settings_title))
    contentDescription = description
    scaleType = ImageView.ScaleType.CENTER
    isClickable = true
    setOnClickListener { onBack() }
}

fun Context.settingsIcon(iconRes: Int, sizeDp: Int = 20) = ImageView(this).apply {
    setImageResource(iconRes)
    setColorFilter(getColor(R.color.settings_title))
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    layoutParams = LinearLayout.LayoutParams(px(sizeDp), px(sizeDp)).apply { marginEnd = px(8) }
}

/**
 * Kit 1.8.4: an icon's middle meets the middle of its title's capital letters, not of the title's text
 * box (the box has font room above the letters, so a box-centred icon reads about 2 dp too high).
 * Measured from the laid-out first line just before drawing, so it holds for any font, size or row;
 * only translationY changes, never the layout.
 */
private fun View.centreOnCaps(title: TextView) {
    val caps = Rect()
    val icon = IntArray(2)
    val text = IntArray(2)
    viewTreeObserver.addOnPreDrawListener {
        val line = title.layout ?: return@addOnPreDrawListener true
        if (title.text.isEmpty() || height == 0) return@addOnPreDrawListener true
        title.paint.getTextBounds("H", 0, 1, caps)
        getLocationInWindow(icon)
        title.getLocationInWindow(text)
        val capsMiddle = text[1] + title.totalPaddingTop + line.getLineBaseline(0) + (caps.top + caps.bottom) / 2f
        val iconMiddle = icon[1] - translationY + height / 2f
        val want = Math.round(capsMiddle - iconMiddle).toFloat()
        if (want != translationY) translationY = want
        true
    }
}

/**
 * Our rule (kit 1.8.1): an icon sits next to its title, the first line of the text beside it, never in
 * the middle of a title + lines block. Call on a horizontal row (gravity top) with the icon and the
 * first line's TextView: whichever is shorter is moved down so their middles line up.
 */
private fun alignToFirstLine(icon: View, iconPx: Int, first: TextView, text: View) {
    val d = (first.lineHeight - iconPx) / 2
    if (d >= 0) (icon.layoutParams as LinearLayout.LayoutParams).topMargin = d
    else (text.layoutParams as? LinearLayout.LayoutParams)?.topMargin = -d
    icon.centreOnCaps(first)
}

/** Icon + heading, like the Karoo's own Settings headings. Every heading has its own icon. */
fun Context.settingsHeading(iconRes: Int, title: String) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, px(14), 0, px(10))
    val titleView = label(title, 18f, R.color.settings_title)
    addView(settingsIcon(iconRes).apply { centreOnCaps(titleView) })
    addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
}

/** A heading with an On / Off switch at the right; the whole row toggles it. */
fun Context.settingsSwitchRow(iconRes: Int, title: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    settingsHeading(iconRes, title).apply {
        tag = SWITCH_ROW_TAG
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val switch = Switch(context).apply {
            isChecked = on
            contentDescription = title
            // The Karoo's own (Android) switch, only recoloured (our pick, 2026-11-11): a
            // white knob in every state (MULTIPLY keeps the knob picture's own shadow), the track full
            // colour (SRC: Android's track is 30 % see-through), Karoo green on, grey off.
            thumbTintList = ColorStateList.valueOf(getColor(R.color.settings_switch_thumb))
            thumbTintMode = PorterDuff.Mode.MULTIPLY
            trackTintList = ColorStateList(states, intArrayOf(getColor(R.color.settings_switch_on), getColor(R.color.settings_switch_off)))
            trackTintMode = PorterDuff.Mode.SRC
            isEnabled = enabled
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        // Android's switch is 48 dp tall (its touch area); trim the row so its help sits as close as
        // under a segmented control.
        setPadding(0, px(6), 0, 0)
        addView(switch)
        if (enabled) setOnClickListener { switch.toggle() } else alpha = SETTINGS_DISABLED_ALPHA
    }

/**
 * The Karoo's segmented control: a pale pill track, the picked option as a light blue-grey pill with
 * dark text (the Karoo's own pill_switch: grey_100, k2LightBlue, k2Black). For two or
 * more named choices (On / Off is a [settingsSwitchRow]). [unavailable]: options greyed out and not
 * tappable; [enabled] false greys the whole control. Names shrink only when they don't fit.
 */
fun <T> Context.settingsSegmented(
    options: List<Pair<String, T>>,
    selected: T,
    enabled: Boolean = true,
    unavailable: Set<T> = emptySet(),
    onPick: (T) -> Unit,
) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setBackgroundResource(R.drawable.bg_segment_track)
    setPadding(px(2), px(2), px(2), px(2))
    // room under it before the divider or next heading (its explanation now sits above it, settingsIntro)
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(42)).apply { bottomMargin = px(14) }
    if (!enabled) alpha = SETTINGS_DISABLED_ALPHA
    options.forEach { (name, value) ->
        val picked = value == selected
        val off = !enabled || (value in unavailable && !picked)
        addView(
            TextView(context).apply {
                text = name
                gravity = Gravity.CENTER
                maxLines = 1
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(px(4), 0, px(4), 0)
                setTextColor(getColor(if (picked) R.color.segment_text_selected else R.color.segment_text))
                if (picked) setBackgroundResource(R.drawable.bg_segment_selected)
                if (value in unavailable && !picked) alpha = SETTINGS_DISABLED_ALPHA
                isSelected = picked
                isEnabled = !off
                isClickable = !off
                if (!off) setOnClickListener { if (!picked) onPick(value) }
            },
            LinearLayout.LayoutParams(0, MATCH_PARENT, 1f),
        )
    }
    // One size for the whole control: the largest (12 → 8 sp; past that, names end in …) at which every name fits its option,
    // so one long name doesn't end up smaller than its neighbours.
    addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ ->
        if (r - l == or_ - ol) return@addOnLayoutChangeListener
        val labels = (0 until (v as LinearLayout).childCount).map { v.getChildAt(it) as TextView }
        val room = (r - l - v.paddingLeft - v.paddingRight) / labels.size - px(8)
        val paint = android.text.TextPaint(labels.first().paint)
        val sp = (12 downTo 8).firstOrNull { size ->
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), resources.displayMetrics)
            labels.all { paint.measureText(it.text.toString()) <= room }
        } ?: 8
        v.post { labels.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat()); it.ellipsize = android.text.TextUtils.TruncateAt.END } }
    }
}

/** A small label above a control inside a section ("Left", "Right"). */
fun Context.settingsSubLabel(value: String) = label(value, 12f, R.color.settings_help).apply {
    setPadding(0, px(10), 0, px(4))
}

/**
 * What a setting does, right under its heading (or switch row) and before its choices (kit 1.8.0, our
 * rule: the explanation always comes first, never under the choices). No gap above, 12 dp below.
 */
fun Context.settingsIntro(value: String) = label(value, 13f, R.color.settings_help).apply {
    setPadding(0, 0, 0, px(12))
}

/** Grey note under a control, for something other than what the setting does (that's [settingsIntro]). */
fun Context.settingsHelp(value: String) = label(value, 13f, R.color.settings_help).apply {
    setPadding(0, px(10), 0, px(14))
}

fun Context.settingsDivider(): View = SettingsDividerView(this).apply {
    setBackgroundColor(getColor(R.color.settings_divider))
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(1))
}

/** Marks a [settingsSwitchRow], so a divider right under it knows to leave room. */
private const val SWITCH_ROW_TAG = "settingsSwitchRow"

/**
 * The divider line. Right under a switch row (a list of switches with nothing after the last one) it
 * keeps 12 dp above itself, so the last switch doesn't sit on the line (kit 1.8.2; under help, a
 * segmented control or choice cards those already leave the room).
 */
private class SettingsDividerView(context: Context) : View(context) {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val p = parent as? android.view.ViewGroup ?: return
        val above = p.getChildAt(p.indexOfChild(this) - 1)
        val lp = layoutParams as? LinearLayout.LayoutParams ?: return
        val want = if (above?.tag == SWITCH_ROW_TAG) (12 * resources.displayMetrics.density).toInt() else 0
        if (lp.topMargin != want) { lp.topMargin = want; layoutParams = lp }
    }
}

enum class SettingsTone { NEUTRAL, OK, WARN }

/**
 * A note that isn't a setting: an icon and text. [SettingsTone.NEUTRAL] grey (a tip, e.g. "Best two
 * rows tall or more"); [SettingsTone.OK] green on a pale green card (good news, e.g. Mountaineer's
 * light-bike note); [SettingsTone.WARN] amber on a pale amber card (needs a look).
 */
fun Context.settingsInfoRow(iconRes: Int, value: String, tone: SettingsTone = SettingsTone.NEUTRAL) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    val color = when (tone) { SettingsTone.NEUTRAL -> R.color.settings_help; SettingsTone.OK -> R.color.settings_ok; SettingsTone.WARN -> R.color.settings_warn }
    if (tone == SettingsTone.NEUTRAL) setPadding(0, px(14), 0, px(14)) else {
        setBackgroundResource(if (tone == SettingsTone.OK) R.drawable.bg_settings_note_ok else R.drawable.bg_settings_note_warn)
        setPadding(px(12), px(10), px(12), px(10))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(8); bottomMargin = px(8) }
    }
    gravity = Gravity.TOP
    val icon = settingsIcon(iconRes, 18).apply { setColorFilter(getColor(if (tone == SettingsTone.NEUTRAL) R.color.settings_title else color)) }
    val text = label(value, 13f, color).apply { layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f) }
    addView(icon); addView(text)
    alignToFirstLine(icon, px(18), text, text)
}

/**
 * A number with its unit on one row (e.g. bike weight): icon and name left, a short field, the unit
 * right. [onChange] gets the text as typed; the app parses and checks it.
 */
fun Context.settingsNumberRow(iconRes: Int, title: String, value: String, unit: String, hint: String = "", decimals: Boolean = true, onChange: (String) -> Unit) =
    settingsHeading(iconRes, title).apply {
        addView(
            EditText(context).apply {
                isSingleLine = true
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or (if (decimals) android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL else 0)
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                setText(value)
                this.hint = hint
                contentDescription = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTextColor(getColor(R.color.settings_title))
                setHintTextColor(getColor(R.color.settings_help))
                setBackgroundResource(R.drawable.bg_settings_input)
                setPadding(px(10), 0, px(10), 0)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) = onChange(s?.toString().orEmpty())
                })
            },
            LinearLayout.LayoutParams(px(80), px(42)),
        )
        addView(label(unit, 16f, R.color.settings_help).apply { setPadding(px(8), 0, 0, 0) })
    }

/** A row that opens another page: icon, title, an optional status line under it, an arrow. */
fun Context.settingsNavRow(iconRes: Int, title: String, status: String? = null, statusColor: Int = R.color.settings_help, onClick: () -> Unit) =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(56)
        isClickable = true
        setOnClickListener { onClick() }
        val icon = settingsIcon(iconRes)
        val titleView = label(title, 18f, R.color.settings_title)
        addView(icon)
        // with a status line the icon stays next to the title, not in the middle of both lines
        if (status != null) {
            gravity = Gravity.TOP
            setPadding(0, px(8), 0, px(8))
            (icon.layoutParams as LinearLayout.LayoutParams).topMargin = (titleView.lineHeight - px(20)) / 2
        }
        icon.centreOnCaps(titleView)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleView)
                if (status != null) addView(label(status, 13f, statusColor))
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        addView(settingsIcon(R.drawable.ic_settings_chevron).apply {
            setColorFilter(getColor(R.color.settings_help))
            (layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_VERTICAL   // the arrow stays in the middle
        })
    }

/**
 * One item in a list of things the rider made (people, fields, workouts): a grey rounded card with
 * [leading] (a colour swatch or a small picture), the name, an optional [tag] (e.g. BETA), status
 * lines in [statusColor] (or one colour per line in [statusColors], e.g. "Karoo · PartnerETA 1" grey
 * above "No connection" amber), and an arrow. For [leading], [settingsSwatch] or a picture.
 */
fun Context.settingsListCard(
    leading: View?,
    title: String,
    status: List<String>,
    statusColor: Int = R.color.settings_help,
    tag: String? = null,
    statusColors: List<Int>? = null,
    onClick: () -> Unit,
) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setBackgroundResource(R.drawable.bg_settings_card)
    setPadding(px(14), px(12), px(8), px(12))
    minimumHeight = px(64)
    isClickable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(8) }
    // the leading picture next to the title (first line), not in the middle of title + status lines
    leading?.let {
        it.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val titleLine = label(title, 18f, R.color.settings_title, 500).lineHeight
        addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            marginEnd = px(12)
            if (status.isNotEmpty()) { gravity = Gravity.TOP; topMargin = maxOf(0, (titleLine - it.measuredHeight) / 2) }
        })
    }
    addView(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(title, 18f, R.color.settings_title, 500))
                    if (tag != null) addView(
                        label(tag, 10f, R.color.settings_tag_text, 600).apply {
                            setBackgroundResource(R.drawable.bg_settings_tag)
                            setPadding(px(5), px(1), px(5), px(1))
                        },
                        LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = px(8) },
                    )
                },
            )
            status.forEachIndexed { i, line -> addView(label(line, 13.5f, statusColors?.getOrNull(i) ?: statusColor)) }
        },
        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
    )
    addView(settingsIcon(R.drawable.ic_settings_chevron).apply { setColorFilter(getColor(R.color.settings_help)) })
}

enum class SettingsButton { MAIN, ADD, REMOVE }

/**
 * Buttons: [SettingsButton.MAIN] in Karoo yellow for the page's one main action (Save, Add Alex);
 * [SettingsButton.ADD] light blue-grey for "+ Add …"; [SettingsButton.REMOVE] red text for
 * Remove / Delete.
 */
fun Context.settingsButton(kind: SettingsButton, value: String, onClick: () -> Unit) = TextView(this).apply {
    text = value
    gravity = Gravity.CENTER
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    typeface = weightFace(600)
    setTextColor(getColor(when (kind) { SettingsButton.MAIN -> R.color.settings_main_text; SettingsButton.ADD -> R.color.settings_add_text; SettingsButton.REMOVE -> R.color.settings_remove }))
    setBackgroundResource(when (kind) { SettingsButton.MAIN -> R.drawable.bg_settings_main; SettingsButton.ADD -> R.drawable.bg_settings_add; SettingsButton.REMOVE -> R.drawable.bg_settings_remove })
    minHeight = px(48)
    isClickable = true
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(48)).apply { topMargin = px(8); bottomMargin = px(8) }
}

/** A labelled text field (name, link, key). */
/** [configure]: anything else on the field itself (IME action, a text watcher, keeping a reference). */
/**
 * [prefix] (kit 1.8.3, PartnerETA's live-link code): fixed start text shown grey inside the box, not
 * editable and not part of the value; the rider types only what follows (the Karoo can't paste).
 * Without it the box is a plain field, as before. [intro] (1.8.3): what to type, between the label and
 * the box, styled like [settingsIntro] (our help-first rule applies to inputs too).
 */
fun Context.settingsInput(
    labelText: String,
    value: String,
    hint: String = "",
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    prefix: String = "",
    intro: String = "",
    configure: EditText.() -> Unit = {},
) =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(labelText, 14f, R.color.settings_help, 500).apply { setPadding(0, px(12), 0, px(6)) })
        // [intro]: what to type, between the label and the box (the help-first rule, like settingsIntro)
        if (intro.isNotEmpty()) addView(settingsIntro(intro))
        val field = EditText(context).apply {
            isSingleLine = true
            this.inputType = inputType
            setText(value)
            this.hint = hint
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTextColor(getColor(R.color.settings_title))
            setHintTextColor(getColor(R.color.settings_help))
            setBackgroundResource(R.drawable.bg_settings_input)
            setPadding(px(12), px(10), px(12), px(10))
            configure()
        }
        if (prefix.isEmpty()) addView(field, LinearLayout.LayoutParams(MATCH_PARENT, px(46)))
        else addView(
            // The box is the row: grey fixed start, then the field, same size, no gap between them
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_settings_input)
                setPadding(px(12), 0, px(12), 0)
                isClickable = true
                setOnClickListener { field.requestFocus() }
                addView(TextView(context).apply {
                    text = prefix
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTextColor(getColor(R.color.settings_help))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                field.background = null
                field.setPadding(0, px(10), 0, px(10))
                if (field.contentDescription == null) field.contentDescription = "$labelText, $prefix"
                addView(field, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            },
            LinearLayout.LayoutParams(MATCH_PARENT, px(46)),
        )
    }

/**
 * The same last rows on every app's Settings, after a divider: update notices on / off, then the
 * version and where to read more. [extra] goes between them, after its own divider (e.g.
 * BetterIntervals' "Show introduction" row, icon ic_settings_intro). [updateHelp]: how this app
 * tells about an update, if not with a card on the field (e.g. a Karoo notification).
 */
fun Context.settingsAppRows(
    form: LinearLayout,
    repo: String,
    extra: List<View> = emptyList(),
    updateHelp: String = getString(R.string.settings_update_notices_help),
    onChange: () -> Unit = {},
) {
    form.addView(settingsDivider())
    form.addView(
        settingsSwitchRow(R.drawable.ic_settings_update, getString(R.string.settings_update_notices), UpdateCheck.isEnabled(this)) {
            UpdateCheck.setEnabled(this, it)
            onChange()
        },
    )
    form.addView(settingsIntro(updateHelp))
    if (extra.isNotEmpty()) form.addView(settingsDivider())
    extra.forEach { form.addView(it) }
    form.addView(settingsDivider())
    val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
    form.addView(
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, px(14), 0, px(14))
            // the icon next to the app's name (the first line), not in the middle of the three lines
            val nameView = label(applicationInfo.loadLabel(packageManager).toString().removePrefix("DDB "), 18f, R.color.settings_title)
            addView(settingsIcon(R.drawable.ic_settings_info).apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = (nameView.lineHeight - px(20)) / 2
                centreOnCaps(nameView)
            })
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    // One line each: name, version, address (long names and debug versions don't wrap).
                    fun TextView.oneLine() = apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                    addView(nameView.oneLine())
                    addView(label(getString(R.string.settings_version, version), 13f, R.color.settings_help).oneLine())
                    // The address shrinks (12 → 9 sp) before it's cut: "karoo-betterintervals" needs it.
                    addView(
                        label("github.com/Dubadai/$repo", 12f, R.color.settings_help).apply {
                            maxLines = 1
                            setAutoSizeTextTypeUniformWithConfiguration(9, 12, 1, TypedValue.COMPLEX_UNIT_SP)
                        },
                        LinearLayout.LayoutParams(MATCH_PARENT, px(18)),
                    )
                },
                LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
            )
        },
    )
}

/** A status line with a coloured dot (e.g. under a link: "● Link works · riding now"). */
fun Context.settingsStatus(value: String, tone: SettingsTone) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, px(6), 0, px(6))
    val color = getColor(when (tone) { SettingsTone.NEUTRAL -> R.color.settings_help; SettingsTone.OK -> R.color.settings_ok; SettingsTone.WARN -> R.color.settings_warn })
    addView(
        View(context).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(color) } },
        LinearLayout.LayoutParams(px(8), px(8)).apply { marginEnd = px(8) },
    )
    addView(label(value, 13.5f, R.color.settings_help, 500).apply { setTextColor(color) })
}

/**
 * The page's MAIN button says it saved: green with [savedText] for [ms], then back to how it was.
 * Call after a save from the MAIN button's click.
 */
fun TextView.settingsFlashSaved(savedText: String, ms: Long = 3000) {
    val before = text
    text = savedText
    setBackgroundResource(R.drawable.bg_settings_saved)
    setTextColor(context.getColor(R.color.settings_saved_text))
    postDelayed({
        text = before
        setBackgroundResource(R.drawable.bg_settings_main)
        setTextColor(context.getColor(R.color.settings_main_text))
    }, ms)
}

/**
 * A colour dot with a fixed size: a plain View sized WRAP_CONTENT fills all the room it's given (e.g.
 * a whole list card), so this one measures itself. [picked]: a dark blue ring around it (the Karoo's focus ring).
 */
class SettingsSwatch(context: Context, private val color: Int, private val picked: Boolean = false, sizeDp: Int = 30) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val size = ((sizeDp + if (picked) 12 else 0) * d).toInt()
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val edge = context.getColor(R.color.settings_title)
    private val ring = context.getColor(R.color.settings_ring)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(size, size)

    override fun onDraw(canvas: android.graphics.Canvas) {
        val c = size / 2f
        var r = c
        if (picked) {
            fill.style = android.graphics.Paint.Style.STROKE; fill.strokeWidth = 3 * d; fill.color = ring
            canvas.drawCircle(c, c, r - 1.5f * d, fill)
            r -= 6 * d
        }
        fill.style = android.graphics.Paint.Style.FILL; fill.color = color
        canvas.drawCircle(c, c, r - d, fill)
        fill.style = android.graphics.Paint.Style.STROKE; fill.strokeWidth = 2 * d; fill.color = edge
        canvas.drawCircle(c, c, r - d, fill)
    }
}

fun Context.settingsSwatch(color: Int, picked: Boolean = false, sizeDp: Int = 30) = SettingsSwatch(this, color, picked, sizeDp)

/**
 * A row of colours to pick from (e.g. PartnerETA's map colour). [colors]: colour → value. The cells
 * share the form's width (the Karoo's form is about 232 dp), and the dots shrink when there are many,
 * so six colours and the picked one's ring all fit.
 */
fun <T> Context.settingsSwatchRow(colors: List<Pair<Int, T>>, selected: T, onPick: (T) -> Unit) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, px(4), 0, px(4))
    val formDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density - 24
    val dotDp = (formDp / colors.size - 16).toInt().coerceIn(18, 30)
    colors.forEach { (color, value) ->
        val picked = value == selected
        addView(
            android.widget.FrameLayout(context).apply {
                isClickable = true
                isSelected = picked
                setOnClickListener { if (!picked) onPick(value) }
                addView(settingsSwatch(color, picked, dotDp), android.widget.FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            },
            LinearLayout.LayoutParams(0, px(dotDp + 22), 1f),
        )
    }
}

/** A picture in the form (e.g. PartnerETA's map preview): full width, rounded, with a description. */
fun Context.settingsPicture(picture: android.graphics.Bitmap, description: String) = ImageView(this).apply {
    setImageBitmap(picture)
    contentDescription = description
    adjustViewBounds = true
    scaleType = ImageView.ScaleType.FIT_CENTER
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) = outline.setRoundRect(0, 0, view.width, view.height, px(12).toFloat())
    }
    clipToOutline = true
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = px(8); bottomMargin = px(8) }
}

/**
 * A list the rider puts in order by dragging (kit 1.1.0, UpAhead's icon priority): grey cards like
 * [settingsListCard], each with its place (1, 2, …), an optional [leading] picture, the name, and a
 * drag handle on the right. Drag a row by its handle; on release the list re-numbers and [onReorder]
 * gets the new order. Only the handle drags, so the page still scrolls everywhere else.
 */
fun <T> Context.settingsRankList(
    items: List<Triple<T, View?, String>>,
    onReorder: (List<T>) -> Unit,
): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    val order = items.toMutableList()
    val gap = px(8)
    fun build() {
        removeAllViews()
        order.forEachIndexed { i, (_, leading, title) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                setPadding(px(14), px(8), px(8), px(8))
                minimumHeight = px(52)
                addView(label("${i + 1}", 14f, R.color.settings_help, 500), LinearLayout.LayoutParams(px(22), WRAP_CONTENT))
                leading?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = px(12) }) }
                addView(label(title, 16f, R.color.settings_title), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            }
            val handle = settingsIcon(R.drawable.ic_settings_handle, 24).apply {
                setColorFilter(getColor(R.color.settings_help))
                setPadding(px(8), px(8), px(8), px(8))
            }
            row.addView(handle, LinearLayout.LayoutParams(px(40), px(40)))
            var startY = 0f
            handle.setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        startY = e.rawY; row.elevation = px(6).toFloat(); true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> { row.translationY = e.rawY - startY; true }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        val step = row.height + gap
                        val to = (i + Math.round(row.translationY / step)).coerceIn(0, order.size - 1)
                        row.translationY = 0f; row.elevation = 0f
                        if (to != i) { order.add(to, order.removeAt(i)); onReorder(order.map { it.first }) }
                        post { build() }
                        true
                    }
                    else -> false
                }
            }
            addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = gap })
        }
    }
    build()
}

/**
 * One choice from a list too long for [settingsSegmented] (kit 1.7.0, from UpAhead): a card per option
 * with its [title], an optional short [line] under it in the help colour, and a radio at the right. The
 * picked card gets a 3 dp ring in Apple system blue 4 dp outside it (corners concentric with the card's) and Apple's picked radio, a blue disc with a white dot (our pick: with the
 * green switch, the one exception to Karoo-native colours). The ring's room is kept on every card, so
 * nothing moves on a tap; the ring hangs 4 dp into the page's side margin so the cards line up with the
 * page's other cards ([SettingsLayout] lets it draw there; a form nested in its own ViewGroup needs
 * clipChildren = false and clipToPadding = false on it too). The whole card is the tap target.
 */
fun <T> Context.settingsChoiceCards(options: List<Triple<String, String?, T>>, selected: T, onPick: (T) -> Unit): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    clipChildren = false; clipToPadding = false
    // room under the last card before a divider or the next heading
    setPadding(0, 0, 0, px(12))
    val blue = getColor(R.color.settings_select_blue)
    options.forEach { (title, line, value) ->
        val picked = value == selected
        val ring = android.widget.FrameLayout(context).apply {
            setPadding(px(4), px(4), px(4), px(4))
            // Concentric with the card (kit 1.8.5): the ring's outer edge is 4 dp out, so its corners are
            // 20 + 4 = 24 dp; the stroke's middle line, which cornerRadius sets, is 1.5 dp in: 22.5 dp.
            if (picked) background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 22.5f * resources.displayMetrics.density; setStroke(px(3), blue) }
            isClickable = true
            isSelected = picked
            contentDescription = if (line == null) title else "$title. $line"
            setOnClickListener { if (!picked) onPick(value) }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_settings_card)
            setPadding(px(16), px(12), px(14), px(12))
            minimumHeight = px(52)
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 16f, R.color.settings_title, if (picked) 600 else 500))
                if (line != null) addView(label(line, 13f, R.color.settings_help).apply { setPadding(0, px(2), 0, 0) })
            }
            addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(ChoiceRadio(context, picked), LinearLayout.LayoutParams(px(24), px(24)))
        }
        ring.addView(card)
        addView(ring, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(2); leftMargin = -px(4); rightMargin = -px(4) })
    }
}

/** Kit 1.4.0's list, now drawn as [settingsChoiceCards] without lines (kept so apps calling it keep working). */
fun <T> Context.settingsChoiceList(options: List<Pair<String, T>>, selected: T, onPick: (T) -> Unit): LinearLayout =
    settingsChoiceCards(options.map { (name, value) -> Triple(name, null, value) }, selected, onPick)

/** The radio of a [settingsChoiceCards] card: a ring, filled with a blue dot when picked. */
/**
 * The radio on a choice card. Not picked: a thin grey ring. Picked (kit 1.8.6, Apple's radio): a solid
 * blue disc, as wide as the ring, with a white dot in the middle.
 */
private class ChoiceRadio(context: Context, private val picked: Boolean) : View(context) {
    private val dp = context.resources.displayMetrics.density
    private val ring = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f * dp
        color = context.getColor(R.color.settings_switch_thumb_off)
    }
    private val disc = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.settings_select_blue) }
    private val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.settings_switch_thumb) }
    override fun onDraw(c: android.graphics.Canvas) {
        val r = minOf(width, height) / 2f
        val x = width / 2f; val y = height / 2f
        if (picked) {
            c.drawCircle(x, y, r - ring.strokeWidth / 2f, disc)   // the ring's outer edge: same size
            c.drawCircle(x, y, 3.75f * dp, dot)
        } else c.drawCircle(x, y, r - ring.strokeWidth, ring)
    }
}
