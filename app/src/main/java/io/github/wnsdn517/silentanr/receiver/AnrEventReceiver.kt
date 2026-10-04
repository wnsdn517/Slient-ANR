package io.github.wnsdn517.silentanr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.data.AnrRecord
import io.github.wnsdn517.silentanr.data.AnrRepository
import io.github.wnsdn517.silentanr.notify.AnrNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives ANR events from the system_server hook, stores them and posts notifications. */
class AnrEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Contract.ACTION_ANR_EVENT) return
        val pkg = intent.getStringExtra(Contract.EXTRA_PACKAGE) ?: return
        val record = AnrRecord(
            timestamp = intent.getLongExtra(Contract.EXTRA_TIMESTAMP, System.currentTimeMillis()),
            packageName = pkg,
            processName = intent.getStringExtra(Contract.EXTRA_PROCESS),
            pid = intent.getIntExtra(Contract.EXTRA_PID, -1),
            uid = intent.getIntExtra(Contract.EXTRA_UID, -1),
            userId = intent.getIntExtra(Contract.EXTRA_USER_ID, 0),
            reason = intent.getStringExtra(Contract.EXTRA_REASON),
            details = intent.getStringExtra(Contract.EXTRA_DETAILS),
            action = intent.getStringExtra(Contract.EXTRA_ACTION) ?: Contract.ACT_DIALOG,
            continuous = intent.getBooleanExtra(Contract.EXTRA_CONTINUOUS, false),
            aboveSystem = intent.getBooleanExtra(Contract.EXTRA_ABOVE_SYSTEM, false),
        )
        val notify = intent.getBooleanExtra(Contract.EXTRA_NOTIFY, false)
        val pending = goAsync()
        scope.launch {
            try {
                val repo = AnrRepository.get(context)
                val id = repo.insert(record)
                if (notify) AnrNotifier.show(context, record.copy(id = id), repo.recentCount(pkg))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
