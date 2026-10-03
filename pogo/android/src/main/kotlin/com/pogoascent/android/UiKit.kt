package com.pogoascent.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Tiny programmatic-UI toolkit (no XML, no AndroidX). Dark navy panels, amber accent, big touch targets. */
object Ui {
  val PANEL = Color.argb(0xE6, 0x10, 0x18, 0x2C)
  val PANEL_SOFT = Color.argb(0xB0, 0x10, 0x18, 0x2C)
  val BUTTON = Color.rgb(0x2A, 0x3B, 0x66)
  val BUTTON_PRESSED = Color.rgb(0x3E, 0x58, 0x9A)
  val BUTTON_DISABLED = Color.rgb(0x2B, 0x2F, 0x3A)
  val ACCENT = Color.rgb(0xFF, 0xB7, 0x03)
  val TEXT = Color.WHITE
  val TEXT_DIM = Color.rgb(0xA8, 0xB3, 0xCF)
  val GOOD = Color.rgb(0x6B, 0xD9, 0x7B)

  fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
  fun dp(c: Context, v: Int): Int = dp(c, v.toFloat())

  fun rounded(color: Int, radiusDp: Float, c: Context, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(c, radiusDp).toFloat()
    if (stroke != 0) setStroke(dp(c, 2), stroke)
  }

  fun panel(c: Context): LinearLayout = LinearLayout(c).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(PANEL, 18f, c)
    val p = dp(c, 18)
    setPadding(p, p, p, p)
  }

  fun title(c: Context, text: String, sizeSp: Float = 28f): TextView = TextView(c).apply {
    this.text = text
    setTextColor(ACCENT)
    textSize = sizeSp
    typeface = Typeface.DEFAULT_BOLD
    gravity = Gravity.CENTER
    setPadding(0, 0, 0, dp(c, 10))
  }

  fun label(c: Context, text: String, sizeSp: Float = 16f, color: Int = TEXT, bold: Boolean = false): TextView = TextView(c).apply {
    this.text = text
    setTextColor(color)
    textSize = sizeSp
    if (bold) typeface = Typeface.DEFAULT_BOLD
  }

  fun button(c: Context, text: String, enabled: Boolean = true, accent: Boolean = false, onClick: () -> Unit): Button = Button(c).apply {
    this.text = text
    isAllCaps = false
    setTextColor(if (accent) Color.rgb(0x1B, 0x1B, 0x1B) else TEXT)
    textSize = 17f
    typeface = Typeface.DEFAULT_BOLD
    minHeight = dp(c, 52)
    minimumHeight = dp(c, 52)
    val normal = rounded(if (accent) ACCENT else BUTTON, 14f, c)
    val pressed = rounded(if (accent) Color.rgb(0xFF, 0xD2, 0x66) else BUTTON_PRESSED, 14f, c)
    val off = rounded(BUTTON_DISABLED, 14f, c)
    background = StateListDrawable().apply {
      addState(intArrayOf(-android.R.attr.state_enabled), off)
      addState(intArrayOf(android.R.attr.state_pressed), pressed)
      addState(intArrayOf(), normal)
    }
    isEnabled = enabled
    if (!enabled) setTextColor(TEXT_DIM)
    setOnClickListener { onClick() }
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      topMargin = dp(c, 6); bottomMargin = dp(c, 6)
    }
  }

  fun spacer(c: Context, dpHeight: Int): View = View(c).apply { layoutParams = LinearLayout.LayoutParams(1, dp(c, dpHeight)) }

  /** ScrollView that wraps its content but never grows taller than [maxHeightPx] (then it scrolls). */
  class MaxHeightScrollView(c: Context, private val maxHeightPx: Int) : ScrollView(c) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
      super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST))
    }
  }

  fun scroll(c: Context, content: View, maxHeightPx: Int = Int.MAX_VALUE / 2): ScrollView = MaxHeightScrollView(c, maxHeightPx).apply {
    isFillViewport = false
    addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
  }

  /** Label + value text on one line, followed by a slider (0..100 steps). */
  fun slider(c: Context, name: String, value: Double, min: Double, max: Double, format: (Double) -> String, onChange: (Double) -> Unit): LinearLayout {
    val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(c, 6), 0, dp(c, 6)) }
    val header = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
    val nameView = label(c, name).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    val valueView = label(c, format(value), color = ACCENT, bold = true)
    header.addView(nameView); header.addView(valueView)
    val bar = SeekBar(c).apply {
      this.max = 100
      progress = (((value - min) / (max - min)) * 100).toInt().coerceIn(0, 100)
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
          val v = min + (max - min) * p / 100.0
          valueView.text = format(v)
          if (fromUser) onChange(v)
        }
        override fun onStartTrackingTouch(sb: SeekBar) {}
        override fun onStopTrackingTouch(sb: SeekBar) {}
      })
    }
    box.addView(header); box.addView(bar)
    return box
  }

  fun toggle(c: Context, name: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
    val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(c, 8), 0, dp(c, 8)) }
    val nameView = label(c, name).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    val sw = Switch(c).apply {
      isChecked = checked
      setOnCheckedChangeListener { _: CompoundButton, v: Boolean -> onChange(v) }
    }
    row.addView(nameView); row.addView(sw)
    return row
  }

  /** A button that cycles through [options]; shows "name: current". */
  fun choice(c: Context, name: String, options: List<String>, selected: Int, onChange: (Int) -> Unit): Button {
    var index = selected.coerceIn(0, options.size - 1)
    lateinit var b: Button
    b = button(c, "$name: ${options[index]}") {
      index = (index + 1) % options.size
      b.text = "$name: ${options[index]}"
      onChange(index)
    }
    return b
  }

  fun centered(c: Context, content: View, widthDp: Int): FrameLayout = FrameLayout(c).apply {
    addView(content, FrameLayout.LayoutParams(dp(c, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
  }
}
