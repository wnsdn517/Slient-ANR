package io.github.wnsdn517.silentanr.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.ui.HookStatus
import io.github.wnsdn517.silentanr.ui.MainViewModel
import io.github.wnsdn517.silentanr.ui.StatsCalculator
import io.github.wnsdn517.silentanr.ui.TimeRange
import io.github.wnsdn517.silentanr.ui.components.EmptyState
import io.github.wnsdn517.silentanr.ui.components.IconBadge
import io.github.wnsdn517.silentanr.ui.components.Panel
import io.github.wnsdn517.silentanr.ui.components.RecordRow
import io.github.wnsdn517.silentanr.ui.components.RowDivider
import io.github.wnsdn517.silentanr.ui.components.ScreenList
import io.github.wnsdn517.silentanr.ui.components.SectionTitle
import io.github.wnsdn517.silentanr.ui.components.StatCell
import io.github.wnsdn517.silentanr.ui.components.StatStrip
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.Labels

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onOpenRecord: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val records by vm.records.collectAsStateWithLifecycle()
    val hook by vm.hook.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today = remember(records) { StatsCalculator.compute(records, TimeRange.DAY) }
    val week = remember(records) { StatsCalculator.compute(records, TimeRange.WEEK) }
    val t = AppTheme.tokens

    ScreenList(
        title = "Silent ANR",
        actions = {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = t.text) }
        },
    ) {
        item { StatusCard(hook, settings.enabled, Labels.mode(settings.defaultMode), vm::refreshHookStatus) }
        item {
            val killed = week.byAction[Contract.ACT_AUTO_KILL] ?: 0
            StatStrip(
                listOf(
                    StatCell("Today", "${today.total}"),
                    StatCell("This week", "${week.total}"),
                    StatCell("Killed", "$killed", emphasis = if (killed > 0) t.danger else null),
                )
            )
        }
        item {
            SectionTitle("Recent") {
                if (records.isNotEmpty()) TextButton(onClick = onOpenLog) { Text("See all") }
            }
            Panel(padding = 0.dp) {
                if (records.isEmpty()) {
                    EmptyState("No ANRs yet", "Apps that stop responding will show up here.")
                } else {
                    records.take(5).forEachIndexed { i, item ->
                        if (i > 0) RowDivider()
                        RecordRow(item) { onOpenRecord(item.s.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(hook: HookStatus, enabled: Boolean, modeLabel: String, onRetry: () -> Unit) {
    val t = AppTheme.tokens
    val status = when {
        hook.checking -> Status(t.neutral, Icons.Filled.Info, "Checking…", "Waiting for the system hook")
        hook.active && enabled -> Status(t.success, Icons.Filled.CheckCircle, "Working", "Default action: $modeLabel")
        hook.active -> Status(t.warning, Icons.Filled.Warning, "Paused", "Blocking is off in Settings")
        hook.moduleEnabled -> Status(t.warning, Icons.Filled.Warning, "Reboot needed", "The module is on but not loaded yet")
        else -> Status(t.danger, Icons.Filled.Warning, "Not active", "Turn on Silent ANR in LSPosed, then reboot")
    }
    Panel(color = status.color.copy(alpha = 0.10f).compositeOver(t.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(status.icon, status.color)
            Spacer(Modifier.width(Space.md + 2.dp))
            Column(Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleLarge, color = t.text)
                Text(status.body, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
            }
            if (!hook.active && !hook.checking) {
                IconButton(onClick = onRetry) { Icon(Icons.Filled.Refresh, "Check again", tint = t.textMuted) }
            }
        }
    }
}

private data class Status(val color: Color, val icon: ImageVector, val title: String, val body: String)
