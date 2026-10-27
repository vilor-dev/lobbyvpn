// lang: Kotlin, file: VpnConfigManager.kt
// writes decoded .ovpn to internal storage, hands path to OpenVPN
package com.noob.lobbyvpn

import android.content.Context
import java.io.File

object VpnConfigManager {

    fun writeConfig(ctx: Context, server: Server): File {
        val dir = ctx.filesDir
        val f = File(dir, "active.ovpn")
        // patch the remote line to match actual IP + port from CSV
        // VPN Gate configs already have correct remote — write as-is
        f.writeText(server.ovpnConfig)
        return f
    }

    fun activeConfig(ctx: Context): File =
        File(ctx.filesDir, "active.ovpn")
}
