package io.github.wnsdn517.silentanr.system

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import io.github.wnsdn517.silentanr.common.Contract
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class ControlResult(val ok: Boolean, val extras: Bundle?)

data class RunningProcess(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val packageName: String,
    val pssKb: Int,
    val vssKb: Long = 0L,
    val rssKb: Long = 0L,
    val cpuTimeMs: Long = 0L,
    val importance: Int,
)

data class RunningServiceInfo(
    val className: String,
    val processName: String,
    val pid: Int,
    val uid: Int,
    val activeMs: Long,
    val isForeground: Boolean,
)

/**
 * Talks to the control receiver that the hook registers inside system_server. Uses ordered
 * broadcasts so the hook can hand back a result; if the hook isn't loaded the result code stays
 * at its initial value and we report failure.
 */
object SystemBridge {
    private const val TIMEOUT_MS = 5_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun send(context: Context, op: String, fill: Intent.() -> Unit = {}): ControlResult {
        val intent = Intent(Contract.ACTION_CONTROL)
            .setPackage("android")
            .putExtra(Contract.EXTRA_OP, op)
            .apply(fill)
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val resultReceiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) {
                        if (cont.isActive) cont.resume(ControlResult(resultCode == Contract.RESULT_OK, getResultExtras(false)))
                    }
                }
                context.sendOrderedBroadcast(intent, null, resultReceiver, mainHandler, Activity.RESULT_CANCELED, null, null)
            }
        } ?: ControlResult(false, null)
    }

    /** Returns the hook version, or null when the system_server hook is not active. */
    suspend fun ping(context: Context): Int? {
        val r = send(context, Contract.OP_PING)
        return if (r.ok) r.extras?.getInt(Contract.EXTRA_HOOK_VERSION) else null
    }

    suspend fun kill(context: Context, pid: Int, packageName: String, userId: Int) =
        send(context, Contract.OP_KILL) {
            putExtra(Contract.EXTRA_PID, pid)
            putExtra(Contract.EXTRA_PACKAGE, packageName)
            putExtra(Contract.EXTRA_USER_ID, userId)
        }.ok

    /** Kill ops return how many processes went away, or null when the hook didn't answer. */
    private fun ControlResult.count(): Int? = if (ok) extras?.getInt(Contract.EXTRA_COUNT) ?: 0 else null

    suspend fun forceStop(context: Context, packageName: String, userId: Int): Int? =
        send(context, Contract.OP_FORCE_STOP) {
            putExtra(Contract.EXTRA_PACKAGE, packageName)
            putExtra(Contract.EXTRA_USER_ID, userId)
        }.count()

    suspend fun killBackground(context: Context, packageName: String, userId: Int): Int? =
        send(context, Contract.OP_KILL_BACKGROUND) {
            putExtra(Contract.EXTRA_PACKAGE, packageName)
            putExtra(Contract.EXTRA_USER_ID, userId)
        }.count()

    suspend fun killAllBackground(context: Context): Int? = send(context, Contract.OP_KILL_ALL_BACKGROUND).count()

    /** Toast text for a kill op's result from [forceStop], [killBackground] or [killAllBackground]. */
    fun describe(count: Int?, nothing: String, systemApp: Boolean = false): String = when {
        count == null -> "The system hook didn't respond"
        count == 0 -> nothing
        else -> "Stopped $count " + (if (count == 1) "process" else "processes") +
            if (systemApp) ". Android restarts system apps on its own." else ""
    }

    suspend fun processes(context: Context): List<RunningProcess>? {
        val r = send(context, Contract.OP_PROCESSES)
        val b = r.extras ?: return null
        if (!r.ok) return null
        val names = b.getStringArray(Contract.EXTRA_PROC_NAMES) ?: return null
        val pkgs = b.getStringArray(Contract.EXTRA_PROC_PKGS) ?: return null
        val pids = b.getIntArray(Contract.EXTRA_PROC_PIDS) ?: return null
        val uids = b.getIntArray(Contract.EXTRA_PROC_UIDS) ?: return null
        val pss = b.getIntArray(Contract.EXTRA_PROC_PSS) ?: return null
        val vss = b.getLongArray(Contract.EXTRA_PROC_VSS)
        val rss = b.getLongArray(Contract.EXTRA_PROC_RSS)
        val cpuTimes = b.getLongArray(Contract.EXTRA_PROC_CPU_TIME)
        val imp = b.getIntArray(Contract.EXTRA_PROC_IMPORTANCE) ?: return null

        return names.indices.map { i ->
            RunningProcess(
                pid = pids[i],
                uid = uids[i],
                processName = names[i],
                packageName = pkgs[i],
                pssKb = pss[i],
                vssKb = vss?.getOrNull(i) ?: 0L,
                rssKb = rss?.getOrNull(i) ?: 0L,
                cpuTimeMs = cpuTimes?.getOrNull(i) ?: 0L,
                importance = imp[i]
            )
        }
    }

    suspend fun services(context: Context, packageName: String? = null): List<RunningServiceInfo>? {
        val r = send(context, Contract.OP_SERVICES) {
            packageName?.let { putExtra(Contract.EXTRA_PACKAGE, it) }
        }
        val b = r.extras ?: return null
        if (!r.ok) return null
        val classes = b.getStringArray(Contract.EXTRA_SERVICE_CLASSES) ?: return null
        val procs = b.getStringArray(Contract.EXTRA_SERVICE_PROCS) ?: return null
        val pids = b.getIntArray(Contract.EXTRA_SERVICE_PIDS) ?: return null
        val uids = b.getIntArray(Contract.EXTRA_SERVICE_UIDS) ?: return null
        val actives = b.getLongArray(Contract.EXTRA_SERVICE_ACTIVES) ?: return null
        val foregrounds = b.getBooleanArray(Contract.EXTRA_SERVICE_FOREGROUNDS) ?: return null

        return classes.indices.map { i ->
            RunningServiceInfo(
                className = classes[i],
                processName = procs[i],
                pid = pids[i],
                uid = uids[i],
                activeMs = actives[i],
                isForeground = foregrounds[i]
            )
        }
    }

    suspend fun stopService(context: Context, packageName: String, className: String): Boolean {
        val r = send(context, Contract.OP_STOP_SERVICE) {
            putExtra(Contract.EXTRA_PACKAGE, packageName)
            putExtra(Contract.EXTRA_SERVICE_CLASS, className)
        }
        return r.ok && (r.extras?.getInt(Contract.EXTRA_COUNT) ?: 0) > 0
    }
}
