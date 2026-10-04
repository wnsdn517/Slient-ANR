package io.github.wnsdn517.silentanr.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import io.github.wnsdn517.silentanr.common.Contract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsState(
    val enabled: Boolean = true,
    val defaultMode: String = Contract.MODE_NOTIFY,
    val notifyAuto: Boolean = true,
    val recordBackground: Boolean = true,
    val retentionDays: Int = 30,
    val maxRecords: Int = 5000,
    val appModes: Map<String, String> = emptyMap(),
    /** ANR type ([Contract.TYPES]) -> mode, for types that have a rule. */
    val typeModes: Map<String, String> = emptyMap(),
)

/**
 * Settings shared with the system_server hook. LSPosed redirects MODE_WORLD_READABLE prefs to a
 * location readable by XSharedPreferences; when the module is not enabled that call throws, which
 * doubles as our "LSPosed hasn't activated the module" signal.
 */
class ModuleSettings private constructor(context: Context) {
    private val prefs: SharedPreferences
    val prefsShared: Boolean

    init {
        var shared = true
        prefs = try {
            @Suppress("DEPRECATION")
            @SuppressLint("WorldReadableFiles")
            val p = context.getSharedPreferences(Contract.PREFS_NAME, Context.MODE_WORLD_READABLE)
            p
        } catch (e: SecurityException) {
            shared = false
            context.getSharedPreferences(Contract.PREFS_NAME, Context.MODE_PRIVATE)
        }
        prefsShared = shared
    }

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private fun read(): SettingsState {
        val modes = prefs.all
            .filterKeys { it.startsWith(Contract.MODE_KEY_PREFIX) }
            .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix(Contract.MODE_KEY_PREFIX) to it } }
            .toMap()
        val types = Contract.TYPES.mapNotNull { type ->
            prefs.getString(Contract.TYPE_KEY_PREFIX + type, null)?.let { type to it }
        }.toMap()
        return SettingsState(
            enabled = prefs.getBoolean(Contract.KEY_ENABLED, true),
            defaultMode = prefs.getString(Contract.KEY_DEFAULT_MODE, Contract.MODE_NOTIFY) ?: Contract.MODE_NOTIFY,
            notifyAuto = prefs.getBoolean(Contract.KEY_NOTIFY_AUTO, true),
            recordBackground = prefs.getBoolean(Contract.KEY_RECORD_BACKGROUND, true),
            retentionDays = prefs.getInt(KEY_RETENTION_DAYS, 30),
            maxRecords = prefs.getInt(KEY_MAX_RECORDS, 5000),
            appModes = modes,
            typeModes = types,
        )
    }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        // commit() so the file is on disk before the hook's next hasFileChanged() check.
        prefs.edit().apply(block).commit()
        _state.value = read()
    }

    fun setEnabled(v: Boolean) = edit { putBoolean(Contract.KEY_ENABLED, v) }
    fun setDefaultMode(v: String) = edit { putString(Contract.KEY_DEFAULT_MODE, v) }
    fun setNotifyAuto(v: Boolean) = edit { putBoolean(Contract.KEY_NOTIFY_AUTO, v) }
    fun setRecordBackground(v: Boolean) = edit { putBoolean(Contract.KEY_RECORD_BACKGROUND, v) }
    fun setRetentionDays(v: Int) = edit { putInt(KEY_RETENTION_DAYS, v) }
    fun setMaxRecords(v: Int) = edit { putInt(KEY_MAX_RECORDS, v) }

    fun setAppMode(packageName: String, mode: String) = edit {
        val key = Contract.MODE_KEY_PREFIX + packageName
        if (mode == Contract.MODE_DEFAULT) remove(key) else putString(key, mode)
    }

    fun setTypeMode(type: String, mode: String) = edit {
        val key = Contract.TYPE_KEY_PREFIX + type
        if (mode == Contract.MODE_DEFAULT) remove(key) else putString(key, mode)
    }

    companion object {
        private const val KEY_RETENTION_DAYS = "retention_days"
        private const val KEY_MAX_RECORDS = "max_records"

        @Volatile
        private var instance: ModuleSettings? = null

        fun get(context: Context): ModuleSettings = instance ?: synchronized(this) {
            instance ?: ModuleSettings(context.applicationContext).also { instance = it }
        }
    }
}
