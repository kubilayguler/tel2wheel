package com.tel2wheel

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.view.Surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Turns device orientation into a steering value. The angle comes from where "world up" points
 * in the screen plane, so tilting the phone towards or away from you does not steer.
 *
 * Prefers the gyro-fused GAME_ROTATION_VECTOR (fast, no magnetometer jumps), and falls back to
 * gravity or the raw accelerometer on phones without a gyroscope.
 */
class SteeringSensor(
    private val sensorManager: SensorManager,
    private val settings: Settings,
    private val onSteer: (Short) -> Unit,
) : SensorEventListener {

    val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    /** Display rotation (Surface.ROTATION_*), set by the activity. */
    @Volatile var displayRotation: Int = Surface.ROTATION_90

    /** Latest uncalibrated angle in radians, for the "center" button. */
    @Volatile var rawAngle: Float = 0f; private set

    /** Latest output in -1..1, for the HUD. */
    @Volatile var output: Float = 0f; private set

    /**
     * Steering lock: while set, the output stays frozen. On release the zero point is moved so the
     * phone's current position maps to the frozen value: lock, reposition the phone, unlock, and
     * steering carries on from where it was without a jump.
     */
    @Volatile var locked = false
    private var wasLocked = false
    private var frozen = 0f

    private val rot = FloatArray(9)
    private val filter = OneEuro()
    private val curve = SteeringCurve()

    fun register(handler: Handler): Boolean {
        val s = sensor ?: return false
        return sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, handler)
    }

    fun unregister() = sensorManager.unregisterListener(this)

    fun calibrate() {
        settings.zeroAngle = rawAngle
        filter.reset()
        curve.reset()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val ux: Float
        val uy: Float
        if (e.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rot, e.values)
            // Third row = world Z axis expressed in device coordinates.
            ux = rot[6]
            uy = rot[7]
        } else {
            // At rest the accelerometer/gravity vector points up in device coordinates.
            ux = e.values[0]
            uy = e.values[1]
        }

        // Steering angle: positive when the wheel is turned clockwise (to the right).
        val raw = when (displayRotation) {
            Surface.ROTATION_90 -> atan2(uy, ux)
            Surface.ROTATION_270 -> atan2(-uy, -ux)
            Surface.ROTATION_180 -> atan2(ux, -uy)
            else -> atan2(-ux, uy)
        }
        rawAngle = raw

        if (locked) {
            if (!wasLocked) { wasLocked = true; frozen = output }
            onSteer((frozen * 32767f).toInt().toShort())
            return
        }
        if (wasLocked) {
            wasLocked = false
            regrip(raw, frozen)
        }

        var a = wrap(raw - settings.zeroAngle)
        if (settings.smoothing) a = filter.filter(a, e.timestamp) else filter.reset()

        if (settings.invert) a = -a
        val n = curve.map(
            Math.toDegrees(a.toDouble()).toFloat(),
            settings.wheelRangeDeg,
            settings.sensitivity,
            settings.returnAssist,
            settings.centerAssist,
            settings.deadzone,
        )
        output = n

        onSteer((n * 32767f).toInt().toShort())
    }

    /** Picks the zero angle that maps [raw] to the steering output [n] (linear part of the curve). */
    private fun regrip(raw: Float, n: Float) {
        val deg = n * settings.wheelRangeDeg / 2f / settings.sensitivity
        var a = Math.toRadians(deg.toDouble()).toFloat()
        if (settings.invert) a = -a
        settings.zeroAngle = wrap(raw - a)
        filter.reset()
        curve.reset()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun wrap(a: Float): Float {
        var x = a
        while (x > PI) x -= (2 * PI).toFloat()
        while (x < -PI) x += (2 * PI).toFloat()
        return x
    }

    /**
     * One Euro filter (Casiez et al.): heavy smoothing when still, almost none when moving fast,
     * so jitter goes away without adding noticeable lag during quick steering. Only used when
     * the user turns smoothing on.
     */
    private class OneEuro(
        private val minCutoff: Float = 1.5f,
        private val beta: Float = 0.6f,
        private val dCutoff: Float = 1.0f,
    ) {
        private var lastT = 0L
        private var x = 0f
        private var dx = 0f

        fun reset() { lastT = 0L }

        fun filter(value: Float, tNanos: Long): Float {
            if (lastT == 0L) {
                lastT = tNanos; x = value; dx = 0f
                return value
            }
            val dt = ((tNanos - lastT) / 1e9f).coerceIn(1e-4f, 0.1f)
            lastT = tNanos
            val rawDx = (value - x) / dt
            dx += alpha(dCutoff, dt) * (rawDx - dx)
            val cutoff = minCutoff + beta * abs(dx)
            x += alpha(cutoff, dt) * (value - x)
            return x
        }

        private fun alpha(cutoff: Float, dt: Float): Float {
            val tau = 1f / (2f * PI.toFloat() * cutoff)
            return 1f / (1f + tau / dt)
        }
    }
}
