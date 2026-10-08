package com.tel2wheel

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * Base for both driving screens: multitouch routing, the two analog pedals, the status line and
 * the layout editor. Subclasses provide the default layout and draw/handle their steering control.
 *
 * Pedals are analog by finger height: 0% at the bottom of the pedal, 100% from its vertical middle
 * upwards (for a full-height pedal, the middle of the screen), or 100% on any touch in digital mode.
 */
@SuppressLint("ViewConstructor")
abstract class DriveView(
    context: Context,
    protected val settings: Settings,
    protected val link: UdpLink,
    private val mode: Int,
) : View(context) {

    var locked = false
        set(v) { field = v; onLockChanged(v); invalidate() }

    protected val density = resources.displayMetrics.density
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
    }
    protected val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 16 * density
    }
    protected val bigText = Paint(text).apply { textSize = 28 * density; isFakeBoldText = true }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        color = Color.rgb(255, 170, 0)
        pathEffect = DashPathEffect(floatArrayOf(10 * density, 8 * density), 0f)
    }
    private val rect = RectF()

    protected val brakeBox = Box()
    protected val throttleBox = Box()
    /** The on-screen wheel, if this mode has one; editable like the pedals. */
    protected open val wheelShape: Circle? = null

    private val zoneOfPointer = IntArray(MAX_POINTERS) { ZONE_NONE }
    private val valueOfPointer = IntArray(MAX_POINTERS)
    private var throttle = 0
    private var brake = 0

    private var lastRateAt = 0L
    private var lastSent = 0L
    private var rate = 0

    // Layout editing.
    private var editor: LayoutEditor? = null
    private val savedBrake = Box()
    private val savedThrottle = Box()
    private val savedWheel = Circle()
    val editing: Boolean get() = editor != null

    init {
        setBackgroundColor(Color.rgb(18, 18, 20))
    }

    /** Sets [brakeBox], [throttleBox] (and the wheel, if any) to the default layout for this size. */
    protected abstract fun defaultLayout(w: Float, h: Float)

    protected abstract fun drawSteering(canvas: Canvas)

    /** A finger landed outside the pedals. Return true to keep receiving its moves. */
    protected open fun steeringDown(id: Int, x: Float, y: Float): Boolean = false
    protected open fun steeringMove(id: Int, x: Float, y: Float) {}
    protected open fun steeringUp(id: Int) {}
    protected open fun onLockChanged(locked: Boolean) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fw = w.toFloat()
        val fh = h.toFloat()
        defaultLayout(fw, fh)
        ControlLayout.decode(settings.layoutFor(mode), fw, fh, brakeBox, throttleBox, wheelShape)
    }

    // ---------------------------------------------------------------- layout editing

    fun startEditing() {
        if (editing || width == 0) return
        releaseAll()
        savedBrake.set(brakeBox)
        savedThrottle.set(throttleBox)
        wheelShape?.let { savedWheel.set(it) }
        editor = LayoutEditor(
            brakeBox, throttleBox, wheelShape, width.toFloat(), height.toFloat(),
            minBoxW = 70 * density, minBoxH = 140 * density, minRadius = 50 * density,
            handleRadius = 30 * density,
        )
        invalidate()
    }

    /** Leaves edit mode, keeping (and saving) the new layout or restoring the previous one. */
    fun finishEditing(save: Boolean) {
        if (!editing) return
        editor = null
        if (save) {
            settings.setLayout(mode, ControlLayout.encode(width.toFloat(), height.toFloat(), brakeBox, throttleBox, wheelShape))
        } else {
            brakeBox.set(savedBrake)
            throttleBox.set(savedThrottle)
            wheelShape?.set(savedWheel)
        }
        invalidate()
    }

    /** Puts the controls back to the default layout (still in edit mode; KAYDET makes it stick). */
    fun resetLayout() {
        editor?.cancel()
        defaultLayout(width.toFloat(), height.toFloat())
        invalidate()
    }

    private fun releaseAll() {
        for (id in 0 until MAX_POINTERS) release(id)
        throttle = 0
        brake = 0
        link.throttle = 0
        link.brake = 0
        link.requestSend()
    }

    // ---------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        editor?.let { ed ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                    ed.down(e.getPointerId(e.actionIndex), e.getX(e.actionIndex), e.getY(e.actionIndex))
                MotionEvent.ACTION_MOVE ->
                    for (i in 0 until e.pointerCount) ed.move(e.getPointerId(i), e.getX(i), e.getY(i))
                MotionEvent.ACTION_POINTER_UP -> ed.up(e.getPointerId(e.actionIndex))
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> ed.cancel()
            }
            invalidate()
            return true
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (id < MAX_POINTERS) {
                    val x = e.getX(i)
                    val y = e.getY(i)
                    zoneOfPointer[id] = when {
                        brakeBox.contains(x, y) -> ZONE_BRAKE
                        throttleBox.contains(x, y) -> ZONE_THROTTLE
                        steeringDown(id, x, y) -> ZONE_STEERING
                        else -> ZONE_NONE
                    }
                    when (zoneOfPointer[id]) {
                        ZONE_BRAKE -> valueOfPointer[id] = pedalValue(brakeBox, y)
                        ZONE_THROTTLE -> valueOfPointer[id] = pedalValue(throttleBox, y)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id >= MAX_POINTERS) continue
                    when (zoneOfPointer[id]) {
                        ZONE_BRAKE -> valueOfPointer[id] = pedalValue(brakeBox, e.getY(i))
                        ZONE_THROTTLE -> valueOfPointer[id] = pedalValue(throttleBox, e.getY(i))
                        ZONE_STEERING -> steeringMove(id, e.getX(i), e.getY(i))
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val id = e.getPointerId(e.actionIndex)
                if (id < MAX_POINTERS) release(id)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                for (id in 0 until MAX_POINTERS) release(id)
            }
        }

        var t = 0
        var b = 0
        for (id in 0 until MAX_POINTERS) {
            when (zoneOfPointer[id]) {
                ZONE_THROTTLE -> t = max(t, valueOfPointer[id])
                ZONE_BRAKE -> b = max(b, valueOfPointer[id])
            }
        }
        if (t != throttle || b != brake) {
            throttle = t
            brake = b
            link.throttle = t
            link.brake = b
            link.requestSend()
        }
        invalidate()
        return true
    }

    private fun release(id: Int) {
        if (zoneOfPointer[id] == ZONE_STEERING) steeringUp(id)
        zoneOfPointer[id] = ZONE_NONE
    }

    private fun pedalValue(box: Box, y: Float): Int {
        if (settings.digitalPedals) return 255
        val bottom = box.b - box.h * 0.04f
        val full = box.cy
        return (((bottom - y) / (bottom - full)).coerceIn(0f, 1f) * 255f).toInt()
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        drawSteering(canvas)
        drawPedal(canvas, brakeBox, brake, Color.rgb(220, 60, 60), "FREN")
        drawPedal(canvas, throttleBox, throttle, Color.rgb(60, 200, 90), "GAZ")
        editor?.let { drawEditOverlay(canvas, it) }
        postInvalidateDelayed(33)
    }

    private fun drawPedal(c: Canvas, box: Box, value: Int, color: Int, label: String) {
        val radius = 16 * density
        rect.set(box.l, box.t, box.r, box.b)
        fill.color = Color.rgb(36, 36, 42)
        c.drawRoundRect(rect, radius, radius, fill)
        if (value > 0) {
            // Fill tracks the finger: whole pedal once past the 100% line in its middle.
            val top = if (value >= 255) box.t else box.b - (box.b - box.cy) * value / 255f
            rect.set(box.l, top, box.r, box.b)
            fill.color = color
            c.drawRoundRect(rect, radius, radius, fill)
        }
        // 100% line.
        stroke.color = Color.argb(90, 255, 255, 255)
        stroke.strokeWidth = 1.5f * density
        c.drawLine(box.l + radius, box.cy, box.r - radius, box.cy, stroke)
        stroke.strokeWidth = 3 * density
        c.drawText(label, box.cx, box.t + 32 * density, bigText)
        c.drawText("${value * 100 / 255}%", box.cx, box.t + 58 * density, text)
    }

    private fun drawEditOverlay(c: Canvas, ed: LayoutEditor) {
        fill.color = Color.argb(70, 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        val targets = if (wheelShape != null) intArrayOf(LayoutEditor.WHEEL, LayoutEditor.BRAKE, LayoutEditor.THROTTLE)
                      else intArrayOf(LayoutEditor.BRAKE, LayoutEditor.THROTTLE)
        for (t in targets) {
            dashed.strokeWidth = (if (ed.active == t) 3.5f else 2f) * density
            when (t) {
                LayoutEditor.WHEEL -> wheelShape?.let { c.drawCircle(it.cx, it.cy, it.r, dashed) }
                else -> {
                    val b = if (t == LayoutEditor.BRAKE) brakeBox else throttleBox
                    rect.set(b.l, b.t, b.r, b.b)
                    c.drawRect(rect, dashed)
                }
            }
            fill.color = Color.rgb(255, 170, 0)
            c.drawCircle(ed.handleX(t), ed.handleY(t), 12 * density, fill)
            fill.color = Color.rgb(18, 18, 20)
            c.drawCircle(ed.handleX(t), ed.handleY(t), 5 * density, fill)
        }

        text.color = Color.rgb(255, 200, 120)
        c.drawText("Sürükle: taşı   •   Turuncu nokta ya da iki parmak: boyutlandır",
            width / 2f, height - 16 * density, text)
        text.color = Color.WHITE
    }

    /** Connection status and packet rate, centered at [cx], bottom line at [bottom]. */
    protected fun drawStatus(canvas: Canvas, cx: Float, bottom: Float) {
        if (editing) return
        val now = System.currentTimeMillis()
        if (now - lastRateAt >= 1000) {
            val sent = link.sentCount
            rate = if (lastRateAt == 0L) 0 else ((sent - lastSent) * 1000 / (now - lastRateAt)).toInt()
            lastSent = sent
            lastRateAt = now
        }
        val target = link.target
        val status = when {
            target == null -> "PC aranıyor…"
            link.connected -> "${link.serverName.ifEmpty { target.hostAddress }}  •  RTT en az %.1f / ort %.1f / en çok %.0f ms"
                .format(link.rttMinMs, link.rttAvgMs, link.rttMaxMs)
            else -> "${target.hostAddress} yanıt vermiyor"
        }
        text.color = if (link.connected) Color.rgb(120, 230, 140) else Color.rgb(255, 190, 80)
        canvas.drawText(status, cx, bottom - 24 * density, text)
        text.color = Color.rgb(160, 160, 170)
        canvas.drawText("$rate paket/sn", cx, bottom, text)
        text.color = Color.WHITE
    }

    /** "Locked" badge under the steering read-out. */
    protected fun drawLockBadge(canvas: Canvas, cx: Float, y: Float) {
        if (!locked) return
        text.color = Color.rgb(255, 170, 0)
        canvas.drawText("DİREKSİYON KİLİTLİ", cx, y, text)
        text.color = Color.WHITE
    }

    private companion object {
        const val MAX_POINTERS = 16
        const val ZONE_NONE = 0
        const val ZONE_BRAKE = 1
        const val ZONE_THROTTLE = 2
        const val ZONE_STEERING = 3
    }
}
