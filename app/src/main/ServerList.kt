// lang: Kotlin, file: ServerList.kt — live VPN Gate fetcher
package com.noob.lobbyvpn

import android.util.Base64
import kotlinx.coroutines.*
import java.net.URL

data class Server(
    val hostname: String,
    val ip: String,
    val score: Long,
    val ping: Int,
    val speed: Long,
    val countryLong: String,
    val countryShort: String,
    val sessions: Int,
    val ovpnConfig: String   // decoded .ovpn text
)

object ServerList {

    private const val API = "https://www.vpngate.net/api/iphone/"

    var cached: List<Server> = emptyList()
        private set

    suspend fun fetch(): List<Server> = withContext(Dispatchers.IO) {
        try {
            val raw = URL(API).readText()
            val lines = raw.lines()
                .dropWhile { !it.startsWith("*vpn_servers") }
                .drop(2)                          // skip *vpn_servers + header
                .filter { it.isNotBlank() && !it.startsWith("*") }

            cached = lines.mapNotNull { parseLine(it) }
            cached
        } catch (e: Exception) {
            cached  // return last good list on fail
        }
    }

    private fun parseLine(line: String): Server? {
        // CSV has commas inside base64 — split on first 14 commas only
        val parts = line.split(",", limit = 15)
        if (parts.size < 15) return null
        return try {
            val ovpnRaw = parts[14].trim()
            val ovpn = String(Base64.decode(ovpnRaw, Base64.DEFAULT))
            Server(
                hostname     = parts[0],
                ip           = parts[1],
                score        = parts[2].toLongOrNull() ?: 0L,
                ping         = parts[3].toIntOrNull() ?: 999,
                speed        = parts[4].toLongOrNull() ?: 0L,
                countryLong  = parts[5],
                countryShort = parts[6],
                sessions     = parts[7].toIntOrNull() ?: 0,
                ovpnConfig   = ovpn
            )
        } catch (_: Exception) { null }
    }

    // low player density countries for Brawl Stars lobby spread
    private val lowDensityCodes = setOf(
        "MD","KZ","GE","AM","AL","MK","ME","BO","PY","NI","HN","SV","MN","KH","LA","IS","LV","EE"
    )

    fun byDensity(): List<Server> {
        val low = cached.filter { it.countryShort in lowDensityCodes }
        return if (low.isNotEmpty()) low.sortedBy { it.ping }
               else cached.sortedBy { it.ping }
    }

    fun byHour(): Server? {
        val pool = byDensity().take(6)
        if (pool.isEmpty()) return null
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return pool[h % pool.size]
    }

    fun byCountry(code: String): List<Server> =
        cached.filter { it.countryShort.equals(code, ignoreCase = true) }
              .sortedBy { it.ping }

    fun allCountries(): List<String> =
        cached.map { it.countryShort }.distinct().sorted()
}
