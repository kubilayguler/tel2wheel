package com.tel2wheel

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Settings dialog built in code: the section for [mode] ([Settings.MODE_TILT] or
 * [Settings.MODE_TOUCH]) plus the shared pedal/connection section; with a null [mode], only the
 * shared section. [onSaved] receives true when the server address changed. With [onEditLayout],
 * the dialog also offers to rearrange the on-screen controls.
 */
fun showSettingsDialog(
    activity: Activity,
    settings: Settings,
    mode: Int?,
    onEditLayout: (() -> Unit)? = null,
    onSaved: (hostChanged: Boolean) -> Unit,
) {
    val d = activity.resources.displayMetrics.density
    val col = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        val p = (20 * d).toInt()
        setPadding(p, p / 2, p, 0)
    }

    val refreshers = mutableListOf<() -> Unit>()
    val savers = mutableListOf<() -> Unit>()

    fun header(label: String) {
        col.addView(TextView(activity).apply {
            text = label
            setTypeface(typeface, Typeface.BOLD)
            textSize = 16f
            setPadding(0, (12 * d).toInt(), 0, (4 * d).toInt())
        })
    }

    /** Integer slider over [min]..[max]; every label is refreshed on any change so they can depend on each other. */
    fun slider(min: Int, max: Int, value: Int, label: () -> String): SeekBar {
        val title = TextView(activity)
        val bar = SeekBar(activity).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) = refreshers.forEach { it() }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        refreshers += { title.text = label() }
        col.addView(title)
        col.addView(bar)
        return bar
    }

    fun checkBox(label: String, checked: Boolean) = CheckBox(activity).apply {
        text = label
        isChecked = checked
        col.addView(this)
    }

    if (mode == Settings.MODE_TILT) {
        header("Eğim direksiyonu")
        lateinit var range: SeekBar
        lateinit var sens: SeekBar
        lateinit var center: SeekBar
        lateinit var ret: SeekBar
        lateinit var dead: SeekBar
        fun rangeDeg() = (range.progress + 9) * 10           // 90..1080, step 10
        fun sensitivity() = (sens.progress + 10) * 0.05f      // 0.50..3.00, step 0.05

        range = slider(9, 108, (settings.wheelRangeDeg / 10).roundToInt()) {
            "Direksiyon açısı (kilitten kilide): ${rangeDeg()}°"
        }
        sens = slider(10, 60, (settings.sensitivity / 0.05f).roundToInt()) {
            val phone = rangeDeg() / 2f / sensitivity()
            "Hassasiyet: %.2fx  (telefonu ±%.0f° çevirince tam kilit)".format(sensitivity(), phone)
        }
        center = slider(0, 100, (settings.centerAssist * 100).roundToInt()) { "Merkez yardımı: %${center.progress}" }
        ret = slider(0, 100, (settings.returnAssist * 100).roundToInt()) { "Toparlama: %${ret.progress}" }
        dead = slider(0, 10, (settings.deadzone * 100).roundToInt()) { "Ölü bölge: %${dead.progress}" }
        val invert = checkBox("Yönü ters çevir", settings.invert)
        val smooth = checkBox("Titreme filtresi (çok az gecikme ekler)", settings.smoothing)

        savers += {
            settings.wheelRangeDeg = rangeDeg().toFloat()
            settings.sensitivity = sensitivity()
            settings.centerAssist = center.progress / 100f
            settings.returnAssist = ret.progress / 100f
            settings.deadzone = dead.progress / 100f
            settings.invert = invert.isChecked
            settings.smoothing = smooth.isChecked
        }
    }

    if (mode == Settings.MODE_TOUCH) {
        header("Dokunmatik direksiyon")
        lateinit var range: SeekBar
        lateinit var sens: SeekBar
        lateinit var ret: SeekBar
        fun rangeDeg() = (range.progress + 18) * 10          // 180..1440, step 10
        fun sensitivity() = (sens.progress + 10) * 0.05f      // 0.50..4.00, step 0.05
        fun returnSpeed() = ret.progress * 60                 // 0..2400 °/s, step 60

        range = slider(18, 144, (settings.touchRangeDeg / 10).roundToInt()) {
            "Direksiyon açısı (kilitten kilide): ${rangeDeg()}°"
        }
        sens = slider(10, 80, (settings.touchSensitivity / 0.05f).roundToInt()) {
            val fingerDeg = rangeDeg() / 2f / sensitivity()
            "Hassasiyet: %.2fx  (parmağını %.0f° çevirince tam kilit)".format(sensitivity(), fingerDeg)
        }
        ret = slider(0, 40, (settings.touchReturnSpeed / 60f).roundToInt()) {
            val s = returnSpeed()
            if (s == 0) "Ortaya dönüş: kapalı (bırakılan açıda kalır)"
            else "Ortaya dönüş hızı: $s°/sn  (tam kilitten %.1f sn)".format(rangeDeg() / 2f / s)
        }

        savers += {
            settings.touchRangeDeg = rangeDeg().toFloat()
            settings.touchSensitivity = sensitivity()
            settings.touchReturnSpeed = returnSpeed().toFloat()
        }
    }

    header("Pedallar ve bağlantı")
    val digital = checkBox("Dijital pedallar (dokununca %100)", settings.digitalPedals)
    val host = EditText(activity).apply {
        hint = "PC IP adresi (boş = otomatik bul)"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        setText(settings.manualHost)
        isSingleLine = true
        col.addView(this)
    }
    refreshers.forEach { it() }

    val builder = AlertDialog.Builder(activity)
    if (onEditLayout != null) builder.setNeutralButton("Yerleşimi düzenle") { _, _ -> onEditLayout() }
    builder
        .setTitle("Ayarlar")
        .setView(ScrollView(activity).apply { addView(col) })
        .setPositiveButton("Kaydet") { _, _ ->
            savers.forEach { it() }
            settings.digitalPedals = digital.isChecked
            val newHost = host.text.toString().trim()
            val changed = newHost != settings.manualHost
            settings.manualHost = newHost
            onSaved(changed)
        }
        .setNegativeButton("İptal", null)
        .show()
}
