package com.soloadmin.hub.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Query("SELECT * FROM attendance_records ORDER BY dateKey DESC")
    fun observeAll(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE dateKey LIKE :monthPrefix || '%' ORDER BY dateKey")
    fun observeMonth(monthPrefix: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance_records WHERE dateKey LIKE :monthPrefix || '%' ORDER BY dateKey")
    suspend fun getMonth(monthPrefix: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance_records WHERE dateKey = :dateKey")
    suspend fun getByDate(dateKey: String): AttendanceEntity?

    @Query("SELECT * FROM attendance_records WHERE dateKey = :dateKey")
    fun observeByDate(dateKey: String): Flow<AttendanceEntity?>

    @Query("SELECT * FROM attendance_records ORDER BY dateKey DESC")
    suspend fun getAll(): List<AttendanceEntity>

    @Query("SELECT COUNT(*) FROM attendance_records")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(record: AttendanceEntity)

    @Upsert
    suspend fun upsertAll(records: List<AttendanceEntity>)

    @Query("DELETE FROM attendance_records WHERE dateKey = :dateKey")
    suspend fun deleteByDate(dateKey: String)

    @Query("DELETE FROM attendance_records")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(records: List<AttendanceEntity>) {
        deleteAll()
        upsertAll(records)
    }
}

@Dao
interface OfficialDutyDao {

    @Query("SELECT * FROM official_duty_records ORDER BY dateKey DESC, createdAt DESC")
    fun observeAll(): Flow<List<OfficialDutyEntity>>

    @Query("SELECT * FROM official_duty_records ORDER BY dateKey DESC, createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<OfficialDutyEntity>>

    @Query("SELECT * FROM official_duty_records ORDER BY dateKey DESC, createdAt DESC")
    suspend fun getAll(): List<OfficialDutyEntity>

    @Query("SELECT * FROM official_duty_records WHERE id = :id")
    suspend fun getById(id: String): OfficialDutyEntity?

    @Query("SELECT COUNT(*) FROM official_duty_records")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(record: OfficialDutyEntity)

    @Upsert
    suspend fun upsertAll(records: List<OfficialDutyEntity>)

    @Delete
    suspend fun delete(record: OfficialDutyEntity)

    @Query("DELETE FROM official_duty_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM official_duty_records")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses ORDER BY dateKey DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY dateKey DESC, createdAt DESC")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(record: ExpenseEntity)

    @Upsert
    suspend fun upsertAll(records: List<ExpenseEntity>)

    @Query("UPDATE expenses SET recovered = :recovered WHERE id = :id")
    suspend fun setRecovered(id: String, recovered: Boolean)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface FinanceDao {

    @Query("SELECT * FROM finance_records ORDER BY dateKey DESC, createdAt DESC")
    fun observeAll(): Flow<List<FinanceEntity>>

    @Query("SELECT * FROM finance_records ORDER BY dateKey DESC, createdAt DESC")
    suspend fun getAll(): List<FinanceEntity>

    @Query("UPDATE finance_records SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM finance_records")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(record: FinanceEntity)

    @Upsert
    suspend fun upsertAll(records: List<FinanceEntity>)

    @Query("DELETE FROM finance_records WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM finance_records")
    suspend fun deleteAll()
}

@Dao
interface KeyDateDao {

    @Query("SELECT * FROM key_dates ORDER BY validUntil ASC")
    fun observeAll(): Flow<List<KeyDateEntity>>

    @Query("SELECT * FROM key_dates ORDER BY validUntil ASC")
    suspend fun getAll(): List<KeyDateEntity>

    @Query("SELECT * FROM key_dates WHERE id = :id")
    suspend fun getById(id: String): KeyDateEntity?

    @Upsert
    suspend fun upsert(record: KeyDateEntity)

    @Upsert
    suspend fun upsertAll(records: List<KeyDateEntity>)

    @Query("DELETE FROM key_dates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM key_dates")
    suspend fun deleteAll()
}

@Dao
interface DailyTaskDao {

    @Query("SELECT * FROM daily_tasks ORDER BY done ASC, sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<DailyTaskEntity>>

    @Query("SELECT * FROM daily_tasks ORDER BY done ASC, sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<DailyTaskEntity>

    @Query("UPDATE daily_tasks SET done = :done, completedAt = :completedAt WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, completedAt: Long?)

    @Upsert
    suspend fun upsert(record: DailyTaskEntity)

    @Upsert
    suspend fun upsertAll(records: List<DailyTaskEntity>)

    @Query("DELETE FROM daily_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM daily_tasks")
    suspend fun deleteAll()
}

@Dao
interface ColleagueDao {

    @Query("SELECT * FROM colleagues ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ColleagueEntity>>

    @Query("SELECT * FROM colleagues ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<ColleagueEntity>

    @Query("SELECT * FROM colleague_info ORDER BY colleagueId, position")
    fun observeAllInfo(): Flow<List<ColleagueInfoEntity>>

    @Query("SELECT * FROM colleague_info ORDER BY colleagueId, position")
    suspend fun getAllInfo(): List<ColleagueInfoEntity>

    @Query("SELECT * FROM colleagues WHERE id = :id")
    suspend fun getById(id: String): ColleagueEntity?

    @Query("SELECT * FROM colleague_info WHERE colleagueId = :id ORDER BY position")
    suspend fun getInfo(id: String): List<ColleagueInfoEntity>

    @Upsert
    suspend fun upsert(record: ColleagueEntity)

    @Upsert
    suspend fun upsertAll(records: List<ColleagueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInfo(records: List<ColleagueInfoEntity>)

    @Query("DELETE FROM colleague_info WHERE colleagueId = :colleagueId")
    suspend fun deleteInfo(colleagueId: String)

    @Query("DELETE FROM colleagues WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM colleagues")
    suspend fun deleteAll()

    @Query("DELETE FROM colleague_info")
    suspend fun deleteAllInfo()

    @Transaction
    suspend fun saveWithInfo(colleague: ColleagueEntity, info: List<ColleagueInfoEntity>) {
        upsert(colleague)
        deleteInfo(colleague.id)
        if (info.isNotEmpty()) insertInfo(info)
    }

    @Transaction
    suspend fun replaceAll(colleagues: List<ColleagueEntity>, info: List<ColleagueInfoEntity>) {
        deleteAll()
        deleteAllInfo()
        if (colleagues.isNotEmpty()) upsertAll(colleagues)
        if (info.isNotEmpty()) insertInfo(info)
    }
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = 1")
    fun observeProfile(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun getProfile(): ProfileEntity?

    @Query("SELECT * FROM profile_info ORDER BY position")
    fun observeInfo(): Flow<List<ProfileInfoEntity>>

    @Query("SELECT * FROM profile_info ORDER BY position")
    suspend fun getInfo(): List<ProfileInfoEntity>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInfo(info: List<ProfileInfoEntity>)

    @Query("DELETE FROM profile_info")
    suspend fun deleteInfo()

    @Transaction
    suspend fun save(profile: ProfileEntity, info: List<ProfileInfoEntity>) {
        upsert(profile)
        deleteInfo()
        if (info.isNotEmpty()) insertInfo(info)
    }
}

@Dao
interface WorkSessionDao {

    @Query("SELECT * FROM work_sessions WHERE dateKey = :dateKey")
    fun observe(dateKey: String): Flow<WorkSessionEntity?>

    @Query("SELECT * FROM work_sessions WHERE dateKey = :dateKey")
    suspend fun get(dateKey: String): WorkSessionEntity?

    @Upsert
    suspend fun upsert(session: WorkSessionEntity)

    @Query("SELECT * FROM work_sessions ORDER BY dateKey")
    suspend fun getAll(): List<WorkSessionEntity>

    @Upsert
    suspend fun upsertAll(sessions: List<WorkSessionEntity>)

    @Query("DELETE FROM work_sessions WHERE dateKey = :dateKey")
    suspend fun delete(dateKey: String)

    @Query("DELETE FROM work_sessions")
    suspend fun deleteAll()
}

/** One DAO for maintenance operations (backup/restore/wipe). */
@Dao
interface MaintenanceDao {
    @Query("SELECT COUNT(*) FROM attendance_records") suspend fun attendanceCount(): Int
    @Query("SELECT COUNT(*) FROM expenses") suspend fun expenseCount(): Int
    @Query("SELECT COUNT(*) FROM official_duty_records") suspend fun dutyCount(): Int
    @Query("SELECT COUNT(*) FROM finance_records") suspend fun financeCount(): Int
    @Query("SELECT COUNT(*) FROM key_dates") suspend fun keyDateCount(): Int
    @Query("SELECT COUNT(*) FROM daily_tasks") suspend fun taskCount(): Int
    @Query("SELECT COUNT(*) FROM colleagues") suspend fun colleagueCount(): Int

    @Query("DELETE FROM attendance_records") suspend fun clearAttendance()
    @Query("DELETE FROM official_duty_records") suspend fun clearDuty()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()
    @Query("DELETE FROM finance_records") suspend fun clearFinance()
    @Query("DELETE FROM key_dates") suspend fun clearKeyDates()
    @Query("DELETE FROM daily_tasks") suspend fun clearTasks()
    @Query("DELETE FROM colleague_info") suspend fun clearColleagueInfo()
    @Query("DELETE FROM colleagues") suspend fun clearColleagues()
    @Query("DELETE FROM profile_info") suspend fun clearProfileInfo()
    @Query("DELETE FROM profile") suspend fun clearProfile()
    @Query("DELETE FROM work_sessions") suspend fun clearSessions()

    @Transaction
    suspend fun wipeEverything() {
        clearAttendance()
        clearDuty()
        clearExpenses()
        clearFinance()
        clearKeyDates()
        clearTasks()
        clearColleagueInfo()
        clearColleagues()
        clearProfileInfo()
        clearProfile()
        clearSessions()
    }
}
