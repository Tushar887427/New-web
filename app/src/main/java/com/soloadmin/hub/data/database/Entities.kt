package com.soloadmin.hub.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Table layout notes
 * ------------------
 * - Classic keys from the legacy app (`exp-1`, `oow-1`, ...) are kept as text primary keys so a
 *   JSON re-import can detect duplicates instead of duplicating rows.
 * - Currency columns hold **paise** (Long). Conversion happens only at the JSON boundary.
 * - Dates are `yyyy-MM-dd` text: sortable, timezone-free, and identical to the legacy format.
 */

@Entity(tableName = "attendance_records")
data class AttendanceEntity(
    @PrimaryKey val dateKey: String,
    /** [com.soloadmin.hub.domain.model.AttendanceStatus.storageKey] */
    val status: String,
    val leaveType: String? = null,
    val inTime: String? = null,
    val outTime: String? = null,
    val note: String = "",
    /** Denormalised link to official duty rows for the same date (0..n). */
    val officialDutyId: String? = null,
    val breakMinutes: Int = 0,
    val updatedAt: Long = 0L
) {
    companion object {
        const val NO_DUTY = ""
    }
}

@Entity(tableName = "official_duty_records", indices = [Index("dateKey")])
data class OfficialDutyEntity(
    @PrimaryKey val id: String,
    val dateKey: String,
    val mode: String,
    val startTime: String,
    val endTime: String,
    val meetingWith: String,
    val location: String = "",
    val remarks: String = "",
    /** Relative file name inside `filesDir/attachments`, or a legacy file name string. */
    val attachmentPath: String? = null,
    val createdAt: Long = 0L
)

@Entity(tableName = "expenses", indices = [Index("owner"), Index("dateKey")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val dateKey: String,
    val title: String,
    val category: String,
    val amountPaise: Long,
    val owner: String,
    val recovered: Boolean = false,
    val note: String = "",
    val createdAt: Long = 0L
)

@Entity(tableName = "finance_records", indices = [Index("dateKey")])
data class FinanceEntity(
    @PrimaryKey val id: String,
    val dateKey: String,
    val title: String,
    val amountPaise: Long,
    val status: String,
    val createdAt: Long = 0L
)

@Entity(tableName = "key_dates", indices = [Index("validUntil")])
data class KeyDateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val validFrom: String,
    val validUntil: String,
    val note: String = "",
    val createdAt: Long = 0L
)

@Entity(tableName = "daily_tasks", indices = [Index("done")])
data class DailyTaskEntity(
    @PrimaryKey val id: String,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val sortOrder: Int = 0
)

@Entity(tableName = "colleagues")
data class ColleagueEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String = "",
    val theme: String = "sky",
    val photoPath: String? = null,
    val coverPath: String? = null,
    val createdAt: Long = 0L,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "colleague_info",
    primaryKeys = ["colleagueId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = ColleagueEntity::class,
            parentColumns = ["id"],
            childColumns = ["colleagueId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("colleagueId")]
)
data class ColleagueInfoEntity(
    val colleagueId: String,
    val position: Int,
    val label: String,
    val value: String
)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String = "Admin",
    val bio: String = "",
    val photoPath: String? = null,
    val weeklyOffDay: Int = 0,
    val shiftStart: String = "09:30",
    val updatedAt: Long = 0L
) {
    companion object { const val SINGLETON_ID = 1 }
}

@Entity(
    tableName = "profile_info",
    primaryKeys = ["position"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProfileInfoEntity(
    val profileId: Int,
    val position: Int,
    val label: String,
    val value: String
)

/** Daily punch session; one row per calendar day so breaks survive process death. */
@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey val dateKey: String,
    val inTime: String? = null,
    val outTime: String? = null,
    val onBreak: Boolean = false,
    val breakStartedAt: Long? = null,
    val breakMinutes: Int = 0,
    val sessionsCount: Int = 0
)
