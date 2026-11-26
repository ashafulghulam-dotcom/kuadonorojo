package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "villages")
data class VillageEntity(
    @PrimaryKey val code: String,
    val name: String,
    val modinName: String,
    val modinPhone: String,
    val updatedAt: Long
)

@Entity(tableName = "staff_accounts")
data class StaffAccountEntity(
    @PrimaryKey val id: String,
    val username: String,
    val passwordHash: String,
    val passwordSalt: String,
    val role: String,
    val villageId: String,
    val villageName: String,
    val officerName: String,
    val whatsappNumber: String,
    val googleEmail: String = "",
    val active: Boolean,
    val isWaliHakim: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "marriage_schedules")
data class MarriageScheduleEntity(
    @PrimaryKey val id: String,
    val registrationNumber: String,
    val groomName: String,
    val groomNik: String,
    val brideName: String,
    val brideNik: String,
    val villageId: String,
    val villageName: String,
    val modinAccountId: String,
    val modinName: String,
    val weddingDate: String,
    val weddingTime: String,
    val locationType: String,
    val locationAddress: String,
    val penghuluAccountId: String = "",
    val penghuluName: String,
    val waliStatus: String = WaliStatus.WALI_NASAB.code,
    val waliRelationship: String = "",
    val waliName: String = "",
    val waliBin: String = "",
    val waliNik: String = "",
    val waliAddress: String = "",
    val waliHakimAccountId: String = "",
    val waliHakimReason: String = "",
    val validationStatus: String,
    val documentsComplete: Boolean,
    val documentChecklistCodes: String = "",
    val verificationNote: String,
    val verifiedByName: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "reminder_settings")
data class ReminderSettingsEntity(
    @PrimaryKey val id: String,
    val autoPhoneNotifEnabled: Boolean,
    val whatsappReminderEnabled: Boolean,
    val remindOnWeddingDay: Boolean,
    val remindOneDayBefore: Boolean,
    val remindThreeDaysBefore: Boolean,
    val reminderTime: String,
    val adminWhatsappNumber: String = "",
    val adminWhatsappApp: String = WhatsAppAppPreference.AUTO.code,
    val whatsappTemplate: String,
    val whatsappPenghuluTemplate: String = ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
    val headOfKuaTitle: String = ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE,
    val headOfKuaName: String = "",
    val headOfKuaNip: String = "",
    val defaultWaliHakimAccountId: String = "",
    val defaultWaliHakimName: String = "",
    val updatedByName: String,
    val updatedAt: Long
)

@Dao
interface KuaDao {
    @Query("SELECT * FROM villages ORDER BY name ASC")
    fun observeVillages(): Flow<List<VillageEntity>>

    @Query("SELECT * FROM villages ORDER BY name ASC")
    suspend fun getAllVillagesOnce(): List<VillageEntity>

