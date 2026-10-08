package com.tel2wheel

import android.content.Context

/**
 * User settings. Values are cached in volatile fields because the sensor thread reads them on
 * every event; setters also persist to SharedPreferences.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("tel2wheel", Context.MODE_PRIVATE)

    /** Last used driving mode, [MODE_TILT] or [MODE_TOUCH]. */
    @Volatile var mode: Int = prefs.getInt(K_MODE, MODE_TILT)
        set(v) { field = v; prefs.edit().putInt(K_MODE, v).apply() }

    /** Saved control layout per mode (see [ControlLayout]); empty = default layout. */
    fun layoutFor(mode: Int): String = prefs.getString(layoutKey(mode), "") ?: ""

    fun setLayout(mode: Int, value: String) {
        prefs.edit().putString(layoutKey(mode), value).apply()
    }

    private fun layoutKey(mode: Int) = if (mode == MODE_TOUCH) K_TOUCH_LAYOUT else K_TILT_LAYOUT

    // --- Touch wheel mode (Dr. Driving style) ---

    /** Lock-to-lock range of the on-screen wheel. */
    @Volatile var touchRangeDeg: Float = prefs.getFloat(K_TOUCH_RANGE, 900f)
        set(v) { field = v; prefs.edit().putFloat(K_TOUCH_RANGE, v).apply() }

    /** Wheel degrees per degree of finger rotation. */
    @Volatile var touchSensitivity: Float = prefs.getFloat(K_TOUCH_SENS, 1.5f)
        set(v) { field = v; prefs.edit().putFloat(K_TOUCH_SENS, v).apply() }

    /** Return-to-center speed after letting go, wheel degrees per second. 0 = stays put. */
    @Volatile var touchReturnSpeed: Float = prefs.getFloat(K_TOUCH_RETURN, 720f)
        set(v) { field = v; prefs.edit().putFloat(K_TOUCH_RETURN, v).apply() }

    // --- Tilt mode ---

    /** Lock-to-lock range of the virtual wheel. */
    @Volatile var wheelRangeDeg: Float = prefs.getFloat(K_RANGE, 270f)
        set(v) { field = v; prefs.edit().putFloat(K_RANGE, v).apply() }

    /** Phone rotation multiplier: full lock at (wheelRangeDeg / 2) / sensitivity of phone rotation. */
    @Volatile var sensitivity: Float = prefs.getFloat(K_SENSITIVITY, 1.5f)
        set(v) { field = v; prefs.edit().putFloat(K_SENSITIVITY, v).apply() }

    /** 0..1, see [SteeringCurve]. */
    @Volatile var centerAssist: Float = prefs.getFloat(K_CENTER, 0.3f)
        set(v) { field = v; prefs.edit().putFloat(K_CENTER, v).apply() }

    /** 0..1, see [SteeringCurve]. */
    @Volatile var returnAssist: Float = prefs.getFloat(K_RETURN, 0.25f)
        set(v) { field = v; prefs.edit().putFloat(K_RETURN, v).apply() }

    @Volatile var deadzone: Float = prefs.getFloat(K_DEADZONE, 0f)
        set(v) { field = v; prefs.edit().putFloat(K_DEADZONE, v).apply() }

    @Volatile var invert: Boolean = prefs.getBoolean(K_INVERT, false)
        set(v) { field = v; prefs.edit().putBoolean(K_INVERT, v).apply() }

    @Volatile var smoothing: Boolean = prefs.getBoolean(K_SMOOTHING, false)
        set(v) { field = v; prefs.edit().putBoolean(K_SMOOTHING, v).apply() }

    @Volatile var digitalPedals: Boolean = prefs.getBoolean(K_DIGITAL, false)
        set(v) { field = v; prefs.edit().putBoolean(K_DIGITAL, v).apply() }

    /** Empty means auto-discovery via the PC's beacon. */
    @Volatile var manualHost: String = prefs.getString(K_HOST, "") ?: ""
        set(v) { field = v; prefs.edit().putString(K_HOST, v).apply() }

    /** Raw sensor angle (radians) that counts as straight ahead. */
    @Volatile var zeroAngle: Float = prefs.getFloat(K_ZERO, 0f)
        set(v) { field = v; prefs.edit().putFloat(K_ZERO, v).apply() }

    companion object {
        const val MODE_TILT = 0
        const val MODE_TOUCH = 1

        private const val K_MODE = "mode"
        private const val K_TILT_LAYOUT = "tiltLayout"
        private const val K_TOUCH_LAYOUT = "touchLayout"
        private const val K_TOUCH_RANGE = "touchRangeDeg"
        private const val K_TOUCH_SENS = "touchSensitivity"
        private const val K_TOUCH_RETURN = "touchReturnSpeed"
        private const val K_RANGE = "wheelRangeDeg"
        private const val K_SENSITIVITY = "sensitivity"
        private const val K_CENTER = "centerAssist"
        private const val K_RETURN = "returnAssist"
        private const val K_DEADZONE = "deadzone"
        private const val K_INVERT = "invert"
        private const val K_SMOOTHING = "smoothing"
        private const val K_DIGITAL = "digitalPedals"
        private const val K_HOST = "manualHost"
        private const val K_ZERO = "zeroAngle"
    }
}
