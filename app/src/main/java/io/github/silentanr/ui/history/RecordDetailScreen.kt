package io.github.silentanr.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.silentanr.common.Contract
import io.github.silentanr.data.AnrRecord
import io.github.silentanr.system.SystemBridge
import io.github.silentanr.ui.MainViewModel
import io.github.silentanr.ui.components.AppIcon
import io.github.silentanr.ui.components.ModeSheet
import io.github.silentanr.ui.components.Panel
import io.github.silentanr.ui.components.ScreenList
import io.github.silentanr.ui.components.SYSTEM_KILL_WARNING
import io.github.silentanr.ui.components.SectionTitle
import io.github.silentanr.ui.components.Tag
import io.github.silentanr.ui.components.SystemKillDialog
import io.github.silentanr.ui.components.ValueRow
import io.github.silentanr.ui.theme.AppTheme
import io.github.silentanr.ui.theme.Mono
import io.github.silentanr.ui.theme.Radius
import io.github.silentanr.ui.theme.Space
import io.github.silentanr.util.AppInfoCache
import io.github.silentanr.util.Format
import io.github.silentanr.util.Labels
import kotlinx.coroutines.launch

@Composable
fun RecordDetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit) {
    val flow = remember(id) { vm.record(id) }
    val record by flow.collectAsState(initial = null)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = AppTheme.tokens
    var confirmDelete by remember { mutableStateOf(false) }
    var pickMode by remember { mutableStateOf(false) }
    var confirmSystemKill by remember { mutableStateOf(false) }

    val r = record
    ScreenList(
        title = "ANR",
        onBack = onBack,
        actions = {
            if (r != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete", tint = t.textMuted) }
            }
        },
    ) {
        if (r == null) return@ScreenList
        val label = AppInfoCache.label(context, r.packageName)
        val isSystem = AppInfoCache.isSystem(context, r.packageName)
        val forceStop = {
            scope.launch {
                val n = vm.forceStop(r.packageName, r.userId)
                Toast.makeText(context, SystemBridge.describe(n, "It wasn't running", isSystem), Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(r.packageName, 48.dp)
                    Spacer(Modifier.width(Space.md + 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleLarge, color = t.text)
                        Text(Format.full(r.timestamp), style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                    }
                }
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Tag(Labels.reasonCategory(r.reason), t.accent)
                    Tag(Labels.userAction(r.userAction) ?: Labels.action(r.action), Labels.actionColor(r.action))
                    if (r.continuous) Tag("Ongoing", t.warning)
                }
                Spacer(Modifier.height(Space.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isSystem) {
                                confirmSystemKill = true
                            } else {
                                forceStop()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = t.danger),
                    ) { Text("Force stop") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${r.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("App info") }
                }
            }
        }
        item {
            Panel(padding = 0.dp) {
                val mode = settings.appModes[r.packageName]
                ValueRow(
                    title = "Next time for this app",
                    subtitle = if (mode == null) "No app rule, follows type and default rules" else null,
                    value = mode?.let { Labels.mode(it) } ?: "Not set",
                    highlight = mode != null,
                    onClick = { pickMode = true },
                )
            }
        }
        item {
            SectionTitle("Details")
            Panel {
                InfoRow("Package", r.packageName)
                InfoRow("Process", r.processName ?: "-")
                InfoRow("PID / UID", "${r.pid} / ${r.uid}")
                if (r.userId != 0) InfoRow("User", "${r.userId}")
                InfoRow("Reason", r.reason ?: "-")
            }
        }
        if (!r.details.isNullOrBlank()) {
            item {
                SectionTitle("System report") {
                    TextButton(onClick = { copy(context, report(context, r)) }) { Text("Copy") }
                }
                Panel {
                    SelectionContainer {
                        Text(
                            r.details,
                            style = Mono,
                            color = t.text,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }

    if (confirmSystemKill && r != null) {
        SystemKillDialog(
            AppInfoCache.label(context, r.packageName),
            onConfirm = {
                scope.launch {
                    val n = vm.forceStop(r.packageName, r.userId)
                    Toast.makeText(context, SystemBridge.describe(n, "It wasn't running", systemApp = true), Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { confirmSystemKill = false },
        )
    }

    if (pickMode && r != null) {
        ModeSheet(
            title = AppInfoCache.label(context, r.packageName),
            selected = settings.appModes[r.packageName] ?: Contract.MODE_DEFAULT,
            defaultLabel = "No app rule",
            killWarning = if (AppInfoCache.isSystem(context, r.packageName)) SYSTEM_KILL_WARNING else null,
            onSelect = { vm.settingsStore.setAppMode(r.packageName, it) },
            onDismiss = { pickMode = false },
        )
    }

    if (confirmDelete && r != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete entry?") },
            text = { Text("This removes the entry and its system report.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(r.id); onBack() }) { Text("Delete", color = t.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val t = AppTheme.tokens
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = t.textMuted, modifier = Modifier.width(88.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = t.text)
        }
    }
}

private fun report(context: android.content.Context, r: AnrRecord) = buildString {
    appendLine("App: ${AppInfoCache.label(context, r.packageName)} (${r.packageName})")
    appendLine("Time: ${Format.full(r.timestamp)}")
    appendLine("Process: ${r.processName} pid=${r.pid} uid=${r.uid} user=${r.userId}")
    appendLine("Reason: ${r.reason}")
    appendLine("Action: ${r.action}${r.userAction?.let { " / $it" } ?: ""}")
    r.details?.let { appendLine(); append(it) }
}

private fun copy(context: android.content.Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ANR", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
