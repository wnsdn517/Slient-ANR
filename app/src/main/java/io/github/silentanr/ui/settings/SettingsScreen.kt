package io.github.silentanr.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.silentanr.BuildConfig
import io.github.silentanr.notify.AnrNotifier
import io.github.silentanr.ui.MainViewModel
import io.github.silentanr.ui.components.Divider
import io.github.silentanr.ui.components.ListRow
import io.github.silentanr.ui.components.Panel
import io.github.silentanr.ui.components.ScreenList
import io.github.silentanr.ui.components.SectionTitle
import io.github.silentanr.ui.components.SwitchRow
import io.github.silentanr.ui.components.ValueRow
import io.github.silentanr.ui.theme.AppTheme
import io.github.silentanr.ui.theme.Space
import io.github.silentanr.util.Format
import io.github.silentanr.util.Labels
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hook by vm.hook.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = AppTheme.tokens
    var confirmClear by remember { mutableStateOf(false) }
    val store = vm.settingsStore

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = vm.exportCsv(uri)
            Toast.makeText(context, if (n >= 0) "Exported $n entries" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    ScreenList(title = "Settings", onBack = onBack) {
        item {
            Panel(padding = 0.dp) {
                SwitchRow("Handle ANR dialogs", "Off: every app gets the normal dialog", settings.enabled, store::setEnabled)
                Divider()
                SwitchRow("Notify on automatic actions", "Silent notification after Wait or Kill", settings.notifyAuto, store::setNotifyAuto)
                Divider()
                SwitchRow("Log background ANRs", "Ones the system kills without a dialog", settings.recordBackground, store::setRecordBackground)
            }
        }
        item {
            SectionTitle("Log")
            Panel(padding = 0.dp) {
                PickerRow(
                    title = "Keep entries for",
                    options = listOf(7, 14, 30, 90, 0),
                    selected = settings.retentionDays,
                    label = { if (it == 0) "Forever" else "$it days" },
                ) {
                    store.setRetentionDays(it)
                    scope.launch { vm.applyRetention() }
                }
                Divider()
                PickerRow(
                    title = "Keep at most",
                    options = listOf(1000, 5000, 20000, 0),
                    selected = settings.maxRecords,
                    label = { if (it == 0) "No limit" else "%,d".format(it) },
                ) {
                    store.setMaxRecords(it)
                    scope.launch { vm.applyRetention() }
                }
                Divider()
                ListRow(
                    title = "Export as CSV",
                    showChevron = true,
                    onClick = { exporter.launch("silent-anr-${Format.day(System.currentTimeMillis()).replace('/', '-')}.csv") },
                )
                Divider()
                ListRow(title = "Delete all entries", onClick = { confirmClear = true })
            }
        }
        item {
            SectionTitle("Notifications")
            Panel(padding = 0.dp) {
                val canNotify = AnrNotifier.canNotify(context)
                ValueRow(
                    title = "Permission",
                    value = if (canNotify) "Allowed" else "Off",
                    highlight = !canNotify,
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
            }
        }
        item {
            Text(
                "Silent ANR ${BuildConfig.VERSION_NAME} · hook " + when {
                    hook.active -> "v${hook.hookVersion}"
                    hook.moduleEnabled -> "waiting for reboot"
                    else -> "not active"
                } + " · scope fixed to System Framework",
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
                modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.md),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete all entries?") },
            text = { Text("Every logged ANR will be removed.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.deleteAll() }) { Text("Delete", color = t.danger) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Row showing the current choice; tapping opens a small menu of the options. */
@Composable
private fun PickerRow(title: String, options: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ValueRow(title = title, value = label(selected), onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { v ->
                DropdownMenuItem(text = { Text(label(v)) }, onClick = { onSelect(v); open = false })
            }
        }
    }
}
