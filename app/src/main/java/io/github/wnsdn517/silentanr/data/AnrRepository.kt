package io.github.wnsdn517.silentanr.data

import android.content.Context
import java.util.concurrent.TimeUnit

class AnrRepository private constructor(private val context: Context) {
    private val dao = AppDatabase.get(context).anrDao()
    private val settings = ModuleSettings.get(context)

    val summaries = dao.observeSummaries()
    val count = dao.observeCount()

    fun observe(id: Long) = dao.observe(id)

    suspend fun all() = dao.all()

    suspend fun insert(record: AnrRecord): Long {
        val id = dao.insert(record)
        applyRetention()
        return id
    }

    suspend fun setUserAction(id: Long, action: String) = dao.setUserAction(id, action)

    suspend fun recentCount(packageName: String, windowMs: Long = TimeUnit.HOURS.toMillis(1)) =
        dao.countForPackageSince(packageName, System.currentTimeMillis() - windowMs)

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteByPackage(packageName: String) = dao.deleteByPackage(packageName)
    suspend fun deleteAll() = dao.deleteAll()

    /** Enforces the retention period and record cap. Returns number of rows removed. */
    suspend fun applyRetention(): Int {
        val s = settings.state.value
        var removed = 0
        if (s.retentionDays > 0) {
            removed += dao.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(s.retentionDays.toLong()))
        }
        if (s.maxRecords > 0) removed += dao.trimTo(s.maxRecords)
        return removed
    }

    fun databaseSizeBytes(): Long =
        listOf("", "-wal", "-shm").sumOf { context.getDatabasePath(AppDatabase.NAME + it).takeIf { f -> f.exists() }?.length() ?: 0L }

    companion object {
        @Volatile
        private var instance: AnrRepository? = null

        fun get(context: Context): AnrRepository = instance ?: synchronized(this) {
            instance ?: AnrRepository(context.applicationContext).also { instance = it }
        }
    }
}
