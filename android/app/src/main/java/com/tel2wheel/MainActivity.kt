package com.tel2wheel

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * One activity, two screens: the mode menu and the driving screen of the chosen mode. The UDP
 * link runs whenever the activity is resumed, so the menu already shows the connection status.
 */
class MainActivity : Activity() {
    private lateinit var settings: Settings
    private lateinit var link: UdpLink
    private lateinit var steering: SteeringSensor
    private lateinit var wifiLock: WifiManager.WifiLock
    private lateinit var multicastLock: WifiManager.MulticastLock

    private val ui = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density

    /** null = menu, otherwise the driving mode on screen. */
    private var driveMode: Int? = null
    private var driveView: DriveView? = null
    private var lockButton: Button? = null
    private var normalButtons: View? = null
    private var editButtons: View? = null
    private var locked = false

    private var menuStatus: TextView? = null
    private val statusTicker = object : Runnable {
        override fun run() {
            menuStatus?.let {
                it.text = statusText()
                it.setTextColor(if (link.connected) Color.rgb(120, 230, 140) else Color.rgb(255, 190, 80))
            }
            ui.removeCallbacks(this)
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        settings = Settings(this)
        link = UdpLink()
        // Runs on the link's sender thread: sensor event -> packet, no thread hop.
        steering = SteeringSensor(getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager, settings) { v ->
            link.steer = v
            link.sendInput()
        }

        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                   else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifiLock = wifi.createWifiLock(mode, "tel2wheel").apply { setReferenceCounted(false) }
        multicastLock = wifi.createMulticastLock("tel2wheel").apply { setReferenceCounted(false) }

        showMenu()
    }

    // ---------------------------------------------------------------- menu

    private fun showMenu() {
        steering.unregister()
        setLocked(false)
        driveMode = null
        driveView = null
        lockButton = null
        normalButtons = null
        editButtons = null
        link.steer = 0
        link.throttle = 0
        link.brake = 0
        link.requestSend()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (24 * density).toInt()
            setPadding(p, p / 2, p, p / 2)
        }
        col.addView(TextView(this).apply {
            text = "Tel2Wheel"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        menuStatus = TextView(this).apply {
            textSize = 14f
            setPadding(0, (4 * density).toInt(), 0, (16 * density).toInt())
        }
        col.addView(menuStatus)

        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cards.addView(modeCard(
            Settings.MODE_TILT,
            "EĞİM DİREKSİYONU",
            "Telefonu direksiyon gibi çevir.\nSolda fren, sağda gaz.\n${settings.wheelRangeDeg.toInt()}° sanal direksiyon.",
        ))
        cards.addView(modeCard(
            Settings.MODE_TOUCH,
            "DOKUNMATİK DİREKSİYON",
            "Dr. Driving tarzı: solda ekrandaki direksiyon,\nsağda fren ve gaz.\n${settings.touchRangeDeg.toInt()}° direksiyon.",
        ))
        col.addView(cards, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        col.addView(button("BAĞLANTI AYARLARI") {
            showSettingsDialog(this, settings, null) { hostChanged -> if (hostChanged) restartLink() }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (42 * density).toInt()).apply {
                topMargin = (16 * density).toInt()
            }
        })

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(18, 18, 20)) }
        root.addView(col)
        setContentView(root)
        statusTicker.run()
    }

