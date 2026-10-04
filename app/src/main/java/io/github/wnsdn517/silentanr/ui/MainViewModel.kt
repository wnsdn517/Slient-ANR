package io.github.wnsdn517.silentanr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.wnsdn517.silentanr.data.AnrRepository
import io.github.wnsdn517.silentanr.data.AnrSummary
import io.github.wnsdn517.silentanr.data.ModuleSettings
import io.github.wnsdn517.silentanr.system.SystemBridge
import io.github.wnsdn517.silentanr.util.AppInfoCache
import io.github.wnsdn517.silentanr.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HookStatus(
    val checking: Boolean = true,
    /** LSPosed accepted MODE_WORLD_READABLE prefs, i.e. the module is enabled in LSPosed. */
    val moduleEnabled: Boolean = false,
    /** The system_server hook answered a ping (scope "System Framework" + reboot done). */
    val hookVersion: Int? = null,
) {
    val active get() = hookVersion != null
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AnrRepository.get(app)
    val settingsStore = ModuleSettings.get(app)
    val settings = settingsStore.state

    /** Summaries with resolved app labels, newest first. */
    val records: StateFlow<List<LabeledSummary>> = repo.summaries
        .map { list -> list.map { LabeledSummary(it, AppInfoCache.label(app, it.packageName)) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loaded: StateFlow<Boolean> = repo.summaries.map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _hook = MutableStateFlow(HookStatus())
    val hook = _hook.asStateFlow()

    init {
        refreshHookStatus()
    }

    fun refreshHookStatus() {
        viewModelScope.launch {
            _hook.value = _hook.value.copy(checking = true)
            val version = SystemBridge.ping(getApplication())
            _hook.value = HookStatus(false, settingsStore.prefsShared, version)
        }
    }

    fun record(id: Long) = repo.observe(id)

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
    fun deleteByPackage(pkg: String) = viewModelScope.launch { repo.deleteByPackage(pkg) }
    fun deleteAll() = viewModelScope.launch { repo.deleteAll() }

    suspend fun applyRetention(): Int = withContext(Dispatchers.IO) { repo.applyRetention() }

    fun databaseSize() = repo.databaseSizeBytes()

    suspend fun forceStop(pkg: String, userId: Int) = SystemBridge.forceStop(getApplication(), pkg, userId)

    /** Writes all records as CSV to a SAF document. Returns number of rows written. */
    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val rows = repo.all()
        app.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
            w.write("﻿time,app,package,process,pid,uid,user,action,user_action,reason\n")
            rows.forEach { r ->
                val cols = listOf(
                    Format.full(r.timestamp), AppInfoCache.label(app, r.packageName), r.packageName,
                    r.processName.orEmpty(), r.pid.toString(), r.uid.toString(), r.userId.toString(),
                    r.action, r.userAction.orEmpty(), r.reason.orEmpty(),
                )
                w.write(cols.joinToString(",") { "\"" + it.replace("\"", "\"\"").replace('\n', ' ') + "\"" })
                w.write("\n")
            }
        } ?: return@withContext -1
        rows.size
    }
}

data class LabeledSummary(val s: AnrSummary, val label: String)
