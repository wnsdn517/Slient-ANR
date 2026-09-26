package io.github.silentanr.ui

import io.github.silentanr.common.Contract
import io.github.silentanr.util.Format
import io.github.silentanr.util.Labels
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class TimeRange(val label: String, val days: Int?) {
    DAY("24h", 1), WEEK("7d", 7), MONTH("30d", 30), ALL("All", null);

    fun since(now: Long = System.currentTimeMillis()): Long =
        days?.let { now - TimeUnit.DAYS.toMillis(it.toLong()) } ?: 0L
}

data class AppCount(val packageName: String, val label: String, val count: Int, val lastTs: Long)

data class Stats(
    val total: Int,
    val blocked: Int,
    val byAction: Map<String, Int>,
    val dailyLabels: List<String>,
    val daily: List<Int>,
    val topApps: List<AppCount>,
    val reasons: List<Pair<String, Int>>,
    val distinctApps: Int,
    val perDay: Float,
)

object StatsCalculator {
    fun compute(records: List<LabeledSummary>, range: TimeRange, now: Long = System.currentTimeMillis()): Stats {
        val since = range.since(now)
        val list = records.filter { it.s.timestamp >= since }

        val byAction = list.groupingBy { it.s.action }.eachCount()
        val blocked = list.count { it.s.action != Contract.ACT_DIALOG }

        // Daily buckets: 24h range -> hourly buckets would duplicate the heat strip, so use
        // 7 days minimum; "all" shows the last 30 days.
        val days = when (range) {
            TimeRange.DAY, TimeRange.WEEK -> 7
            TimeRange.MONTH, TimeRange.ALL -> 30
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val daily = IntArray(days)
        val dailyLabels = (0 until days).map { i -> Format.day(todayStart - (days - 1 - i) * dayMs) }
        records.forEach { r ->
            val ts = r.s.timestamp
            val daysAgo = if (ts >= todayStart) 0 else ((todayStart - ts - 1) / dayMs).toInt() + 1
            val i = days - 1 - daysAgo
            if (i in 0 until days) daily[i]++
        }

        val topApps = list.groupBy { it.s.packageName }
            .map { (pkg, rs) -> AppCount(pkg, rs.first().label, rs.size, rs.maxOf { it.s.timestamp }) }
            .sortedByDescending { it.count }

        val reasons = list.groupingBy { Labels.reasonCategory(it.s.reason) }.eachCount()
            .toList().sortedByDescending { it.second }

        val spanDays = range.days ?: run {
            val first = list.minOfOrNull { it.s.timestamp } ?: now
            ((now - first) / dayMs + 1).toInt()
        }
        return Stats(
            total = list.size,
            blocked = blocked,
            byAction = byAction,
            dailyLabels = dailyLabels,
            daily = daily.toList(),
            topApps = topApps,
            reasons = reasons,
            distinctApps = topApps.size,
            perDay = if (spanDays > 0) list.size.toFloat() / spanDays else 0f,
        )
    }
}
