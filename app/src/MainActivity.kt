// lang: Kotlin, file: MainActivity.kt
// UI is intentionally shit — raw layouts, no styling, vpn is the point
package com.noob.lobbyvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import java.util.Calendar

class MainActivity : Activity() {

    private val VPN_REQUEST = 1
    private var autoHandler  = Handler(Looper.getMainLooper())
    private var autoRunnable: Runnable? = null
    private var selectedServer: Server? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // raw layout — no XML, pure ugly code-built UI
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 4, 4, 4)
        }

        val title = TextView(this).apply { text = "LOBBYVPN v1"; textSize = 11f }
        root.addView(title)

        // country spinner
        val spinnerLabel = TextView(this).apply { text = "country:" }
        root.addView(spinnerLabel)

        val spinner = Spinner(this)
        val names = ServerList.all.map { "${it.code} ${it.country}" }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
        root.addView(spinner)

        // auto-low-density toggle
        val autoLow = CheckBox(this).apply { text = "auto low density" }
        root.addView(autoLow)

        // auto-hour toggle
        val autoHour = CheckBox(this).apply { text = "auto by hour" }
        root.addView(autoHour)

        // status dump
        val status = TextView(this).apply {
            text = "status: idle"
            textSize = 9f
        }
        root.addView(status)

        // connect button — ugly, no style
        val btnConnect = Button(this).apply { text = "CONNECT" }
        root.addView(btnConnect)

        val btnStop = Button(this).apply { text = "STOP" }
        root.addView(btnStop)

        btnConnect.setOnClickListener {
            val server = when {
                autoHour.isChecked  -> ServerList.byHour()
                autoLow.isChecked   -> ServerList.lowestDensity(1).first()
                else -> ServerList.all[spinner.selectedItemPosition]
            }
            selectedServer = server
            VpnWorker.currentServer = server
            status.text = "connecting → ${server.country} ${server.host}"

            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, VPN_REQUEST)
            else startVpn()

            if (autoHour.isChecked) startAutoRotate(status)
        }

        btnStop.setOnClickListener {
            stopAutoRotate()
            startService(Intent(this, VpnWorker::class.java).apply {
                action = VpnWorker.ACTION_STOP
            })
            status.text = "status: stopped"
        }

        setContentView(root)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == VPN_REQUEST && res == RESULT_OK) startVpn()
    }

    private fun startVpn() {
        startService(Intent(this, VpnWorker::class.java).apply {
            action = VpnWorker.ACTION_START
        })
    }

    private fun startAutoRotate(status: TextView) {
        stopAutoRotate()
        autoRunnable = object : Runnable {
            override fun run() {
                val next = ServerList.byHour()
                VpnWorker.currentServer = next
                // restart service with new server
                startService(Intent(this@MainActivity, VpnWorker::class.java).apply {
                    action = VpnWorker.ACTION_STOP
                })
                Handler(Looper.getMainLooper()).postDelayed({
                    startVpn()
                    status.text = "auto → ${next.country} ${next.host}"
                }, 1500)
                // check again in 60 minutes
                autoHandler.postDelayed(this, 60 * 60 * 1000L)
            }
        }
        // first rotation check in 60 min
        autoHandler.postDelayed(autoRunnable!!, 60 * 60 * 1000L)
    }

    private fun stopAutoRotate() {
        autoRunnable?.let { autoHandler.removeCallbacks(it) }
        autoRunnable = null
    }
}
