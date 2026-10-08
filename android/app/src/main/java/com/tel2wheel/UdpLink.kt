package com.tel2wheel

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException

/**
 * UDP link to the PC. All sends happen on one high-priority thread ([handler]); the sensor
 * listener is registered on that same thread so a sensor event turns into a packet with no
 * thread hop. Packets and buffers are preallocated: nothing is allocated per send.
 */
class UdpLink {
    // Input state, written by the sensor (sender thread) and the touch handler (UI thread).
    @Volatile var steer: Short = 0
    @Volatile var throttle: Int = 0
    @Volatile var brake: Int = 0

    // Status for the HUD.
    @Volatile var target: InetAddress? = null; private set
    @Volatile var serverName: String = ""; private set
    // RTT over the last RTT_WINDOW pings (~2 s): min = what the link can do, max = spikes.
    @Volatile var rttMinMs: Float = -1f; private set
    @Volatile var rttAvgMs: Float = -1f; private set
    @Volatile var rttMaxMs: Float = -1f; private set
    @Volatile var lastPongAt: Long = 0; private set
    @Volatile var sentCount: Long = 0; private set

    val connected: Boolean get() = lastPongAt != 0L && SystemClock.elapsedRealtime() - lastPongAt < 1500

    lateinit var handler: Handler; private set

    private var thread: HandlerThread? = null
    private var socket: DatagramSocket? = null
    private var beaconSocket: DatagramSocket? = null
    @Volatile private var running = false
    val isRunning: Boolean get() = running
    @Volatile private var autoDiscover = true

    private val inBuf = ByteArray(INPUT_LEN)
    private val inPkt = DatagramPacket(inBuf, INPUT_LEN)
    private val pingBuf = ByteArray(PING_LEN)
    private val pingPkt = DatagramPacket(pingBuf, PING_LEN)
    private var seq = 0
    private val rttWindow = FloatArray(RTT_WINDOW)
    private var rttCount = 0
    private var lastSendAt = 0L

    private val sendRunnable = Runnable { sendInput() }

    private val tickRunnable = object : Runnable {
        private var ticks = 0
        override fun run() {
            if (!running) return
            // Keepalive in case the sensor goes quiet, so the PC watchdog never fires.
            if (SystemClock.elapsedRealtime() - lastSendAt > 40) sendInput()
            if (++ticks % 4 == 0) sendPing()
            handler.postDelayed(this, 50)
        }
    }

    fun start(manualHost: String) {
        if (running) return
        running = true

        val t = HandlerThread("udp-send", Process.THREAD_PRIORITY_URGENT_DISPLAY).also { it.start() }
        thread = t
        handler = Handler(t.looper)

        val s = DatagramSocket()
        // DSCP EF: Wi-Fi maps it to the WMM voice queue (highest priority, shortest backoff).
        try { s.trafficClass = 0xB8 } catch (_: SocketException) { }
        s.sendBufferSize = 8 * 1024
        socket = s

        inBuf[0] = MAGIC; inBuf[1] = TYPE_INPUT
        pingBuf[0] = MAGIC; pingBuf[1] = TYPE_PING

        autoDiscover = manualHost.isBlank()
        if (!autoDiscover) handler.post {
            try { setTargetOnSender(InetAddress.getByName(manualHost.trim()), DEFAULT_PORT, "") } catch (_: IOException) { }
        }

        Thread({ receiveLoop(s) }, "udp-recv").apply { isDaemon = true; start() }
        Thread({ beaconLoop() }, "udp-beacon").apply { isDaemon = true; start() }
        handler.post(tickRunnable)
    }

    fun stop() {
        if (!running) return
        running = false
        val h = handler
        val t = thread
        h.removeCallbacksAndMessages(null)
        // Release everything on the PC immediately instead of waiting for its watchdog.
        h.post {
            steer = 0; throttle = 0; brake = 0
            sendInput()
            socket?.close()
            socket = null
        }
        t?.quitSafely()
        thread = null
        beaconSocket?.close()
        beaconSocket = null
        target = null
        resetRtt()
    }

    /** From the UI thread: push current state now rather than at the next sensor event. */
    fun requestSend() {
        if (running) handler.post(sendRunnable)
    }

