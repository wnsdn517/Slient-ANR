package io.github.silentanr.system

import android.app.ActivityManager
import android.content.Context
import java.io.File

data class MemorySnapshot(
    val totalBytes: Long,
    val availBytes: Long,
    val thresholdBytes: Long,
    val lowMemory: Boolean,
    val swapTotalKb: Long,
    val swapFreeKb: Long,
    val cachedKb: Long,
    val buffersKb: Long,
    val zramKb: Long,
) {
    val usedBytes get() = totalBytes - availBytes
    val usedFraction get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
    val swapUsedFraction get() = if (swapTotalKb > 0) (swapTotalKb - swapFreeKb).toFloat() / swapTotalKb else 0f
}

object MemoryMonitor {
    fun snapshot(context: Context): MemorySnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val meminfo = readProcMeminfo()
        return MemorySnapshot(
            totalBytes = mi.totalMem,
            availBytes = mi.availMem,
            thresholdBytes = mi.threshold,
            lowMemory = mi.lowMemory,
            swapTotalKb = meminfo["SwapTotal"] ?: 0,
            swapFreeKb = meminfo["SwapFree"] ?: 0,
            cachedKb = meminfo["Cached"] ?: 0,
            buffersKb = meminfo["Buffers"] ?: 0,
            zramKb = (meminfo["SwapTotal"] ?: 0) - (meminfo["SwapFree"] ?: 0),
        )
    }

    private fun readProcMeminfo(): Map<String, Long> = try {
        File("/proc/meminfo").readLines().mapNotNull { line ->
            val parts = line.split(':', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val kb = parts[1].trim().substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
            parts[0].trim() to kb
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    /** Runs a command as root (LSPosed users have Magisk/KernelSU). Returns exit code or null. */
    fun runRoot(command: String): Int? = try {
        val p = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText()
        p.waitFor()
    } catch (e: Exception) {
        null
    }
}