    private fun modeCard(mode: Int, title: String, description: String): View {
        val selected = settings.mode == mode
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val p = (20 * density).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.rgb(36, 36, 42))
                setStroke(((if (selected) 3 else 1) * density).toInt().coerceAtLeast(1),
                    if (selected) Color.rgb(255, 170, 0) else Color.rgb(70, 70, 80))
            }
            addView(TextView(context).apply {
                text = title
                textSize = 20f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = description
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(170, 170, 180))
                setPadding(0, (10 * density).toInt(), 0, 0)
            })
            isClickable = true
            setOnClickListener { startDrive(mode) }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                val m = (8 * density).toInt()
                setMargins(m, 0, m, 0)
            }
        }
    }

    private fun statusText(): String {
        val target = link.target
        return when {
            target == null -> "PC aranıyor…"
            link.connected -> "Bağlı: ${link.serverName.ifEmpty { target.hostAddress }}  •  %.1f ms".format(link.rttAvgMs)
            else -> "${target.hostAddress} yanıt vermiyor"
        }
    }

    // ---------------------------------------------------------------- driving

    private fun startDrive(mode: Int) {
        settings.mode = mode
        driveMode = mode
        menuStatus = null
        locked = false

        val view: DriveView = if (mode == Settings.MODE_TILT) {
            if (steering.sensor == null) Toast.makeText(this, "Bu telefonda uygun sensör yok", Toast.LENGTH_LONG).show()
            steering.locked = false
            steering.displayRotation = currentRotation()
            if (link.isRunning) steering.register(link.handler)
            TiltDriveView(this, settings, link, steering)
        } else {
            link.steer = 0
            TouchDriveView(this, settings, link)
        }
        driveView = view

        val lock = button("KİLİT") { setLocked(!locked) }
        lockButton = lock
        val settingsButton = button("AYAR") {
            showSettingsDialog(this, settings, mode, onEditLayout = { setEditing(true) }) { hostChanged ->
                if (hostChanged) restartLink()
            }
        }
        val menuButton = button("MENÜ") { showMenu() }

        val root = FrameLayout(this)
        root.addView(view)
        val lp: FrameLayout.LayoutParams
        val buttons: View
        if (mode == Settings.MODE_TILT) {
            val center = button("ORTALA") {
                steering.calibrate()
                Toast.makeText(this, "Orta nokta ayarlandı", Toast.LENGTH_SHORT).show()
            }
            // 2x2 grid so it fits in the middle third between the pedals.
            buttons = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(row(center, lock))
                addView(row(settingsButton, menuButton))
            }
            lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        } else {
            // Above the wheel, clear of the pedals on the right.
            buttons = row(lock, settingsButton, menuButton)
            lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START).apply { leftMargin = (12 * density).toInt() }
        }
        lp.topMargin = (8 * density).toInt()
        root.addView(buttons, lp)
        normalButtons = buttons

        val edit = row(
            button("KAYDET") { setEditing(false, save = true) },
            button("VARSAYILAN") { driveView?.resetLayout() },
            button("İPTAL") { setEditing(false, save = false) },
        ).apply {
            visibility = View.GONE
            getChildAt(0).setBackgroundColor(Color.rgb(40, 140, 70))
        }
        root.addView(edit, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = (8 * density).toInt() })
        editButtons = edit

        setContentView(root)
        updateLockButton()
    }

    /** Layout edit mode: drag/resize the controls; KAYDET keeps the new layout, İPTAL restores the old one. */
    private fun setEditing(on: Boolean, save: Boolean = false) {
        val view = driveView ?: return
        if (on) {
            view.startEditing()
            Toast.makeText(this, "Kontrolleri sürükleyerek taşı, turuncu noktayla ya da iki parmakla boyutlandır", Toast.LENGTH_LONG).show()
        } else {
            view.finishEditing(save)
        }
        normalButtons?.visibility = if (on) View.GONE else View.VISIBLE
        editButtons?.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun setLocked(v: Boolean) {
        locked = v
        steering.locked = v
        driveView?.locked = v
        updateLockButton()
    }

    private fun updateLockButton() {
        lockButton?.apply {
            text = if (locked) "KİLİDİ AÇ" else "KİLİT"
            setBackgroundColor(if (locked) Color.rgb(200, 120, 0) else Color.rgb(50, 50, 60))
        }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it) }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        minWidth = 0
        minimumWidth = (84 * density).toInt()
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.rgb(50, 50, 60))
        val p = (12 * density).toInt()
        setPadding(p, 0, p, 0)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, (42 * density).toInt(),
        ).apply { val m = (3 * density).toInt(); setMargins(m, m, m, m) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (driveView?.editing == true) setEditing(false, save = false)
        else if (driveMode != null) showMenu()
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isSustainedPerformanceModeSupported) window.setSustainedPerformanceMode(true)
        wifiLock.acquire()
        multicastLock.acquire()
        startLink()
        statusTicker.run()
    }

    override fun onPause() {
        ui.removeCallbacks(statusTicker)
        stopLink()
        if (wifiLock.isHeld) wifiLock.release()
        if (multicastLock.isHeld) multicastLock.release()
        super.onPause()
    }

    private fun startLink() {
        steering.displayRotation = currentRotation()
        link.start(settings.manualHost)
        if (driveMode == Settings.MODE_TILT) steering.register(link.handler)
    }

    private fun stopLink() {
        steering.unregister()
        link.stop()
    }

    private fun restartLink() {
        stopLink()
        startLink()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        hideSystemUi()
        // Always landscape, never portrait, and never flipping mid-drive. Keep the reverse side
        // only if the system already put us there.
        val wanted = if (currentRotation() == Surface.ROTATION_270) ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                     else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        steering.displayRotation = currentRotation()
    }

    @Suppress("DEPRECATION")
    private fun currentRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation ?: 1
        else windowManager.defaultDisplay.rotation

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }
}
