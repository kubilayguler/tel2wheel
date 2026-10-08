package com.tel2wheel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color

/** Tilt mode: steer by rotating the phone. Brake on the left third, throttle on the right third. */
@SuppressLint("ViewConstructor")
class TiltDriveView(
    context: Context,
    settings: Settings,
    link: UdpLink,
    private val steering: SteeringSensor,
) : DriveView(context, settings, link, Settings.MODE_TILT) {

    override fun defaultLayout(w: Float, h: Float) {
        val pad = 8 * density
        val pw = w * PEDAL_WIDTH
        brakeBox.set(pad, pad, pw - pad, h - pad)
        throttleBox.set(w - pw + pad, pad, w - pad, h - pad)
    }

    override fun drawSteering(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h * 0.5f
        val r = minOf(w * (0.5f - PEDAL_WIDTH) * 0.6f, h * 0.17f)

        stroke.color = Color.rgb(90, 90, 100)
        canvas.drawCircle(cx, cy, r, stroke)
        // Shows the virtual wheel, so at full lock it sits at ±(range / 2).
        val wheelDeg = steering.output * settings.wheelRangeDeg / 2f
        canvas.save()
        canvas.rotate(wheelDeg, cx, cy)
        stroke.color = if (locked) Color.rgb(255, 170, 0) else Color.WHITE
        canvas.drawLine(cx - r, cy, cx + r, cy, stroke)
        canvas.drawLine(cx, cy, cx, cy + r, stroke)
        fill.color = Color.rgb(255, 170, 0)
        canvas.drawCircle(cx, cy - r, 6 * density, fill)
        canvas.restore()
        canvas.drawText("${wheelDeg.toInt()}°", cx, cy + r + 34 * density, bigText)
        drawLockBadge(canvas, cx, cy + r + 56 * density)

        drawStatus(canvas, cx, h - 16 * density)
    }

    private companion object {
        const val PEDAL_WIDTH = 0.33f
    }
}
