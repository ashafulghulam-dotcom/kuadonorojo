package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CloudSyncStatus
import com.example.data.DashboardMode
import com.example.data.DocumentChecklist
import com.example.data.KuaFirestoreSyncService
import com.example.data.KuaRepository
import com.example.data.KuaSessionManager
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.ScheduleDraft
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.util.KuaBackupPayload
import com.example.util.PasswordHasher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class KuaViewModel(
    private val repository: KuaRepository,
    private val sessionManager: KuaSessionManager,
    private val firestoreSync: KuaFirestoreSyncService,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val onScheduleReminderAlarm: (ReminderSettings) -> Unit = {}
) : ViewModel() {

    private val _activeStaffAccount = MutableStateFlow<StaffAccount?>(null)
    val activeStaffAccount: StateFlow<StaffAccount?> = _activeStaffAccount.asStateFlow()

    private val _rememberSessionDefault = MutableStateFlow(sessionManager.isRememberSessionPreferred())
    val rememberSessionDefault: StateFlow<Boolean> = _rememberSessionDefault.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _dashboardMode = MutableStateFlow(DashboardMode.ADMIN_KUA)
    val dashboardMode: StateFlow<DashboardMode> = _dashboardMode.asStateFlow()

    private val _previewVillageId = MutableStateFlow<String?>(null)
    val previewVillageId: StateFlow<String?> = _previewVillageId.asStateFlow()

    private val _previewModinId = MutableStateFlow<String?>(null)
    val previewModinId: StateFlow<String?> = _previewModinId.asStateFlow()

    private val _previewPenghuluId = MutableStateFlow<String?>(null)
    val previewPenghuluId: StateFlow<String?> = _previewPenghuluId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow(
        CloudSyncStatus(
            userEmail = currentUserEmail,
            isSyncing = true,
            isOnlineSynced = false
        )
    )
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = _cloudSyncStatus.asStateFlow()

    private var didAttemptSessionRestore = false

    init {
        viewModelScope.launch {
            repository.ensureDefaultDonorojoData()
            restoreSavedStaffSessionIfAvailable()
            onScheduleReminderAlarm(repository.getReminderSettingsOnce())
            // Ensure initial cloud profile & workspace mirror are up-to-date
            firestoreSync.upsertUserProfile()
            startCloudRealtimeSync()
            // Perform one initial background push of local data so first-time users immediately sync to cloud
            val syncResult = repository.syncAllLocalToCloud()
            _cloudSyncStatus.update { current ->
                syncResult.fold(
                    onSuccess = { count ->
                        current.copy(
                            isSyncing = false,
                            isOnlineSynced = true,
                            syncedScheduleCount = maxOf(current.syncedScheduleCount, count),
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = null
                        )
                    },
                    onFailure = { err ->
                        current.copy(
                            isSyncing = false,
                            errorMessage = err.localizedMessage
                        )
                    }
                )
            }
        }
        viewModelScope.launch {
            delay(SPLASH_DURATION_MILLIS)
            _showSplash.value = false
        }
    }

    private suspend fun restoreSavedStaffSessionIfAvailable() {
        if (didAttemptSessionRestore) return
        didAttemptSessionRestore = true
        val saved = sessionManager.getSavedSession(forGoogleUid = currentUserId) ?: return
        val accounts = repository.getAllStaffAccountsOnce()
        val matched = accounts.firstOrNull { it.id == saved.staffAccountId && it.active } ?: return
        applyLoggedInStaffAccount(
            matched = matched,
            preferredMode = if (matched.resolvedRole() == UserRole.ADMIN) saved.dashboardMode else null
        )
    }

    private fun startCloudRealtimeSync() {
        // Owned villages
        viewModelScope.launch {
            firestoreSync.observeOwnedVillages(currentUserId)
                .catch { err ->
                    if (err is CancellationException) throw err
                    _cloudSyncStatus.update { it.copy(isSyncing = false, errorMessage = err.localizedMessage) }
                }
                .collect { remoteVillages ->
                    repository.mergeRemoteVillages(remoteVillages)
                    markCloudSyncTick()
                }
        }
        // Shared villages (invited Modin/Penghulu)
        viewModelScope.launch {
            firestoreSync.observeSharedVillages()
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { sharedVillages ->
                    repository.mergeRemoteVillages(sharedVillages)
                    markCloudSyncTick()
                }
        }
        // Owned staff accounts
        viewModelScope.launch {
            firestoreSync.observeOwnedStaffAccounts(currentUserId)
                .catch { err ->
                    if (err is CancellationException) throw err
                    _cloudSyncStatus.update { it.copy(isSyncing = false, errorMessage = err.localizedMessage) }
                }
                .collect { records ->
                    repository.mergeRemoteStaffAccounts(records.map { it.account })
                    markCloudSyncTick()
                }
        }
        // Invited staff accounts (cross-device Modin/Penghulu)
        viewModelScope.launch {
            firestoreSync.observeInvitedStaffAccounts()
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { records ->
                    repository.mergeRemoteStaffAccounts(records.map { it.account })
                    markCloudSyncTick()
                }
        }
        // Owned reminder settings
        viewModelScope.launch {
            firestoreSync.observeOwnedReminderSettings(currentUserId)
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { remoteSettings ->
                    if (remoteSettings != null) {
                        repository.mergeRemoteReminderSettings(remoteSettings)
                        onScheduleReminderAlarm(remoteSettings)
                        markCloudSyncTick()
                    }
                }
        }
        // Shared reminder settings
        viewModelScope.launch {
            firestoreSync.observeSharedReminderSettings()
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { sharedSettings ->
                    if (sharedSettings != null) {
                        repository.mergeRemoteReminderSettings(sharedSettings)
                        onScheduleReminderAlarm(sharedSettings)
                        markCloudSyncTick()
                    }
                }
        }
        // Owned marriage schedules
        viewModelScope.launch {
            firestoreSync.observeOwnedSchedules(currentUserId)
                .catch { err ->
                    if (err is CancellationException) throw err
                    _cloudSyncStatus.update { it.copy(isSyncing = false, errorMessage = err.localizedMessage) }
                }
                .collect { remoteSchedules ->
                    repository.mergeRemoteSchedules(remoteSchedules)
                    markCloudSyncTick(scheduleCount = remoteSchedules.size)
                }
        }
        // Shared marriage schedules (invited Modin/Penghulu)
        viewModelScope.launch {
            firestoreSync.observeSharedSchedules()
                .catch { err ->
                    if (err is CancellationException) throw err
                }
                .collect { sharedSchedules ->
                    repository.mergeRemoteSchedules(sharedSchedules)
                    markCloudSyncTick(scheduleCount = sharedSchedules.size)
                }
        }
    }

    private fun markCloudSyncTick(scheduleCount: Int? = null) {
        _cloudSyncStatus.update { current ->
            current.copy(
                isSyncing = false,
                isOnlineSynced = true,
                syncedScheduleCount = scheduleCount ?: current.syncedScheduleCount,
                lastSyncedAtMillis = System.currentTimeMillis(),
                errorMessage = null
            )
        }
    }

    val staffAccountsState: StateFlow<UiState<List<StaffAccount>>> = repository.observeStaffAccounts()
        .map<List<StaffAccount>, UiState<List<StaffAccount>>> { accounts ->
            val active = _activeStaffAccount.value
            if (active != null) {
                val refreshed = accounts.firstOrNull { it.id == active.id }
                if (refreshed != null) {
                    _activeStaffAccount.value = refreshed
                }
            }
            UiState.Success(accounts)
        }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Gagal memuat data akun petugas."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val reminderSettingsState: StateFlow<UiState<ReminderSettings>> = repository.observeReminderSettings()
        .map<ReminderSettings, UiState<ReminderSettings>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Gagal memuat pengaturan pengingat."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val villagesState: StateFlow<UiState<List<Village>>> = repository.observeAllVillages()
        .map<List<Village>, UiState<List<Village>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Gagal memuat daftar desa Donorojo."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val schedulesState: StateFlow<UiState<List<MarriageSchedule>>> = repository.observeAllSchedules()
        .map<List<MarriageSchedule>, UiState<List<MarriageSchedule>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(UiState.Error(error.message ?: "Gagal memuat data jadwal nikah."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun loginWithCredentials(
        username: String,
        password: String,
        rememberSession: Boolean = _rememberSessionDefault.value
    ) {
        val cleanUser = username.trim().lowercase(Locale.ROOT)
        val cleanPass = password.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            _loginError.value = "Masukkan username dan password terlebih dahulu."
            return
        }

        val accounts = (staffAccountsState.value as? UiState.Success)?.data.orEmpty()
        if (accounts.isEmpty()) {
            _loginError.value = "Menyiapkan data akun KUA Donorojo, silakan coba beberapa detik lagi."
            return
        }

        val matched = accounts.firstOrNull { it.username.equals(cleanUser, ignoreCase = true) }
        if (matched == null) {
            _loginError.value = "Username tidak ditemukan. Periksa kembali penulisan username."
            return
        }
        if (!matched.active) {
            _loginError.value = "Akun ini sedang dinonaktifkan oleh Admin KUA Donorojo."
            return
        }
        if (!PasswordHasher.verifyPassword(cleanPass, matched.passwordSalt, matched.passwordHash)) {
            _loginError.value = "Password salah. Silakan coba lagi atau hubungi Admin KUA Donorojo."
            return
        }

        _loginError.value = null
        _rememberSessionDefault.value = rememberSession
        sessionManager.setRememberSessionPreferred(rememberSession)
        val targetMode = applyLoggedInStaffAccount(matched = matched, preferredMode = null)
        sessionManager.saveSession(
            staffAccountId = matched.id,
            dashboardMode = targetMode,
            googleUid = currentUserId,
            rememberSession = rememberSession
        )
    }

    private fun applyLoggedInStaffAccount(
        matched: StaffAccount,
        preferredMode: DashboardMode?
    ): DashboardMode {
        _activeStaffAccount.value = matched
        val resolvedMode = preferredMode ?: when (matched.resolvedRole()) {
            UserRole.ADMIN -> DashboardMode.ADMIN_KUA
            UserRole.PENGHULU -> DashboardMode.PENGHULU
            UserRole.MODIN -> DashboardMode.MODIN_DESA
        }
        _dashboardMode.value = resolvedMode
        _previewVillageId.value = matched.villageId.takeIf { it.isNotBlank() }
        _previewModinId.value = if (matched.resolvedRole() == UserRole.MODIN) matched.id else null
        _previewPenghuluId.value = if (matched.resolvedRole() == UserRole.PENGHULU) matched.id else null
        return resolvedMode
    }

    fun logoutStaffSession() {
        sessionManager.clearSession()
        _activeStaffAccount.value = null
        _loginError.value = null
        _previewVillageId.value = null
        _previewModinId.value = null
        _previewPenghuluId.value = null
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun switchDashboardMode(mode: DashboardMode) {
        if (_activeStaffAccount.value?.resolvedRole() == UserRole.ADMIN) {
            _dashboardMode.value = mode
            sessionManager.updateDashboardMode(mode)
        }
    }

    fun selectPreviewVillage(villageId: String?) {
        _previewVillageId.value = villageId
    }

    fun selectPreviewModin(modinId: String?) {
        _previewModinId.value = modinId
    }

    fun selectPreviewPenghulu(penghuluId: String?) {
        _previewPenghuluId.value = penghuluId
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun postMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun triggerManualCloudSync() {
        viewModelScope.launch {
            _cloudSyncStatus.update { it.copy(isSyncing = true, errorMessage = null) }
            val result = repository.syncAllLocalToCloud()
            result.fold(
                onSuccess = { count ->
                    _cloudSyncStatus.update {
                        it.copy(
                            isSyncing = false,
                            isOnlineSynced = true,
                            syncedScheduleCount = count,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = null
                        )
                    }
                    _actionMessage.value = "Sinkronisasi cloud selesai ($count jadwal tersimpan online)."
                },
                onFailure = { error ->
                    _cloudSyncStatus.update {
                        it.copy(
                            isSyncing = false,
                            errorMessage = error.localizedMessage ?: "Gagal menyinkronkan ke cloud."
                        )
                    }
                    _actionMessage.value = "Gagal sinkronisasi cloud: ${error.localizedMessage ?: "periksa koneksi internet."}"
                }
            )
        }
    }

    fun restoreFromBackup(
        payload: KuaBackupPayload,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.restoreFromBackup(payload)
            _isSaving.value = false
            result.fold(
                onSuccess = { summary ->
                    onScheduleReminderAlarm(repository.getReminderSettingsOnce())
                    _actionMessage.value = summary
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal memulihkan file backup."
                }
            )
        }
    }

    fun createModinAccount(
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String = "",
        active: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createModinAccount(
                village = village,
                username = username,
                password = password,
                officerName = officerName,
                whatsappNumber = whatsappNumber,
                googleEmail = googleEmail,
                active = active
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Akun Modin \"$officerName\" (${village.name}) berhasil ditambahkan."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menambahkan akun Modin."
                }
            )
        }
    }

    fun createPenghuluAccount(
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String = "",
        active: Boolean,
        isWaliHakim: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createPenghuluAccount(
                username = username,
                password = password,
                officerName = officerName,
                whatsappNumber = whatsappNumber,
                googleEmail = googleEmail,
                active = active,
                isWaliHakim = isWaliHakim
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Akun Penghulu \"$officerName\" berhasil ditambahkan."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menambahkan akun Penghulu."
                }
            )
        }
    }

    fun updateStaffAccount(
        account: StaffAccount,
        newUsername: String,
        newPassword: String?,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String = account.googleEmail,
        active: Boolean,
        newVillage: Village? = null,
        isWaliHakim: Boolean = account.isWaliHakim,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateStaffAccount(
                account = account,
                newUsername = newUsername,
                newPassword = newPassword,
                officerName = officerName,
                whatsappNumber = whatsappNumber,
                googleEmail = googleEmail,
                active = active,
                newVillage = newVillage,
                isWaliHakim = isWaliHakim
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Pengaturan akun \"$officerName\" berhasil diperbarui."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal memperbarui akun."
                }
            )
        }
    }

    fun updateOwnProfile(
        officerName: String,
        whatsappNumber: String,
        username: String,
        currentPassword: String,
        newPassword: String?,
        onSuccess: () -> Unit = {}
    ) {
        val active = _activeStaffAccount.value
        if (active == null) {
            _actionMessage.value = "Sesi login tidak ditemukan. Silakan masuk kembali."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateOwnProfile(
                accountId = active.id,
                newOfficerName = officerName,
                newWhatsappNumber = whatsappNumber,
                newUsername = username,
                currentPassword = currentPassword,
                newPassword = newPassword
            )
            _isSaving.value = false
            result.fold(
                onSuccess = { updatedAccount ->
                    _activeStaffAccount.value = updatedAccount
                    val changedPass = !newPassword.isNullOrBlank()
                    _actionMessage.value = if (changedPass) {
                        "Profil dan password akun Anda berhasil diperbarui."
                    } else {
                        "Profil akun \"${updatedAccount.officerName}\" berhasil diperbarui."
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal memperbarui profil akun Anda."
                }
            )
        }
    }

    fun deleteStaffAccount(
        account: StaffAccount,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteStaffAccount(account)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    val roleTitle = if (account.resolvedRole() == UserRole.PENGHULU) "Penghulu" else "Modin"
                    _actionMessage.value = "Akun $roleTitle \"${account.officerName}\" telah dihapus."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menghapus akun petugas."
                }
            )
        }
    }

    fun saveReminderSettings(settings: ReminderSettings) {
        viewModelScope.launch {
            _isSaving.value = true
            val officerName = _activeStaffAccount.value?.officerName ?: "Admin KUA Donorojo"
            val result = repository.saveReminderSettings(settings, officerName)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    onScheduleReminderAlarm(settings)
                    _actionMessage.value = "Pengaturan Smart Reminder & jadwal notifikasi latar belakang disimpan."
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menyimpan pengaturan Smart Reminder."
                }
            )
        }
    }

    fun saveHeadOfKuaSettings(
        title: String,
        name: String,
        nip: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val officerName = _activeStaffAccount.value?.officerName ?: "Admin KUA Donorojo"
            val result = repository.saveHeadOfKuaSettings(
                title = title,
                name = name,
                nip = nip,
                updatedByName = officerName
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Data Kepala KUA Donorojo berhasil disimpan."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menyimpan data Kepala KUA."
                }
            )
        }
    }

    fun saveWaliHakimSettings(
        authorizedPenghuluIds: Set<String>,
        defaultWaliHakimAccountId: String,
        defaultWaliHakimName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val officerName = _activeStaffAccount.value?.officerName ?: "Admin KUA Donorojo"
            val result = repository.saveWaliHakimSettings(
                authorizedPenghuluIds = authorizedPenghuluIds,
                defaultWaliHakimAccountId = defaultWaliHakimAccountId,
                defaultWaliHakimName = defaultWaliHakimName,
                updatedByName = officerName
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Pengaturan Penghulu Wali Hakim berhasil disimpan."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menyimpan pengaturan Wali Hakim."
                }
            )
        }
    }

    fun saveSchedule(
        draft: ScheduleDraft,
        village: Village,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val adminName = _activeStaffAccount.value?.officerName ?: "Admin KUA Donorojo"
            val result = repository.saveSchedule(draft, village, adminName)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = if (draft.id.isNullOrBlank()) {
                        "Jadwal nikah berhasil disimpan untuk ${village.name}."
                    } else {
                        "Data jadwal nikah berhasil diperbarui."
                    }
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menyimpan jadwal nikah."
                }
            )
        }
    }

    fun updateVerification(
        scheduleId: String,
        newStatus: ValidationStatus,
        checklist: DocumentChecklist,
        documentsComplete: Boolean,
        verificationNote: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val adminName = _activeStaffAccount.value?.officerName ?: "Admin KUA Donorojo"
            val result = repository.updateVerificationStatus(
                scheduleId = scheduleId,
                newStatus = newStatus,
                checklist = checklist,
                documentsComplete = documentsComplete,
                verificationNote = verificationNote,
                verifiedByName = adminName
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Status validasi & checklist berkas diperbarui ke \"${newStatus.label}\"."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal memperbarui status validasi."
                }
            )
        }
    }

    fun deleteSchedule(scheduleId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.deleteSchedule(scheduleId)
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Data jadwal nikah telah dihapus."
                    onSuccess()
                },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Gagal menghapus jadwal nikah."
                }
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val SPLASH_DURATION_MILLIS = 950L
    }
}
