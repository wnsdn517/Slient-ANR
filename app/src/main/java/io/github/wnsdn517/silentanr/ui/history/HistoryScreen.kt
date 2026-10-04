package io.github.wnsdn517.silentanr.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.ui.LabeledSummary
import io.github.wnsdn517.silentanr.ui.MainViewModel
import io.github.wnsdn517.silentanr.ui.TimeRange
import io.github.wnsdn517.silentanr.ui.components.EmptyState
import io.github.wnsdn517.silentanr.ui.components.RecordRow
import io.github.wnsdn517.silentanr.ui.components.RowDivider
import io.github.wnsdn517.silentanr.ui.components.ScreenList
import io.github.wnsdn517.silentanr.ui.components.SearchField
import io.github.wnsdn517.silentanr.ui.components.Segmented
import io.github.wnsdn517.silentanr.ui.components.groupItem
import io.github.wnsdn517.silentanr.ui.components.gap
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.Labels

enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    APP_NAME("App name"),
    FREQUENCY("Most frequent app"),
}

@Composable
fun HistoryScreen(
    vm: MainViewModel,
    packageFilter: String?,
    onClearPackage: () -> Unit,
    onOpenRecord: (Long) -> Unit,
) {
    val records by vm.records.collectAsStateWithLifecycle()
    val t = AppTheme.tokens

    var query by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(TimeRange.ALL) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.NEWEST) }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    val filtered = remember(records, query, range, sort, action, type, packageFilter) {
        filterAndSort(records, query, range, sort, action, type, packageFilter)
    }
    val anyFilter = query.isNotEmpty() || range != TimeRange.ALL || action != null || type != null || packageFilter != null

    ScreenList(
        title = "Log",
        subtitle = if (filtered.size == records.size) "${records.size} entries" else "${filtered.size} of ${records.size} entries",
        itemSpacing = 0.dp,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options", tint = t.textMuted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    SortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.label) },
                            onClick = { sort = o; menu = false },
                            trailingIcon = { if (o == sort) Icon(Icons.Filled.Check, null, tint = t.accent) },
                        )
                    }
                    if (anyFilter) {
                        HorizontalDivider(color = t.outline)
                        DropdownMenuItem(text = { Text("Clear filters") }, onClick = {
                            query = ""; range = TimeRange.ALL; action = null; type = null; onClearPackage(); menu = false
                        })
                    }
                }
            }
        },
    ) {
        item { SearchField(query, { query = it }, "Search apps, packages, reasons") }
        gap(Space.sm)
        item { Segmented(TimeRange.entries.map { it to it.label }, range, { range = it }) }
        gap(Space.xs)
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                packageFilter?.let { pkg ->
                    FilterChip(
                        selected = true,
                        onClick = onClearPackage,
                        label = { Text(records.firstOrNull { it.s.packageName == pkg }?.label ?: pkg) },
                        trailingIcon = { Icon(Icons.Filled.Close, "Remove", Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
                MenuChip(
                    label = action?.let { Labels.action(it) } ?: "Any outcome",
                    active = action != null,
                    options = listOf(null to "Any outcome") + Labels.allActions.map { it to Labels.action(it) },
                    onSelect = { action = it },
                )
                MenuChip(
                    label = type?.let { Labels.type(it) } ?: "Any type",
                    active = type != null,
                    options = listOf(null to "Any type") + Contract.TYPES.map { it to Labels.type(it) },
                    onSelect = { type = it },
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyState(
                    if (records.isEmpty()) "Nothing logged yet" else "No matches",
                    if (records.isEmpty()) "ANRs are recorded here automatically." else "Try a wider filter.",
                )
            }
        } else {
            item { Spacer(Modifier.height(Space.sm)) }
            itemsIndexed(filtered, key = { _, it -> it.s.id }) { i, item ->
                Column(Modifier.groupItem(i, filtered.size)) {
                    if (i > 0) RowDivider()
                    RecordRow(item) { onOpenRecord(item.s.id) }
                }
            }
        }
    }
}

/** Filter chip that opens a small menu of choices. */
@Composable
private fun MenuChip(label: String, active: Boolean, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

private fun filterAndSort(
    records: List<LabeledSummary>,
    query: String,
    range: TimeRange,
    sort: SortOrder,
    action: String?,
    type: String?,
    pkg: String?,
): List<LabeledSummary> {
    val since = range.since()
    val q = query.trim().lowercase()
    val list = records.filter { r ->
        r.s.timestamp >= since &&
            (action == null || r.s.action == action) &&
            (pkg == null || r.s.packageName == pkg) &&
            (type == null || Contract.typeOf(r.s.reason) == type) &&
            (q.isEmpty() || r.label.lowercase().contains(q) || r.s.packageName.lowercase().contains(q) ||
                r.s.reason?.lowercase()?.contains(q) == true || r.s.processName?.lowercase()?.contains(q) == true)
    }
    return when (sort) {
        SortOrder.NEWEST -> list.sortedByDescending { it.s.timestamp }
        SortOrder.OLDEST -> list.sortedBy { it.s.timestamp }
        SortOrder.APP_NAME -> list.sortedWith(compareBy<LabeledSummary> { it.label.lowercase() }.thenByDescending { it.s.timestamp })
        SortOrder.FREQUENCY -> {
            val counts = list.groupingBy { it.s.packageName }.eachCount()
            list.sortedWith(
                compareByDescending<LabeledSummary> { counts[it.s.packageName] ?: 0 }
                    .thenBy { it.s.packageName }
                    .thenByDescending { it.s.timestamp }
            )
        }
    }
}
