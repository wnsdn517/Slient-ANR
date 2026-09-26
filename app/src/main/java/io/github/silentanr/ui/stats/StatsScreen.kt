package io.github.silentanr.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.silentanr.ui.MainViewModel
import io.github.silentanr.ui.StatsCalculator
import io.github.silentanr.ui.TimeRange
import io.github.silentanr.ui.components.AppIcon
import io.github.silentanr.ui.components.BarChart
import io.github.silentanr.ui.components.ChartSlice
import io.github.silentanr.ui.components.DonutChart
import io.github.silentanr.ui.components.EmptyState
import io.github.silentanr.ui.components.Panel
import io.github.silentanr.ui.components.RankedBars
import io.github.silentanr.ui.components.ScreenList
import io.github.silentanr.ui.components.SectionTitle
import io.github.silentanr.ui.components.Segmented
import io.github.silentanr.ui.components.StatCell
import io.github.silentanr.ui.components.StatStrip
import io.github.silentanr.ui.theme.AppTheme
import io.github.silentanr.util.Labels
import java.util.Locale

@Composable
fun StatsScreen(vm: MainViewModel, onOpenApp: (String) -> Unit) {
    val records by vm.records.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.WEEK) }
    val stats = remember(records, range) { StatsCalculator.compute(records, range) }
    val t = AppTheme.tokens

    ScreenList(title = "Stats") {
        item { Segmented(TimeRange.entries.map { it to it.label }, range, { range = it }) }
        item {
            StatStrip(
                listOf(
                    StatCell("ANRs", "${stats.total}"),
                    StatCell("Apps", "${stats.distinctApps}"),
                    StatCell("Per day", String.format(Locale.US, "%.1f", stats.perDay)),
                )
            )
        }
        if (stats.total == 0) {
            item { Panel { EmptyState("Nothing in this range", "Pick a longer range or wait for the first ANR.") } }
            return@ScreenList
        }
        item {
            SectionTitle("Per day")
            Panel {
                BarChart(
                    values = stats.daily,
                    labels = stats.dailyLabels,
                    labelEvery = if (stats.daily.size > 10) 5 else 1,
                )
            }
        }
        item {
            SectionTitle("Outcome")
            Panel {
                val actions = Labels.allActions.filter { (stats.byAction[it] ?: 0) > 0 }
                DonutChart(
                    slices = actions.map { ChartSlice(Labels.action(it), stats.byAction[it] ?: 0, Labels.actionColor(it)) },
                    centerLabel = "hidden",
                    centerValue = "${stats.blocked}",
                )
            }
        }
        item {
            SectionTitle("Apps")
            Panel {
                val top = stats.topApps.take(5)
                RankedBars(
                    items = top.map { it.label to it.count },
                    leading = { i -> AppIcon(top[i].packageName, 28.dp) },
                    onClick = { i -> onOpenApp(top[i].packageName) },
                )
            }
        }
        item {
            SectionTitle("ANR type")
            Panel { RankedBars(items = stats.reasons, color = t.chart[1]) }
        }
    }
}
