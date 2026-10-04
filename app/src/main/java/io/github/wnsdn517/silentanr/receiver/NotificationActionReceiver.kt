package io.github.wnsdn517.silentanr.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.data.AnrRepository
import io.github.wnsdn517.silentanr.data.ModuleSettings
import io.github.wnsdn517.silentanr.data.UserAction
import io.github.wnsdn517.silentanr.system.SystemBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1)
        val pkg = intent.getStringExtra(Contract.EXTRA_PACKAGE) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        NotificationManagerCompat.from(context).cancel(notificationId)

        val pending = goAsync()
        scope.launch {
            try {
                val repo = AnrRepository.get(context)
                when (intent.action) {
                    ACTION_CLOSE -> {
                        SystemBridge.kill(
                            context,
                            pid = intent.getIntExtra(Contract.EXTRA_PID, -1),
                            packageName = pkg,
                            userId = intent.getIntExtra(Contract.EXTRA_USER_ID, 0),
                        )
                        if (recordId > 0) repo.setUserAction(recordId, UserAction.CLOSED)
                    }
                    ACTION_WAIT -> if (recordId > 0) repo.setUserAction(recordId, UserAction.WAITED)
                    ACTION_ALWAYS_WAIT -> {
                        ModuleSettings.get(context).setAppMode(pkg, Contract.MODE_WAIT)
                        if (recordId > 0) repo.setUserAction(recordId, UserAction.WAITED)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CLOSE = "io.github.wnsdn517.silentanr.notification.CLOSE"
        const val ACTION_WAIT = "io.github.wnsdn517.silentanr.notification.WAIT"
        const val ACTION_ALWAYS_WAIT = "io.github.wnsdn517.silentanr.notification.ALWAYS_WAIT"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
