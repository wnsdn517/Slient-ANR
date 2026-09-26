package io.github.silentanr.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnrDao {
    @Query(
        "SELECT id, timestamp, package_name, process_name, pid, user_id, reason, action, user_action " +
            "FROM anr ORDER BY timestamp DESC"
    )
    fun observeSummaries(): Flow<List<AnrSummary>>

    @Query("SELECT * FROM anr WHERE id = :id")
    fun observe(id: Long): Flow<AnrRecord?>

    @Query("SELECT * FROM anr ORDER BY timestamp DESC")
    suspend fun all(): List<AnrRecord>

    @Insert
    suspend fun insert(record: AnrRecord): Long

    @Query("UPDATE anr SET user_action = :userAction WHERE id = :id")
    suspend fun setUserAction(id: Long, userAction: String)

    @Query("SELECT COUNT(*) FROM anr WHERE package_name = :packageName AND timestamp >= :since")
    suspend fun countForPackageSince(packageName: String, since: Long): Int

    @Query("DELETE FROM anr WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM anr WHERE package_name = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM anr")
    suspend fun deleteAll()

    @Query("DELETE FROM anr WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM anr WHERE id NOT IN (SELECT id FROM anr ORDER BY timestamp DESC LIMIT :max)")
    suspend fun trimTo(max: Int): Int

    @Query("SELECT COUNT(*) FROM anr")
    fun observeCount(): Flow<Int>
}
