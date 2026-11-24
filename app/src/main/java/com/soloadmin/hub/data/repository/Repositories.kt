package com.soloadmin.hub.data.repository

import com.soloadmin.hub.data.database.SoloDatabase
import com.soloadmin.hub.data.files.MediaStore
import com.soloadmin.hub.domain.logic.BackupMapper
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.BackupSnapshot
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.domain.model.DecodedBackup
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.ImportConflictPolicy
import com.soloadmin.hub.domain.model.ImportGroup
import com.soloadmin.hub.domain.model.ImportOptions
import com.soloadmin.hub.domain.model.ImportPreview
import com.soloadmin.hub.domain.model.ImportResult
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.WorkSession
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AttendanceRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<AttendanceRecord>> =
        db.attendanceDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeMonth(monthKey: String): Flow<List<AttendanceRecord>> =
        db.attendanceDao().observeMonth(monthKey).map { rows -> rows.map { it.toDomain() } }

    fun observeDate(dateKey: String): Flow<AttendanceRecord?> =
        db.attendanceDao().observeByDate(dateKey).map { it?.toDomain() }

    suspend fun get(dateKey: String): AttendanceRecord? = db.attendanceDao().getByDate(dateKey)?.toDomain()
    suspend fun getAll(): List<AttendanceRecord> = db.attendanceDao().getAll().map { it.toDomain() }

    suspend fun upsert(record: AttendanceRecord) = db.attendanceDao().upsert(record.toEntity())

    suspend fun upsertAll(records: List<AttendanceRecord>) =
        db.attendanceDao().upsertAll(records.map { it.toEntity() })

    suspend fun delete(dateKey: String) = db.attendanceDao().deleteByDate(dateKey)

    /** Writes a punch/break result, preserving leave status, notes and duty links. */
    suspend fun saveSession(session: WorkSession, existing: AttendanceRecord?, isLate: Boolean) {
        val now = System.currentTimeMillis()
        val status = when {
            existing != null && existing.status.isLeave -> existing.status
            existing != null && existing.status.isOfficialDuty -> existing.status
            session.inTime.isNullOrBlank() -> existing?.status ?: AttendanceStatus.PRESENT
            else -> when {
                isLate -> AttendanceStatus.LATE
                existing?.status == AttendanceStatus.LATE && session.outTime.isNullOrBlank() -> AttendanceStatus.LATE
                else -> AttendanceStatus.PRESENT
            }
        }
        val merged = AttendanceRecord(
            dateKey = session.dateKey,
            status = status,
            leaveType = existing?.leaveType,
            inTime = session.inTime ?: existing?.inTime,
            outTime = session.outTime ?: existing?.outTime,
            note = existing?.note.orEmpty(),
            officialDutyId = existing?.officialDutyId,
            breakMinutes = session.breakMinutes
        )
        db.attendanceDao().upsert(merged.toEntity(now))
    }
}

class OfficialDutyRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<OfficialDutyRecord>> =
        db.officialDutyDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeRecent(limit: Int): Flow<List<OfficialDutyRecord>> =
        db.officialDutyDao().observeRecent(limit).map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<OfficialDutyRecord> = db.officialDutyDao().getAll().map { it.toDomain() }
    suspend fun get(id: String): OfficialDutyRecord? = db.officialDutyDao().getById(id)?.toDomain()

    /**
     * Saving an official-duty record always marks the same date as Official Duty on the calendar,
     * exactly like the legacy form did (status Outstation + duty times).
     */
    suspend fun saveWithAttendance(record: OfficialDutyRecord) = withContext(Dispatchers.IO) {
        db.officialDutyDao().upsert(record.toEntity())
        val existing = db.attendanceDao().getByDate(record.dateKey)
        val attendance = AttendanceRecord(
            dateKey = record.dateKey,
            status = com.soloadmin.hub.domain.model.AttendanceStatus.OUTSTATION,
            inTime = record.startTime,
            outTime = record.endTime,
            note = "Official Duty: ${record.meetingWith}",
            officialDutyId = record.id,
            breakMinutes = existing?.breakMinutes ?: 0
        )
        db.attendanceDao().upsert(attendance.toEntity())
    }

    suspend fun delete(record: OfficialDutyRecord) = db.officialDutyDao().deleteById(record.id)

}

class ExpenseRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<ExpenseRecord>> =
        db.expenseDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<ExpenseRecord> = db.expenseDao().getAll().map { it.toDomain() }

    suspend fun add(record: ExpenseRecord) = db.expenseDao().upsert(record.toEntity())
    suspend fun update(record: ExpenseRecord) = db.expenseDao().upsert(record.toEntity())
    suspend fun setRecovered(id: String, recovered: Boolean) = db.expenseDao().setRecovered(id, recovered)
    suspend fun delete(id: String) = db.expenseDao().deleteById(id)
}

class FinanceRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<FinanceRecord>> =
        db.financeDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<FinanceRecord> = db.financeDao().getAll().map { it.toDomain() }

    suspend fun add(record: FinanceRecord) = db.financeDao().upsert(record.toEntity())
    suspend fun update(record: FinanceRecord) = db.financeDao().upsert(record.toEntity())
    suspend fun setStatus(id: String, status: com.soloadmin.hub.domain.model.FinanceStatus) =
        db.financeDao().setStatus(id, status.storageKey)

    suspend fun delete(id: String) = db.financeDao().deleteById(id)
}

class KeyDateRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<KeyDateRecord>> =
        db.keyDateDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<KeyDateRecord> = db.keyDateDao().getAll().map { it.toDomain() }
    suspend fun get(id: String): KeyDateRecord? = db.keyDateDao().getById(id)?.toDomain()
    suspend fun upsert(record: KeyDateRecord) = db.keyDateDao().upsert(record.toEntity())
    suspend fun delete(id: String) = db.keyDateDao().deleteById(id)
}

class TaskRepository(private val db: SoloDatabase) {

    fun observeAll(): Flow<List<DailyTaskRecord>> =
        db.dailyTaskDao().observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun getAll(): List<DailyTaskRecord> = db.dailyTaskDao().getAll().map { it.toDomain() }

    suspend fun add(task: DailyTaskRecord) = db.dailyTaskDao().upsert(task.toEntity())

    suspend fun setDone(id: String, done: Boolean) =
        db.dailyTaskDao().setDone(id, done, if (done) System.currentTimeMillis() else null)

    suspend fun update(task: DailyTaskRecord) = db.dailyTaskDao().upsert(task.toEntity())
    suspend fun delete(id: String) = db.dailyTaskDao().deleteById(id)
}

class ColleagueRepository(private val db: SoloDatabase, private val mediaStore: MediaStore) {

    fun observeAll(): Flow<List<ColleagueRecord>> =
        combine(
            db.colleagueDao().observeAll(),
            db.colleagueDao().observeAllInfo()
        ) { colleagues, info ->
            val grouped = info.groupBy { it.colleagueId }
            colleagues.map { entity -> entity.toDomain(grouped[entity.id].orEmpty()) }
        }

    suspend fun getAll(): List<ColleagueRecord> = withContext(Dispatchers.IO) {
        val info = db.colleagueDao().getAllInfo().groupBy { it.colleagueId }
        db.colleagueDao().getAll().map { it.toDomain(info[it.id].orEmpty()) }
    }

    suspend fun save(record: ColleagueRecord) = withContext(Dispatchers.IO) {
        db.colleagueDao().saveWithInfo(record.toEntity(), record.toInfoEntities())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val entity = db.colleagueDao().getById(id)
        db.colleagueDao().deleteById(id)
        entity?.photoPath?.let { mediaStore.deleteImage(it) }
        entity?.coverPath?.let { mediaStore.deleteImage(it) }
    }
}

class ProfileRepository(private val db: SoloDatabase, private val mediaStore: MediaStore) {

    fun observe(): Flow<ProfileRecord> = combine(
        db.profileDao().observeProfile(),
        db.profileDao().observeInfo()
    ) { profile, info ->
        if (profile == null) ProfileRecord() else profile.toDomain(info)
    }

    suspend fun get(): ProfileRecord = withContext(Dispatchers.IO) {
        val entity = db.profileDao().getProfile()
        if (entity == null) ProfileRecord() else entity.toDomain(db.profileDao().getInfo())
    }

    suspend fun save(record: ProfileRecord) = withContext(Dispatchers.IO) {
        val previous = db.profileDao().getProfile()
        if (previous?.photoPath != null && previous.photoPath != record.photoPath) {
            mediaStore.deleteImage(previous.photoPath)
        }
        db.profileDao().save(record.toEntity(), record.toInfoEntities())
    }
}

class WorkSessionRepository(private val db: SoloDatabase) {

    fun observe(dateKey: String): Flow<WorkSession> =
        db.workSessionDao().observe(dateKey).map { it?.toDomain() ?: WorkSession.empty(dateKey) }

    suspend fun get(dateKey: String): WorkSession =
        db.workSessionDao().get(dateKey)?.toDomain() ?: WorkSession.empty(dateKey)

    suspend fun save(session: WorkSession) = db.workSessionDao().upsert(session.toEntity())

    suspend fun getAll(): List<WorkSession> = db.workSessionDao().getAll().map { it.toDomain() }
}

/**
 * Backup / restore / reset orchestration.
 *
 * Every write runs inside a single Room transaction, so a failure halfway through a restore can
 * never leave a half-imported database. Image writes happen before the transaction; only files
 * that end up referenced by a database row are kept.
 */
