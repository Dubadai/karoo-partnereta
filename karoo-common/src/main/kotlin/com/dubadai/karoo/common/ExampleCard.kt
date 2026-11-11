package com.dubadai.karoo.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "Example" card at the top of a Settings page (design: karoo-starter/docs/design/settings-preview/).
 * Shows the app's field as the current settings make it, drawn by the field's own renderer with
 * made-up values, so the rider sees a change before riding. Call [show] again after every change.
 * Part of karoo-common, with the `example_*` colours and the `example` string.
 */
class ExampleCard(context: Context) : LinearLayout(context) {
    private val image = ImageView(context)
    private val tileBackground: android.graphics.drawable.Drawable

    init {
        orientation = VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(context.getColor(R.color.example_card))
        }
        setPadding(dp(10), dp(6), dp(10), dp(10))
        addView(
            TextView(context).apply {
                text = context.getString(R.string.example)
                isAllCaps = true
                letterSpacing = 0.06f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(context.getColor(R.color.example_label))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
        )
        image.apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // The Karoo's field tile behind the field: white or black, thin border, rounded corners.
            tileBackground = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(context.getColor(R.color.example_tile))
                setStroke(dp(1).coerceAtLeast(1), context.getColor(R.color.example_tile_border))
            }
            background = tileBackground
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(6).toFloat())
            }
            clipToOutline = true
        }
        addView(image, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
        })
    }

    /**
     * [field]: the field as the renderer draws it, at the size it should appear (in pixels).
     * [framed] false: the picture draws its own tile (e.g. SmallerFields' ring around a tile), so the
     * card's tile background and rounded clip are left out.
     */
    fun show(field: Bitmap, description: String, framed: Boolean = true) {
        image.setImageBitmap(field)
        image.contentDescription = description
        image.background = if (framed) tileBackground else null
        image.clipToOutline = framed
    }

    /** Taps on the picture, in the picture's own pixels (e.g. pick which half of a tile to edit). */
    fun onTap(handler: (x: Float, y: Float) -> Unit) {
        image.isClickable = true
        image.setOnTouchListener { v, e ->
            if (e.action == android.view.MotionEvent.ACTION_UP) {
                val d = (v as ImageView).drawable ?: return@setOnTouchListener false
                val m = android.graphics.Matrix().also { v.imageMatrix.invert(it) }
                val pt = floatArrayOf(e.x - v.paddingLeft, e.y - v.paddingTop).also { m.mapPoints(it) }
                if (pt[0] in 0f..d.intrinsicWidth.toFloat() && pt[1] in 0f..d.intrinsicHeight.toFloat()) handler(pt[0], pt[1])
                v.performClick()
            }
            true
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

/**
 * A Settings page (design round 2, karoo-starter/docs/design/settings-preview/): a fixed bar with
 * the back arrow and "Settings"; under it the app's name as a big title, the Example [card] and the
 * settings ([form]), which all scroll together (the card isn't pinned, so the settings get the room).
 */
class SettingsLayout(
    context: Context,
    back: View,
    barTitle: String,
    private val title: View,
    card: ExampleCard?,
    form: View,
    pageColor: Int,
    textColor: Int,
) : LinearLayout(context) {
    /** The page's scroll view: a page that rebuilds its form on every change can keep the position. */
    val scroll = android.widget.ScrollView(context)
    private val cardHolder = android.widget.FrameLayout(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(pageColor)
        // The bar: back arrow left, "Settings" centred, 18 sp
        addView(
            android.widget.FrameLayout(context).apply {
                addView(back, android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL).apply { marginStart = dp(4) })
                addView(
                    TextView(context).apply {
                        text = barTitle
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        // No extra font padding, so the text's middle lines up with the arrow's
                        includeFontPadding = false
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                        setTextColor(textColor)
                    },
                    android.widget.FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
                )
            },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(4) },
        )
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(cardHolder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(form, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        cardHolder.setPadding(dp(12), dp(4), dp(12), dp(4))
        // A page without a field to show (a list of people or fields) has no card.
        if (card != null) cardHolder.addView(card, android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        else cardHolder.visibility = View.GONE
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Shows or hides the Example card, for an activity whose pages differ (e.g. a picker page without a field). */
    fun showCard(visible: Boolean) {
        cardHolder.visibility = if (visible && cardHolder.childCount > 0) View.VISIBLE else View.GONE
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
