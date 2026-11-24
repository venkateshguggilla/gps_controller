package com.venky.gpscontroller

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

/**
 * A joystick that floats above other apps (needs the "Display over other apps" permission).
 * Its deflection is written to [MockState.joyX] / [MockState.joyY]; the service turns that into movement.
 */
@SuppressLint("ClickableViewAccessibility")
class JoystickOverlay(context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (16 * density).toInt()
        y = (220 * density).toInt()
    }

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        // Drag this bar to move the joystick around the screen.
        val handle = TextView(context).apply {
            text = "⋮⋮ move"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            val pad = (6 * density).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            background = GradientDrawable().apply {
                setColor(0x99000000.toInt())
                cornerRadius = 12 * density
            }
        }
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downX = e.rawX
                    downY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - downX).toInt()
                    params.y = startY + (e.rawY - downY).toInt()
                    runCatching { wm.updateViewLayout(this, params) }
                }
            }
            true
        }
        addView(handle)
        addView(JoystickView(context))
    }

    fun show() = wm.addView(root, params)

    fun hide() {
        runCatching { wm.removeView(root) }
    }
}

@SuppressLint("ClickableViewAccessibility")
private class JoystickView(context: Context) : View(context) {
    private val sizePx = (140 * resources.displayMetrics.density).toInt()
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE61565C0.toInt() }
    private var knobX = 0f
    private var knobY = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val c = width / 2f
        val knobRadius = c * 0.35f
        val travel = c - knobRadius
        canvas.drawCircle(c, c, c, basePaint)
        canvas.drawCircle(c + knobX * travel, c + knobY * travel, knobRadius, knobPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val c = width / 2f
                val travel = c - c * 0.35f
                var dx = (event.x - c) / travel
                var dy = (event.y - c) / travel
                val mag = hypot(dx, dy)
                if (mag > 1f) {
                    dx /= mag
                    dy /= mag
                }
                knobX = dx
                knobY = dy
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                knobX = 0f
                knobY = 0f
            }
        }
        MockState.joyX = knobX
        MockState.joyY = knobY
        invalidate()
        return true
    }
}
