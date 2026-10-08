package com.tel2wheel

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign

/**
 * Maps the phone's angle to the steering axis (-1..1). Pure logic, no Android, so it can be unit
 * tested. Sensor thread only (keeps state for the return assist).
 *
 *  - wheelRangeDeg: lock-to-lock range of the virtual wheel (270 = ±135° at full lock).
 *  - sensitivity:   phone rotation multiplier. 1.5 with 270° means ±90° of phone = full lock.
 *  - returnAssist:  0..1. While unwinding towards center the wheel returns faster than the phone
 *                   (output follows a convex curve down to zero), like a real wheel's self-aligning.
 *                   Turning back in catches up to the normal mapping at a steeper slope, so there
 *                   is never a jump and never a lasting offset.
 *  - centerAssist:  0..1. Soft detent around straight ahead: output is pulled towards zero within a
 *                   few degrees of center, monotonic and continuous.
 */
class SteeringCurve {
    private var sign = 0f
    private var mode = LINEAR
    private var ext = 0f          // furthest |n| in the current direction (hysteresis)
    private var anchorN = 0f      // RETURN: where unwinding started
    private var anchorOut = 0f
    private var fromN = 0f        // CATCHUP: where turning back in started
    private var fromOut = 0f
    private var lastX = 0f        // previous |n| and output, so mode switches start exactly there
    private var lastOut = 0f

    fun reset() {
        sign = 0f
        mode = LINEAR
        ext = 0f
    }

    fun map(
        angleDeg: Float,
        wheelRangeDeg: Float,
        sensitivity: Float,
        returnAssist: Float,
        centerAssist: Float,
        deadzone: Float,
    ): Float {
        val half = wheelRangeDeg / 2f
        var n = (angleDeg * sensitivity / half).coerceIn(-1f, 1f)

        n = applyReturn(n, returnAssist)

        if (centerAssist > 0f) {
            // Width: ~5° of phone rotation, expressed in axis units.
            val w = (CENTER_WIDTH_DEG * sensitivity / half).coerceAtLeast(1e-3f)
            val q = n / w
            n -= CENTER_MAX * centerAssist * n * exp(-q * q)
        }

        if (deadzone > 0f) {
            n = if (abs(n) <= deadzone) 0f else sign(n) * (abs(n) - deadzone) / (1f - deadzone)
        }
        return n.coerceIn(-1f, 1f)
    }

    private fun applyReturn(n: Float, strength: Float): Float {
        if (strength <= 0f) {
            reset()
            return n
        }
        val p = 1f + 2f * strength
        val s = sign(n)
        val x = abs(n)
        if (s != sign) {
            // Crossed center: every mode outputs ~0 there, so restarting linear is seamless.
            sign = s
            mode = LINEAR
            ext = x
            lastX = x; lastOut = x
            return n
        }

        when (mode) {
            LINEAR -> if (x >= ext) {
                ext = x
            } else if (x < ext - EPS) {
                anchorN = lastX; anchorOut = lastOut
                mode = RETURN; ext = x
            }
            RETURN -> if (x <= ext) {
                ext = x
            } else if (x > ext + EPS) {
                fromN = lastX; fromOut = lastOut
                mode = CATCHUP; ext = x
            }
            CATCHUP -> if (x >= ext) {
                ext = x
            } else if (x < ext - EPS) {
                anchorN = lastX; anchorOut = lastOut
                mode = RETURN; ext = x
            }
        }

        val out = when (mode) {
            RETURN -> returnCurve(x, p)
            CATCHUP -> {
                val v = catchup(x, p)
                if (v >= x) { mode = LINEAR; x } else v
            }
            else -> x
        }
        lastX = x
        lastOut = out.coerceIn(0f, x)
        return s * lastOut
    }

    private fun returnCurve(x: Float, p: Float): Float =
        if (anchorN <= 0f) 0f else anchorOut * (x / anchorN).coerceIn(0f, 1f).pow(p)

    private fun catchup(x: Float, p: Float): Float = fromOut + (x - fromN) * p

    private companion object {
        const val LINEAR = 0
        const val RETURN = 1
        const val CATCHUP = 2
        const val EPS = 0.004f          // ~0.25° of phone rotation: ignores sensor noise
        const val CENTER_WIDTH_DEG = 5f
        const val CENTER_MAX = 0.9f     // keeps the curve strictly monotonic
    }
}
