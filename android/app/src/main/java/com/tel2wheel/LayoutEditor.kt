package com.tel2wheel

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Drag-and-resize editing of the on-screen controls, in pixels. Pure logic, UI thread only.
 *
 *  - One finger on a control moves it.
 *  - One finger on its handle (top-right corner of a pedal, top-right of the wheel rim) resizes it.
 *  - A second finger on the same control pinches it (uniform scale around its center).
 *
 * Everything is kept on screen and within minimum sizes.
 */
class LayoutEditor(
    private val brake: Box,
    private val throttle: Box,
    private val wheel: Circle?,
    private val width: Float,
    private val height: Float,
    private val minBoxW: Float,
    private val minBoxH: Float,
    private val minRadius: Float,
    private val handleRadius: Float,
) {
    private var target = NONE
    private var mode = IDLE
    private var p1 = -1
    private var p2 = -1
    private val px = FloatArray(MAX_POINTERS)
    private val py = FloatArray(MAX_POINTERS)
    private var grabDx = 0f
    private var grabDy = 0f

    // Pinch start state.
    private var startDist = 1f
    private var startMidX = 0f
    private var startMidY = 0f
    private var startCx = 0f
    private var startCy = 0f
    private var startW = 0f
    private var startH = 0f
    private var startR = 0f

    /** The control being edited, for highlighting: BRAKE, THROTTLE, WHEEL or NONE. */
    val active: Int get() = target

    fun handleX(t: Int): Float = when (t) {
        BRAKE -> brake.r
        THROTTLE -> throttle.r
        else -> wheel!!.cx + wheel.r * DIAG
    }

    fun handleY(t: Int): Float = when (t) {
        BRAKE -> brake.t
        THROTTLE -> throttle.t
        else -> wheel!!.cy - wheel.r * DIAG
    }

    /** Returns true if the finger landed on a control. */
    fun down(id: Int, x: Float, y: Float): Boolean {
        if (id !in 0 until MAX_POINTERS) return false
        px[id] = x; py[id] = y

        if (p1 == -1) {
            // Handles win over bodies; pedals are drawn above the wheel, so they win too.
            val order = if (wheel != null) intArrayOf(BRAKE, THROTTLE, WHEEL) else intArrayOf(BRAKE, THROTTLE)
            val handle = order.firstOrNull { hypot(x - handleX(it), y - handleY(it)) <= handleRadius }
            val body = order.firstOrNull { hits(it, x, y) }
            val t = handle ?: body ?: return false
            target = t
            mode = if (handle != null) RESIZE else MOVE
            p1 = id
            grab()
            return true
        }
        if (p2 == -1 && mode == MOVE && hits(target, x, y, slack = 1.5f)) {
            p2 = id
            mode = PINCH
            startDist = max(1f, dist())
            startMidX = (px[p1] + px[p2]) / 2f
            startMidY = (py[p1] + py[p2]) / 2f
            startCx = cx(target); startCy = cy(target)
            when (target) {
                WHEEL -> startR = wheel!!.r
                else -> { startW = box(target).w; startH = box(target).h }
            }
            return true
        }
        return false
    }

    fun move(id: Int, x: Float, y: Float) {
        if (id !in 0 until MAX_POINTERS) return
        px[id] = x; py[id] = y
        when (mode) {
            MOVE -> if (id == p1) moveTo(x - grabDx, y - grabDy)
            RESIZE -> if (id == p1) resizeTo(x, y)
            PINCH -> if (id == p1 || id == p2) pinch()
        }
    }

    fun up(id: Int) {
        when (id) {
            p2 -> {
                p2 = -1
                mode = MOVE
                grab()
            }
            p1 -> if (p2 != -1) {
                p1 = p2
                p2 = -1
                mode = MOVE
                grab()
            } else {
                p1 = -1
                mode = IDLE
                target = NONE
            }
        }
    }

    fun cancel() {
        p1 = -1; p2 = -1; mode = IDLE; target = NONE
    }

    private fun grab() {
        grabDx = px[p1] - cx(target)
        grabDy = py[p1] - cy(target)
    }

    private fun dist() = hypot(px[p1] - px[p2], py[p1] - py[p2])

    private fun hits(t: Int, x: Float, y: Float, slack: Float = 1f): Boolean = when (t) {
        BRAKE, THROTTLE -> {
            val b = box(t)
            val extra = (slack - 1f) * min(b.w, b.h) / 2f
            x in b.l - extra..b.r + extra && y in b.t - extra..b.b + extra
        }
        WHEEL -> wheel!!.contains(x, y, slack * 1.1f)
        else -> false
    }

    private fun box(t: Int) = if (t == BRAKE) brake else throttle
    private fun cx(t: Int) = if (t == WHEEL) wheel!!.cx else box(t).cx
    private fun cy(t: Int) = if (t == WHEEL) wheel!!.cy else box(t).cy

    private fun moveTo(cx: Float, cy: Float) {
        if (target == WHEEL) {
            val c = wheel!!
            c.cx = cx; c.cy = cy
            clampCircle(c)
        } else {
            val b = box(target)
            val hw = b.w / 2f
            val hh = b.h / 2f
            b.set(cx - hw, cy - hh, cx + hw, cy + hh)
            clampBox(b)
        }
    }

    /** Pedal: the top-right corner follows the finger, bottom-left stays. Wheel: radius = finger distance. */
    private fun resizeTo(x: Float, y: Float) {
        if (target == WHEEL) {
            val c = wheel!!
            c.r = (hypot(x - c.cx, y - c.cy)).coerceIn(minRadius, maxRadius())
            clampCircle(c)
        } else {
            val b = box(target)
            b.r = x.coerceIn(b.l + minBoxW, width)
            b.t = y.coerceIn(0f, b.b - minBoxH)
        }
    }

    private fun pinch() {
        if (p1 == -1 || p2 == -1) return
        val s = dist() / startDist
        val cx = startCx + (px[p1] + px[p2]) / 2f - startMidX
        val cy = startCy + (py[p1] + py[p2]) / 2f - startMidY
        if (target == WHEEL) {
            val c = wheel!!
            c.r = (startR * s).coerceIn(minRadius, maxRadius())
            c.cx = cx; c.cy = cy
            clampCircle(c)
        } else {
            val b = box(target)
            val w = (startW * s).coerceIn(minBoxW, width)
            val h = (startH * s).coerceIn(minBoxH, height)
            b.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            clampBox(b)
        }
    }

    private fun maxRadius() = min(width, height) / 2f

    private fun clampBox(b: Box) {
        val dx = when {
            b.l < 0f -> -b.l
            b.r > width -> width - b.r
            else -> 0f
        }
        val dy = when {
            b.t < 0f -> -b.t
            b.b > height -> height - b.b
            else -> 0f
        }
        b.set(b.l + dx, b.t + dy, b.r + dx, b.b + dy)
    }

    private fun clampCircle(c: Circle) {
        c.cx = c.cx.coerceIn(c.r, width - c.r)
        c.cy = c.cy.coerceIn(c.r, height - c.r)
    }

    companion object {
        const val NONE = 0
        const val BRAKE = 1
        const val THROTTLE = 2
        const val WHEEL = 3

        private const val IDLE = 0
        private const val MOVE = 1
        private const val RESIZE = 2
        private const val PINCH = 3
        private const val MAX_POINTERS = 16
        private const val DIAG = 0.7071f
    }
}
