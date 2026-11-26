package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class RemoteStaffRecord(
    val docId: String,
    val ownerUid: String,
    val account: StaffAccount
)

data class RemoteWorkspaceSnapshot(
    val villages: List<Village>,
    val staffAccounts: List<StaffAccount>,
    val reminderSettings: ReminderSettings?,
    val schedules: List<MarriageSchedule>
)

class KuaFirestoreSyncService(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val remoteStaffDocIdByLocalId = ConcurrentHashMap<String, String>()
    private val remoteStaffOwnerByLocalId = ConcurrentHashMap<String, String>()

    private fun currentUid(): String? = auth.currentUser?.uid
    private fun currentVerifiedEmailLower(): String? {
        val user = auth.currentUser ?: return null
        if (!user.isEmailVerified) return null
        return user.email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeDocId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "doc_default" }

    fun villageDocId(ownerUid: String, villageCode: String): String =
        sanitizeDocId("${ownerUid}_${villageCode}")

    fun staffDocId(ownerUid: String, localId: String): String =
        sanitizeDocId("${ownerUid}_${localId}")

    fun settingsDocId(ownerUid: String): String =
        sanitizeDocId("config_${ownerUid}")

    fun scheduleDocId(scheduleId: String): String =
        sanitizeDocId(scheduleId)

    suspend fun upsertUserProfile(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Belum login Google."))
        val uid = user.uid
        val path = "users/$uid"
        val email = user.email?.trim()?.lowercase(Locale.ROOT)?.take(150)
            ?.takeIf { it.length >= 3 } ?: "user@kuadonorojo.id"
        val displayName = user.displayName?.trim()?.take(100)
            ?.takeIf { it.isNotBlank() } ?: email.substringBefore("@").take(100).ifBlank { "Petugas KUA" }

        return try {
            val docRef = db.collection(COL_USERS).document(uid)
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val updateData = mapOf<String, Any?>(
                    "email" to email,
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateData).await()
            } else {
                val createData = mapOf<String, Any?>(
                    "userId" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "role" to UserRole.MODIN.code,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createData).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeOwnedVillages(uid: String): Flow<List<Village>> {
        val query = db.collection(COL_VILLAGES).whereEqualTo("createdByUid", uid)
        return observeVillageQuery(query, "$COL_VILLAGES?createdByUid=$uid")
    }

    fun observeSharedVillages(): Flow<List<Village>> {
        val email = currentVerifiedEmailLower() ?: return flowOf(emptyList())
        val query = db.collection(COL_VILLAGES).whereArrayContains("allowedReaderEmails", email)
        return observeVillageQuery(query, "$COL_VILLAGES?allowedReaderEmails=$email")
    }

    private fun observeVillageQuery(query: Query, path: String): Flow<List<Village>> =
        query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseVillage(it) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

    fun observeOwnedStaffAccounts(uid: String): Flow<List<RemoteStaffRecord>> {
        val query = db.collection(COL_STAFF).whereEqualTo("ownerUid", uid)
        return observeStaffQuery(query, "$COL_STAFF?ownerUid=$uid")
    }

    fun observeInvitedStaffAccounts(): Flow<List<RemoteStaffRecord>> {
        val email = currentVerifiedEmailLower() ?: return flowOf(emptyList())
        val query = db.collection(COL_STAFF).whereEqualTo("googleEmail", email)
        return observeStaffQuery(query, "$COL_STAFF?googleEmail=$email")
    }

    private fun observeStaffQuery(query: Query, path: String): Flow<List<RemoteStaffRecord>> =
        query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val record = parseStaffRecord(doc)
                    if (record != null) {
                        remoteStaffDocIdByLocalId[record.account.id] = record.docId
                        remoteStaffOwnerByLocalId[record.account.id] = record.ownerUid
                    }
                    record
                }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

    fun observeOwnedReminderSettings(uid: String): Flow<ReminderSettings?> {
        val query = db.collection(COL_SETTINGS).whereEqualTo("ownerUid", uid)
        return observeSettingsQuery(query, "$COL_SETTINGS?ownerUid=$uid")
    }

    fun observeSharedReminderSettings(): Flow<ReminderSettings?> {
        val email = currentVerifiedEmailLower() ?: return flowOf(null)
        val query = db.collection(COL_SETTINGS).whereArrayContains("allowedReaderEmails", email)
        return observeSettingsQuery(query, "$COL_SETTINGS?allowedReaderEmails=$email")
    }

    private fun observeSettingsQuery(query: Query, path: String): Flow<ReminderSettings?> =
        query.snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()?.let { parseReminderSettings(it) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

    fun observeOwnedSchedules(uid: String): Flow<List<MarriageSchedule>> {
        val query = db.collection(COL_SCHEDULES).whereEqualTo("createdByUid", uid)
        return observeScheduleQuery(query, "$COL_SCHEDULES?createdByUid=$uid")
    }

    fun observeSharedSchedules(): Flow<List<MarriageSchedule>> {
        val email = currentVerifiedEmailLower() ?: return flowOf(emptyList())
        val query = db.collection(COL_SCHEDULES).whereArrayContains("allowedMemberEmails", email)
        return observeScheduleQuery(query, "$COL_SCHEDULES?allowedMemberEmails=$email")
    }

    private fun observeScheduleQuery(query: Query, path: String): Flow<List<MarriageSchedule>> =
        query.snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseSchedule(it) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

    suspend fun pullAllRemoteDataOnce(uid: String): Result<RemoteWorkspaceSnapshot> {
        if (currentUid() == null) return Result.failure(IllegalStateException("Belum login Google."))
        val email = currentVerifiedEmailLower()
        val isBootstrapAdmin = email == BOOTSTRAP_ADMIN_EMAIL

        return try {
            val villageMap = linkedMapOf<String, Village>()
            val staffMap = linkedMapOf<String, StaffAccount>()
            val scheduleMap = linkedMapOf<String, MarriageSchedule>()
            var latestSettings: ReminderSettings? = null

            fun ingestVillages(docs: List<DocumentSnapshot>) {
                docs.mapNotNull { parseVillage(it) }.forEach { v ->
                    val existing = villageMap[v.code]
                    if (existing == null || v.updatedAt >= existing.updatedAt) {
                        villageMap[v.code] = v
                    }
                }
            }

            fun ingestStaff(docs: List<DocumentSnapshot>) {
                docs.mapNotNull { doc ->
                    parseStaffRecord(doc)?.also { rec ->
                        remoteStaffDocIdByLocalId[rec.account.id] = rec.docId
                        remoteStaffOwnerByLocalId[rec.account.id] = rec.ownerUid
                    }
                }.forEach { rec ->
                    val existing = staffMap[rec.account.id]
                    if (existing == null || rec.account.updatedAt >= existing.updatedAt) {
                        staffMap[rec.account.id] = rec.account
                    }
                }
            }

            fun ingestSettings(docs: List<DocumentSnapshot>) {
                docs.map { parseReminderSettings(it) }.forEach { s ->
                    val current = latestSettings
                    if (current == null || s.updatedAt >= current.updatedAt) {
                        latestSettings = s
                    }
                }
            }

            fun ingestSchedules(docs: List<DocumentSnapshot>) {
                docs.mapNotNull { parseSchedule(it) }.forEach { s ->
                    val existing = scheduleMap[s.id]
                    if (existing == null || s.updatedAt >= existing.updatedAt) {
                        scheduleMap[s.id] = s
                    }
                }
            }

            if (isBootstrapAdmin) {
                runCatching { db.collection(COL_VILLAGES).get().await().documents }.onSuccess(::ingestVillages)
                runCatching { db.collection(COL_STAFF).get().await().documents }.onSuccess(::ingestStaff)
                runCatching { db.collection(COL_SETTINGS).get().await().documents }.onSuccess(::ingestSettings)
                runCatching { db.collection(COL_SCHEDULES).get().await().documents }.onSuccess(::ingestSchedules)
            } else {
                runCatching {
                    db.collection(COL_VILLAGES).whereEqualTo("createdByUid", uid).get().await().documents
                }.onSuccess(::ingestVillages)
                runCatching {
                    db.collection(COL_STAFF).whereEqualTo("ownerUid", uid).get().await().documents
                }.onSuccess(::ingestStaff)
                runCatching {
                    db.collection(COL_SETTINGS).whereEqualTo("ownerUid", uid).get().await().documents
                }.onSuccess(::ingestSettings)
                runCatching {
                    db.collection(COL_SCHEDULES).whereEqualTo("createdByUid", uid).get().await().documents
                }.onSuccess(::ingestSchedules)

                if (!email.isNullOrBlank()) {
                    runCatching {
                        db.collection(COL_VILLAGES).whereArrayContains("allowedReaderEmails", email).get().await().documents
                    }.onSuccess(::ingestVillages)
                    runCatching {
                        db.collection(COL_STAFF).whereEqualTo("googleEmail", email).get().await().documents
                    }.onSuccess(::ingestStaff)
                    runCatching {
                        db.collection(COL_SETTINGS).whereArrayContains("allowedReaderEmails", email).get().await().documents
                    }.onSuccess(::ingestSettings)
                    runCatching {
                        db.collection(COL_SCHEDULES).whereArrayContains("allowedMemberEmails", email).get().await().documents
                    }.onSuccess(::ingestSchedules)
                }
            }

            Result.success(
                RemoteWorkspaceSnapshot(
                    villages = villageMap.values.toList(),
                    staffAccounts = staffMap.values.toList(),
                    reminderSettings = latestSettings,
                    schedules = scheduleMap.values.toList()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "workspace_pull/$uid")
            Result.failure(e)
        }
    }

    suspend fun pushVillage(
        village: Village,
        allStaff: List<StaffAccount>,
        skipIfExistsOnCloud: Boolean = false
    ): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val docId = villageDocId(uid, village.code)
        val path = "$COL_VILLAGES/$docId"
        val villageModinEmail = allStaff.firstOrNull {
            it.resolvedRole() == UserRole.MODIN &&
                it.villageId == village.code &&
                it.googleEmail.isNotBlank()
        }?.googleEmail?.trim()?.lowercase(Locale.ROOT).orEmpty().take(150)

        val readerEmails = collectWorkspaceReaderEmails(allStaff, limit = 25)

        return try {
            val docRef = db.collection(COL_VILLAGES).document(docId)
            val exists = docRef.get().await().exists()
            if (exists && skipIfExistsOnCloud) {
                return Result.success(Unit)
            }
            if (exists) {
                val updateMap = mapOf<String, Any?>(
                    "name" to village.name.trim().take(80).ifBlank { "Desa ${village.code}" },
                    "modinName" to village.modinName.trim().take(100),
                    "modinEmail" to villageModinEmail,
                    "modinPhone" to village.modinPhone.trim().take(30),
                    "allowedReaderEmails" to readerEmails,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf<String, Any?>(
                    "name" to village.name.trim().take(80).ifBlank { "Desa ${village.code}" },
                    "code" to village.code.trim().lowercase(Locale.ROOT).take(64),
                    "modinName" to village.modinName.trim().take(100),
                    "modinEmail" to villageModinEmail,
                    "modinPhone" to village.modinPhone.trim().take(30),
                    "allowedReaderEmails" to readerEmails,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun pushStaffAccount(
        account: StaffAccount,
        skipIfExistsOnCloud: Boolean = false
    ): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val docId = staffDocId(uid, account.id)
        val path = "$COL_STAFF/$docId"
        val cleanEmail = account.googleEmail.trim().lowercase(Locale.ROOT).take(150)

        return try {
            val docRef = db.collection(COL_STAFF).document(docId)
            val exists = docRef.get().await().exists()
            if (exists && skipIfExistsOnCloud) {
                remoteStaffDocIdByLocalId[account.id] = docId
                remoteStaffOwnerByLocalId[account.id] = uid
                return Result.success(Unit)
            }
            if (exists) {
                val updateMap = mapOf<String, Any?>(
                    "username" to account.username.trim().lowercase(Locale.ROOT).take(50),
                    "passwordHash" to account.passwordHash,
                    "passwordSalt" to account.passwordSalt,
                    "officerName" to account.officerName.trim().take(100),
                    "whatsappNumber" to account.whatsappNumber.trim().take(30),
                    "googleEmail" to cleanEmail,
                    "active" to account.active,
                    "waliHakimAuthorized" to (account.resolvedRole() == UserRole.PENGHULU && account.isWaliHakim),
                    "villageId" to account.villageId.trim().take(64),
                    "villageName" to account.villageName.trim().take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf<String, Any?>(
                    "localId" to sanitizeDocId(account.id).take(80),
                    "username" to account.username.trim().lowercase(Locale.ROOT).take(50),
                    "passwordHash" to account.passwordHash,
                    "passwordSalt" to account.passwordSalt,
                    "role" to account.resolvedRole().code,
                    "villageId" to account.villageId.trim().take(64),
                    "villageName" to account.villageName.trim().take(80),
                    "officerName" to account.officerName.trim().take(100),
                    "whatsappNumber" to account.whatsappNumber.trim().take(30),
                    "googleEmail" to cleanEmail,
                    "active" to account.active,
                    "waliHakimAuthorized" to (account.resolvedRole() == UserRole.PENGHULU && account.isWaliHakim),
                    "ownerUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createMap).await()
            }
            remoteStaffDocIdByLocalId[account.id] = docId
            remoteStaffOwnerByLocalId[account.id] = uid
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnStaffProfileInCloud(account: StaffAccount): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val mappedDocId = remoteStaffDocIdByLocalId[account.id] ?: staffDocId(uid, account.id)
        val mappedOwner = remoteStaffOwnerByLocalId[account.id] ?: uid
        val path = "$COL_STAFF/$mappedDocId"

        return try {
            val docRef = db.collection(COL_STAFF).document(mappedDocId)
            val exists = docRef.get().await().exists()
            if (!exists) {
                return if (mappedOwner == uid) {
                    pushStaffAccount(account)
                } else {
                    Result.success(Unit)
                }
            }
            val updateMap = mapOf<String, Any?>(
                "username" to account.username.trim().lowercase(Locale.ROOT).take(50),
                "passwordHash" to account.passwordHash,
                "passwordSalt" to account.passwordSalt,
                "officerName" to account.officerName.trim().take(100),
                "whatsappNumber" to account.whatsappNumber.trim().take(30),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStaffAccount(localId: String): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val docId = staffDocId(uid, localId)
        val path = "$COL_STAFF/$docId"
        return try {
            db.collection(COL_STAFF).document(docId).delete().await()
            remoteStaffDocIdByLocalId.remove(localId)
            remoteStaffOwnerByLocalId.remove(localId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun pushReminderSettings(
        settings: ReminderSettings,
        allStaff: List<StaffAccount>,
        skipIfExistsOnCloud: Boolean = false
    ): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val docId = settingsDocId(uid)
        val path = "$COL_SETTINGS/$docId"
        val readerEmails = collectWorkspaceReaderEmails(allStaff, limit = 25)

        val cleanTemplate = settings.whatsappTemplate.trim().ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE }.take(800)
        val cleanPenghuluTemplate = settings.whatsappPenghuluTemplate.trim()
            .ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }.take(800)
        val cleanTime = settings.reminderTime.trim().takeIf { Regex("^[0-9]{2}:[0-9]{2}$").matches(it) } ?: "07:00"

        return try {
            val docRef = db.collection(COL_SETTINGS).document(docId)
            val exists = docRef.get().await().exists()
            if (exists && skipIfExistsOnCloud) {
                return Result.success(Unit)
            }
            val commonFields = mapOf<String, Any?>(
                "autoPhoneNotifEnabled" to settings.autoPhoneNotifEnabled,
                "whatsappReminderEnabled" to settings.whatsappReminderEnabled,
                "remindOnWeddingDay" to settings.remindOnWeddingDay,
                "remindOneDayBefore" to settings.remindOneDayBefore,
                "remindThreeDaysBefore" to settings.remindThreeDaysBefore,
                "reminderTime" to cleanTime,
                "adminWhatsappNumber" to settings.adminWhatsappNumber.trim().take(30),
                "adminWhatsappApp" to WhatsAppAppPreference.fromCode(settings.adminWhatsappApp).code,
                "whatsappTemplate" to cleanTemplate,
                "whatsappPenghuluTemplate" to cleanPenghuluTemplate,
                "headOfKuaTitle" to settings.headOfKuaTitle.trim().ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE }.take(100),
                "headOfKuaName" to settings.headOfKuaName.trim().take(120),
                "headOfKuaNip" to settings.headOfKuaNip.trim().take(40),
                "defaultWaliHakimAccountId" to settings.defaultWaliHakimAccountId.trim().take(80),
                "defaultWaliHakimName" to settings.defaultWaliHakimName.trim().take(120),
                "updatedByName" to settings.updatedByName.trim().take(100),
                "allowedReaderEmails" to readerEmails,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            if (exists) {
                docRef.update(commonFields).await()
            } else {
                val createFields = commonFields + mapOf(
                    "ownerUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createFields).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun pushSchedule(
        schedule: MarriageSchedule,
        village: Village?,
        allStaff: List<StaffAccount>
    ): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Belum login Google."))
        val docId = scheduleDocId(schedule.id)
        val path = "$COL_SCHEDULES/$docId"
        val parentVillageDocId = villageDocId(uid, schedule.villageId)

        // Ensure parent village document exists in Firestore so rules exists()/get() check passes.
        if (village != null) {
            val villageRef = db.collection(COL_VILLAGES).document(parentVillageDocId)
            val villageExists = try {
                villageRef.get().await().exists()
            } catch (_: Exception) {
                false
            }
            if (!villageExists) {
                pushVillage(village, allStaff)
            }
        }

        val memberEmails = collectScheduleMemberEmails(schedule, allStaff)
        val cleanChecklistCodes = schedule.resolvedChecklist().serialize().take(120)

        return try {
            val docRef = db.collection(COL_SCHEDULES).document(docId)
            val exists = docRef.get().await().exists()
            val commonFields = mapOf<String, Any?>(
                "registrationNumber" to schedule.registrationNumber.trim().take(40),
                "groomName" to schedule.groomName.trim().take(100),
                "groomNik" to schedule.groomNik.trim().take(20),
                "brideName" to schedule.brideName.trim().take(100),
                "brideNik" to schedule.brideNik.trim().take(20),
                "villageDocId" to parentVillageDocId,
                "villageId" to schedule.villageId.trim().take(64),
                "villageName" to schedule.villageName.trim().take(80),
                "modinAccountId" to schedule.modinAccountId.trim().take(80),
                "modinName" to schedule.modinName.trim().take(100),
                "weddingDate" to schedule.weddingDate.trim().take(10),
                "weddingTime" to schedule.weddingTime.trim().take(5),
                "locationType" to schedule.resolvedLocationType().code,
                "locationAddress" to schedule.locationAddress.trim().take(250),
                "penghuluAccountId" to schedule.penghuluAccountId.trim().take(80),
                "penghuluName" to schedule.penghuluName.trim().take(100),
                "waliStatus" to schedule.resolvedWaliStatus().code,
                "waliRelationship" to schedule.waliRelationship.trim().take(120),
                "waliName" to schedule.waliName.trim().take(120),
                "waliBin" to schedule.waliBin.trim().take(100),
                "waliNik" to schedule.waliNik.trim().take(20),
                "waliAddress" to schedule.waliAddress.trim().take(250),
                "waliHakimAccountId" to schedule.waliHakimAccountId.trim().take(80),
                "waliHakimReason" to schedule.waliHakimReason.trim().take(250),
                "validationStatus" to schedule.resolvedStatus().code,
                "documentsComplete" to schedule.documentsComplete,
                "documentChecklistCodes" to cleanChecklistCodes,
                "verificationNote" to schedule.verificationNote.trim().take(500),
                "verifiedByName" to schedule.verifiedByName.trim().take(100),
                "allowedMemberEmails" to memberEmails,
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            if (exists) {
                docRef.update(commonFields).await()
            } else {
                val createFields = commonFields + mapOf(
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createFields).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun pushScheduleVerification(
        scheduleId: String,
        newStatus: ValidationStatus,
        documentsComplete: Boolean,
        checklistCodes: String,
        verificationNote: String,
        verifiedByName: String
    ): Result<Unit> {
        if (currentUid() == null) return Result.failure(IllegalStateException("Belum login Google."))
        val docId = scheduleDocId(scheduleId)
        val path = "$COL_SCHEDULES/$docId"

        return try {
            val docRef = db.collection(COL_SCHEDULES).document(docId)
            val exists = docRef.get().await().exists()
            if (!exists) return Result.success(Unit)
            val updateMap = mapOf<String, Any?>(
                "validationStatus" to newStatus.code,
                "documentsComplete" to documentsComplete,
                "documentChecklistCodes" to checklistCodes.take(120),
                "verificationNote" to verificationNote.trim().take(500),
                "verifiedByName" to verifiedByName.trim().take(100),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        if (currentUid() == null) return Result.failure(IllegalStateException("Belum login Google."))
        val docId = scheduleDocId(scheduleId)
        val path = "$COL_SCHEDULES/$docId"
        return try {
            db.collection(COL_SCHEDULES).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncAllLocalToCloud(
        villages: List<Village>,
        staffAccounts: List<StaffAccount>,
        settings: ReminderSettings,
        schedules: List<MarriageSchedule>,
        seedTimestampThreshold: Long = 1L
    ): Result<Int> {
        if (currentUid() == null) return Result.failure(IllegalStateException("Belum login Google."))
        var failure: Throwable? = null
        upsertUserProfile()

        val villagesByCode = villages.associateBy { it.code }
        villages.forEach { v ->
            val r = pushVillage(
                village = v,
                allStaff = staffAccounts,
                skipIfExistsOnCloud = v.updatedAt <= seedTimestampThreshold
            )
            if (r.isFailure && failure == null) failure = r.exceptionOrNull()
        }
        staffAccounts.forEach { account ->
            val r = pushStaffAccount(
                account = account,
                skipIfExistsOnCloud = account.updatedAt <= seedTimestampThreshold
            )
            if (r.isFailure && failure == null) failure = r.exceptionOrNull()
        }
        val settingsResult = pushReminderSettings(
            settings = settings,
            allStaff = staffAccounts,
            skipIfExistsOnCloud = settings.updatedAt <= seedTimestampThreshold
        )
        if (settingsResult.isFailure && failure == null) failure = settingsResult.exceptionOrNull()

        var pushedSchedules = 0
        schedules.forEach { schedule ->
            val r = pushSchedule(schedule, villagesByCode[schedule.villageId], staffAccounts)
            if (r.isSuccess) pushedSchedules++ else if (failure == null) failure = r.exceptionOrNull()
        }

        return if (failure != null && pushedSchedules == 0 && schedules.isNotEmpty()) {
            Result.failure(failure!!)
        } else {
            Result.success(pushedSchedules)
        }
    }

    private fun collectWorkspaceReaderEmails(
        allStaff: List<StaffAccount>,
        limit: Int
    ): List<String> = allStaff
        .map { it.googleEmail.trim().lowercase(Locale.ROOT) }
        .filter { it.contains("@") && it.length in 3..150 }
        .distinct()
        .take(limit)

    private fun collectScheduleMemberEmails(
        schedule: MarriageSchedule,
        allStaff: List<StaffAccount>
    ): List<String> {
        val matched = linkedSetOf<String>()
        allStaff.forEach { account ->
            val email = account.googleEmail.trim().lowercase(Locale.ROOT)
            if (!email.contains("@") || email.length !in 3..150) return@forEach
            val isRelevant = when (account.resolvedRole()) {
                UserRole.ADMIN -> true
                UserRole.MODIN -> (schedule.modinAccountId.isNotBlank() && account.id == schedule.modinAccountId) ||
                    (schedule.villageId.isNotBlank() && account.villageId == schedule.villageId)
                UserRole.PENGHULU -> (schedule.penghuluAccountId.isNotBlank() && account.id == schedule.penghuluAccountId) ||
                    (schedule.waliHakimAccountId.isNotBlank() && account.id == schedule.waliHakimAccountId)
            }
            if (isRelevant) matched.add(email)
        }
        return matched.take(15)
    }

    private fun readTimestampMillis(doc: DocumentSnapshot, field: String): Long {
        val ts = doc.getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        return ts?.toDate()?.time ?: System.currentTimeMillis()
    }

    private fun parseVillage(doc: DocumentSnapshot): Village? {
        val code = doc.getString("code")?.trim().orEmpty()
        val name = doc.getString("name")?.trim().orEmpty()
        if (code.isBlank() || name.isBlank()) return null
        return Village(
            id = code,
            name = name,
            code = code,
            modinName = doc.getString("modinName").orEmpty(),
            modinPhone = doc.getString("modinPhone").orEmpty(),
            updatedAt = readTimestampMillis(doc, "updatedAt")
        )
    }

    private fun parseStaffRecord(doc: DocumentSnapshot): RemoteStaffRecord? {
        val localId = doc.getString("localId")?.trim().orEmpty()
        val username = doc.getString("username")?.trim().orEmpty()
        val passwordHash = doc.getString("passwordHash").orEmpty()
        val passwordSalt = doc.getString("passwordSalt").orEmpty()
        if (localId.isBlank() || username.isBlank() || passwordHash.length != 64) return null
        val ownerUid = doc.getString("ownerUid").orEmpty()
        val account = StaffAccount(
            id = localId,
            username = username,
            passwordHash = passwordHash,
            passwordSalt = passwordSalt,
            role = doc.getString("role") ?: UserRole.MODIN.code,
            villageId = doc.getString("villageId").orEmpty(),
            villageName = doc.getString("villageName").orEmpty(),
            officerName = doc.getString("officerName").orEmpty(),
            whatsappNumber = doc.getString("whatsappNumber").orEmpty(),
            googleEmail = doc.getString("googleEmail").orEmpty(),
            active = doc.getBoolean("active") ?: true,
            isWaliHakim = doc.getBoolean("waliHakimAuthorized") ?: false,
            createdAt = readTimestampMillis(doc, "createdAt"),
            updatedAt = readTimestampMillis(doc, "updatedAt")
        )
        return RemoteStaffRecord(docId = doc.id, ownerUid = ownerUid, account = account)
    }

    private fun parseReminderSettings(doc: DocumentSnapshot): ReminderSettings {
        return ReminderSettings(
            id = ReminderSettings.GLOBAL_DOC_ID,
            autoPhoneNotifEnabled = doc.getBoolean("autoPhoneNotifEnabled") ?: true,
            whatsappReminderEnabled = doc.getBoolean("whatsappReminderEnabled") ?: true,
            remindOnWeddingDay = doc.getBoolean("remindOnWeddingDay") ?: true,
            remindOneDayBefore = doc.getBoolean("remindOneDayBefore") ?: true,
            remindThreeDaysBefore = doc.getBoolean("remindThreeDaysBefore") ?: false,
            reminderTime = doc.getString("reminderTime")?.ifBlank { "07:00" } ?: "07:00",
            adminWhatsappNumber = doc.getString("adminWhatsappNumber").orEmpty(),
            adminWhatsappApp = doc.getString("adminWhatsappApp") ?: WhatsAppAppPreference.AUTO.code,
            whatsappTemplate = doc.getString("whatsappTemplate")
                ?.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE }
                ?: ReminderSettings.DEFAULT_WA_TEMPLATE,
            whatsappPenghuluTemplate = doc.getString("whatsappPenghuluTemplate")
                ?.ifBlank { ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE }
                ?: ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE,
            headOfKuaTitle = doc.getString("headOfKuaTitle")
                ?.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE }
                ?: ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE,
            headOfKuaName = doc.getString("headOfKuaName").orEmpty(),
            headOfKuaNip = doc.getString("headOfKuaNip").orEmpty(),
            defaultWaliHakimAccountId = doc.getString("defaultWaliHakimAccountId").orEmpty(),
            defaultWaliHakimName = doc.getString("defaultWaliHakimName").orEmpty(),
            updatedByName = doc.getString("updatedByName").orEmpty(),
            updatedAt = readTimestampMillis(doc, "updatedAt")
        )
    }

    private fun parseSchedule(doc: DocumentSnapshot): MarriageSchedule? {
        val regNum = doc.getString("registrationNumber")?.trim().orEmpty()
        val groomName = doc.getString("groomName")?.trim().orEmpty()
        val brideName = doc.getString("brideName")?.trim().orEmpty()
        if (regNum.isBlank() || groomName.isBlank() || brideName.isBlank()) return null

        return MarriageSchedule(
            id = doc.id,
            registrationNumber = regNum,
            groomName = groomName,
            groomNik = doc.getString("groomNik").orEmpty(),
            brideName = brideName,
            brideNik = doc.getString("brideNik").orEmpty(),
            villageId = doc.getString("villageId").orEmpty(),
            villageName = doc.getString("villageName").orEmpty(),
            modinAccountId = doc.getString("modinAccountId").orEmpty(),
            modinName = doc.getString("modinName").orEmpty(),
            weddingDate = doc.getString("weddingDate").orEmpty(),
            weddingTime = doc.getString("weddingTime").orEmpty(),
            locationType = doc.getString("locationType") ?: LocationType.KUA.code,
            locationAddress = doc.getString("locationAddress").orEmpty(),
            penghuluAccountId = doc.getString("penghuluAccountId").orEmpty(),
            penghuluName = doc.getString("penghuluName").orEmpty(),
            waliStatus = doc.getString("waliStatus") ?: WaliStatus.WALI_NASAB.code,
            waliRelationship = doc.getString("waliRelationship").orEmpty(),
            waliName = doc.getString("waliName").orEmpty(),
            waliBin = doc.getString("waliBin").orEmpty(),
            waliNik = doc.getString("waliNik").orEmpty(),
            waliAddress = doc.getString("waliAddress").orEmpty(),
            waliHakimAccountId = doc.getString("waliHakimAccountId").orEmpty(),
            waliHakimReason = doc.getString("waliHakimReason").orEmpty(),
            validationStatus = doc.getString("validationStatus") ?: ValidationStatus.MENUNGGU.code,
            documentsComplete = doc.getBoolean("documentsComplete") ?: false,
            documentChecklistCodes = doc.getString("documentChecklistCodes").orEmpty(),
            verificationNote = doc.getString("verificationNote").orEmpty(),
            verifiedByName = doc.getString("verifiedByName").orEmpty(),
            createdAt = readTimestampMillis(doc, "createdAt"),
            updatedAt = readTimestampMillis(doc, "updatedAt")
        )
    }

    private companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "minhashaf02@gmail.com"
        const val COL_USERS = "users"
        const val COL_VILLAGES = "villages"
        const val COL_STAFF = "staff_accounts"
        const val COL_SETTINGS = "reminder_settings"
        const val COL_SCHEDULES = "marriage_schedules"
    }
}
