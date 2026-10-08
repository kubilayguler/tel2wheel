package com.tel2wheel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Touch wheel mode (Dr. Driving style): an on-screen wheel on the left that you turn with a
 * finger, brake and throttle pedals on the right. The wheel springs back to center when released.
 */
@SuppressLint("ViewConstructor")
class TouchDriveView(
    context: Context,
    settings: Settings,
    link: UdpLink,
) : DriveView(context, settings, link, Settings.MODE_TOUCH) {

    override val wheelShape = Circle()
    private val wheel = TouchWheel()
    private var wheelPointer = -1
    private var statusCx = 0f
    private var lastFrameNanos = 0L

    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun defaultLayout(w: Float, h: Float) {
        val pad = 8 * density
        val gap = 10 * density
        val pedalW = w * 0.2f
        throttleBox.set(w - pad - pedalW, pad, w - pad, h - pad)
        brakeBox.set(throttleBox.l - gap - pedalW, pad, throttleBox.l - gap, h - pad)
        val areaW = brakeBox.l - gap
        statusCx = areaW / 2f
        wheelShape.cx = areaW / 2f
        wheelShape.cy = h * 0.5f
        wheelShape.r = minOf(areaW * 0.4f, h * 0.3f)
    }

    override fun steeringDown(id: Int, x: Float, y: Float): Boolean {
        // Generous grab zone around the wheel, like Dr. Driving.
        if (wheelPointer != -1 || !wheelShape.contains(x, y, GRAB_ZONE)) return false
        wheelPointer = id
        if (!locked) wheel.grab(x - wheelShape.cx, y - wheelShape.cy, wheelShape.r * MIN_RADIUS)
        return true
    }

    override fun steeringMove(id: Int, x: Float, y: Float) {
        if (id != wheelPointer || locked) return
        val c = wheelShape
        wheel.drag(x - c.cx, y - c.cy, c.r * MIN_RADIUS, settings.touchSensitivity, settings.touchRangeDeg)
        send()
    }

    override fun steeringUp(id: Int) {
        if (id != wheelPointer) return
        wheelPointer = -1
        wheel.release()
        lastFrameNanos = 0L
    }

    override fun onLockChanged(locked: Boolean) {
        // A finger that was on the wheel across a lock change has to touch again to grab it.
        wheel.release()
        wheelPointer = -1
    }

    private fun send() {
        link.steer = (wheel.output(settings.touchRangeDeg) * 32767f).toInt().toShort()
        link.requestSend()
    }

    override fun drawSteering(canvas: Canvas) {
        wheel.clampTo(settings.touchRangeDeg)

        // Spring back to center while released, at display refresh rate.
        if (!locked) {
            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1e9f
            val before = wheel.angleDeg
            val moving = wheel.tick(dt, settings.touchReturnSpeed)
            if (wheel.angleDeg != before) send()
            if (moving || (!wheel.grabbed && wheel.angleDeg != 0f && settings.touchReturnSpeed > 0f)) {
                lastFrameNanos = now
                postInvalidateOnAnimation()
            } else {
                lastFrameNanos = 0L
            }
        }

        val cx = wheelShape.cx
        val cy = wheelShape.cy
        val radius = wheelShape.r
        val accent = if (locked) Color.rgb(255, 170, 0) else Color.WHITE
        canvas.save()
        canvas.rotate(wheel.angleDeg, cx, cy)
        rim.strokeWidth = radius * 0.16f
        rim.color = if (locked) Color.rgb(120, 90, 30) else Color.rgb(70, 70, 80)
        canvas.drawCircle(cx, cy, radius, rim)
        stroke.color = accent
        stroke.strokeWidth = radius * 0.07f
        canvas.drawLine(cx - radius, cy, cx + radius, cy, stroke)
        canvas.drawLine(cx, cy, cx, cy + radius, stroke)
        stroke.strokeWidth = 3 * density
        fill.color = Color.rgb(255, 170, 0)
        canvas.drawCircle(cx, cy - radius, radius * 0.09f, fill)
        canvas.restore()

        fill.color = Color.rgb(36, 36, 42)
        canvas.drawCircle(cx, cy, radius * 0.3f, fill)
        canvas.drawText("${wheel.angleDeg.toInt()}°", cx, cy + 10 * density, bigText)
        drawLockBadge(canvas, cx, cy + radius * 0.62f)

        drawStatus(canvas, statusCx, height - 16 * density)
    }

    private companion object {
        /** Fraction of the radius around the hub where finger angle is ignored. */
        const val MIN_RADIUS = 0.25f

        /** Touches within this many radii of the center grab the wheel. */
        const val GRAB_ZONE = 1.5f
    }
}
