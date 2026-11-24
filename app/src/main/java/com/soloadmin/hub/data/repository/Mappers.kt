package com.soloadmin.hub.data.repository

import com.soloadmin.hub.data.database.AttendanceEntity
import com.soloadmin.hub.data.database.ColleagueEntity
import com.soloadmin.hub.data.database.ColleagueInfoEntity
import com.soloadmin.hub.data.database.DailyTaskEntity
import com.soloadmin.hub.data.database.ExpenseEntity
import com.soloadmin.hub.data.database.FinanceEntity
import com.soloadmin.hub.data.database.KeyDateEntity
import com.soloadmin.hub.data.database.OfficialDutyEntity
import com.soloadmin.hub.data.database.ProfileEntity
import com.soloadmin.hub.data.database.ProfileInfoEntity
import com.soloadmin.hub.data.database.WorkSessionEntity
import com.soloadmin.hub.domain.logic.Money
import com.soloadmin.hub.domain.model.AttendanceRecord
import com.soloadmin.hub.domain.model.AttendanceStatus
import com.soloadmin.hub.domain.model.ColleagueRecord
import com.soloadmin.hub.domain.model.ColleagueTheme
import com.soloadmin.hub.domain.model.DailyTaskRecord
import com.soloadmin.hub.domain.model.DutyMode
import com.soloadmin.hub.domain.model.ExpenseOwner
import com.soloadmin.hub.domain.model.ExpenseRecord
import com.soloadmin.hub.domain.model.FinanceRecord
import com.soloadmin.hub.domain.model.FinanceStatus
import com.soloadmin.hub.domain.model.InfoField
import com.soloadmin.hub.domain.model.KeyDateRecord
import com.soloadmin.hub.domain.model.OfficialDutyRecord
import com.soloadmin.hub.domain.model.ProfileRecord
import com.soloadmin.hub.domain.model.WorkSession

/** Entity ↔ domain translation. Nothing else in the app knows about Room row classes. */

fun AttendanceEntity.toDomain(): AttendanceRecord = AttendanceRecord(
    dateKey = dateKey,
    status = AttendanceStatus.fromStorage(status) ?: AttendanceStatus.PRESENT,
    leaveType = leaveType,
    inTime = inTime?.takeIf { it.isNotBlank() },
    outTime = outTime?.takeIf { it.isNotBlank() },
    note = note,
    officialDutyId = officialDutyId,
    breakMinutes = breakMinutes
)

fun AttendanceRecord.toEntity(updatedAt: Long = System.currentTimeMillis()): AttendanceEntity = AttendanceEntity(
    dateKey = dateKey,
    status = status.storageKey,
    leaveType = leaveType,
    inTime = inTime,
    outTime = outTime,
    note = note,
    officialDutyId = officialDutyId,
    breakMinutes = breakMinutes,
    updatedAt = updatedAt
)

fun OfficialDutyEntity.toDomain(): OfficialDutyRecord = OfficialDutyRecord(
    id = id,
    dateKey = dateKey,
    mode = DutyMode.fromLabel(mode),
    startTime = startTime,
    endTime = endTime,
    meetingWith = meetingWith,
    location = location,
    remarks = remarks,
    attachmentPath = attachmentPath,
    createdAt = createdAt
)

fun OfficialDutyRecord.toEntity(): OfficialDutyEntity = OfficialDutyEntity(
    id = id,
    dateKey = dateKey,
    mode = mode.label,
    startTime = startTime,
    endTime = endTime,
    meetingWith = meetingWith,
    location = location,
    remarks = remarks,
    attachmentPath = attachmentPath,
    createdAt = createdAt
)

fun ExpenseEntity.toDomain(): ExpenseRecord = ExpenseRecord(
    id = id,
    dateKey = dateKey,
    title = title,
    category = category,
    amount = Money(amountPaise),
    owner = ExpenseOwner.fromStorage(owner),
    recovered = recovered,
    note = note,
    createdAt = createdAt
)

fun ExpenseRecord.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    dateKey = dateKey,
    title = title,
    category = category,
    amountPaise = amount.paise,
    owner = owner.storageKey,
    recovered = recovered,
    note = note,
    createdAt = createdAt
)