class DataTransferRepository(
    private val db: SoloDatabase,
    private val mediaStore: MediaStore,
    private val profileRepository: ProfileRepository
) {

    suspend fun snapshot(): BackupSnapshot = withContext(Dispatchers.IO) {
        val info = db.colleagueDao().getAllInfo().groupBy { it.colleagueId }
        BackupSnapshot(
            profile = profileRepository.get(),
            attendance = db.attendanceDao().getAll().map { it.toDomain() },
            duties = db.officialDutyDao().getAll().map { it.toDomain() },
            expenses = db.expenseDao().getAll().map { it.toDomain() },
            finances = db.financeDao().getAll().map { it.toDomain() },
            keyDates = db.keyDateDao().getAll().map { it.toDomain() },
            tasks = db.dailyTaskDao().getAll().map { it.toDomain() },
            colleagues = db.colleagueDao().getAll().map { it.toDomain(info[it.id].orEmpty()) },
            workSessions = db.workSessionDao().getAll().map { it.toDomain() }
        )
    }

    suspend fun preview(decoded: DecodedBackup, options: ImportOptions): ImportPreview =
        withContext(Dispatchers.IO) {
            BackupMapper.preview(decoded, snapshot(), options, java.time.LocalDate.now())
        }

    suspend fun apply(decoded: DecodedBackup, options: ImportOptions): ImportResult =
        withContext(Dispatchers.IO) {
            val current = snapshot()
            val preview = BackupMapper.preview(decoded, current, options, java.time.LocalDate.now())
            val mapped = BackupMapper.toDomain(decoded, current, options)
            val overwrite = options.replaceAll || options.conflictPolicy == ImportConflictPolicy.OVERWRITE

            var savedImages = 0
            val writtenFiles = mutableSetOf<String>()

            fun keep(fileName: String?) {
                if (fileName != null) writtenFiles.add(File(fileName).name)
            }

            // Materialise legacy base64 images before touching the database.
            val colleaguePhotos = HashMap<String, Pair<String?, String?>>()
            mapped.colleagues.forEach { colleague ->
                val photo = mediaStore.importDataUrl(colleague.photoPath, "colleague")
                val cover = mediaStore.importDataUrl(colleague.coverPath, "cover")
                if (photo != null) { savedImages++; keep(photo); colleaguePhotos[colleague.id] = photo to cover }
                else if (cover != null) { savedImages++; keep(cover); colleaguePhotos[colleague.id] = null to cover }
            }
            val profilePhoto = mediaStore.importDataUrl(decoded.profile?.photoDataUrl, "profile")
            if (profilePhoto != null) { savedImages++; keep(profilePhoto) }

            val existingAttendance = current.attendance.associateBy { it.dateKey }
            val attendanceToWrite = mapped.attendance.filter { !existingAttendance.containsKey(it.dateKey) || overwrite }

            fun <T> uniqueById(incoming: List<T>, idOf: (T) -> String, existingIds: Set<String>): List<T> =
                incoming.filter { overwrite || idOf(it) !in existingIds }

            val dutiesToWrite = uniqueById(mapped.duties, { it.id }, current.duties.map { it.id }.toSet())
            val expensesToWrite = uniqueById(mapped.expenses, { it.id }, current.expenses.map { it.id }.toSet())
            val financesToWrite = uniqueById(mapped.finances, { it.id }, current.finances.map { it.id }.toSet())
            val keyDatesToWrite = uniqueById(mapped.keyDates, { it.id }, current.keyDates.map { it.id }.toSet())
            val tasksToWrite = uniqueById(mapped.tasks, { it.id }, current.tasks.map { it.id }.toSet())
            val colleaguesToWrite = uniqueById(mapped.colleagues, { it.id }, current.colleagues.map { it.id }.toSet())
                .map { colleague ->
                    val images = colleaguePhotos[colleague.id]
                    if (images == null) colleague
                    else colleague.copy(photoPath = images.first, coverPath = images.second ?: colleague.coverPath)
                }

            val profileToWrite = if (options.importProfile) {
                BackupMapper.mapProfile(decoded)?.let { profile ->
                    profile.copy(photoPath = profilePhoto ?: current.profile.photoPath)
                }
            } else {
                null
            }

            // Sessions only come from this app's own backups.
            val sessionsToWrite: List<WorkSession> = decoded.workSessions.mapNotNull { row ->
                val session = BackupMapper.sanitizeWorkSession(row) ?: return@mapNotNull null
                val exists = current.workSessions.any { it.dateKey == session.dateKey }
                if (exists && !overwrite) null else session
            }

            val sessionsToDelete = if (options.replaceAll) current.workSessions.filter { old ->
                sessionsToWrite.none { it.dateKey == old.dateKey }
            } else emptyList()

            db.withTransaction {
                if (options.replaceAll) {
                    if (attendanceToWrite.isEmpty()) db.attendanceDao().deleteAll()
                    dutiesToWrite.ifEmpty { db.officialDutyDao().deleteAll() }
                    expensesToWrite.ifEmpty { db.expenseDao().deleteAll() }
                    financesToWrite.ifEmpty { db.financeDao().deleteAll() }
                    keyDatesToWrite.ifEmpty { db.keyDateDao().deleteAll() }
                    tasksToWrite.ifEmpty { db.dailyTaskDao().deleteAll() }
                    if (colleaguesToWrite.isEmpty()) {
                        db.colleagueDao().deleteAll()
                        db.colleagueDao().deleteAllInfo()
                    }
                }

                if (attendanceToWrite.isNotEmpty()) db.attendanceDao().upsertAll(attendanceToWrite.map { it.toEntity() })
                if (dutiesToWrite.isNotEmpty()) db.officialDutyDao().upsertAll(dutiesToWrite.map { it.toEntity() })
                if (expensesToWrite.isNotEmpty()) db.expenseDao().upsertAll(expensesToWrite.map { it.toEntity() })
                if (financesToWrite.isNotEmpty()) db.financeDao().upsertAll(financesToWrite.map { it.toEntity() })
                if (keyDatesToWrite.isNotEmpty()) db.keyDateDao().upsertAll(keyDatesToWrite.map { it.toEntity() })
                if (tasksToWrite.isNotEmpty()) db.dailyTaskDao().upsertAll(tasksToWrite.map { it.toEntity() })
                colleaguesToWrite.forEach { colleague ->
                    db.colleagueDao().saveWithInfo(colleague.toEntity(), colleague.toInfoEntities())
                }
                if (profileToWrite != null) {
                    db.profileDao().save(profileToWrite.toEntity(), profileToWrite.toInfoEntities())
                }
                sessionsToDelete.forEach { db.workSessionDao().delete(it.dateKey) }
                if (sessionsToWrite.isNotEmpty()) db.workSessionDao().upsertAll(sessionsToWrite.map { it.toEntity() })
            }

            // Only now is it safe to drop images nothing references.
            val referencedPhotos = mutableSetOf<String>()
            fun addPhoto(path: String?) {
                path?.let { name ->
                    if (!name.startsWith("data:") && !name.contains('/')) referencedPhotos.add(name)
                }
            }
            db.colleagueDao().getAll().forEach { addPhoto(it.photoPath); addPhoto(it.coverPath) }
            addPhoto(db.profileDao().getProfile()?.photoPath)
            mediaStore.pruneOrphans(referencedPhotos, referencedAttachments())

            ImportResult(preview = preview, replacedExisting = options.replaceAll, savedImages = savedImages)
        }

    private suspend fun referencedAttachments(): Set<String> =
        db.officialDutyDao().getAll().mapNotNull { it.attachmentPath?.let { path -> File(path).name } }.toSet()

    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction { db.maintenanceDao().wipeEverything() }
        mediaStore.pruneOrphans(emptySet(), emptySet())
    }

    /** Sample content matching the legacy defaults; only applied to an empty database. */
    suspend fun seedIfEmpty(today: java.time.LocalDate): Boolean = withContext(Dispatchers.IO) {
        val empty = db.maintenanceDao().attendanceCount() == 0 &&
            db.maintenanceDao().expenseCount() == 0 &&
            db.maintenanceDao().dutyCount() == 0 &&
            db.maintenanceDao().financeCount() == 0 &&
            db.maintenanceDao().keyDateCount() == 0 &&
            db.maintenanceDao().taskCount() == 0 &&
            db.maintenanceDao().colleagueCount() == 0
        if (!empty) return@withContext false
        com.soloadmin.hub.data.seed.SampleData.seed(db, today)
        true
    }

    /** Replaces the database with the bundled sample set (Settings → Reset). */
    suspend fun resetToSample(today: java.time.LocalDate) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.maintenanceDao().wipeEverything()
            com.soloadmin.hub.data.seed.SampleData.seed(db, today)
        }
        mediaStore.pruneOrphans(emptySet(), emptySet())
    }

    suspend fun counts(): Map<ImportGroup, Int> = withContext(Dispatchers.IO) {
        mapOf(
            ImportGroup.ATTENDANCE to db.maintenanceDao().attendanceCount(),
            ImportGroup.EXPENSES to db.maintenanceDao().expenseCount(),
            ImportGroup.OFFICIAL_DUTY to db.maintenanceDao().dutyCount(),
            ImportGroup.FINANCE to db.maintenanceDao().financeCount(),
            ImportGroup.KEY_DATES to db.maintenanceDao().keyDateCount(),
            ImportGroup.TASKS to db.maintenanceDao().taskCount(),
            ImportGroup.COLLEAGUES to db.maintenanceDao().colleagueCount()
        )
    }
}
