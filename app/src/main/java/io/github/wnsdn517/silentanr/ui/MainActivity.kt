package io.github.wnsdn517.silentanr.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.wnsdn517.silentanr.R
import io.github.wnsdn517.silentanr.notify.AnrNotifier
import io.github.wnsdn517.silentanr.ui.history.HistoryScreen
import io.github.wnsdn517.silentanr.ui.history.RecordDetailScreen
import io.github.wnsdn517.silentanr.ui.home.HomeScreen
import io.github.wnsdn517.silentanr.ui.resources.ResourcesScreen
import io.github.wnsdn517.silentanr.ui.rules.RulesScreen
import io.github.wnsdn517.silentanr.ui.settings.SettingsScreen
import io.github.wnsdn517.silentanr.ui.stats.StatsScreen
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.SilentAnrTheme

private enum class Tab(val label: String, val icon: Int) {
    HOME("Overview", R.drawable.ic_nav_home),
    LOG("Log", R.drawable.ic_nav_log),
    RULES("Rules", R.drawable.ic_nav_rules),
    STATS("Stats", R.drawable.ic_nav_stats),
    SYSTEM("System", R.drawable.ic_nav_system),
}

/** Screens pushed on top of the tabs. */
private object Routes {
    const val SETTINGS = "settings"
    fun record(id: Long) = "record/$id"
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingRecordId by mutableStateOf<Long?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingRecordId = recordIdFrom(intent)
        if (Build.VERSION.SDK_INT >= 33 && !AnrNotifier.canNotify(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            SilentAnrTheme {
                AppRoot(vm, pendingRecordId) { pendingRecordId = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recordIdFrom(intent)?.let { pendingRecordId = it }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshHookStatus()
    }

    private fun recordIdFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_RECORD_ID, -1L)?.takeIf { it > 0 }

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
    }
}

private val StackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

@Composable
private fun AppRoot(vm: MainViewModel, pendingRecordId: Long?, onConsumed: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val stack = rememberSaveable(saver = StackSaver) { mutableStateListOf<String>() }
    var logPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val holder = rememberSaveableStateHolder()
    val t = AppTheme.tokens

    val push: (String) -> Unit = { stack.add(it) }
    val pop: () -> Unit = {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }
    val openLogFor: (String) -> Unit = { pkg -> logPackage = pkg; stack.clear(); tab = Tab.LOG }

    BackHandler(enabled = stack.isNotEmpty() || tab != Tab.HOME) {
        if (stack.isNotEmpty()) {
            pop()
        } else {
            tab = Tab.HOME
        }
    }

    LaunchedEffect(pendingRecordId) {
        if (pendingRecordId != null) {
            push(Routes.record(pendingRecordId))
            onConsumed()
        }
    }

    val top = stack.lastOrNull()
    Scaffold(
        containerColor = t.bg,
        bottomBar = {
            if (top == null) {
                Column {
                    NavigationBar(containerColor = t.surface, tonalElevation = 0.dp) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(painterResource(item.icon), null) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = t.accent,
                                    selectedTextColor = t.accent,
                                    indicatorColor = t.accentSoft,
                                    unselectedIconColor = t.textMuted,
                                    unselectedTextColor = t.textMuted,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
            holder.SaveableStateProvider(top ?: tab.name) {
                when {
                    top == Routes.SETTINGS -> SettingsScreen(vm, onBack = pop)
                    top != null && top.startsWith("record/") ->
                        RecordDetailScreen(vm, top.removePrefix("record/").toLongOrNull() ?: 0L, onBack = pop)
                    tab == Tab.HOME -> HomeScreen(
                        vm,
                        onOpenRecord = { push(Routes.record(it)) },
                        onOpenSettings = { push(Routes.SETTINGS) },
                        onOpenLog = { tab = Tab.LOG },
                    )
                    tab == Tab.LOG -> HistoryScreen(
                        vm,
                        packageFilter = logPackage,
                        onClearPackage = { logPackage = null },
                        onOpenRecord = { push(Routes.record(it)) },
                    )
                    tab == Tab.RULES -> RulesScreen(vm, onOpenLog = openLogFor)
                    tab == Tab.STATS -> StatsScreen(vm, onOpenApp = openLogFor)
                    else -> ResourcesScreen(vm)
                }
            }
        }
    }
}
