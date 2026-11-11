package com.dubadai.karoo.common

import android.content.Context
import android.content.res.ColorStateList
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
 *   [settingsHeading] + [settingsSegmented] or [settingsSwitchRow], [settingsHelp], [settingsDivider].
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

/** Icon + heading, like the Karoo's own Settings headings. Every heading has its own icon. */
fun Context.settingsHeading(iconRes: Int, title: String) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    setPadding(0, px(14), 0, px(10))
    addView(settingsIcon(iconRes))
    addView(label(title, 18f, R.color.settings_title), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
}

/** A heading with an On / Off switch at the right; the whole row toggles it. */
fun Context.settingsSwitchRow(iconRes: Int, title: String, on: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    settingsHeading(iconRes, title).apply {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val switch = Switch(context).apply {
            isChecked = on
            contentDescription = title
            thumbTintList = ColorStateList(states, intArrayOf(getColor(R.color.segment_selected), getColor(R.color.settings_switch_thumb_off)))
            trackTintList = ColorStateList(states, intArrayOf(getColor(R.color.segment_selected), getColor(R.color.segment_text)))
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
 * The Karoo's segmented control: a pale pill track, the picked option as a blue-grey pill. For two or
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
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(42))
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

/** Grey help under a control. */
fun Context.settingsHelp(value: String) = label(value, 13f, R.color.settings_help).apply {
    setPadding(0, px(10), 0, px(14))
}

fun Context.settingsDivider() = View(this).apply {
    setBackgroundColor(getColor(R.color.settings_divider))
    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(1))
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
    addView(settingsIcon(iconRes, 18).apply { setColorFilter(getColor(if (tone == SettingsTone.NEUTRAL) R.color.settings_title else color)) })
    addView(label(value, 13f, color), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
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
        addView(settingsIcon(iconRes))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(title, 18f, R.color.settings_title))
                if (status != null) addView(label(status, 13f, statusColor))
            },
            LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
        )
        addView(settingsIcon(R.drawable.ic_settings_chevron).apply { setColorFilter(getColor(R.color.settings_help)) })
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
    leading?.let { addView(it, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = px(12) }) }
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
fun Context.settingsInput(
    labelText: String,
    value: String,
    hint: String = "",
    inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    configure: EditText.() -> Unit = {},
) =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(labelText, 14f, R.color.settings_help, 500).apply { setPadding(0, px(12), 0, px(6)) })
        addView(
            EditText(context).apply {
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
    form.addView(settingsHelp(updateHelp))
    if (extra.isNotEmpty()) form.addView(settingsDivider())
    extra.forEach { form.addView(it) }
    form.addView(settingsDivider())
    val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
    form.addView(
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(14), 0, px(14))
            addView(settingsIcon(R.drawable.ic_settings_info))
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    // One line each: name, version, address (long names and debug versions don't wrap).
                    fun TextView.oneLine() = apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                    addView(label(applicationInfo.loadLabel(packageManager).toString().removePrefix("DDB "), 18f, R.color.settings_title).oneLine())
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
 * a whole list card), so this one measures itself. [picked]: a blue-grey ring around it.
 */
class SettingsSwatch(context: Context, private val color: Int, private val picked: Boolean = false, sizeDp: Int = 30) : View(context) {
    private val d = context.resources.displayMetrics.density
    private val size = ((sizeDp + if (picked) 12 else 0) * d).toInt()
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val edge = context.getColor(R.color.settings_title)
    private val ring = context.getColor(R.color.segment_selected)

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
