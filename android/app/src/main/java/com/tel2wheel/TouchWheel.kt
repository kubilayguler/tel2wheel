package com.tel2wheel

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sign

/**
 * On-screen steering wheel (Dr. Driving style). The finger's angle around the wheel's center is
 * tracked incrementally, so the wheel can turn several full rotations (900° lock-to-lock).
 * When released it springs back to center at a constant speed. Pure logic, UI thread only.
 */
class TouchWheel {
    /** Current wheel angle in degrees, positive = clockwise (right). */
    var angleDeg = 0f
        private set

    var grabbed = false
        private set

    private var lastFinger = 0f
    private var fingerValid = false

    /** [dx], [dy]: finger position relative to the wheel center, screen coordinates (y down). */
    fun grab(dx: Float, dy: Float, minRadius: Float) {
        grabbed = true
        fingerValid = hypot(dx, dy) >= minRadius
        lastFinger = fingerAngle(dx, dy)
    }

    /**
     * Rotates the wheel by how far the finger went around the center since the last call, times
     * [sensitivity]. Near the center the angle is meaningless, so movement there is ignored.
     */
    fun drag(dx: Float, dy: Float, minRadius: Float, sensitivity: Float, rangeDeg: Float) {
        if (!grabbed) return
        val a = fingerAngle(dx, dy)
        if (hypot(dx, dy) < minRadius) {
            fingerValid = false
            return
        }
        if (fingerValid) {
            var d = a - lastFinger
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            val half = rangeDeg / 2f
            angleDeg = (angleDeg + d * sensitivity).coerceIn(-half, half)
        }
        lastFinger = a
        fingerValid = true
    }

    fun release() {
        grabbed = false
    }

    /** Springs back towards center while released. Returns true while still moving. */
    fun tick(dtSec: Float, returnSpeedDegPerSec: Float): Boolean {
        if (grabbed || angleDeg == 0f || returnSpeedDegPerSec <= 0f) return false
        val step = returnSpeedDegPerSec * dtSec.coerceIn(0f, 0.1f)
        angleDeg = if (abs(angleDeg) <= step) 0f else angleDeg - sign(angleDeg) * step
        return angleDeg != 0f
    }

    /** Keeps the angle inside a (possibly smaller) range after a settings change. */
    fun clampTo(rangeDeg: Float) {
        val half = rangeDeg / 2f
        angleDeg = angleDeg.coerceIn(-half, half)
    }

    fun reset() {
        angleDeg = 0f
        grabbed = false
    }

    /** Output for the steering axis, -1..1. */
    fun output(rangeDeg: Float): Float = (angleDeg / (rangeDeg / 2f)).coerceIn(-1f, 1f)

    private companion object {
        /** Clockwise angle from 12 o'clock, degrees. */
        fun fingerAngle(dx: Float, dy: Float): Float =
            Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat()
    }
}
