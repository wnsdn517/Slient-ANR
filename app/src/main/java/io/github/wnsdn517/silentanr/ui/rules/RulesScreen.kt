package io.github.wnsdn517.silentanr.ui.rules

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.ui.MainViewModel
import io.github.wnsdn517.silentanr.ui.components.AppIcon
import io.github.wnsdn517.silentanr.ui.components.Divider
import io.github.wnsdn517.silentanr.ui.components.ModeSheet
import io.github.wnsdn517.silentanr.ui.components.Panel
import io.github.wnsdn517.silentanr.ui.components.RowDivider
import io.github.wnsdn517.silentanr.ui.components.SYSTEM_KILL_WARNING
import io.github.wnsdn517.silentanr.ui.components.ScreenList
import io.github.wnsdn517.silentanr.ui.components.SectionTitle
import io.github.wnsdn517.silentanr.ui.components.SearchField
import io.github.wnsdn517.silentanr.ui.components.Segmented
import io.github.wnsdn517.silentanr.ui.components.groupItem
import io.github.wnsdn517.silentanr.ui.components.ValueRow
import io.github.wnsdn517.silentanr.ui.components.gap
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Radius
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.AppInfoCache
import io.github.wnsdn517.silentanr.util.Labels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String, val system: Boolean)

class AppsViewModel(app: Application) : AndroidViewModel(app) {
    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps = _apps.asStateFlow()

    init {
        viewModelScope.launch {
            _apps.value = withContext(Dispatchers.IO) {
                app.packageManager.getInstalledApplications(0)
                    .filter { it.packageName != app.packageName }
                    .map { InstalledApp(it.packageName, AppInfoCache.label(app, it.packageName), AppInfoCache.isSystem(it)) }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }
}

private enum class AppFilter(val label: String) { WITH_RULE("With rule"), USER("User"), ALL("All") }

/** Which rule the open picker edits. */
private sealed interface Target {
    data object Default : Target
    data class Type(val type: String) : Target
    data class App(val app: InstalledApp) : Target
}

@Composable
fun RulesScreen(vm: MainViewModel, onOpenLog: (String) -> Unit) {
    val appsVm: AppsViewModel = viewModel()
    val apps by appsVm.apps.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    val store = vm.settingsStore
    val context = LocalContext.current
    val t = AppTheme.tokens

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.USER) }
    var editing by remember { mutableStateOf<Target?>(null) }

    val anrCounts = remember(records) { records.groupingBy { it.s.packageName }.eachCount() }
    val visible = remember(apps, query, filter, settings.appModes) {
        val q = query.trim().lowercase()
        apps.orEmpty().filter { a ->
            val pass = when (filter) {
                AppFilter.WITH_RULE -> a.packageName in settings.appModes
                AppFilter.USER -> !a.system
                AppFilter.ALL -> true
            }
            pass && (q.isEmpty() || a.label.lowercase().contains(q) || a.packageName.lowercase().contains(q))
        }
    }
    val defaultLabel = Labels.mode(settings.defaultMode)

    ScreenList(
        title = "Rules",
        subtitle = "App rule, then type rule, then default",
        itemSpacing = 0.dp,
    ) {
        item {
            Panel(padding = 0.dp) {
                ValueRow(
                    title = "Default action",
                    subtitle = "When no other rule matches",
                    value = defaultLabel,
                    highlight = true,
                    onClick = { editing = Target.Default },
                )
            }
        }
        item {
            SectionTitle("By ANR type")
            Panel(padding = 0.dp) {
                Contract.TYPES.forEachIndexed { i, type ->
                    if (i > 0) Divider()
                    val mode = settings.typeModes[type]
                    ValueRow(
                        title = Labels.type(type),
                        subtitle = Labels.typeHint(type),
                        value = mode?.let { Labels.mode(it) } ?: "Default",
                        highlight = mode != null,
                        onClick = { editing = Target.Type(type) },
                    )
                }
            }
        }
        item {
            SectionTitle("By app" + if (settings.appModes.isNotEmpty()) " (${settings.appModes.size})" else "")
        }
        item { SearchField(query, { query = it }, "Search apps") }
        gap(Space.sm)
        item { Segmented(AppFilter.entries.map { it to it.label }, filter, { filter = it }) }
        gap(Space.md)
        when {
            apps == null -> item {
                Row(Modifier.fillMaxWidth().padding(Space.xl), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }
            visible.isEmpty() -> item {
                Text(
                    if (filter == AppFilter.WITH_RULE && query.isEmpty()) "No app has its own rule yet." else "No apps match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.textMuted,
                    modifier = Modifier.padding(Space.lg),
                )
            }
            else -> itemsIndexed(visible, key = { _, it -> it.packageName }) { i, app ->
                val mode = settings.appModes[app.packageName]
                Column(Modifier.groupItem(i, visible.size)) {
                    if (i > 0) RowDivider()
                    ValueRow(
                        title = app.label,
                        subtitle = app.packageName,
                        leading = { AppIcon(app.packageName) },
                        value = mode?.let { Labels.mode(it) } ?: "",
                        highlight = mode != null,
                        onClick = { editing = Target.App(app) },
                    )
                }
            }
        }
    }

    when (val target = editing) {
        null -> Unit
        Target.Default -> ModeSheet(
            title = "Default action",
            selected = settings.defaultMode,
            onSelect = store::setDefaultMode,
            killWarning = "Also closes system apps that hang",
            onDismiss = { editing = null },
        )
        is Target.Type -> ModeSheet(
            title = Labels.type(target.type),
            selected = settings.typeModes[target.type] ?: Contract.MODE_DEFAULT,
            defaultLabel = "Default ($defaultLabel)",
            onSelect = { store.setTypeMode(target.type, it) },
            killWarning = "Also closes system apps that hang",
            onDismiss = { editing = null },
        )
        is Target.App -> {
            val pkg = target.app.packageName
            val count = anrCounts[pkg] ?: 0
            ModeSheet(
                title = target.app.label,
                selected = settings.appModes[pkg] ?: Contract.MODE_DEFAULT,
                defaultLabel = "No app rule",
                onSelect = { store.setAppMode(pkg, it) },
                killWarning = if (target.app.system) SYSTEM_KILL_WARNING else null,
                onDismiss = { editing = null },
                header = {
                    Row(Modifier.padding(horizontal = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg)
                        Spacer(Modifier.width(Space.md))
                        Column {
                            Text(target.app.label, style = MaterialTheme.typography.titleMedium, color = t.text)
                            Text(pkg, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
                        }
                    }
                },
                footer = {
                    if (count > 0) {
                        OutlinedButton(
                                onClick = { editing = null; onOpenLog(pkg) },
                            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
                        ) { Text(if (count == 1) "View 1 logged ANR" else "View $count logged ANRs") }
                    }
                },
            )
        }
    }
}