    @Query("SELECT COUNT(*) FROM villages")
    suspend fun countVillages(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVillages(villages: List<VillageEntity>)

    @Query("UPDATE villages SET modinName = :modinName, modinPhone = :modinPhone, updatedAt = :updatedAt WHERE code = :code")
    suspend fun updateVillageModin(code: String, modinName: String, modinPhone: String, updatedAt: Long)

    @Query("UPDATE villages SET name = 'Desa Sumberrejo' WHERE code = 'sumberrejo' AND name != 'Desa Sumberrejo'")
    suspend fun normalizeSumberrejoVillage()

    @Query("UPDATE staff_accounts SET villageName = 'Desa Sumberrejo' WHERE villageId = 'sumberrejo' AND villageName != 'Desa Sumberrejo'")
    suspend fun normalizeSumberrejoStaffAccounts()

    @Query("UPDATE marriage_schedules SET villageName = 'Desa Sumberrejo' WHERE villageId = 'sumberrejo' AND villageName != 'Desa Sumberrejo'")
    suspend fun normalizeSumberrejoSchedules()

    @Query("SELECT * FROM staff_accounts")
    fun observeStaffAccounts(): Flow<List<StaffAccountEntity>>

    @Query("SELECT * FROM staff_accounts")
    suspend fun getAllStaffAccountsOnce(): List<StaffAccountEntity>

    @Query("SELECT COUNT(*) FROM staff_accounts")
    suspend fun countStaffAccounts(): Int

    @Query("SELECT COUNT(*) FROM staff_accounts WHERE role = :role")
    suspend fun countStaffByRole(role: String): Int

    @Query("SELECT * FROM staff_accounts WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getStaffAccountByUsername(username: String): StaffAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaffAccount(account: StaffAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStaffAccounts(accounts: List<StaffAccountEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStaffAccountsIfMissing(accounts: List<StaffAccountEntity>)

    @Query("SELECT * FROM staff_accounts WHERE role = :role")
    suspend fun getStaffByRoleOnce(role: String): List<StaffAccountEntity>

    @Query("SELECT * FROM staff_accounts WHERE id = :id LIMIT 1")
    suspend fun getStaffAccountById(id: String): StaffAccountEntity?

    @Query("UPDATE staff_accounts SET isWaliHakim = :isWaliHakim, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStaffWaliHakim(id: String, isWaliHakim: Boolean, updatedAt: Long)

    @Query("UPDATE staff_accounts SET whatsappNumber = :whatsappNumber, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStaffWhatsapp(id: String, whatsappNumber: String, updatedAt: Long)

    @Query("DELETE FROM staff_accounts WHERE id = :id")
    suspend fun deleteStaffAccount(id: String)

    @Query("SELECT * FROM marriage_schedules ORDER BY weddingDate ASC, weddingTime ASC")
    fun observeSchedules(): Flow<List<MarriageScheduleEntity>>

    @Query("SELECT * FROM marriage_schedules ORDER BY weddingDate ASC, weddingTime ASC")
    suspend fun getAllSchedulesOnce(): List<MarriageScheduleEntity>

    @Query("SELECT * FROM marriage_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): MarriageScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: MarriageScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedules(schedules: List<MarriageScheduleEntity>)

    @Query(
        "UPDATE marriage_schedules SET validationStatus = :status, documentsComplete = :docsComplete, " +
            "documentChecklistCodes = :checklistCodes, verificationNote = :note, " +
            "verifiedByName = :verifiedByName, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateVerification(
        id: String,
        status: String,
        docsComplete: Boolean,
        checklistCodes: String,
        note: String,
        verifiedByName: String,
        updatedAt: Long
    )

    @Query("DELETE FROM marriage_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: String)

    @Query("UPDATE marriage_schedules SET modinName = :modinName, updatedAt = :updatedAt WHERE modinAccountId = :accountId")
    suspend fun updateSchedulesModinName(accountId: String, modinName: String, updatedAt: Long)

    @Query("UPDATE marriage_schedules SET penghuluName = :penghuluName, updatedAt = :updatedAt WHERE penghuluAccountId = :accountId")
    suspend fun updateSchedulesPenghuluName(accountId: String, penghuluName: String, updatedAt: Long)

    @Query("UPDATE marriage_schedules SET waliName = :waliName, updatedAt = :updatedAt WHERE waliHakimAccountId = :accountId AND waliStatus = 'WALI_HAKIM'")
    suspend fun updateSchedulesWaliHakimName(accountId: String, waliName: String, updatedAt: Long)

    @Query("SELECT * FROM reminder_settings WHERE id = :id LIMIT 1")
    fun observeReminderSettings(id: String = ReminderSettings.GLOBAL_DOC_ID): Flow<ReminderSettingsEntity?>

    @Query("SELECT * FROM reminder_settings WHERE id = :id LIMIT 1")
    suspend fun getReminderSettingsOnce(id: String = ReminderSettings.GLOBAL_DOC_ID): ReminderSettingsEntity?

    @Query("SELECT COUNT(*) FROM reminder_settings")
    suspend fun countReminderSettings(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderSettings(settings: ReminderSettingsEntity)
}

@Database(
    entities = [
        VillageEntity::class,
        StaffAccountEntity::class,
        MarriageScheduleEntity::class,
        ReminderSettingsEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class KuaLocalDatabase : RoomDatabase() {
    abstract fun kuaDao(): KuaDao

    companion object {
        @Volatile
        private var INSTANCE: KuaLocalDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE marriage_schedules ADD COLUMN modinAccountId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE marriage_schedules ADD COLUMN modinName TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN headOfKuaTitle TEXT NOT NULL DEFAULT 'Kepala KUA Donorojo'"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN headOfKuaName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN headOfKuaNip TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN penghuluAccountId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE staff_accounts ADD COLUMN isWaliHakim INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN defaultWaliHakimAccountId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN defaultWaliHakimName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliStatus TEXT NOT NULL DEFAULT 'WALI_NASAB'"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliRelationship TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliName TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliBin TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliNik TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliAddress TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliHakimAccountId TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN waliHakimReason TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN adminWhatsappNumber TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN adminWhatsappApp TEXT NOT NULL DEFAULT 'AUTO'"
                )
                db.execSQL(
                    "ALTER TABLE reminder_settings ADD COLUMN whatsappPenghuluTemplate TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE staff_accounts ADD COLUMN googleEmail TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE marriage_schedules ADD COLUMN documentChecklistCodes TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): KuaLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KuaLocalDatabase::class.java,
                    "kua_donorojo_local.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
