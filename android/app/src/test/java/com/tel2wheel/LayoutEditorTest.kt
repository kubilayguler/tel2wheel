package com.tel2wheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutEditorTest {
    private val w = 1000f
    private val h = 500f

    private val brake = Box(500f, 10f, 700f, 490f)
    private val throttle = Box(750f, 10f, 950f, 490f)
    private val wheel = Circle(250f, 250f, 150f)

    private fun editor(withWheel: Boolean = true) = LayoutEditor(
        brake, throttle, if (withWheel) wheel else null, w, h,
        minBoxW = 70f, minBoxH = 140f, minRadius = 50f, handleRadius = 30f,
    )

    @Test
    fun dragMovesControl_andKeepsItOnScreen() {
        val ed = editor()
        assertTrue(ed.down(0, 600f, 300f))           // brake body
        ed.move(0, 400f, 250f)
        assertEquals(300f, brake.l, 1e-3f)
        assertEquals(200f, brake.w, 1e-3f)
        ed.move(0, 2000f, 2000f)                     // far off screen
        assertEquals(w, brake.r, 1e-3f)
        assertEquals(h, brake.b, 1e-3f)
        assertEquals(480f, brake.h, 1e-3f)           // size unchanged
    }

    @Test
    fun cornerHandleResizesPedal_withMinimums() {
        val ed = editor()
        assertTrue(ed.down(0, 950f, 10f))            // throttle top-right handle
        ed.move(0, 900f, 250f)
        assertEquals(750f, throttle.l, 1e-3f)        // bottom-left stays
        assertEquals(490f, throttle.b, 1e-3f)
        assertEquals(900f, throttle.r, 1e-3f)
        assertEquals(250f, throttle.t, 1e-3f)
        ed.move(0, 0f, 499f)                         // tries to collapse it
        assertEquals(70f, throttle.w, 1e-3f)
        assertEquals(140f, throttle.h, 1e-3f)
    }

    @Test
    fun wheelRimHandleSetsRadius() {
        val ed = editor()
        val hx = ed.handleX(LayoutEditor.WHEEL)
        val hy = ed.handleY(LayoutEditor.WHEEL)
        assertTrue(ed.down(0, hx, hy))
        ed.move(0, 250f + 100f, 250f)
        assertEquals(100f, wheel.r, 1e-3f)
        ed.move(0, 250f + 10f, 250f)
        assertEquals(50f, wheel.r, 1e-3f)            // minimum
    }

    @Test
    fun twoFingerPinchScalesAroundCenter() {
        val ed = editor()
        assertTrue(ed.down(0, 200f, 250f))
        assertTrue(ed.down(1, 300f, 250f))           // distance 100
        ed.move(1, 250f + 50f * 1.5f, 250f)          // second finger moves out
        ed.move(0, 250f - 50f * 1.5f, 250f)          // distance 150 → ×1.5, same midpoint
        assertEquals(225f, wheel.r, 1e-3f)
        assertEquals(250f, wheel.cx, 1e-3f)
        assertEquals(250f, wheel.cy, 1e-3f)          // still on screen: r ≤ h/2
    }

    @Test
    fun liftingOneFingerOfPinch_continuesAsDrag() {
        val ed = editor()
        ed.down(0, 600f, 200f)
        ed.down(1, 600f, 300f)
        ed.up(1)
        ed.move(0, 650f, 200f)
        assertEquals(550f, brake.l, 1e-3f)
    }

    @Test
    fun emptySpaceIsNotGrabbed_andWheelIgnoredWithoutOne() {
        assertFalse(editor().down(0, 470f, 480f))
        assertFalse(editor(withWheel = false).down(0, 250f, 250f))
    }

    @Test
    fun layoutRoundTrips_andRejectsGarbage() {
        val s = ControlLayout.encode(w, h, brake, throttle, wheel)
        val b = Box(); val t = Box(); val c = Circle()
        assertTrue(ControlLayout.decode(s, w, h, b, t, c))
        assertEquals(brake.l, b.l, 1e-3f); assertEquals(throttle.b, t.b, 1e-3f); assertEquals(wheel.r, c.r, 1e-3f)

        val untouched = Box(1f, 2f, 3f, 4f)
        assertFalse(ControlLayout.decode("", w, h, untouched, t, c))
        assertFalse(ControlLayout.decode("1,2,x", w, h, untouched, t, c))
        assertFalse(ControlLayout.decode(s, w, h, untouched, t, null))   // 11 values for an 8-value layout
        assertEquals(1f, untouched.l, 0f)
    }
}
