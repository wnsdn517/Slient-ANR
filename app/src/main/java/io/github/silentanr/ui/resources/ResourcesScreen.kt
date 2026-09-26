package io.github.silentanr.ui.resources

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.silentanr.system.MemoryMonitor
import io.github.silentanr.system.MemorySnapshot
import io.github.silentanr.system.RunningProcess
import io.github.silentanr.system.SystemBridge
import io.github.silentanr.ui.MainViewModel
import io.github.silentanr.ui.components.AppIcon
import io.github.silentanr.ui.components.CardTitle
import io.github.silentanr.ui.components.groupItem
import io.github.silentanr.ui.components.ListRow
import io.github.silentanr.ui.components.Panel
import io.github.silentanr.ui.components.ProgressBar
import io.github.silentanr.ui.components.RowDivider
import io.github.silentanr.ui.components.gap
import io.github.silentanr.ui.components.RingGauge
import io.github.silentanr.ui.components.ScreenList
import io.github.silentanr.ui.components.SectionTitle
import io.github.silentanr.ui.components.SystemKillDialog
import io.github.silentanr.ui.components.Segmented
import io.github.silentanr.ui.components.Tag
import io.github.silentanr.ui.theme.AppTheme
import io.github.silentanr.ui.theme.Radius
import io.github.silentanr.ui.theme.Figures
import io.github.silentanr.ui.theme.Space
import io.github.silentanr.util.AppInfoCache
import io.github.silentanr.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProcessGroup(
    val packageName: String,
    val label: String,
    val uid: Int,
    val pssKb: Int,
    val processes: List<RunningProcess>,
    /** Lower value = more important (foreground). */
    val importance: Int,
)

class ResourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val _memory = MutableStateFlow<MemorySnapshot?>(null)
    val memory = _memory.asStateFlow()
    private val _processes = MutableStateFlow<List<ProcessGroup>?>(null)
    val processes = _processes.asStateFlow()
    private val _loadingProcs = MutableStateFlow(false)
    val loadingProcs = _loadingProcs.asStateFlow()
    private val _hookAvailable = MutableStateFlow(true)
    val hookAvailable = _hookAvailable.asStateFlow()

    suspend fun refreshMemory() {
        _memory.value = withContext(Dispatchers.IO) { MemoryMonitor.snapshot(getApplication()) }
    }

    fun refreshProcesses() = viewModelScope.launch {
        _loadingProcs.value = true
        val app = getApplication<Application>()
        val list = SystemBridge.processes(app)
        _hookAvailable.value = list != null
        _processes.value = list?.let { procs ->
            withContext(Dispatchers.Default) {
                procs.groupBy { it.packageName }.map { (pkg, ps) ->
                    ProcessGroup(
                        packageName = pkg,
                        label = AppInfoCache.label(app, pkg),
                        uid = ps.first().uid,
                        pssKb = ps.sumOf { it.pssKb },
                        processes = ps,
                        importance = ps.minOf { it.importance },
                    )
                }
            }
        }
        _loadingProcs.value = false
    }

    fun killBackground(pkg: String, userId: Int, onDone: (String) -> Unit) = viewModelScope.launch {
        val n = SystemBridge.killBackground(getApplication(), pkg, userId)
        onDone(SystemBridge.describe(n, "Nothing stopped: its processes are in use"))
        refreshAfterKill()
    }

    fun forceStop(pkg: String, userId: Int, onDone: (String) -> Unit) = viewModelScope.launch {
        val n = SystemBridge.forceStop(getApplication(), pkg, userId)
        onDone(SystemBridge.describe(n, "It wasn't running", AppInfoCache.isSystem(getApplication(), pkg)))
        refreshAfterKill()
    }

    fun killAllBackground(onDone: (String) -> Unit) = viewModelScope.launch {
        val n = SystemBridge.killAllBackground(getApplication())
        onDone(SystemBridge.describe(n, "No cached apps to stop"))
        refreshAfterKill()
    }

    fun dropCaches(onDone: (String) -> Unit) = viewModelScope.launch {
        val code = withContext(Dispatchers.IO) { MemoryMonitor.runRoot("sync; echo 3 > /proc/sys/vm/drop_caches") }
        onDone(if (code == 0) "Caches dropped" else "Failed: root access is needed")
        refreshMemory()
    }

    private suspend fun refreshAfterKill() {
        delay(600)
        refreshMemory()
        refreshProcesses()
    }
}

