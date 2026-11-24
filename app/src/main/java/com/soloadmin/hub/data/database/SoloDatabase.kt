package com.soloadmin.hub.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AttendanceEntity::class,
        OfficialDutyEntity::class,
        ExpenseEntity::class,
        FinanceEntity::class,
        KeyDateEntity::class,
        DailyTaskEntity::class,
        ColleagueEntity::class,
        ColleagueInfoEntity::class,
        ProfileEntity::class,
        ProfileInfoEntity::class,
        WorkSessionEntity::class
    ],
    version = SoloDatabase.VERSION,
    exportSchema = true
)
abstract class SoloDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao
    abstract fun officialDutyDao(): OfficialDutyDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun financeDao(): FinanceDao
    abstract fun keyDateDao(): KeyDateDao
    abstract fun dailyTaskDao(): DailyTaskDao
    abstract fun colleagueDao(): ColleagueDao
    abstract fun profileDao(): ProfileDao
    abstract fun workSessionDao(): WorkSessionDao
    abstract fun maintenanceDao(): MaintenanceDao

    companion object {
        const val VERSION = 1
        const val NAME = "solo_admin.db"

        @Volatile
        private var instance: SoloDatabase? = null

        fun get(context: Context): SoloDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): SoloDatabase =
            Room.databaseBuilder(context, SoloDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
