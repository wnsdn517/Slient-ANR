package io.github.wnsdn517.silentanr.ui.resources

import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.wnsdn517.silentanr.system.MemoryMonitor
import io.github.wnsdn517.silentanr.system.MemorySnapshot
import io.github.wnsdn517.silentanr.system.RunningProcess
import io.github.wnsdn517.silentanr.system.RunningServiceInfo
import io.github.wnsdn517.silentanr.system.SystemBridge
import io.github.wnsdn517.silentanr.ui.MainViewModel
import io.github.wnsdn517.silentanr.ui.components.AppIcon
import io.github.wnsdn517.silentanr.ui.components.CardTitle
import io.github.wnsdn517.silentanr.ui.components.ListRow
import io.github.wnsdn517.silentanr.ui.components.Panel
import io.github.wnsdn517.silentanr.ui.components.ProgressBar
import io.github.wnsdn517.silentanr.ui.components.RingGauge
import io.github.wnsdn517.silentanr.ui.components.RowDivider
import io.github.wnsdn517.silentanr.ui.components.ScreenList
import io.github.wnsdn517.silentanr.ui.components.SectionTitle
import io.github.wnsdn517.silentanr.ui.components.Segmented
import io.github.wnsdn517.silentanr.ui.components.SystemKillDialog
import io.github.wnsdn517.silentanr.ui.components.Tag
import io.github.wnsdn517.silentanr.ui.components.gap
import io.github.wnsdn517.silentanr.ui.components.groupItem
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Figures
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.AppInfoCache
import io.github.wnsdn517.silentanr.util.Format
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
    val vssKb: Long,
    val rssKb: Long,
    val cpuTimeMs: Long,
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
                        vssKb = ps.sumOf { it.vssKb },
                        rssKb = ps.sumOf { it.rssKb },
                        cpuTimeMs = ps.sumOf { it.cpuTimeMs },
                        processes = ps,
                        importance = ps.minOf { it.importance },
                    )
                }
            }
        }
        _loadingProcs.value = false
    }

    suspend fun getServices(pkg: String): List<RunningServiceInfo>? {
        return SystemBridge.services(getApplication(), pkg)
    }

    fun stopService(pkg: String, className: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val ok = SystemBridge.stopService(getApplication(), pkg, className)
        onDone(if (ok) "Service stopped" else "Could not stop service")
        refreshProcesses()
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

private enum class ProcSort(val label: String) {
    MEMORY("PSS Mem"),
    VIRTUAL_MEM("VSS Mem"),
    CPU_TIME("CPU Time"),
    IMPORTANCE("Priority"),
    NAME("Name")
}

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
    var selectedGroup by remember { mutableStateOf<ProcessGroup?>(null) }

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
                ProcSort.VIRTUAL_MEM -> list.sortedByDescending { it.vssKb }
                ProcSort.CPU_TIME -> list.sortedByDescending { it.cpuTimeMs }
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
                    ProcessRow(
                        group = group,
                        totalBytes = memory?.totalBytes ?: 0L,
                        rvm = rvm,
                        toast = toast,
                        onClick = { selectedGroup = group }
                    )
                }
            }
        }
    }

    selectedGroup?.let { group ->
        AppDetailsAndServicesSheet(
            group = group,
            rvm = rvm,
            toast = toast,
            onDismiss = { selectedGroup = null }
        )
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
private fun ProcessRow(
    group: ProcessGroup,
    totalBytes: Long,
    rvm: ResourcesViewModel,
    toast: (String) -> Unit,
    onClick: () -> Unit
) {
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
    val extraInfo = buildString {
        append("PSS: ").append(Format.kb(group.pssKb.toLong()))
        if (group.vssKb > 0) append(" · VSS: ").append(Format.kb(group.vssKb))
        if (group.cpuTimeMs > 0) append(" · CPU: ").append(Format.duration(group.cpuTimeMs))
    }

    Column {
        ListRow(
            title = group.label,
            subtitle = extraInfo + if (isSystem) " · system" else "",
            leading = { AppIcon(group.packageName) },
            onClick = onClick,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(impLabel, impColor)
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More", tint = t.textMuted) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("App Details & Services") },
                                onClick = { menu = false; onClick() }
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailsAndServicesSheet(
    group: ProcessGroup,
    rvm: ResourcesViewModel,
    toast: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val t = AppTheme.tokens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSystem = remember(group.packageName) { AppInfoCache.isSystem(context, group.packageName) }
    var pendingKill by remember { mutableStateOf<(() -> Unit)?>(null) }

    val guarded: (() -> Unit) -> Unit = { action ->
        if (isSystem) {
            pendingKill = action
        } else {
            action()
        }
    }

    var services by remember { mutableStateOf<List<RunningServiceInfo>?>(null) }
    var loadingServices by remember { mutableStateOf(true) }

    fun refreshServices() {
        loadingServices = true
        scope.launch {
            services = rvm.getServices(group.packageName)
            loadingServices = false
        }
    }

    LaunchedEffect(group.packageName) {
        refreshServices()
    }

    val userId = group.uid / 100_000

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = Space.lg, vertical = Space.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(group.packageName, size = 48.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(group.label, style = MaterialTheme.typography.titleLarge, color = t.text)
                    Text(group.packageName, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                }
                if (isSystem) Tag("System", t.warning)
            }

            Spacer(Modifier.height(Space.lg))

            // Resource Statistics Card
            Panel(padding = Space.md, color = t.surfaceMuted) {
                Text("Process Resource Usage", style = MaterialTheme.typography.titleSmall, color = t.text)
                Spacer(Modifier.height(Space.sm))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetric("PSS Memory", Format.kb(group.pssKb.toLong()))
                    DetailMetric("RSS Memory", if (group.rssKb > 0) Format.kb(group.rssKb) else "-")
                    DetailMetric("VSS Memory", if (group.vssKb > 0) Format.kb(group.vssKb) else "-")
                }
                Spacer(Modifier.height(Space.xs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailMetric("Total CPU Time", if (group.cpuTimeMs > 0) Format.duration(group.cpuTimeMs) else "-")
                    DetailMetric("UID", "${group.uid}")
                    DetailMetric("Processes", "${group.processes.size}")
                }
            }

            Spacer(Modifier.height(Space.md))

            // Management Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(
                    onClick = { guarded { rvm.killBackground(group.packageName, userId, toast); onDismiss() } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Kill Cached")
                }
                Button(
                    onClick = { guarded { rvm.forceStop(group.packageName, userId, toast); onDismiss() } },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Force Stop")
                }
            }

            Spacer(Modifier.height(Space.lg))

            // Running Services Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Running Services (${services?.size ?: 0})",
                    style = MaterialTheme.typography.titleMedium,
                    color = t.text,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { refreshServices() }) {
                    Icon(Icons.Outlined.Refresh, "Refresh Services", tint = t.textMuted)
                }
            }

            Spacer(Modifier.height(Space.xs))

            when {
                loadingServices -> {
                    Text("Loading services…", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                }
                services.isNullOrEmpty() -> {
                    Text("No active services running for this app.", style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        LazyColumn {
                            items(services!!) { s ->
                                val shortClass = s.className.substringAfterLast('.')
                                Panel(padding = Space.sm, color = t.surfaceMuted, modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(shortClass, style = MaterialTheme.typography.titleSmall, color = t.text)
                                                if (s.isForeground) {
                                                    Spacer(Modifier.width(Space.xs))
                                                    Tag("FG Service", t.success)
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(s.className, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                                            Text(
                                                "Active: ${Format.duration(s.activeMs)} · Proc: ${s.processName} (pid: ${s.pid})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = t.textMuted
                                            )
                                        }
                                        TextButton(onClick = {
                                            rvm.stopService(group.packageName, s.className) { msg ->
                                                toast(msg)
                                                refreshServices()
                                            }
                                        }) {
                                            Icon(Icons.Filled.Close, null, tint = t.danger)
                                            Spacer(Modifier.width(2.dp))
                                            Text("Stop", color = t.danger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingKill?.let { action ->
        SystemKillDialog(group.label, onConfirm = action, onDismiss = { pendingKill = null })
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    val t = AppTheme.tokens
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = t.textMuted)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(Figures), color = t.text)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun importanceLabel(importance: Int) = AppTheme.tokens.let { t ->
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
