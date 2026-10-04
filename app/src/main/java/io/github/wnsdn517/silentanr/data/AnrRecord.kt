package io.github.wnsdn517.silentanr.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "anr",
    indices = [Index("timestamp"), Index("package_name")],
)
data class AnrRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "process_name") val processName: String?,
    val pid: Int,
    val uid: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    val reason: String?,
    /** Full ANR report (CPU usage etc.). Only loaded for the detail view. */
    val details: String?,
    /** What the hook did: one of Contract.ACT_*. */
    val action: String,
    /** What the user did afterwards from the notification (UserAction.*), if anything. */
    @ColumnInfo(name = "user_action") val userAction: String? = null,
    val continuous: Boolean = false,
    @ColumnInfo(name = "above_system") val aboveSystem: Boolean = false,
)

/** List projection without the (large) details column. */
data class AnrSummary(
    val id: Long,
    val timestamp: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "process_name") val processName: String?,
    val pid: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    val reason: String?,
    val action: String,
    @ColumnInfo(name = "user_action") val userAction: String?,
)

object UserAction {
    const val CLOSED = "USER_CLOSED"
    const val WAITED = "USER_WAITED"
}
