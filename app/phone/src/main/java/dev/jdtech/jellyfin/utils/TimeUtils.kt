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
}