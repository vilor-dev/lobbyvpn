// lang: Kotlin, file: VpnWorker.kt
// real VpnService tunnel — routes all traffic through selected server via socket protect
package com.noob.lobbyvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.DatagramPacket

class VpnWorker : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var running = false

    companion object {
        var currentServer: Server? = null
        const val ACTION_START = "START"
        const val ACTION_STOP  = "STOP"
        const val CHANNEL_ID   = "vpn_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        currentServer?.let { start(it) }
        return START_STICKY
    }

    private fun start(s: Server) {
        createNotificationChannel()
        startForeground(1, buildNotification(s))

        val builder = Builder()
            .setSession("LobbyVPN")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)   // route all traffic
            .setMtu(1500)

        tun = builder.establish() ?: return
        running = true

        Thread {
            val sock = DatagramSocket()
            protect(sock)   // bypass VPN for tunnel socket itself
            val remote = InetAddress.getByName(s.host)

            val inBuf  = ByteArray(32767)
            val outBuf = ByteArray(32767)
            val input  = tun!!.fileDescriptor.let {
                java.io.FileInputStream(it)
            }
            val output = tun!!.fileDescriptor.let {
                java.io.FileOutputStream(it)
            }

            // raw UDP tunnel — production would layer WireGuard or OpenVPN framing here
            while (running) {
                try {
                    val n = input.read(inBuf)
                    if (n > 0) {
                        val pkt = DatagramPacket(inBuf, n, remote, s.port)
                        sock.send(pkt)
                    }
                    val resp = DatagramPacket(outBuf, outBuf.size)
                    sock.soTimeout = 20
                    try {
                        sock.receive(resp)
                        if (resp.length > 0) output.write(outBuf, 0, resp.length)
                    } catch (_: Exception) {}
                } catch (e: Exception) {
                    if (running) continue else break
                }
            }
            sock.close()
        }.start()
    }

    private fun stop() {
        running = false
        tun?.close()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(s: Server): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LobbyVPN — ${s.country}")
            .setContentText("connected ${s.host}:${s.port}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
}