    /** Sender thread only. */
    fun sendInput() {
        val s = socket ?: return
        if (inPkt.address == null) return
        seq = (seq + 1) and 0xFFFF
        val st = steer.toInt()
        inBuf[2] = seq.toByte(); inBuf[3] = (seq shr 8).toByte()
        inBuf[4] = st.toByte(); inBuf[5] = (st shr 8).toByte()
        inBuf[6] = throttle.toByte()
        inBuf[7] = brake.toByte()
        try {
            s.send(inPkt)
            sentCount++
            lastSendAt = SystemClock.elapsedRealtime()
        } catch (_: IOException) { }
    }

    private fun sendPing() {
        val s = socket ?: return
        if (pingPkt.address == null) return
        writeLongLe(pingBuf, 2, System.nanoTime())
        try { s.send(pingPkt) } catch (_: IOException) { }
    }

    private fun setTargetOnSender(addr: InetAddress, port: Int, name: String) {
        inPkt.address = addr; inPkt.port = port
        pingPkt.address = addr; pingPkt.port = port
        target = addr
        serverName = name
        resetRtt()
    }

    private fun resetRtt() {
        lastPongAt = 0
        rttCount = 0
        rttMinMs = -1f; rttAvgMs = -1f; rttMaxMs = -1f
    }

    /** Receive thread only. */
    private fun recordRtt(rtt: Float) {
        rttWindow[rttCount % RTT_WINDOW] = rtt
        rttCount++
        val n = minOf(rttCount, RTT_WINDOW)
        var min = Float.MAX_VALUE; var max = 0f; var sum = 0f
        for (i in 0 until n) {
            val v = rttWindow[i]
            if (v < min) min = v
            if (v > max) max = v
            sum += v
        }
        rttMinMs = min; rttAvgMs = sum / n; rttMaxMs = max
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buf = ByteArray(64)
        val pkt = DatagramPacket(buf, buf.size)
        while (running && !s.isClosed) {
            try {
                pkt.length = buf.size
                s.receive(pkt)
            } catch (_: IOException) {
                continue
            }
            if (pkt.length >= PING_LEN && buf[0] == MAGIC && buf[1] == TYPE_PONG) {
                val rtt = (System.nanoTime() - readLongLe(buf, 2)) / 1_000_000f
                if (rtt < 0 || rtt > 5000) continue
                recordRtt(rtt)
                lastPongAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private fun beaconLoop() {
        val s = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000
                bind(InetSocketAddress(BEACON_PORT))
            }
        } catch (_: IOException) {
            return
        }
        beaconSocket = s
        val buf = ByteArray(128)
        val pkt = DatagramPacket(buf, buf.size)
        while (running && !s.isClosed) {
            try {
                pkt.length = buf.size
                s.receive(pkt)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: IOException) {
                break
            }
            if (!autoDiscover || pkt.length < 4 || buf[0] != MAGIC || buf[1] != TYPE_BEACON) continue
            val port = (buf[2].toInt() and 0xFF) or ((buf[3].toInt() and 0xFF) shl 8)
            val name = String(buf, 4, pkt.length - 4, Charsets.UTF_8)
            val addr = pkt.address
            // Switch servers only if the current one stopped answering.
            if (addr != target && !connected) {
                handler.post { if (running) setTargetOnSender(addr, port, name) }
            } else if (addr == target && serverName.isEmpty()) {
                serverName = name
            }
        }
        s.close()
    }

    companion object {
        const val MAGIC: Byte = 0x57
        const val TYPE_INPUT: Byte = 1
        const val TYPE_PING: Byte = 2
        const val TYPE_PONG: Byte = 3
        const val TYPE_BEACON: Byte = 4
        const val INPUT_LEN = 12
        const val PING_LEN = 10
        const val DEFAULT_PORT = 47800
        const val BEACON_PORT = 47801
        const val RTT_WINDOW = 10

        private fun writeLongLe(b: ByteArray, off: Int, v: Long) {
            for (i in 0 until 8) b[off + i] = (v shr (8 * i)).toByte()
        }

        private fun readLongLe(b: ByteArray, off: Int): Long {
            var v = 0L
            for (i in 0 until 8) v = v or ((b[off + i].toLong() and 0xFF) shl (8 * i))
            return v
        }
    }
}
