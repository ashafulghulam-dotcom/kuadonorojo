package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DashboardMode
import com.example.data.KuaRepository
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.ScheduleDraft
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.util.PasswordHasher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class KuaViewModel(
    private val repository: KuaRepository
) : ViewModel() {

    private val _activeStaffAccount = MutableStateFlow<StaffAccount?>(null)
    val activeStaffAccount: StateFlow<StaffAccount?> = _activeStaffAccount.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _dashboardMode = MutableStateFlow(DashboardMode.ADMIN_KUA)
    val dashboardMode: StateFlow<DashboardMode> = _dashboardMode.asStateFlow()

    private val _previewVillageId = MutableStateFlow<String?>(null)
    val previewVillageId: StateFlow<String?> = _previewVillageId.asStateFlow()

    private val _previewPenghuluId = MutableStateFlow<String?>(null)
    val previewPenghuluId: StateFlow<String?> = _previewPenghuluId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _showSplash = MutableStateFlow(true)
    val showSplash: StateFlow<Boolean> = _showSplash.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureDefaultDonorojoData()
        }
        viewModelScope.launch {
            delay(SPLASH_DURATION_MILLIS)
            _showSplash.value = false
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
            emit(UiState.Error(error.message ?: "Gagal memuat data jadwal nikah."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun loginWithCredentials(username: String, password: String) {
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
        _activeStaffAccount.value = matched
        _dashboardMode.value = when (matched.resolvedRole()) {
            UserRole.ADMIN -> DashboardMode.ADMIN_KUA
            UserRole.PENGHULU -> DashboardMode.PENGHULU
            UserRole.MODIN -> DashboardMode.MODIN_DESA
        }
        _previewVillageId.value = matched.villageId.takeIf { it.isNotBlank() }
        _previewPenghuluId.value = if (matched.resolvedRole() == UserRole.PENGHULU) matched.id else null
    }

    fun logoutStaffSession() {
        _activeStaffAccount.value = null
        _loginError.value = null
        _previewVillageId.value = null
        _previewPenghuluId.value = null
    }

    fun clearLoginError() {
        _loginError.value = null
    }

    fun switchDashboardMode(mode: DashboardMode) {
        if (_activeStaffAccount.value?.resolvedRole() == UserRole.ADMIN) {
            _dashboardMode.value = mode
        }
    }

    fun selectPreviewVillage(villageId: String?) {
        _previewVillageId.value = villageId
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

    fun createModinAccount(
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
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
        active: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createPenghuluAccount(
                username = username,
                password = password,
                officerName = officerName,
                whatsappNumber = whatsappNumber,
                active = active
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
        active: Boolean,
        newVillage: Village? = null,
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
                active = active,
                newVillage = newVillage
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
                    _actionMessage.value = "Pengaturan Smart Reminder berhasil disimpan."
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
                documentsComplete = documentsComplete,
                verificationNote = verificationNote,
                verifiedByName = adminName
            )
            _isSaving.value = false
            result.fold(
                onSuccess = {
                    _actionMessage.value = "Status validasi diperbarui ke \"${newStatus.label}\"."
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
        const val SPLASH_DURATION_MILLIS = 1100L
    }
}
