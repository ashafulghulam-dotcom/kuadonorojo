package com.example.data

import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

class KuaRepository(
    private val dao: KuaDao
) {
    /**
     * Automatically seeds the 8 official Donorojo villages, 9 staff accounts
     * (Admin KUA + 8 Modin Desa), and global Smart Reminder settings in local Room DB.
     */
    suspend fun ensureDefaultDonorojoData() {
        val now = System.currentTimeMillis()

        if (dao.countVillages() == 0) {
            val villages = DonorojoOfficialDirectory.eightModinPresets.map { preset ->
                VillageEntity(
                    code = preset.villageCode,
                    name = preset.villageName,
                    modinName = preset.defaultOfficerName,
                    modinPhone = "",
                    updatedAt = now
                )
            }
            dao.upsertVillages(villages)
        }

        if (dao.countStaffAccounts() == 0) {
            val accounts = mutableListOf<StaffAccountEntity>()

            val adminSalt = PasswordHasher.generateSalt()
            val adminHash = PasswordHasher.hashPassword(DEFAULT_ADMIN_PASSWORD, adminSalt)
            accounts.add(
                StaffAccountEntity(
                    id = DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID,
                    username = DEFAULT_ADMIN_USERNAME,
                    passwordHash = adminHash,
                    passwordSalt = adminSalt,
                    role = UserRole.ADMIN.code,
                    villageId = "",
                    villageName = "KUA Donorojo",
                    officerName = "Admin KUA Donorojo",
                    whatsappNumber = "",
                    active = true,
                    createdAt = now,
                    updatedAt = now
                )
            )

            for (preset in DonorojoOfficialDirectory.eightModinPresets) {
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hashPassword(preset.defaultPassword, salt)
                accounts.add(
                    StaffAccountEntity(
                        id = preset.accountId,
                        username = preset.defaultUsername,
                        passwordHash = hash,
                        passwordSalt = salt,
                        role = UserRole.MODIN.code,
                        villageId = preset.villageCode,
                        villageName = preset.villageName,
                        officerName = preset.defaultOfficerName,
                        whatsappNumber = "",
                        active = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            dao.insertStaffAccountsIfMissing(accounts)
        }

        if (dao.countReminderSettings() == 0) {
            dao.upsertReminderSettings(
                ReminderSettingsEntity(
                    id = ReminderSettings.GLOBAL_DOC_ID,
                    autoPhoneNotifEnabled = true,
                    whatsappReminderEnabled = true,
                    remindOnWeddingDay = true,
                    remindOneDayBefore = true,
                    remindThreeDaysBefore = false,
                    reminderTime = "07:00",
                    whatsappTemplate = ReminderSettings.DEFAULT_WA_TEMPLATE,
                    updatedByName = "Admin KUA Donorojo",
                    updatedAt = now
                )
            )
        }
    }

    fun observeAllVillages(): Flow<List<Village>> =
        dao.observeVillages().map { list ->
            list.map {
                Village(
                    id = it.code,
                    name = it.name,
                    code = it.code,
                    modinName = it.modinName,
                    modinPhone = it.modinPhone,
                    updatedAt = it.updatedAt
                )
            }
        }

    fun observeStaffAccounts(): Flow<List<StaffAccount>> =
        dao.observeStaffAccounts().map { list ->
            list.map {
                StaffAccount(
                    id = it.id,
                    username = it.username,
                    passwordHash = it.passwordHash,
                    passwordSalt = it.passwordSalt,
                    role = it.role,
                    villageId = it.villageId,
                    villageName = it.villageName,
                    officerName = it.officerName,
                    whatsappNumber = it.whatsappNumber,
                    active = it.active,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }.sortedWith(
                compareBy<StaffAccount> { if (it.resolvedRole() == UserRole.ADMIN) 0 else 1 }
                    .thenBy { it.villageName.lowercase(Locale.ROOT) }
                    .thenBy { it.officerName.lowercase(Locale.ROOT) }
            )
        }

    fun observeAllSchedules(): Flow<List<MarriageSchedule>> =
        dao.observeSchedules().map { list ->
            list.map {
                MarriageSchedule(
                    id = it.id,
                    registrationNumber = it.registrationNumber,
                    groomName = it.groomName,
                    groomNik = it.groomNik,
                    brideName = it.brideName,
                    brideNik = it.brideNik,
                    villageId = it.villageId,
                    villageName = it.villageName,
                    modinAccountId = it.modinAccountId,
                    modinName = it.modinName,
                    weddingDate = it.weddingDate,
                    weddingTime = it.weddingTime,
                    locationType = it.locationType,
                    locationAddress = it.locationAddress,
                    penghuluName = it.penghuluName,
                    validationStatus = it.validationStatus,
                    documentsComplete = it.documentsComplete,
                    verificationNote = it.verificationNote,
                    verifiedByName = it.verifiedByName,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
        }

    fun observeReminderSettings(): Flow<ReminderSettings> =
        dao.observeReminderSettings().map { entity ->
            if (entity == null) {
                ReminderSettings()
            } else {
                ReminderSettings(
                    id = entity.id,
                    autoPhoneNotifEnabled = entity.autoPhoneNotifEnabled,
                    whatsappReminderEnabled = entity.whatsappReminderEnabled,
                    remindOnWeddingDay = entity.remindOnWeddingDay,
                    remindOneDayBefore = entity.remindOneDayBefore,
                    remindThreeDaysBefore = entity.remindThreeDaysBefore,
                    reminderTime = entity.reminderTime,
                    whatsappTemplate = entity.whatsappTemplate,
                    updatedByName = entity.updatedByName,
                    updatedAt = entity.updatedAt
                )
            }
        }

    suspend fun createModinAccount(
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        active: Boolean
    ): Result<Unit> {
        val cleanUsername = username.trim().lowercase(Locale.ROOT)
        val cleanOfficer = officerName.trim()
        val cleanPhone = whatsappNumber.trim()

        if (cleanUsername.length !in 3..50 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUsername)) {
            return Result.failure(
                IllegalArgumentException("Username harus 3–50 karakter (huruf kecil, angka, titik, garis bawah).")
            )
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password minimal 4 karakter."))
        }
        if (cleanOfficer.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama Modin harus 2–100 karakter."))
        }
        if (cleanPhone.length > 30) {
            return Result.failure(IllegalArgumentException("Nomor WhatsApp maksimal 30 karakter."))
        }

        return try {
            val duplicate = dao.getStaffAccountByUsername(cleanUsername)
            if (duplicate != null) {
                return Result.failure(
                    IllegalArgumentException("Username \"$cleanUsername\" sudah digunakan petugas lain.")
                )
            }
            val now = System.currentTimeMillis()
            val salt = PasswordHasher.generateSalt()
            val hash = PasswordHasher.hashPassword(password, salt)
            val newId = "modin_${village.code}_${UUID.randomUUID().toString().take(8)}"

            dao.upsertStaffAccount(
                StaffAccountEntity(
                    id = newId,
                    username = cleanUsername,
                    passwordHash = hash,
                    passwordSalt = salt,
                    role = UserRole.MODIN.code,
                    villageId = village.code,
                    villageName = village.name,
                    officerName = cleanOfficer,
                    whatsappNumber = cleanPhone,
                    active = active,
                    createdAt = now,
                    updatedAt = now
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateStaffAccount(
        account: StaffAccount,
        newUsername: String,
        newPassword: String?,
        officerName: String,
        whatsappNumber: String,
        active: Boolean,
        newVillage: Village? = null
    ): Result<Unit> {
        val cleanUsername = newUsername.trim().lowercase(Locale.ROOT)
        val cleanOfficer = officerName.trim()
        val cleanPhone = whatsappNumber.trim()

        if (cleanUsername.length !in 3..50 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUsername)) {
            return Result.failure(
                IllegalArgumentException("Username harus 3–50 karakter (huruf kecil, angka, titik, garis bawah).")
            )
        }
        if (!newPassword.isNullOrBlank() && newPassword.length < 4) {
            return Result.failure(IllegalArgumentException("Password baru minimal 4 karakter."))
        }
        if (cleanOfficer.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama petugas harus 2–100 karakter."))
        }
        if (cleanPhone.length > 30) {
            return Result.failure(IllegalArgumentException("Nomor WhatsApp maksimal 30 karakter."))
        }

        return try {
            val duplicate = dao.getStaffAccountByUsername(cleanUsername)
            if (duplicate != null && duplicate.id != account.id) {
                return Result.failure(
                    IllegalArgumentException("Username \"$cleanUsername\" sudah digunakan petugas lain.")
                )
            }
            val now = System.currentTimeMillis()
            val salt = if (!newPassword.isNullOrBlank()) PasswordHasher.generateSalt() else account.passwordSalt
            val hash = if (!newPassword.isNullOrBlank()) PasswordHasher.hashPassword(newPassword, salt) else account.passwordHash
            val targetVillageId = if (account.resolvedRole() == UserRole.MODIN && newVillage != null) {
                newVillage.code
            } else {
                account.villageId
            }
            val targetVillageName = if (account.resolvedRole() == UserRole.MODIN && newVillage != null) {
                newVillage.name
            } else {
                account.villageName
            }

            dao.upsertStaffAccount(
                StaffAccountEntity(
                    id = account.id,
                    username = cleanUsername,
                    passwordHash = hash,
                    passwordSalt = salt,
                    role = account.role,
                    villageId = targetVillageId,
                    villageName = targetVillageName,
                    officerName = cleanOfficer,
                    whatsappNumber = cleanPhone,
                    active = active,
                    createdAt = account.createdAt,
                    updatedAt = now
                )
            )

            if (account.resolvedRole() == UserRole.MODIN && targetVillageId.isNotBlank()) {
                dao.updateVillageModin(
                    code = targetVillageId,
                    modinName = cleanOfficer,
                    modinPhone = cleanPhone,
                    updatedAt = now
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStaffAccount(account: StaffAccount): Result<Unit> {
        if (account.resolvedRole() == UserRole.ADMIN || account.id == DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID) {
            return Result.failure(IllegalArgumentException("Akun Admin KUA utama tidak dapat dihapus."))
        }
        return try {
            dao.deleteStaffAccount(account.id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveReminderSettings(
        settings: ReminderSettings,
        updatedByName: String
    ): Result<Unit> {
        val cleanTemplate = settings.whatsappTemplate.trim()
        val cleanTime = settings.reminderTime.trim()
        if (!Regex("^[0-9]{2}:[0-9]{2}$").matches(cleanTime)) {
            return Result.failure(IllegalArgumentException("Format jam pengingat harus HH:mm."))
        }
        if (cleanTemplate.length !in 5..800) {
            return Result.failure(IllegalArgumentException("Template pesan WA harus 5–800 karakter."))
        }

        return try {
            dao.upsertReminderSettings(
                ReminderSettingsEntity(
                    id = ReminderSettings.GLOBAL_DOC_ID,
                    autoPhoneNotifEnabled = settings.autoPhoneNotifEnabled,
                    whatsappReminderEnabled = settings.whatsappReminderEnabled,
                    remindOnWeddingDay = settings.remindOnWeddingDay,
                    remindOneDayBefore = settings.remindOneDayBefore,
                    remindThreeDaysBefore = settings.remindThreeDaysBefore,
                    reminderTime = cleanTime,
                    whatsappTemplate = cleanTemplate,
                    updatedByName = updatedByName.take(100),
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSchedule(
        draft: ScheduleDraft,
        village: Village,
        verifiedByName: String
    ): Result<Unit> {
        val regNum = draft.registrationNumber.trim()
        val groom = draft.groomName.trim()
        val groomNik = draft.groomNik.trim()
        val bride = draft.brideName.trim()
        val brideNik = draft.brideNik.trim()
        val weddingDate = draft.weddingDate.trim()
        val weddingTime = draft.weddingTime.trim()
        val address = draft.locationAddress.trim()
        val penghulu = draft.penghuluName.trim()
        val note = draft.verificationNote.trim()

        if (regNum.length !in 3..40) {
            return Result.failure(IllegalArgumentException("Nomor pendaftaran harus 3–40 karakter."))
        }
        if (groom.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama calon suami harus 2–100 karakter."))
        }
        if (bride.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama calon istri harus 2–100 karakter."))
        }
        if (groomNik.length > 20 || brideNik.length > 20) {
            return Result.failure(IllegalArgumentException("NIK maksimal 20 karakter."))
        }
        if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(weddingDate)) {
            return Result.failure(IllegalArgumentException("Format tanggal akad harus YYYY-MM-DD."))
        }
        if (!Regex("^[0-9]{2}:[0-9]{2}$").matches(weddingTime)) {
            return Result.failure(IllegalArgumentException("Format jam akad harus HH:mm."))
        }
        if (address.length !in 2..250) {
            return Result.failure(IllegalArgumentException("Alamat akad harus 2–250 karakter."))
        }
        if (penghulu.length > 100) {
            return Result.failure(IllegalArgumentException("Nama penghulu maksimal 100 karakter."))
        }
        if (note.length > 500) {
            return Result.failure(IllegalArgumentException("Catatan verifikasi maksimal 500 karakter."))
        }

        return try {
            val now = System.currentTimeMillis()
            val id = draft.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val existing = if (!draft.id.isNullOrBlank()) dao.getScheduleById(id) else null
            val verifier = if (draft.validationStatus == ValidationStatus.TERVERIFIKASI ||
                draft.validationStatus == ValidationStatus.SELESAI
            ) {
                verifiedByName.take(100)
            } else {
                ""
            }

            dao.upsertSchedule(
                MarriageScheduleEntity(
                    id = id,
                    registrationNumber = regNum,
                    groomName = groom,
                    groomNik = groomNik,
                    brideName = bride,
                    brideNik = brideNik,
                    villageId = village.code,
                    villageName = village.name,
                    modinAccountId = draft.modinAccountId.trim(),
                    modinName = draft.modinName.trim().take(100),
                    weddingDate = weddingDate,
                    weddingTime = weddingTime,
                    locationType = draft.locationType.code,
                    locationAddress = address,
                    penghuluName = penghulu,
                    validationStatus = draft.validationStatus.code,
                    documentsComplete = draft.documentsComplete,
                    verificationNote = note,
                    verifiedByName = verifier,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVerificationStatus(
        scheduleId: String,
        newStatus: ValidationStatus,
        documentsComplete: Boolean,
        verificationNote: String,
        verifiedByName: String
    ): Result<Unit> {
        val cleanNote = verificationNote.trim()
        if (cleanNote.length > 500) {
            return Result.failure(IllegalArgumentException("Catatan verifikasi maksimal 500 karakter."))
        }
        return try {
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong." }
            dao.updateVerification(
                id = scheduleId,
                status = newStatus.code,
                docsComplete = documentsComplete,
                note = cleanNote,
                verifiedByName = verifiedByName.trim().take(100),
                updatedAt = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong." }
            dao.deleteSchedule(scheduleId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