private enum class ProcSort(val label: String) { MEMORY("Memory"), IMPORTANCE("Priority"), NAME("Name") }

@Composable
fun ResourcesScreen(vm: MainViewModel) {
    val rvm: ResourcesViewModel = viewModel()
    val memory by rvm.memory.collectAsStateWithLifecycle()
    val processes by rvm.processes.collectAsStateWithLifecycle()
    val loading by rvm.loadingProcs.collectAsStateWithLifecycle()
    val hookAvailable by rvm.hookAvailable.collectAsStateWithLifecycle()
    val recordCount by vm.records.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = AppTheme.tokens
    var sort by rememberSaveable { mutableStateOf(ProcSort.MEMORY) }
    var dbSize by remember { mutableStateOf(0L) }

    val toast: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    // Live memory refresh while the screen is visible.
    LaunchedEffect(Unit) {
        rvm.refreshProcesses()
        while (true) {
            rvm.refreshMemory()
            dbSize = withContext(Dispatchers.IO) { vm.databaseSize() }
            delay(2_000)
        }
    }

    val sorted = remember(processes, sort) {
        processes.orEmpty().let { list ->
            when (sort) {
                ProcSort.MEMORY -> list.sortedByDescending { it.pssKb }
                ProcSort.IMPORTANCE -> list.sortedWith(compareBy<ProcessGroup> { it.importance }.thenByDescending { it.pssKb })
                ProcSort.NAME -> list.sortedBy { it.label.lowercase() }
            }
        }
    }

    ScreenList(
        title = "System",
        itemSpacing = 0.dp,
        actions = {
            IconButton(onClick = { rvm.refreshProcesses() }) { Icon(Icons.Outlined.Refresh, "Refresh", tint = t.text) }
        },
    ) {
        item { memory?.let { MemoryCard(it) } }
        gap()
        item {
            Panel {
                CardTitle("Clean up")
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Button(onClick = { rvm.killAllBackground(toast) }, modifier = Modifier.weight(1f)) {
                        Text("Kill cached apps")
                    }
                    OutlinedButton(onClick = { rvm.dropCaches(toast) }, modifier = Modifier.weight(1f)) {
                        Text("Drop caches (root)")
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Kill cached apps stops idle background processes. Drop caches needs root.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textMuted,
                )
            }
        }
        gap()
        item {
            Panel {
                CardTitle("Log storage")
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(Format.bytes(dbSize), style = MaterialTheme.typography.titleLarge.merge(Figures), color = t.text)
                        Text(
                            "${recordCount.size} entries · kept ${if (settings.retentionDays > 0) "${settings.retentionDays} days" else "forever"} · cap ${if (settings.maxRecords > 0) "${settings.maxRecords}" else "none"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = t.textMuted,
                        )
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val n = vm.applyRetention()
                            Toast.makeText(context, "Removed $n entries", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Trim now") }
                }
            }
        }
        gap()
        item {
            SectionTitle("Running apps (${sorted.size})")
            Segmented(ProcSort.entries.map { it to it.label }, sort, { sort = it })
        }
        gap(Space.md)
        when {
            !hookAvailable -> item {
                Panel {
                    Text("The process list comes from the system hook, which isn't active.", style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
                }
            }
            processes == null && loading -> item {
                Panel { Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = t.textMuted) }
            }
            else -> itemsIndexed(sorted, key = { _, it -> it.packageName + it.uid }) { i, group ->
                Column(Modifier.groupItem(i, sorted.size)) {
                    if (i > 0) RowDivider()
                    ProcessRow(group, memory?.totalBytes ?: 0L, rvm, toast)
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(m: MemorySnapshot) {
    val t = AppTheme.tokens
    val color = when {
        m.lowMemory || m.usedFraction > 0.9f -> t.danger
        m.usedFraction > 0.75f -> t.warning
        else -> t.success
    }
    Panel {
        CardTitle("Memory")
        Row(verticalAlignment = Alignment.CenterVertically) {
            RingGauge(m.usedFraction, color) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Format.percent(m.usedFraction), style = MaterialTheme.typography.titleLarge.merge(Figures), color = t.text)
                    Text("used", style = MaterialTheme.typography.labelSmall, color = t.textMuted)
                }
            }
            Spacer(Modifier.width(Space.xl))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MemLine("Total", Format.bytes(m.totalBytes))
                MemLine("Available", Format.bytes(m.availBytes))
                MemLine("Cached", Format.kb(m.cachedKb + m.buffersKb))
                MemLine("Low-memory line", Format.bytes(m.thresholdBytes))
                if (m.lowMemory) Tag("Low memory", t.danger)
            }
        }
        if (m.swapTotalKb > 0) {
            Spacer(Modifier.height(Space.lg))
            Row {
                Text("Swap / zRAM", style = MaterialTheme.typography.labelMedium, color = t.textMuted, modifier = Modifier.weight(1f))
                Text(
                    "${Format.kb(m.swapTotalKb - m.swapFreeKb)} / ${Format.kb(m.swapTotalKb)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = t.text,
                )
            }
            Spacer(Modifier.height(Space.xs))
            ProgressBar(m.swapUsedFraction, t.chart[4])
        }
    }
}

@Composable
private fun MemLine(label: String, value: String) {
    val t = AppTheme.tokens
    Row {
        Text(label, style = MaterialTheme.typography.bodySmall, color = t.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge.merge(Figures), color = t.text)
    }
}

@Composable
private fun ProcessRow(group: ProcessGroup, totalBytes: Long, rvm: ResourcesViewModel, toast: (String) -> Unit) {
    val t = AppTheme.tokens
    var menu by remember { mutableStateOf(false) }
    var pendingKill by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current
    val isSystem = remember(group.packageName) { AppInfoCache.isSystem(context, group.packageName) }
    val guarded: (() -> Unit) -> Unit = { action ->
        if (isSystem) {
            pendingKill = action
        } else {
            action()
        }
    }
    val userId = group.uid / 100_000
    val (impLabel, impColor) = importanceLabel(group.importance)
    Column {
        ListRow(
            title = group.label,
            subtitle = "${Format.kb(group.pssKb.toLong())} · ${group.processes.size} ${if (group.processes.size == 1) "process" else "processes"}" +
                if (isSystem) " · system" else "",
            leading = { AppIcon(group.packageName) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(impLabel, impColor)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = t.textMuted) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Kill background processes") }, onClick = {
                                menu = false; guarded { rvm.killBackground(group.packageName, userId, toast) }
                            })
                            DropdownMenuItem(text = { Text("Force stop", color = t.danger) }, onClick = {
                                menu = false; guarded { rvm.forceStop(group.packageName, userId, toast) }
                            })
                        }
                    }
                }
            },
        )
        pendingKill?.let { action ->
            SystemKillDialog(group.label, onConfirm = action, onDismiss = { pendingKill = null })
        }
        if (totalBytes > 0) {
            ProgressBar(
                group.pssKb * 1024f / totalBytes,
                t.accent,
                Modifier.padding(start = 70.dp, end = Space.lg, bottom = Space.md),
                height = 4.dp,
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun importanceLabel(importance: Int) = AppTheme.tokens.let { t ->
    // Exact buckets: 125 (foreground service), 325 (top app, screen off) and 350 (heavy-weight)
    // sit between the "round" levels and were mislabelled by a <= ladder.
    when {
        importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground" to t.success
        importance <= RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground service" to t.success
        importance <= RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible" to t.info
        importance <= RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "Perceptible" to t.info
        importance <= RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service" to t.warning
        importance <= RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> "On top, screen off" to t.info
        importance <= RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> "Heavy" to t.warning
        else -> "Cached" to t.neutral
    }
}
