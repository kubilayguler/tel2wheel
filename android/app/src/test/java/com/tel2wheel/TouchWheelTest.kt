package com.tel2wheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class TouchWheelTest {
    private val r = 100f
    private val minR = 25f

    /** Finger position on a circle of radius [r], [deg] clockwise from 12 o'clock (screen y down). */
    private fun pos(deg: Float): Pair<Float, Float> {
        val a = Math.toRadians(deg.toDouble())
        return (sin(a) * r).toFloat() to (-cos(a) * r).toFloat()
    }

    private fun TouchWheel.turn(from: Float, to: Float, sens: Float = 1f, range: Float = 900f) {
        val (x0, y0) = pos(from)
        grab(x0, y0, minR)
        val step = if (to > from) 2f else -2f
        var a = from
        while ((step > 0 && a < to) || (step < 0 && a > to)) {
            a += step
            val (x, y) = pos(a)
            drag(x, y, minR, sens, range)
        }
    }

    @Test
    fun followsFinger_acrossMultipleRotations() {
        val w = TouchWheel()
        w.turn(0f, 400f)               // more than a full turn clockwise
        assertEquals(400f, w.angleDeg, 0.5f)
        assertEquals(400f / 450f, w.output(900f), 1e-3f)
    }

    @Test
    fun sensitivityScalesRotation_andClampsAtLock() {
        val w = TouchWheel()
        w.turn(0f, 100f, sens = 2f)
        assertEquals(200f, w.angleDeg, 1f)
        w.turn(100f, 400f, sens = 2f)
        assertEquals(450f, w.angleDeg, 1e-3f)      // clamped at full lock
        w.turn(400f, 390f, sens = 2f)
        assertEquals(430f, w.angleDeg, 1f)         // reverses immediately, no dead travel
    }

    @Test
    fun ignoresFingerNearHub() {
        val w = TouchWheel()
        w.grab(0f, -50f, minR)
        w.drag(2f, -2f, minR, 1f, 900f)            // finger crosses the hub: huge angle change
        w.drag(50f, 0f, minR, 1f, 900f)
        assertEquals(0f, w.angleDeg, 1e-3f)
    }

    @Test
    fun springsBackWhenReleased_andStaysWhenSpeedZero() {
        val w = TouchWheel()
        w.turn(0f, 90f)
        w.release()
        assertFalse(w.tick(0.05f, 0f))
        assertEquals(90f, w.angleDeg, 0.5f)

        assertTrue(w.tick(0.05f, 720f))            // 36° per 50 ms
        assertEquals(54f, w.angleDeg, 0.5f)
        repeat(10) { w.tick(0.05f, 720f) }
        assertEquals(0f, w.angleDeg, 0f)
    }

    @Test
    fun doesNotReturnWhileHeld() {
        val w = TouchWheel()
        w.turn(0f, 90f)
        assertFalse(w.tick(0.05f, 720f))
        assertEquals(90f, w.angleDeg, 0.5f)
    }
}