fun FinanceEntity.toDomain(): FinanceRecord = FinanceRecord(
    id = id,
    dateKey = dateKey,
    title = title,
    amount = Money(amountPaise),
    status = if (FinanceStatus.isReceived(status)) FinanceStatus.RECEIVED else FinanceStatus.PENDING,
    createdAt = createdAt
)

fun FinanceRecord.toEntity(): FinanceEntity = FinanceEntity(
    id = id,
    dateKey = dateKey,
    title = title,
    amountPaise = amount.paise,
    status = status.storageKey,
    createdAt = createdAt
)

fun KeyDateEntity.toDomain(): KeyDateRecord = KeyDateRecord(
    id = id,
    title = title,
    category = category,
    validFrom = validFrom,
    validUntil = validUntil,
    note = note,
    createdAt = createdAt
)

fun KeyDateRecord.toEntity(): KeyDateEntity = KeyDateEntity(
    id = id,
    title = title,
    category = category,
    validFrom = validFrom,
    validUntil = validUntil,
    note = note,
    createdAt = createdAt
)

fun DailyTaskEntity.toDomain(): DailyTaskRecord = DailyTaskRecord(
    id = id,
    text = text,
    done = done,
    createdAt = createdAt,
    completedAt = completedAt,
    sortOrder = sortOrder
)

fun DailyTaskRecord.toEntity(): DailyTaskEntity = DailyTaskEntity(
    id = id,
    text = text,
    done = done,
    createdAt = createdAt,
    completedAt = completedAt,
    sortOrder = sortOrder
)

fun ColleagueEntity.toDomain(info: List<ColleagueInfoEntity>): ColleagueRecord = ColleagueRecord(
    id = id,
    name = name,
    role = role,
    theme = ColleagueTheme.fromKey(theme),
    photoPath = photoPath,
    coverPath = coverPath,
    info = info.sortedBy { it.position }.map { InfoField(it.label, it.value) },
    createdAt = createdAt,
    sortOrder = sortOrder
)

fun ColleagueRecord.toEntity(): ColleagueEntity = ColleagueEntity(
    id = id,
    name = name,
    role = role,
    theme = theme.key,
    photoPath = photoPath,
    coverPath = coverPath,
    createdAt = createdAt,
    sortOrder = sortOrder
)

fun ColleagueRecord.toInfoEntities(): List<ColleagueInfoEntity> =
    info.mapIndexed { index, field -> ColleagueInfoEntity(id, index, field.label, field.value) }

fun ProfileEntity.toDomain(info: List<ProfileInfoEntity>): ProfileRecord = ProfileRecord(
    name = name,
    bio = bio,
    photoPath = photoPath,
    weeklyOffDay = weeklyOffDay,
    shiftStart = shiftStart,
    info = info.sortedBy { it.position }.map { InfoField(it.label, it.value) }
)

fun ProfileRecord.toEntity(updatedAt: Long = System.currentTimeMillis()): ProfileEntity = ProfileEntity(
    name = name,
    bio = bio,
    photoPath = photoPath,
    weeklyOffDay = weeklyOffDay,
    shiftStart = shiftStart,
    updatedAt = updatedAt
)

fun ProfileRecord.toInfoEntities(): List<ProfileInfoEntity> =
    info.mapIndexed { index, field ->
        ProfileInfoEntity(ProfileEntity.SINGLETON_ID, index, field.label, field.value)
    }

fun WorkSessionEntity.toDomain(): WorkSession = WorkSession(
    dateKey = dateKey,
    inTime = inTime,
    outTime = outTime,
    onBreak = onBreak,
    breakStartedAt = breakStartedAt,
    breakMinutes = breakMinutes,
    sessionsCount = sessionsCount
)

fun WorkSession.toEntity(): WorkSessionEntity = WorkSessionEntity(
    dateKey = dateKey,
    inTime = inTime,
    outTime = outTime,
    onBreak = onBreak,
    breakStartedAt = breakStartedAt,
    breakMinutes = breakMinutes,
    sessionsCount = sessionsCount
)
