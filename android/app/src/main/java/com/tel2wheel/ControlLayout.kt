package com.tel2wheel

import kotlin.math.hypot

/** Axis-aligned box in pixels. Plain floats (not RectF) so layout logic is unit-testable. */
class Box(var l: Float = 0f, var t: Float = 0f, var r: Float = 0f, var b: Float = 0f) {
    val w get() = r - l
    val h get() = b - t
    val cx get() = (l + r) / 2f
    val cy get() = (t + b) / 2f

    fun set(l: Float, t: Float, r: Float, b: Float) {
        this.l = l; this.t = t; this.r = r; this.b = b
    }

    fun set(o: Box) = set(o.l, o.t, o.r, o.b)

    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}

/** Circle in pixels. */
class Circle(var cx: Float = 0f, var cy: Float = 0f, var r: Float = 0f) {
    fun set(o: Circle) {
        cx = o.cx; cy = o.cy; r = o.r
    }

    fun contains(x: Float, y: Float, scale: Float = 1f) = hypot(x - cx, y - cy) <= r * scale
}

/**
 * Saved positions of the on-screen controls: comma-separated fractions of the view size
 * (box edges as fractions of width/height, wheel radius as a fraction of height), so a layout
 * survives screen size changes.
 */
object ControlLayout {
    fun encode(w: Float, h: Float, brake: Box, throttle: Box, wheel: Circle?): String {
        val v = mutableListOf(
            brake.l / w, brake.t / h, brake.r / w, brake.b / h,
            throttle.l / w, throttle.t / h, throttle.r / w, throttle.b / h,
        )
        if (wheel != null) v += listOf(wheel.cx / w, wheel.cy / h, wheel.r / h)
        return v.joinToString(",")
    }

    /** Applies [s] to the shapes. Returns false and leaves them untouched if [s] is empty or malformed. */
    fun decode(s: String, w: Float, h: Float, brake: Box, throttle: Box, wheel: Circle?): Boolean {
        if (s.isBlank()) return false
        val v = s.split(',').map { it.trim().toFloatOrNull() ?: return false }
        if (v.size != (if (wheel != null) 11 else 8)) return false
        if (v.any { it < -0.01f || it > 1.01f }) return false
        brake.set(v[0] * w, v[1] * h, v[2] * w, v[3] * h)
        throttle.set(v[4] * w, v[5] * h, v[6] * w, v[7] * h)
        if (wheel != null) {
            wheel.cx = v[8] * w; wheel.cy = v[9] * h; wheel.r = v[10] * h
        }
        return true
    }
}
