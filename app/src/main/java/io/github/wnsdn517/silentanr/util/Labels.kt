package io.github.wnsdn517.silentanr.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.data.UserAction
import io.github.wnsdn517.silentanr.ui.theme.AppTheme

data class ModeInfo(val id: String, val title: String, val description: String)

object Labels {
    val modes = listOf(
        ModeInfo(Contract.MODE_NOTIFY, "Notify", "Hide the dialog, send a notification instead"),
        ModeInfo(Contract.MODE_WAIT, "Wait", "Hide the dialog and let the app recover"),
        ModeInfo(Contract.MODE_KILL, "Kill", "Close the app right away"),
        ModeInfo(Contract.MODE_DIALOG, "System dialog", "Show the normal Android dialog"),
    )

    fun mode(id: String): String = when (id) {
        Contract.MODE_DEFAULT -> "Default"
        else -> modes.firstOrNull { it.id == id }?.title ?: id
    }

    fun action(action: String): String = when (action) {
        Contract.ACT_NOTIFIED -> "Notified"
        Contract.ACT_AUTO_WAIT -> "Waited"
        Contract.ACT_AUTO_KILL -> "Killed"
        Contract.ACT_DIALOG -> "Dialog shown"
        Contract.ACT_BACKGROUND_KILLED -> "Background kill"
        else -> action
    }

    fun userAction(action: String?): String? = when (action) {
        UserAction.CLOSED -> "Closed by you"
        UserAction.WAITED -> "You waited"
        else -> null
    }

    val allActions = listOf(
        Contract.ACT_NOTIFIED, Contract.ACT_AUTO_WAIT, Contract.ACT_AUTO_KILL,
        Contract.ACT_DIALOG, Contract.ACT_BACKGROUND_KILLED,
    )

    @Composable
    fun actionColor(action: String): Color {
        val t = AppTheme.tokens
        return when (action) {
            Contract.ACT_NOTIFIED -> t.accent
            Contract.ACT_AUTO_WAIT -> t.warning
            Contract.ACT_AUTO_KILL -> t.danger
            Contract.ACT_DIALOG -> t.info
            else -> t.neutral
        }
    }

    fun type(type: String): String = when (type) {
        Contract.TYPE_INPUT -> "Input not handled"
        Contract.TYPE_SERVICE -> "Service timeout"
        Contract.TYPE_BROADCAST -> "Broadcast timeout"
        Contract.TYPE_PROVIDER -> "Content provider"
        Contract.TYPE_JOB -> "Job timeout"
        Contract.TYPE_START -> "Slow app start"
        else -> "Other"
    }

    fun typeHint(type: String): String = when (type) {
        Contract.TYPE_INPUT -> "The app froze while you were using it"
        Contract.TYPE_SERVICE -> "A background service took too long"
        Contract.TYPE_BROADCAST -> "A broadcast receiver took too long"
        Contract.TYPE_PROVIDER -> "A content provider didn't answer"
        Contract.TYPE_JOB -> "A scheduled job took too long"
        Contract.TYPE_START -> "The app took too long to launch"
        else -> "Anything that doesn't fit above"
    }

    /** Readable ANR type for a raw reason ("Input dispatching timed out ..."). */
    fun reasonCategory(reason: String?): String = type(Contract.typeOf(reason))
}
