// lang: Kotlin, file: RawTunnelWorker.kt
// fallback if ics-openvpn not present — bare VpnService UDP forward
package com.noob.lobbyvpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object RawTunnelWorker {

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false

    fun start(ctx: Context, s: Server) {
        val svc = ctx as? VpnService ?: return
        val builder = VpnService.Builder()
            .setSession("LobbyVPN-raw")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)
        tun = builder.establish() ?: return
        running = true
        Thread {
            val sock = DatagramSocket()
            svc.protect(sock)
            val remote = InetAddress.getByName(s.ip)
            val input  = java.io.FileInputStream(tun!!.fileDescriptor)
            val output = java.io.FileOutputStream(tun!!.fileDescriptor)
            val buf    = ByteArray(32767)
            while (running) {
                try {
                    val n = input.read(buf)
                    if (n > 0) sock.send(DatagramPacket(buf, n, remote, 1194))
                    val r = DatagramPacket(buf, buf.size)
                    sock.soTimeout = 20
                    try { sock.receive(r); if (r.length > 0) output.write(buf, 0, r.length) }
                    catch (_: Exception) {}
                } catch (_: Exception) { if (!running) break }
            }
            sock.close()
        }.start()
    }

    fun stop() {
        running = false
        tun?.close()
        tun = null
    }
}
