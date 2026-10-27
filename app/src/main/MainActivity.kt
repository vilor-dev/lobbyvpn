// lang: Kotlin, file: MainActivity.kt — live fetch, country picker, auto modes
package com.noob.lobbyvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private val scope       = MainScope()
    private val autoHandler = Handler(Looper.getMainLooper())
    private var autoRunnable: Runnable? = null
    private val VPN_REQ     = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 2, 2, 2)
        }

        val title  = TextView(this).apply { text = "LOBBYVPN"; textSize = 10f }
        val status = TextView(this).apply { text = "idle"; textSize = 9f }
        val fetchBtn = Button(this).apply { text = "FETCH SERVERS" }

        val countryLabel = TextView(this).apply { text = "country:" }
        val countrySpinner = Spinner(this)

        val autoLow  = CheckBox(this).apply { text = "auto low density" }
        val autoHour = CheckBox(this).apply { text = "auto hour rotate" }

        val connect = Button(this).apply { text = "CONNECT" }
        val stop    = Button(this).apply { text = "STOP" }

        for (v in listOf(title, fetchBtn, status, countryLabel,
                         countrySpinner, autoLow, autoHour, connect, stop))
            root.addView(v)

        setContentView(root)

        fetchBtn.setOnClickListener {
            status.text = "fetching..."
            scope.launch {
                ServerList.fetch()
                val codes = ServerList.allCountries()
                val adapter = ArrayAdapter(this@MainActivity,
                    android.R.layout.simple_spinner_item, codes)
                countrySpinner.adapter = adapter
                status.text = "loaded ${ServerList.cached.size} servers"
            }
        }

        connect.setOnClickListener {
            val server = when {
                autoHour.isChecked -> ServerList.byHour()
                autoLow.isChecked  -> ServerList.byDensity().firstOrNull()
                else -> {
                    val code = countrySpinner.selectedItem as? String ?: ""
                    ServerList.byCountry(code).firstOrNull()
                        ?: ServerList.cached.firstOrNull()
                }
            } ?: run { status.text = "no servers — fetch first"; return@setOnClickListener }

            VpnWorker.currentServer = server
            status.text = "→ ${server.countryLong} ${server.ip} ping:${server.ping}ms"

            val perm = VpnService.prepare(this)
            if (perm != null) startActivityForResult(perm, VPN_REQ)
            else launchVpn(server)

            if (autoHour.isChecked) startAutoRotate(status)
        }

        stop.setOnClickListener {
            stopAutoRotate()
            startService(Intent(this, VpnWorker::class.java)
                .apply { action = VpnWorker.ACTION_STOP })
            status.text = "stopped"
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (req == VPN_REQ && res == RESULT_OK)
            VpnWorker.currentServer?.let { launchVpn(it) }
    }

    private fun launchVpn(s: Server) {
        startService(Intent(this, VpnWorker::class.java)
            .apply { action = VpnWorker.ACTION_START })
    }

    private fun startAutoRotate(status: TextView) {
        stopAutoRotate()
        autoRunnable = object : Runnable {
            override fun run() {
                scope.launch {
                    ServerList.fetch()
                    val next = ServerList.byHour() ?: return@launch
                    VpnWorker.currentServer = next
                    startService(Intent(this@MainActivity, VpnWorker::class.java)
                        .apply { action = VpnWorker.ACTION_STOP })
                    delay(1500)
                    launchVpn(next)
                    status.text = "rotated → ${next.countryLong} ${next.ip}"
                }
                autoHandler.postDelayed(this, 60 * 60 * 1000L)
            }
        }
        autoHandler.postDelayed(autoRunnable!!, 60 * 60 * 1000L)
    }

    private fun stopAutoRotate() {
        autoRunnable?.let { autoHandler.removeCallbacks(it) }
        autoRunnable = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopAutoRotate()
    }
}
