package dev.jdtech.jellyfin.utils

object TimeUtils {
    fun ticksToSeconds(ticks: Long): Long {
        return ticks
            .div(10000000)
    }

    fun ticksToMinutes(ticks: Long): Long {
        return ticksToSeconds(ticks)
            .div(60)
    }

    fun minutesToHours(min: Long): String {
        val hrs = min / 60
        val mins = min % 60
        if (hrs == 0L) return "${mins}m"
        else if (hrs > 0L) return "${hrs}h ${mins}m"
        else return ""
    }
}