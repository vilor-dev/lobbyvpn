// lang: Kotlin, file: VpnWorker.kt — OpenVPN3 via Intent bridge
// routes through ics-openvpn (de.blinkt.openvpn) which handles the real tunnel
package com.noob.lobbyvpn

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder

class VpnWorker : Service() {

    companion object {
        var currentServer: Server? = null
        const val ACTION_START = "START"
        const val ACTION_STOP  = "STOP"

        // ics-openvpn external control — launches tunnel from .ovpn file
        private const val ICS_PKG    = "de.blinkt.openvpn"
        private const val ICS_LAUNCH = "$ICS_PKG.LaunchVPN"

        fun startTunnel(ctx: Context, server: Server) {
            val cfg = VpnConfigManager.writeConfig(ctx, server)
            val i = Intent().apply {
                component = ComponentName(ICS_PKG, ICS_LAUNCH)
                putExtra("net.openvpn.openvpn.PROFILE_PATH", cfg.absolutePath)
                putExtra("net.openvpn.openvpn.PROFILE_NAME", "${server.countryShort}-${server.ip}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { ctx.startActivity(i) }
            catch (_: Exception) {
                // ics-openvpn not installed — fall back to raw VpnService tunnel
                RawTunnelWorker.start(ctx, server)
            }
        }

        fun stopTunnel(ctx: Context) {
            val i = Intent().apply {
                component = ComponentName(ICS_PKG, "$ICS_PKG.DisconnectVPN")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try { ctx.startActivity(i) }
            catch (_: Exception) { RawTunnelWorker.stop() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> currentServer?.let { startTunnel(this, it) }
            ACTION_STOP  -> stopTunnel(this)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
