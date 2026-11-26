package com.example.data

import com.example.util.KuaBackupPayload
import com.example.util.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

class KuaRepository(
    private val dao: KuaDao,
    private val firestoreSync: KuaFirestoreSyncService? = null
) {
    /**
     * Automatically seeds the 8 official Donorojo villages, official staff accounts
     * (Admin KUA + Penghulu + 8 Modin Desa), and global Smart Reminder settings in local Room DB.
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
                    googleEmail = "",
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
                        googleEmail = "",
                        active = true,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            dao.insertStaffAccountsIfMissing(accounts)
        }

        if (dao.countStaffByRole(UserRole.PENGHULU.code) == 0) {
            val penghuluAccounts = DonorojoOfficialDirectory.defaultPenghuluPresets.map { preset ->
                val salt = PasswordHasher.generateSalt()
                val hash = PasswordHasher.hashPassword(preset.defaultPassword, salt)
                StaffAccountEntity(
                    id = preset.accountId,
                    username = preset.defaultUsername,
                    passwordHash = hash,
                    passwordSalt = salt,
                    role = UserRole.PENGHULU.code,
                    villageId = "",
                    villageName = "KUA Donorojo",
                    officerName = preset.defaultOfficerName,
                    whatsappNumber = "",
                    googleEmail = "",
                    active = true,
                    isWaliHakim = true,
                    createdAt = now,
                    updatedAt = now
                )
            }
            dao.insertStaffAccountsIfMissing(penghuluAccounts)
        }

        if (dao.countReminderSettings() == 0) {
            val adminPhone = dao.getStaffAccountById(DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID)
                ?.whatsappNumber
                .orEmpty()
            dao.upsertReminderSettings(
                ReminderSettingsEntity(
                    id = ReminderSettings.GLOBAL_DOC_ID,
                    autoPhoneNotifEnabled = true,
                    whatsappReminderEnabled = true,
                    remindOnWeddingDay = true,
                    remindOneDayBefore = true,
                    remindThreeDaysBefore = false,
                    reminderTime = "07:00",
                    adminWhatsappNumber = adminPhone,
                    adminWhatsappApp = WhatsAppAppPreference.AUTO.code,
                    whatsappTemplate = ReminderSettings.DEFAULT_WA_TEMPLATE,
                    whatsappPenghuluTemplate = ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
                    headOfKuaTitle = ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE,
                    headOfKuaName = "",
                    headOfKuaNip = "",
                    updatedByName = "Admin KUA Donorojo",
                    updatedAt = now
                )
            )
        } else {
            val existingSettings = dao.getReminderSettingsOnce()
            val adminAccount = dao.getStaffAccountById(DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID)
            if (existingSettings != null &&
                existingSettings.adminWhatsappNumber.isBlank() &&
                !adminAccount?.whatsappNumber.isNullOrBlank()
            ) {
                dao.upsertReminderSettings(
                    existingSettings.copy(
                        adminWhatsappNumber = adminAccount!!.whatsappNumber.trim(),
                        whatsappPenghuluTemplate = existingSettings.whatsappPenghuluTemplate
                            .ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE },
                        updatedAt = now
                    )
                )
            }
        }

        dao.normalizeSumberrejoVillage()
        dao.normalizeSumberrejoStaffAccounts()
        dao.normalizeSumberrejoSchedules()
    }

    fun observeAllVillages(): Flow<List<Village>> =
        dao.observeVillages().map { list -> list.map { it.toDomain() } }

    fun observeStaffAccounts(): Flow<List<StaffAccount>> =
        dao.observeStaffAccounts().map { list ->
            list.map { it.toDomain() }.sortedWith(
                compareBy<StaffAccount> {
                    when (it.resolvedRole()) {
                        UserRole.ADMIN -> 0
                        UserRole.PENGHULU -> 1
                        UserRole.MODIN -> 2
                    }
                }
                    .thenBy { it.villageName.lowercase(Locale.ROOT) }
                    .thenBy { it.officerName.lowercase(Locale.ROOT) }
            )
        }

    fun observeAllSchedules(): Flow<List<MarriageSchedule>> =
        dao.observeSchedules().map { list -> list.map { it.toDomain() } }

    fun observeReminderSettings(): Flow<ReminderSettings> =
        dao.observeReminderSettings().map { entity -> entity?.toDomain() ?: ReminderSettings() }

    suspend fun getAllVillagesOnce(): List<Village> =
        dao.getAllVillagesOnce().map { it.toDomain() }

    suspend fun getAllStaffAccountsOnce(): List<StaffAccount> =
        dao.getAllStaffAccountsOnce().map { it.toDomain() }

    suspend fun getAllSchedulesOnce(): List<MarriageSchedule> =
        dao.getAllSchedulesOnce().map { it.toDomain() }

    suspend fun getReminderSettingsOnce(): ReminderSettings =
        dao.getReminderSettingsOnce()?.toDomain() ?: ReminderSettings()

    // --- Real-Time Cloud Merge Helpers ---

    suspend fun mergeRemoteVillages(remoteVillages: List<Village>) {
        if (remoteVillages.isEmpty()) return
        val localMap = dao.getAllVillagesOnce().associateBy { it.code }
        val toUpsert = remoteVillages.mapNotNull { remote ->
            val local = localMap[remote.code]
            if (local == null || remote.updatedAt >= local.updatedAt) {
                VillageEntity(
                    code = remote.code,
                    name = if (remote.code == "sumberrejo") "Desa Sumberrejo" else remote.name,
                    modinName = remote.modinName,
                    modinPhone = remote.modinPhone,
                    updatedAt = remote.updatedAt
                )
            } else {
                null
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertVillages(toUpsert)
        }
    }

    suspend fun mergeRemoteStaffAccounts(remoteAccounts: List<StaffAccount>) {
        if (remoteAccounts.isEmpty()) return
        val localMap = dao.getAllStaffAccountsOnce().associateBy { it.id }
        val toUpsert = remoteAccounts.mapNotNull { remote ->
            val local = localMap[remote.id]
            if (local == null || remote.updatedAt >= local.updatedAt) {
                remote.toEntity()
            } else {
                null
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertStaffAccounts(toUpsert)
        }
    }

    suspend fun mergeRemoteReminderSettings(remote: ReminderSettings?) {
        if (remote == null) return
        val local = dao.getReminderSettingsOnce()
        if (local == null || remote.updatedAt >= local.updatedAt) {
            dao.upsertReminderSettings(remote.toEntity())
        }
    }

    suspend fun mergeRemoteSchedules(remoteSchedules: List<MarriageSchedule>) {
        if (remoteSchedules.isEmpty()) return
        val localMap = dao.getAllSchedulesOnce().associateBy { it.id }
        val toUpsert = remoteSchedules.mapNotNull { remote ->
            val local = localMap[remote.id]
            if (local == null || remote.updatedAt >= local.updatedAt) {
                remote.toEntity()
            } else {
                null
            }
        }
        if (toUpsert.isNotEmpty()) {
            dao.upsertSchedules(toUpsert)
        }
    }

    suspend fun syncAllLocalToCloud(): Result<Int> {
        val sync = firestoreSync ?: return Result.success(0)
        return sync.syncAllLocalToCloud(
            villages = getAllVillagesOnce(),
            staffAccounts = getAllStaffAccountsOnce(),
            settings = getReminderSettingsOnce(),
            schedules = getAllSchedulesOnce()
        )
    }

    suspend fun restoreFromBackup(payload: KuaBackupPayload): Result<String> {
        return try {
            val now = System.currentTimeMillis()
            if (payload.villages.isNotEmpty()) {
                dao.upsertVillages(
                    payload.villages.map { v ->
                        VillageEntity(
                            code = v.code,
                            name = if (v.code == "sumberrejo") "Desa Sumberrejo" else v.name,
                            modinName = v.modinName,
                            modinPhone = v.modinPhone,
                            updatedAt = maxOf(v.updatedAt, now)
                        )
                    }
                )
            }
            if (payload.staffAccounts.isNotEmpty()) {
                dao.upsertStaffAccounts(
                    payload.staffAccounts.map { it.copy(updatedAt = maxOf(it.updatedAt, now)).toEntity() }
                )
            }
            if (payload.schedules.isNotEmpty()) {
                dao.upsertSchedules(
                    payload.schedules.map { it.copy(updatedAt = maxOf(it.updatedAt, now)).toEntity() }
                )
            }
            payload.reminderSettings?.let { settings ->
                dao.upsertReminderSettings(settings.copy(updatedAt = maxOf(settings.updatedAt, now)).toEntity())
            }

            dao.normalizeSumberrejoVillage()
            dao.normalizeSumberrejoStaffAccounts()
            dao.normalizeSumberrejoSchedules()

            // Push restored data to Firestore cloud if signed in
            syncAllLocalToCloud()

            Result.success(
                "Berhasil memulihkan ${payload.schedules.size} jadwal nikah dan ${payload.staffAccounts.size} akun petugas."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Staff Account Mutations ---

    suspend fun createModinAccount(
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String = "",
        active: Boolean
    ): Result<Unit> {
        val cleanUsername = username.trim().lowercase(Locale.ROOT)
        val cleanOfficer = officerName.trim()
        val cleanPhone = whatsappNumber.trim()
        val cleanEmail = googleEmail.trim().lowercase(Locale.ROOT)

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
        if (cleanEmail.isNotEmpty() && (!cleanEmail.contains("@") || cleanEmail.length > 150)) {
            return Result.failure(IllegalArgumentException("Format email Google petugas tidak valid."))
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

            val entity = StaffAccountEntity(
                id = newId,
                username = cleanUsername,
                passwordHash = hash,
                passwordSalt = salt,
                role = UserRole.MODIN.code,
                villageId = village.code,
                villageName = village.name,
                officerName = cleanOfficer,
                whatsappNumber = cleanPhone,
                googleEmail = cleanEmail,
                active = active,
                createdAt = now,
                updatedAt = now
            )
            dao.upsertStaffAccount(entity)
            firestoreSync?.pushStaffAccount(entity.toDomain())
            if (cleanEmail.isNotEmpty()) {
                syncReaderEmailsToCloud()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPenghuluAccount(
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String = "",
        active: Boolean,
        isWaliHakim: Boolean = false
    ): Result<Unit> {
        val cleanUsername = username.trim().lowercase(Locale.ROOT)
        val cleanOfficer = officerName.trim()
        val cleanPhone = whatsappNumber.trim()
        val cleanEmail = googleEmail.trim().lowercase(Locale.ROOT)

        if (cleanUsername.length !in 3..50 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUsername)) {
            return Result.failure(
                IllegalArgumentException("Username harus 3–50 karakter (huruf kecil, angka, titik, garis bawah).")
            )
        }
        if (password.length < 4) {
            return Result.failure(IllegalArgumentException("Password minimal 4 karakter."))
        }
        if (cleanOfficer.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama Penghulu harus 2–100 karakter."))
        }
        if (cleanPhone.length > 30) {
            return Result.failure(IllegalArgumentException("Nomor WhatsApp maksimal 30 karakter."))
        }
        if (cleanEmail.isNotEmpty() && (!cleanEmail.contains("@") || cleanEmail.length > 150)) {
            return Result.failure(IllegalArgumentException("Format email Google petugas tidak valid."))
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
            val newId = "penghulu_${UUID.randomUUID().toString().take(8)}"

            val entity = StaffAccountEntity(
                id = newId,
                username = cleanUsername,
                passwordHash = hash,
                passwordSalt = salt,
                role = UserRole.PENGHULU.code,
                villageId = "",
                villageName = "KUA Donorojo",
                officerName = cleanOfficer,
                whatsappNumber = cleanPhone,
                googleEmail = cleanEmail,
                active = active,
                isWaliHakim = isWaliHakim,
                createdAt = now,
                updatedAt = now
            )
            dao.upsertStaffAccount(entity)
            firestoreSync?.pushStaffAccount(entity.toDomain())
            if (cleanEmail.isNotEmpty()) {
                syncReaderEmailsToCloud()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
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
        googleEmail: String = account.googleEmail,
        active: Boolean,
        newVillage: Village? = null,
        isWaliHakim: Boolean = account.isWaliHakim
    ): Result<Unit> {
        val cleanUsername = newUsername.trim().lowercase(Locale.ROOT)
        val cleanOfficer = officerName.trim()
        val cleanPhone = whatsappNumber.trim()
        val cleanEmail = googleEmail.trim().lowercase(Locale.ROOT)

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
        if (cleanEmail.isNotEmpty() && (!cleanEmail.contains("@") || cleanEmail.length > 150)) {
            return Result.failure(IllegalArgumentException("Format email Google petugas tidak valid."))
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
            val resolvedWaliHakim = if (account.resolvedRole() == UserRole.PENGHULU) isWaliHakim else false

            val updatedEntity = StaffAccountEntity(
                id = account.id,
                username = cleanUsername,
                passwordHash = hash,
                passwordSalt = salt,
                role = account.role,
                villageId = targetVillageId,
                villageName = targetVillageName,
                officerName = cleanOfficer,
                whatsappNumber = cleanPhone,
                googleEmail = cleanEmail,
                active = active,
                isWaliHakim = resolvedWaliHakim,
                createdAt = account.createdAt,
                updatedAt = now
            )
            dao.upsertStaffAccount(updatedEntity)

            if (account.resolvedRole() == UserRole.MODIN) {
                if (targetVillageId.isNotBlank()) {
                    dao.updateVillageModin(
                        code = targetVillageId,
                        modinName = cleanOfficer,
                        modinPhone = cleanPhone,
                        updatedAt = now
                    )
                }
                dao.updateSchedulesModinName(account.id, cleanOfficer, now)
            }
            if (account.resolvedRole() == UserRole.PENGHULU) {
                dao.updateSchedulesPenghuluName(account.id, cleanOfficer, now)
                dao.updateSchedulesWaliHakimName(account.id, cleanOfficer, now)
                val currentSettings = dao.getReminderSettingsOnce()
                if (currentSettings != null && currentSettings.defaultWaliHakimAccountId == account.id) {
                    dao.upsertReminderSettings(
                        currentSettings.copy(
                            defaultWaliHakimName = cleanOfficer.take(120),
                            updatedAt = now
                        )
                    )
                }
            }
            if (account.resolvedRole() == UserRole.ADMIN || account.id == DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID) {
                val currentSettings = dao.getReminderSettingsOnce()
                dao.upsertReminderSettings(
                    ReminderSettingsEntity(
                        id = ReminderSettings.GLOBAL_DOC_ID,
                        autoPhoneNotifEnabled = currentSettings?.autoPhoneNotifEnabled ?: true,
                        whatsappReminderEnabled = currentSettings?.whatsappReminderEnabled ?: true,
                        remindOnWeddingDay = currentSettings?.remindOnWeddingDay ?: true,
                        remindOneDayBefore = currentSettings?.remindOneDayBefore ?: true,
                        remindThreeDaysBefore = currentSettings?.remindThreeDaysBefore ?: false,
                        reminderTime = currentSettings?.reminderTime ?: "07:00",
                        adminWhatsappNumber = cleanPhone,
                        adminWhatsappApp = currentSettings?.adminWhatsappApp ?: WhatsAppAppPreference.AUTO.code,
                        whatsappTemplate = currentSettings?.whatsappTemplate ?: ReminderSettings.DEFAULT_WA_TEMPLATE,
                        whatsappPenghuluTemplate = currentSettings?.whatsappPenghuluTemplate
                            ?.ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }
                            ?: ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
                        headOfKuaTitle = currentSettings?.headOfKuaTitle ?: ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE,
                        headOfKuaName = currentSettings?.headOfKuaName ?: "",
                        headOfKuaNip = currentSettings?.headOfKuaNip ?: "",
                        defaultWaliHakimAccountId = currentSettings?.defaultWaliHakimAccountId ?: "",
                        defaultWaliHakimName = currentSettings?.defaultWaliHakimName ?: "",
                        updatedByName = cleanOfficer.take(100),
                        updatedAt = now
                    )
                )
            }

            firestoreSync?.pushStaffAccount(updatedEntity.toDomain())
            if (cleanEmail != account.googleEmail.trim().lowercase(Locale.ROOT) ||
                account.resolvedRole() == UserRole.ADMIN
            ) {
                syncReaderEmailsToCloud()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(
        accountId: String,
        newOfficerName: String,
        newWhatsappNumber: String,
        newUsername: String,
        currentPassword: String,
        newPassword: String?
    ): Result<StaffAccount> {
        val cleanOfficer = newOfficerName.trim()
        val cleanPhone = newWhatsappNumber.trim()
        val cleanUsername = newUsername.trim().lowercase(Locale.ROOT)
        val cleanNewPass = newPassword?.trim().orEmpty()

        if (cleanOfficer.length !in 2..100) {
            return Result.failure(IllegalArgumentException("Nama lengkap harus 2–100 karakter."))
        }
        if (cleanPhone.length > 30) {
            return Result.failure(IllegalArgumentException("Nomor WhatsApp maksimal 30 karakter."))
        }
        if (cleanUsername.length !in 3..50 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUsername)) {
            return Result.failure(
                IllegalArgumentException("Username harus 3–50 karakter (huruf kecil, angka, titik, _ atau -).")
            )
        }
        if (cleanNewPass.isNotEmpty() && cleanNewPass.length < 4) {
            return Result.failure(IllegalArgumentException("Password baru minimal 4 karakter."))
        }

        return try {
            val stored = dao.getStaffAccountById(accountId)
                ?: return Result.failure(IllegalArgumentException("Data akun petugas tidak ditemukan."))
            if (!stored.active) {
                return Result.failure(IllegalArgumentException("Akun Anda sedang nonaktif. Hubungi Admin KUA Donorojo."))
            }

            val usernameChanged = !stored.username.equals(cleanUsername, ignoreCase = true)
            val passwordChanged = cleanNewPass.isNotEmpty()

            if (usernameChanged || passwordChanged) {
                if (currentPassword.isBlank()) {
                    return Result.failure(
                        IllegalArgumentException("Masukkan password saat ini untuk mengubah username atau password.")
                    )
                }
                val verified = PasswordHasher.verifyPassword(
                    inputPassword = currentPassword.trim(),
                    salt = stored.passwordSalt,
                    expectedHash = stored.passwordHash
                )
                if (!verified) {
                    return Result.failure(
                        IllegalArgumentException("Password saat ini tidak sesuai. Periksa kembali password Anda.")
                    )
                }
            }

            if (usernameChanged) {
                val duplicate = dao.getStaffAccountByUsername(cleanUsername)
                if (duplicate != null && duplicate.id != stored.id) {
                    return Result.failure(
                        IllegalArgumentException("Username \"$cleanUsername\" sudah digunakan petugas lain.")
                    )
                }
            }

            val now = System.currentTimeMillis()
            val updatedSalt = if (passwordChanged) PasswordHasher.generateSalt() else stored.passwordSalt
            val updatedHash = if (passwordChanged) {
                PasswordHasher.hashPassword(cleanNewPass, updatedSalt)
            } else {
                stored.passwordHash
            }

            val updatedEntity = stored.copy(
                username = cleanUsername,
                passwordHash = updatedHash,
                passwordSalt = updatedSalt,
                officerName = cleanOfficer,
                whatsappNumber = cleanPhone,
                updatedAt = now
            )
            dao.upsertStaffAccount(updatedEntity)

            val resolvedRole = UserRole.fromCode(stored.role)
            if (resolvedRole == UserRole.MODIN) {
                if (stored.villageId.isNotBlank()) {
                    dao.updateVillageModin(
                        code = stored.villageId,
                        modinName = cleanOfficer,
                        modinPhone = cleanPhone,
                        updatedAt = now
                    )
                }
                dao.updateSchedulesModinName(stored.id, cleanOfficer, now)
            } else if (resolvedRole == UserRole.PENGHULU) {
                dao.updateSchedulesPenghuluName(stored.id, cleanOfficer, now)
                dao.updateSchedulesWaliHakimName(stored.id, cleanOfficer, now)
                val currentSettings = dao.getReminderSettingsOnce()
                if (currentSettings != null && currentSettings.defaultWaliHakimAccountId == stored.id) {
                    dao.upsertReminderSettings(
                        currentSettings.copy(
                            defaultWaliHakimName = cleanOfficer.take(120),
                            updatedAt = now
                        )
                    )
                }
            }

            val domainAccount = updatedEntity.toDomain()
            firestoreSync?.updateOwnStaffProfileInCloud(domainAccount)
            Result.success(domainAccount)
        } catch (e: CancellationException) {
            throw e
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
            firestoreSync?.deleteStaffAccount(account.id)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Reminder & KUA Settings ---

    suspend fun saveReminderSettings(
        settings: ReminderSettings,
        updatedByName: String
    ): Result<Unit> {
        val cleanTemplate = settings.whatsappTemplate.trim()
        val cleanPenghuluTemplate = settings.whatsappPenghuluTemplate.trim()
            .ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }
        val cleanTime = settings.reminderTime.trim()
        val cleanAdminWa = settings.adminWhatsappNumber.trim()
        val cleanAdminWaApp = WhatsAppAppPreference.fromCode(settings.adminWhatsappApp).code

        if (!Regex("^[0-9]{2}:[0-9]{2}$").matches(cleanTime)) {
            return Result.failure(IllegalArgumentException("Format jam pengingat harus HH:mm."))
        }
        if (cleanAdminWa.length > 30) {
            return Result.failure(IllegalArgumentException("Nomor WA Admin KUA maksimal 30 karakter."))
        }
        if (cleanTemplate.length !in 5..800) {
            return Result.failure(IllegalArgumentException("Template pesan WA Modin harus 5–800 karakter."))
        }
        if (cleanPenghuluTemplate.length !in 5..800) {
            return Result.failure(IllegalArgumentException("Template pesan WA Penghulu harus 5–800 karakter."))
        }

        return try {
            val now = System.currentTimeMillis()
            val entity = ReminderSettingsEntity(
                id = ReminderSettings.GLOBAL_DOC_ID,
                autoPhoneNotifEnabled = settings.autoPhoneNotifEnabled,
                whatsappReminderEnabled = settings.whatsappReminderEnabled,
                remindOnWeddingDay = settings.remindOnWeddingDay,
                remindOneDayBefore = settings.remindOneDayBefore,
                remindThreeDaysBefore = settings.remindThreeDaysBefore,
                reminderTime = cleanTime,
                adminWhatsappNumber = cleanAdminWa,
                adminWhatsappApp = cleanAdminWaApp,
                whatsappTemplate = cleanTemplate,
                whatsappPenghuluTemplate = cleanPenghuluTemplate,
                headOfKuaTitle = settings.headOfKuaTitle.trim().ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
                headOfKuaName = settings.headOfKuaName.trim().take(120),
                headOfKuaNip = settings.headOfKuaNip.trim().take(40),
                defaultWaliHakimAccountId = settings.defaultWaliHakimAccountId.trim(),
                defaultWaliHakimName = settings.defaultWaliHakimName.trim().take(120),
                updatedByName = updatedByName.take(100),
                updatedAt = now
            )
            dao.upsertReminderSettings(entity)
            dao.updateStaffWhatsapp(
                id = DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID,
                whatsappNumber = cleanAdminWa,
                updatedAt = now
            )
            firestoreSync?.pushReminderSettings(entity.toDomain(), getAllStaffAccountsOnce())
            dao.getStaffAccountById(DonorojoOfficialDirectory.ADMIN_ACCOUNT_ID)?.let { admin ->
                firestoreSync?.pushStaffAccount(admin.toDomain())
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveHeadOfKuaSettings(
        title: String,
        name: String,
        nip: String,
        updatedByName: String
    ): Result<Unit> {
        val cleanTitle = title.trim().ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE }
        val cleanName = name.trim()
        val cleanNip = nip.trim()

        if (cleanTitle.length > 100) {
            return Result.failure(IllegalArgumentException("Jabatan maksimal 100 karakter."))
        }
        if (cleanName.length !in 2..120) {
            return Result.failure(IllegalArgumentException("Nama Kepala KUA harus 2–120 karakter."))
        }
        if (cleanNip.length > 40) {
            return Result.failure(IllegalArgumentException("NIP Kepala KUA maksimal 40 karakter."))
        }

        return try {
            val current = dao.getReminderSettingsOnce()
            val now = System.currentTimeMillis()
            val entity = ReminderSettingsEntity(
                id = ReminderSettings.GLOBAL_DOC_ID,
                autoPhoneNotifEnabled = current?.autoPhoneNotifEnabled ?: true,
                whatsappReminderEnabled = current?.whatsappReminderEnabled ?: true,
                remindOnWeddingDay = current?.remindOnWeddingDay ?: true,
                remindOneDayBefore = current?.remindOneDayBefore ?: true,
                remindThreeDaysBefore = current?.remindThreeDaysBefore ?: false,
                reminderTime = current?.reminderTime ?: "07:00",
                adminWhatsappNumber = current?.adminWhatsappNumber ?: "",
                adminWhatsappApp = current?.adminWhatsappApp ?: WhatsAppAppPreference.AUTO.code,
                whatsappTemplate = current?.whatsappTemplate ?: ReminderSettings.DEFAULT_WA_TEMPLATE,
                whatsappPenghuluTemplate = current?.whatsappPenghuluTemplate
                    ?.ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }
                    ?: ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
                headOfKuaTitle = cleanTitle,
                headOfKuaName = cleanName,
                headOfKuaNip = cleanNip,
                defaultWaliHakimAccountId = current?.defaultWaliHakimAccountId ?: "",
                defaultWaliHakimName = current?.defaultWaliHakimName ?: "",
                updatedByName = updatedByName.take(100),
                updatedAt = now
            )
            dao.upsertReminderSettings(entity)
            firestoreSync?.pushReminderSettings(entity.toDomain(), getAllStaffAccountsOnce())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveWaliHakimSettings(
        authorizedPenghuluIds: Set<String>,
        defaultWaliHakimAccountId: String,
        defaultWaliHakimName: String,
        updatedByName: String
    ): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val cleanDefaultId = defaultWaliHakimAccountId.trim()
            val effectiveAuthorizedIds = if (cleanDefaultId.isNotBlank()) {
                authorizedPenghuluIds + cleanDefaultId
            } else {
                authorizedPenghuluIds
            }

            val penghulus = dao.getStaffByRoleOnce(UserRole.PENGHULU.code)
            penghulus.forEach { entity ->
                val shouldBeWaliHakim = entity.id in effectiveAuthorizedIds
                if (entity.isWaliHakim != shouldBeWaliHakim) {
                    dao.updateStaffWaliHakim(
                        id = entity.id,
                        isWaliHakim = shouldBeWaliHakim,
                        updatedAt = now
                    )
                    firestoreSync?.pushStaffAccount(
                        entity.copy(isWaliHakim = shouldBeWaliHakim, updatedAt = now).toDomain()
                    )
                }
            }

            val matchedDefault = penghulus.firstOrNull { it.id == cleanDefaultId }
            val resolvedDefaultName = when {
                matchedDefault != null -> matchedDefault.officerName
                else -> defaultWaliHakimName.trim().take(120)
            }

            val current = dao.getReminderSettingsOnce()
            val settingsEntity = ReminderSettingsEntity(
                id = ReminderSettings.GLOBAL_DOC_ID,
                autoPhoneNotifEnabled = current?.autoPhoneNotifEnabled ?: true,
                whatsappReminderEnabled = current?.whatsappReminderEnabled ?: true,
                remindOnWeddingDay = current?.remindOnWeddingDay ?: true,
                remindOneDayBefore = current?.remindOneDayBefore ?: true,
                remindThreeDaysBefore = current?.remindThreeDaysBefore ?: false,
                reminderTime = current?.reminderTime ?: "07:00",
                adminWhatsappNumber = current?.adminWhatsappNumber ?: "",
                adminWhatsappApp = current?.adminWhatsappApp ?: WhatsAppAppPreference.AUTO.code,
                whatsappTemplate = current?.whatsappTemplate ?: ReminderSettings.DEFAULT_WA_TEMPLATE,
                whatsappPenghuluTemplate = current?.whatsappPenghuluTemplate
                    ?.ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }
                    ?: ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
                headOfKuaTitle = current?.headOfKuaTitle ?: ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE,
                headOfKuaName = current?.headOfKuaName ?: "",
                headOfKuaNip = current?.headOfKuaNip ?: "",
                defaultWaliHakimAccountId = matchedDefault?.id ?: "",
                defaultWaliHakimName = resolvedDefaultName,
                updatedByName = updatedByName.take(100),
                updatedAt = now
            )
            dao.upsertReminderSettings(settingsEntity)
            firestoreSync?.pushReminderSettings(settingsEntity.toDomain(), getAllStaffAccountsOnce())
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Marriage Schedule Mutations ---

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

            val checklistCodes = draft.documentChecklist.serialize()
            val docsComplete = draft.documentsComplete || draft.documentChecklist.isAllMandatoryComplete

            val entity = MarriageScheduleEntity(
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
                penghuluAccountId = draft.penghuluAccountId.trim(),
                penghuluName = penghulu,
                waliStatus = draft.waliStatus.code,
                waliRelationship = draft.waliRelationship.trim().take(120),
                waliName = draft.waliName.trim().take(120),
                waliBin = draft.waliBin.trim().take(100),
                waliNik = draft.waliNik.trim().take(20),
                waliAddress = draft.waliAddress.trim().take(250),
                waliHakimAccountId = draft.waliHakimAccountId.trim(),
                waliHakimReason = draft.waliHakimReason.trim().take(250),
                validationStatus = draft.validationStatus.code,
                documentsComplete = docsComplete,
                documentChecklistCodes = checklistCodes,
                verificationNote = note,
                verifiedByName = verifier,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            dao.upsertSchedule(entity)
            firestoreSync?.pushSchedule(
                schedule = entity.toDomain(),
                village = village,
                allStaff = getAllStaffAccountsOnce()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVerificationStatus(
        scheduleId: String,
        newStatus: ValidationStatus,
        checklist: DocumentChecklist,
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
            val codes = checklist.serialize()
            val resolvedComplete = documentsComplete || checklist.isAllMandatoryComplete
            val now = System.currentTimeMillis()
            dao.updateVerification(
                id = scheduleId,
                status = newStatus.code,
                docsComplete = resolvedComplete,
                checklistCodes = codes,
                note = cleanNote,
                verifiedByName = verifiedByName.trim().take(100),
                updatedAt = now
            )
            firestoreSync?.pushScheduleVerification(
                scheduleId = scheduleId,
                newStatus = newStatus,
                documentsComplete = resolvedComplete,
                checklistCodes = codes,
                verificationNote = cleanNote,
                verifiedByName = verifiedByName
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        return try {
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong." }
            dao.deleteSchedule(scheduleId)
            firestoreSync?.deleteSchedule(scheduleId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncReaderEmailsToCloud() {
        val sync = firestoreSync ?: return
        val allStaff = getAllStaffAccountsOnce()
        getAllVillagesOnce().forEach { v -> sync.pushVillage(v, allStaff) }
        sync.pushReminderSettings(getReminderSettingsOnce(), allStaff)
    }

    // --- Entity Mappers ---

    private fun VillageEntity.toDomain() = Village(
        id = code,
        name = name,
        code = code,
        modinName = modinName,
        modinPhone = modinPhone,
        updatedAt = updatedAt
    )

    private fun StaffAccountEntity.toDomain() = StaffAccount(
        id = id,
        username = username,
        passwordHash = passwordHash,
        passwordSalt = passwordSalt,
        role = role,
        villageId = villageId,
        villageName = villageName,
        officerName = officerName,
        whatsappNumber = whatsappNumber,
        googleEmail = googleEmail,
        active = active,
        isWaliHakim = isWaliHakim,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun StaffAccount.toEntity() = StaffAccountEntity(
        id = id,
        username = username,
        passwordHash = passwordHash,
        passwordSalt = passwordSalt,
        role = role,
        villageId = villageId,
        villageName = villageName,
        officerName = officerName,
        whatsappNumber = whatsappNumber,
        googleEmail = googleEmail,
        active = active,
        isWaliHakim = isWaliHakim,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MarriageScheduleEntity.toDomain() = MarriageSchedule(
        id = id,
        registrationNumber = registrationNumber,
        groomName = groomName,
        groomNik = groomNik,
        brideName = brideName,
        brideNik = brideNik,
        villageId = villageId,
        villageName = villageName,
        modinAccountId = modinAccountId,
        modinName = modinName,
        weddingDate = weddingDate,
        weddingTime = weddingTime,
        locationType = locationType,
        locationAddress = locationAddress,
        penghuluAccountId = penghuluAccountId,
        penghuluName = penghuluName,
        waliStatus = waliStatus,
        waliRelationship = waliRelationship,
        waliName = waliName,
        waliBin = waliBin,
        waliNik = waliNik,
        waliAddress = waliAddress,
        waliHakimAccountId = waliHakimAccountId,
        waliHakimReason = waliHakimReason,
        validationStatus = validationStatus,
        documentsComplete = documentsComplete,
        documentChecklistCodes = documentChecklistCodes,
        verificationNote = verificationNote,
        verifiedByName = verifiedByName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun MarriageSchedule.toEntity() = MarriageScheduleEntity(
        id = id,
        registrationNumber = registrationNumber,
        groomName = groomName,
        groomNik = groomNik,
        brideName = brideName,
        brideNik = brideNik,
        villageId = villageId,
        villageName = villageName,
        modinAccountId = modinAccountId,
        modinName = modinName,
        weddingDate = weddingDate,
        weddingTime = weddingTime,
        locationType = locationType,
        locationAddress = locationAddress,
        penghuluAccountId = penghuluAccountId,
        penghuluName = penghuluName,
        waliStatus = waliStatus,
        waliRelationship = waliRelationship,
        waliName = waliName,
        waliBin = waliBin,
        waliNik = waliNik,
        waliAddress = waliAddress,
        waliHakimAccountId = waliHakimAccountId,
        waliHakimReason = waliHakimReason,
        validationStatus = validationStatus,
        documentsComplete = documentsComplete,
        documentChecklistCodes = documentChecklistCodes,
        verificationNote = verificationNote,
        verifiedByName = verifiedByName,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ReminderSettingsEntity.toDomain() = ReminderSettings(
        id = id,
        autoPhoneNotifEnabled = autoPhoneNotifEnabled,
        whatsappReminderEnabled = whatsappReminderEnabled,
        remindOnWeddingDay = remindOnWeddingDay,
        remindOneDayBefore = remindOneDayBefore,
        remindThreeDaysBefore = remindThreeDaysBefore,
        reminderTime = reminderTime,
        adminWhatsappNumber = adminWhatsappNumber,
        adminWhatsappApp = adminWhatsappApp.ifBlank { WhatsAppAppPreference.AUTO.code },
        whatsappTemplate = whatsappTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE },
        whatsappPenghuluTemplate = whatsappPenghuluTemplate.ifBlank {
            ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
        },
        headOfKuaTitle = headOfKuaTitle.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
        headOfKuaName = headOfKuaName,
        headOfKuaNip = headOfKuaNip,
        defaultWaliHakimAccountId = defaultWaliHakimAccountId,
        defaultWaliHakimName = defaultWaliHakimName,
        updatedByName = updatedByName,
        updatedAt = updatedAt
    )

    private fun ReminderSettings.toEntity() = ReminderSettingsEntity(
        id = ReminderSettings.GLOBAL_DOC_ID,
        autoPhoneNotifEnabled = autoPhoneNotifEnabled,
        whatsappReminderEnabled = whatsappReminderEnabled,
        remindOnWeddingDay = remindOnWeddingDay,
        remindOneDayBefore = remindOneDayBefore,
        remindThreeDaysBefore = remindThreeDaysBefore,
        reminderTime = reminderTime,
        adminWhatsappNumber = adminWhatsappNumber,
        adminWhatsappApp = adminWhatsappApp.ifBlank { WhatsAppAppPreference.AUTO.code },
        whatsappTemplate = whatsappTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE },
        whatsappPenghuluTemplate = whatsappPenghuluTemplate.ifBlank {
            ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
        },
        headOfKuaTitle = headOfKuaTitle.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
        headOfKuaName = headOfKuaName,
        headOfKuaNip = headOfKuaNip,
        defaultWaliHakimAccountId = defaultWaliHakimAccountId,
        defaultWaliHakimName = defaultWaliHakimName,
        updatedByName = updatedByName,
        updatedAt = updatedAt
    )
}
