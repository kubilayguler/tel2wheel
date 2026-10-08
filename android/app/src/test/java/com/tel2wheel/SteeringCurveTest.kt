package com.tel2wheel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SteeringCurveTest {
    private fun SteeringCurve.at(deg: Float, ret: Float = 0f, center: Float = 0f) =
        map(deg, 270f, 1.5f, ret, center, 0f)

    /** Feeds a path of phone angles in 0.1° steps and returns the outputs. */
    private fun sweep(curve: SteeringCurve, vararg points: Float, ret: Float, center: Float): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        var cur = points[0]
        out += cur to curve.at(cur, ret, center)
        for (target in points.drop(1)) {
            val step = if (target > cur) 0.1f else -0.1f
            while (abs(target - cur) > 0.05f) {
                cur += step
                out += cur to curve.at(cur, ret, center)
            }
        }
        return out
    }

    @Test
    fun linearMapping_fullLockAtRangeOverSensitivity() {
        val c = SteeringCurve()
        assertEquals(1f, c.at(90f), 1e-4f)
        assertEquals(0.5f, c.at(45f), 1e-4f)
        assertEquals(-1f, c.at(-120f), 1e-4f)
    }

    @Test
    fun noJumps_withBothAssists() {
        val path = sweep(SteeringCurve(), 0f, 80f, 20f, 60f, 35f, 85f, 0f, -50f, -10f, -40f, 10f, ret = 0.7f, center = 0.5f)
        val maxStep = path.zipWithNext { a, b -> abs(b.second - a.second) }.max()
        // 0.1° of phone ≈ 0.0011 axis units; allow for the steepest slopes of both assists.
        assertTrue("max step $maxStep", maxStep < 0.01f)
    }

    @Test
    fun unwinding_returnsFasterThanLinear_andReachesZero() {
        val path = sweep(SteeringCurve(), 0f, 80f, 0f, ret = 0.5f, center = 0f)
        val at40back = path.last { abs(it.first - 40f) < 0.06f }.second
        val linear40 = 40f * 1.5f / 135f
        assertTrue("$at40back should be < $linear40", at40back < linear40 * 0.8f)
        assertEquals(0f, path.last().second, 1e-3f)
    }

    @Test
    fun turningBackIn_catchesUpToLinear() {
        val path = sweep(SteeringCurve(), 0f, 80f, 20f, 70f, ret = 0.6f, center = 0f)
        assertEquals(70f * 1.5f / 135f, path.last().second, 1e-3f)
    }

    @Test
    fun centerAssist_isMonotonic_andSoftAtCenter() {
        val path = sweep(SteeringCurve(), -15f, 15f, ret = 0f, center = 1f)
        path.zipWithNext { a, b -> assertTrue("${a} -> ${b}", b.second >= a.second - 1e-6f) }
        val at1 = path.first { abs(it.first - 1f) < 0.06f }.second
        assertTrue("center pull at 1°: $at1", at1 < 1f * 1.5f / 135f * 0.5f)
    }
}
