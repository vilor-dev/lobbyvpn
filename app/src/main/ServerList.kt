// lang: Kotlin, file: ServerList.kt
package com.noob.lobbyvpn

data class Server(
    val country: String,
    val code: String,
    val host: String,
    val port: Int,
    // lower = fewer BS players = better lobby spread
    val density: Int
)

object ServerList {
    val all = listOf(
        Server("Moldova",        "MD", "94.176.233.11",   1194, 1),
        Server("Kazakhstan",     "KZ", "185.220.101.5",   1194, 2),
        Server("Georgia",        "GE", "185.220.101.8",   1194, 2),
        Server("Armenia",        "AM", "185.220.101.12",  1194, 1),
        Server("Albania",        "AL", "185.220.101.15",  1194, 2),
        Server("North Macedonia","MK", "185.220.101.19",  1194, 1),
        Server("Montenegro",     "ME", "185.220.101.22",  1194, 1),
        Server("Bolivia",        "BO", "186.10.68.5",     1194, 2),
        Server("Paraguay",       "PY", "186.10.68.9",     1194, 2),
        Server("Nicaragua",      "NI", "190.60.33.14",    1194, 1),
        Server("Honduras",       "HN", "190.60.33.18",    1194, 1),
        Server("El Salvador",    "SV", "190.60.33.22",    1194, 1),
        Server("Mongolia",       "MN", "103.26.68.5",     1194, 1),
        Server("Cambodia",       "KH", "103.26.68.9",     1194, 2),
        Server("Laos",           "LA", "103.26.68.13",    1194, 1),
        Server("Iceland",        "IS", "185.220.101.30",  1194, 3),
        Server("Latvia",         "LV", "185.220.101.34",  1194, 3),
        Server("Estonia",        "EE", "185.220.101.38",  1194, 3)
    )

    fun lowestDensity(count: Int = 3) =
        all.sortedBy { it.density }.take(count)

    fun byHour(): Server {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        // rotate through low-density pool by hour slot
        val pool = lowestDensity(6)
        return pool[h % pool.size]
    }
}
