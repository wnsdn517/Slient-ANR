package io.github.silentanr.util

import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val short = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
    private val day = SimpleDateFormat("M/d", Locale.getDefault())

    fun full(ts: Long): String = synchronized(full) { full.format(Date(ts)) }
    fun short(ts: Long): String = synchronized(short) { short.format(Date(ts)) }
    fun day(ts: Long): String = synchronized(day) { day.format(Date(ts)) }

    fun relative(ts: Long): String =
        DateUtils.getRelativeTimeSpanString(ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    fun bytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun kb(kb: Long) = bytes(kb * 1024)
    fun percent(f: Float) = String.format(Locale.US, "%.0f%%", f * 100)
}
