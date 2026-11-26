package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CloudSyncStatus
import com.example.data.DashboardMode
import com.example.data.DocumentChecklist
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.ScheduleDraft
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.data.WhatsAppAppPreference
import com.example.data.formatShortTimestamp
import com.example.data.todayIsoDate
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.KpiSummaryStrip
import com.example.ui.components.KuaHeroHeader
import com.example.ui.components.MonthlyCalendarSection
import com.example.ui.components.RecapAnalyticsSection
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailSheet
import com.example.ui.components.SearchAndStatusFilterBar
import com.example.ui.components.TodayWeddingsSpotlightSection
import com.example.ui.components.WaDigestQuickCard
import com.example.ui.theme.StatusCancelledBg
import com.example.ui.theme.StatusCancelledFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg
import com.example.util.KuaBackupPayload
import com.example.util.KuaDataTransferManager
import com.example.util.SmartReminderManager
import com.example.util.SmartReminderManager.ReminderRecipientType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AdminTab(val label: String) {
    SCHEDULES("Jadwal"),
    VERIFICATION("Verifikasi"),
    CALCULATOR("Hitung"),
    REMINDER("Reminder"),
    ACCOUNTS("Akun")
}

private enum class AdminScheduleViewMode(val label: String) {
    LIST("Daftar"),
    CALENDAR("Kalender"),
    ANALYTICS("Rekap Grafik")
}

@Composable
fun AdminDashboardScreen(
    activeAccount: StaffAccount,
    villagesState: UiState<List<Village>>,
    schedulesState: UiState<List<MarriageSchedule>>,
    staffAccountsState: UiState<List<StaffAccount>>,
    reminderSettingsState: UiState<ReminderSettings>,
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus(),
    isSaving: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onPostMessage: (String) -> Unit,
    onSwitchMode: (DashboardMode) -> Unit,
    onLogoutStaff: () -> Unit,
    onTriggerCloudSync: () -> Unit = {},
    onRestoreBackup: (KuaBackupPayload, () -> Unit) -> Unit = { _, _ -> },
    onSaveSchedule: (ScheduleDraft, Village, () -> Unit) -> Unit,
    onUpdateVerification: (String, ValidationStatus, DocumentChecklist, Boolean, String, () -> Unit) -> Unit,
    onDeleteSchedule: (String, () -> Unit) -> Unit,
    onCreateModinAccount: (
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String,
        active: Boolean,
        onSuccess: () -> Unit
    ) -> Unit,
    onCreatePenghuluAccount: (
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String,
        active: Boolean,
        isWaliHakim: Boolean,
        onSuccess: () -> Unit
    ) -> Unit,
    onUpdateStaffAccount: (
        account: StaffAccount,
        newUsername: String,
        newPassword: String?,
        officerName: String,
        whatsappNumber: String,
        googleEmail: String,
        active: Boolean,
        newVillage: Village?,
        isWaliHakim: Boolean,
        onSuccess: () -> Unit
    ) -> Unit,
    onDeleteStaffAccount: (StaffAccount, () -> Unit) -> Unit,
    onSaveReminderSettings: (ReminderSettings) -> Unit,
    onSaveHeadOfKuaSettings: (title: String, name: String, nip: String, onSuccess: () -> Unit) -> Unit,
    onSaveWaliHakimSettings: (
        authorizedPenghuluIds: Set<String>,
        defaultWaliHakimAccountId: String,
        defaultWaliHakimName: String,
        onSuccess: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AdminTab.SCHEDULES) }
    var scheduleViewMode by remember { mutableStateOf(AdminScheduleViewMode.LIST) }
    var selectedCalendarDate by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<ValidationStatus?>(null) }
    var selectedVillageFilter by remember { mutableStateOf<String?>(null) }
    var selectedAccountRoleFilter by remember { mutableStateOf<UserRole?>(null) }
    var selectedAccountVillageFilter by remember { mutableStateOf<String?>(null) }

    var detailSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var verifyingSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var editingSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var showScheduleForm by remember { mutableStateOf(false) }
    var deletingSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var showAddModinDialog by remember { mutableStateOf(false) }
    var showAddPenghuluDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<StaffAccount?>(null) }
    var deletingAccount by remember { mutableStateOf<StaffAccount?>(null) }
    var showMonthlyReportSheet by remember { mutableStateOf(false) }
    var showHeadOfKuaDialog by remember { mutableStateOf(false) }
    var showWaliHakimSettingsDialog by remember { mutableStateOf(false) }
    var pendingRestorePayload by remember { mutableStateOf<KuaBackupPayload?>(null) }
    var isProcessingTransfer by remember { mutableStateOf(false) }

    var hasNotifPermission by remember {
        mutableStateOf(SmartReminderManager.hasNotificationPermission(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            onPostMessage("Izin notifikasi HP aktif. Smart Reminder siap digunakan.")
        } else {
            onPostMessage("Izin notifikasi ditolak. Aktifkan di Pengaturan HP agar notifikasi muncul.")
        }
    }

    val requestNotifPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotifPermission = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearMessage()
        }
    }

    BackHandler(enabled = selectedTab != AdminTab.SCHEDULES) {
        selectedTab = AdminTab.SCHEDULES
    }

    val villages = (villagesState as? UiState.Success)?.data.orEmpty()
    val schedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val staffAccounts = (staffAccountsState as? UiState.Success)?.data.orEmpty()
    val reminderSettings = (reminderSettingsState as? UiState.Success)?.data ?: ReminderSettings()

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessingTransfer = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = KuaDataTransferManager.buildBackupJsonBytes(
                        villages = villages,
                        staffAccounts = staffAccounts,
                        schedules = schedules,
                        settings = reminderSettings
                    )
                    KuaDataTransferManager.writeBytesToUri(context, uri, bytes).getOrThrow()
                }
            }
            isProcessingTransfer = false
            result.onSuccess {
                onPostMessage("File backup JSON berhasil disimpan (${schedules.size} jadwal & ${staffAccounts.size} akun).")
            }.onFailure { err ->
                onPostMessage(err.localizedMessage ?: "Gagal menyimpan file backup JSON.")
            }
        }
    }

    val openRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isProcessingTransfer = true
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                KuaDataTransferManager.readBytesFromUri(context, uri)
                    .mapCatching { bytes -> KuaDataTransferManager.parseBackupJson(bytes).getOrThrow() }
            }
            isProcessingTransfer = false
            parsed.onSuccess { payload ->
                pendingRestorePayload = payload
            }.onFailure { err ->
                onPostMessage(err.localizedMessage ?: "File backup tidak valid atau gagal dibaca.")
            }
        }
    }

    val todayIso = remember { todayIsoDate() }
    val todaySchedules = remember(schedules, todayIso) {
        schedules.filter {
            it.weddingDate == todayIso && it.resolvedStatus() != ValidationStatus.DIBATALKAN
        }.sortedBy { it.weddingTime }
    }

    val pendingCount = remember(schedules) {
        schedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.MENUNGGU ||
                st == ValidationStatus.VERIFIKASI_BERKAS ||
                st == ValidationStatus.PERLU_REVISI
        }
    }
    val verifiedCount = remember(schedules) {
        schedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.TERVERIFIKASI || st == ValidationStatus.SELESAI
        }
    }

    val filteredSchedules = remember(
        schedules,
        searchQuery,
        selectedStatusFilter,
        selectedVillageFilter,
        selectedTab,
        scheduleViewMode,
        selectedCalendarDate
    ) {
        schedules.filter { item ->
            val status = item.resolvedStatus()
            val matchesTab = if (selectedTab == AdminTab.VERIFICATION) {
                status == ValidationStatus.MENUNGGU ||
                    status == ValidationStatus.VERIFIKASI_BERKAS ||
                    status == ValidationStatus.PERLU_REVISI
            } else {
                true
            }
            val matchesDate = if (selectedTab == AdminTab.SCHEDULES &&
                scheduleViewMode == AdminScheduleViewMode.CALENDAR &&
                !selectedCalendarDate.isNullOrBlank()
            ) {
                item.weddingDate == selectedCalendarDate
            } else {
                true
            }
            val matchesStatus = selectedStatusFilter == null || status == selectedStatusFilter
            val matchesVillage = selectedVillageFilter == null || item.villageId == selectedVillageFilter
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                item.groomName.lowercase().contains(q) ||
                item.brideName.lowercase().contains(q) ||
                item.villageName.lowercase().contains(q) ||
                item.modinName.lowercase().contains(q) ||
                item.registrationNumber.lowercase().contains(q) ||
                item.penghuluName.lowercase().contains(q)
            matchesTab && matchesDate && matchesStatus && matchesVillage && matchesSearch
        }
    }

    val filteredStaffAccounts = remember(staffAccounts, selectedAccountRoleFilter, selectedAccountVillageFilter) {
        staffAccounts.filter { account ->
            val role = account.resolvedRole()
            val matchesRole = selectedAccountRoleFilter == null || role == selectedAccountRoleFilter
            val matchesVillage = selectedAccountVillageFilter == null ||
                (role == UserRole.MODIN && account.villageId == selectedAccountVillageFilter)
            matchesRole && matchesVillage
        }
    }

    val triggerPhoneNotification: (MarriageSchedule) -> Unit = { schedule ->
        if (!hasNotifPermission) {
            requestNotifPermission()
        } else {
            val sent = SmartReminderManager.sendScheduleNotification(
                context = context,
                schedule = schedule,
                prefixLabel = "Pengingat KUA Donorojo"
            )
            if (sent) {
                onPostMessage("Notifikasi HP terkirim untuk ${schedule.groomName} & ${schedule.brideName}.")
            } else {
                onPostMessage("Gagal mengirim notifikasi HP. Periksa izin notifikasi perangkat.")
            }
        }
    }

    val triggerWhatsAppReminder: (
        MarriageSchedule,
        ReminderRecipientType,
        String,
        String,
        WhatsAppAppPreference
    ) -> Unit = { schedule, recipientType, template, adminWaOverride, appPref ->
        val resolvedAdminWa = adminWaOverride.trim().ifBlank {
            SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts)
        }
        val recipientAccount = when (recipientType) {
            ReminderRecipientType.MODIN -> SmartReminderManager.resolveAssignedModin(schedule, staffAccounts)
            ReminderRecipientType.PENGHULU -> SmartReminderManager.resolveAssignedPenghulu(schedule, staffAccounts)
        }
        val targetPhone = recipientAccount?.whatsappNumber.orEmpty()
        val targetLabel = when (recipientType) {
            ReminderRecipientType.MODIN -> schedule.modinName.ifBlank {
                recipientAccount?.officerName ?: "Modin ${schedule.villageName}"
            }
            ReminderRecipientType.PENGHULU -> schedule.penghuluName.ifBlank {
                recipientAccount?.officerName ?: "Penghulu KUA"
            }
        }
        val message = SmartReminderManager.formatWhatsAppMessage(
            template = template,
            schedule = schedule,
            recipientType = recipientType,
            adminWhatsappNumber = resolvedAdminWa
        )
        val launchedApp = SmartReminderManager.launchWhatsAppReminder(
            context = context,
            rawPhone = targetPhone,
            message = message,
            appPreference = appPref
        )
        when {
            targetPhone.isBlank() -> {
                onPostMessage("Nomor WA $targetLabel belum diisi di tab Akun. $launchedApp dibuka untuk pilih kontak manual.")
            }
            resolvedAdminWa.isBlank() -> {
                onPostMessage("Membuka $launchedApp ke $targetLabel. Atur Nomor WA Admin KUA di tab Reminder untuk identitas pengirim resmi.")
            }
            else -> {
                onPostMessage("Membuka $launchedApp (WA Admin $resolvedAdminWa) ke $targetLabel.")
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                NavigationBarItem(
                    selected = selectedTab == AdminTab.SCHEDULES,
                    onClick = { selectedTab = AdminTab.SCHEDULES },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AdminTab.SCHEDULES) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = null
                        )
                    },
                    label = { Text(AdminTab.SCHEDULES.label, maxLines = 1) },
                    modifier = Modifier.testTag("admin_tab_schedules")
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.VERIFICATION,
                    onClick = { selectedTab = AdminTab.VERIFICATION },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AdminTab.VERIFICATION) Icons.Filled.FactCheck else Icons.Outlined.FactCheck,
                            contentDescription = null
                        )
                    },
                    label = { Text("Cek ($pendingCount)", maxLines = 1) },
                    modifier = Modifier.testTag("admin_tab_verification")
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.CALCULATOR,
                    onClick = { selectedTab = AdminTab.CALCULATOR },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AdminTab.CALCULATOR) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                            contentDescription = null
                        )
                    },
                    label = { Text(AdminTab.CALCULATOR.label, maxLines = 1) },
                    modifier = Modifier.testTag("admin_tab_calculator")
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.REMINDER,
                    onClick = { selectedTab = AdminTab.REMINDER },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AdminTab.REMINDER) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsActive,
                            contentDescription = null
                        )
                    },
                    label = { Text(AdminTab.REMINDER.label, maxLines = 1) },
                    modifier = Modifier.testTag("admin_tab_reminder")
                )
                NavigationBarItem(
                    selected = selectedTab == AdminTab.ACCOUNTS,
                    onClick = { selectedTab = AdminTab.ACCOUNTS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == AdminTab.ACCOUNTS) Icons.Filled.ManageAccounts else Icons.Outlined.ManageAccounts,
                            contentDescription = null
                        )
                    },
                    label = { Text(AdminTab.ACCOUNTS.label, maxLines = 1) },
                    modifier = Modifier.testTag("admin_tab_accounts")
                )
                }
            }
        },
        floatingActionButton = {
            when (selectedTab) {
                AdminTab.SCHEDULES, AdminTab.VERIFICATION -> {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingSchedule = null
                            showScheduleForm = true
                        },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_schedule)) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_add_schedule")
                    )
                }

                AdminTab.ACCOUNTS -> {
                    val isPenghuluFilter = selectedAccountRoleFilter == UserRole.PENGHULU
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isPenghuluFilter) {
                                showAddPenghuluDialog = true
                            } else {
                                showAddModinDialog = true
                            }
                        },
                        icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                        text = { Text(if (isPenghuluFilter) "Tambah Penghulu" else "Tambah Modin") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag(if (isPenghuluFilter) "fab_add_penghulu" else "fab_add_modin")
                    )
                }

                else -> Unit
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero_header") {
                    KuaHeroHeader(
                        title = "Dasbor Admin KUA",
                        subtitle = "KUA Donorojo • Input & Verifikasi Akhir",
                        officerName = "${activeAccount.officerName} (@${activeAccount.username})",
                        roleBadge = "Admin KUA • Real-Time",
                        isAdminAccount = true,
                        currentMode = DashboardMode.ADMIN_KUA,
                        onSwitchMode = onSwitchMode,
                        onSignOut = onLogoutStaff,
                        onOpenMonthlyReport = { showMonthlyReportSheet = true }
                    )
                }

                item(key = "kpi_strip") {
                    KpiSummaryStrip(
                        totalCount = schedules.size,
                        pendingCount = pendingCount,
                        verifiedCount = verifiedCount
                    )
                }

                when (selectedTab) {
                    AdminTab.SCHEDULES, AdminTab.VERIFICATION -> {
                        if (selectedTab == AdminTab.SCHEDULES) {
                            item(key = "monthly_report_quick_card") {
                                Surface(
                                    onClick = { showMonthlyReportSheet = true },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("card_open_monthly_report")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PictureAsPdf,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Download Laporan Bulanan (A4)",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "PDF siap cetak ukuran A4 vertikal dengan kop resmi KUA Donorojo & rekap 8 desa.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Download,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "A4",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "wa_digest_quick_card") {
                                val resolvedAdminWa = remember(reminderSettings, staffAccounts) {
                                    SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts)
                                }
                                WaDigestQuickCard(
                                    schedules = schedules,
                                    scopeLabel = "Semua Wilayah (8 Desa)",
                                    adminWhatsappNumber = resolvedAdminWa,
                                    appPreference = reminderSettings.resolvedAdminWhatsappApp(),
                                    villages = villages,
                                    onFeedback = onPostMessage
                                )
                            }

                            item(key = "admin_schedule_view_switcher") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AdminScheduleViewMode.entries.forEach { mode ->
                                        val icon = when (mode) {
                                            AdminScheduleViewMode.LIST -> Icons.Filled.List
                                            AdminScheduleViewMode.CALENDAR -> Icons.Filled.CalendarMonth
                                            AdminScheduleViewMode.ANALYTICS -> Icons.Filled.BarChart
                                        }
                                        FilterChip(
                                            selected = scheduleViewMode == mode,
                                            onClick = { scheduleViewMode = mode },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(mode.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("admin_view_mode_${mode.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            if (scheduleViewMode == AdminScheduleViewMode.CALENDAR) {
                                item(key = "admin_monthly_calendar") {
                                    MonthlyCalendarSection(
                                        schedules = schedules,
                                        selectedIsoDate = selectedCalendarDate,
                                        onSelectDate = { selectedCalendarDate = it }
                                    )
                                }
                            } else if (scheduleViewMode == AdminScheduleViewMode.ANALYTICS) {
                                item(key = "admin_recap_analytics") {
                                    RecapAnalyticsSection(
                                        villages = villages,
                                        schedules = schedules
                                    )
                                }
                            } else {
                                item(key = "today_spotlight") {
                                    TodayWeddingsSpotlightSection(
                                        todaySchedules = todaySchedules,
                                        isAdminMode = true,
                                        onScheduleClick = { detailSchedule = it },
                                        onSendWhatsApp = { item ->
                                            triggerWhatsAppReminder(
                                                item,
                                                ReminderRecipientType.MODIN,
                                                reminderSettings.whatsappTemplate,
                                                SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts),
                                                reminderSettings.resolvedAdminWhatsappApp()
                                            )
                                        },
                                        onSendWhatsAppPenghulu = { item ->
                                            triggerWhatsAppReminder(
                                                item,
                                                ReminderRecipientType.PENGHULU,
                                                reminderSettings.whatsappPenghuluTemplate,
                                                SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts),
                                                reminderSettings.resolvedAdminWhatsappApp()
                                            )
                                        },
                                        onSendPhoneNotif = { item ->
                                            triggerPhoneNotification(item)
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedTab == AdminTab.VERIFICATION || scheduleViewMode != AdminScheduleViewMode.ANALYTICS) {
                        item(key = "search_filter_bar") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchAndStatusFilterBar(
                                    searchQuery = searchQuery,
                                    onSearchChange = { searchQuery = it },
                                    selectedStatus = if (selectedTab == AdminTab.VERIFICATION) null else selectedStatusFilter,
                                    onSelectStatus = {
                                        if (selectedTab == AdminTab.VERIFICATION) {
                                            selectedTab = AdminTab.SCHEDULES
                                        }
                                        selectedStatusFilter = it
                                    }
                                )
                                if (villages.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedVillageFilter == null,
                                            onClick = { selectedVillageFilter = null },
                                            label = { Text("Semua 8 Desa") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                        villages.forEach { village ->
                                            FilterChip(
                                                selected = selectedVillageFilter == village.code,
                                                onClick = {
                                                    selectedVillageFilter =
                                                        if (selectedVillageFilter == village.code) null else village.code
                                                },
                                                label = { Text(village.name.removePrefix("Desa ")) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        when (schedulesState) {
                            is UiState.Loading -> {
                                item(key = "loading_schedules") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is UiState.Error -> {
                                item(key = "error_schedules") {
                                    ErrorStateCard(message = schedulesState.message)
                                }
                            }

                            is UiState.Success -> {
                                if (filteredSchedules.isEmpty()) {
                                    item(key = "empty_schedules") {
                                        EmptyStateCard(
                                            icon = if (selectedTab == AdminTab.VERIFICATION) Icons.Filled.Verified else Icons.Filled.EventAvailable,
                                            title = if (selectedTab == AdminTab.VERIFICATION) {
                                                "Semua Berkas Telah Diverifikasi"
                                            } else {
                                                "Belum Ada Jadwal Nikah"
                                            },
                                            description = if (selectedTab == AdminTab.VERIFICATION) {
                                                "Tidak ada pendaftaran yang menunggu pemeriksaan atau perbaikan saat ini."
                                            } else {
                                                "Tekan tombol Daftar Nikah untuk menambahkan jadwal akad baru di wilayah KUA Donorojo."
                                            },
                                            actionLabel = if (selectedTab == AdminTab.SCHEDULES && villages.isNotEmpty()) {
                                                "Input Pendaftaran Pertama"
                                            } else {
                                                null
                                            },
                                            onAction = {
                                                editingSchedule = null
                                                showScheduleForm = true
                                            }
                                        )
                                    }
                                } else {
                                    items(filteredSchedules, key = { it.id }) { schedule ->
                                        ScheduleCard(
                                            schedule = schedule,
                                            isAdminMode = true,
                                            onClick = { detailSchedule = schedule },
                                            onQuickVerify = { verifyingSchedule = schedule },
                                            onEdit = {
                                                editingSchedule = schedule
                                                showScheduleForm = true
                                            },
                                            onDelete = { deletingSchedule = schedule }
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }

                    AdminTab.CALCULATOR -> {
                        item(key = "kua_calculators_section") {
                            KuaCalculatorsSection()
                        }
                    }

                    AdminTab.REMINDER -> {
                        item(key = "reminder_wa_digest_card") {
                            val resolvedAdminWa = remember(reminderSettings, staffAccounts) {
                                SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts)
                            }
                            WaDigestQuickCard(
                                schedules = schedules,
                                scopeLabel = "Semua Wilayah (8 Desa)",
                                adminWhatsappNumber = resolvedAdminWa,
                                appPreference = reminderSettings.resolvedAdminWhatsappApp(),
                                villages = villages,
                                onFeedback = onPostMessage
                            )
                        }
                        item(key = "smart_reminder_section") {
                            SmartReminderSection(
                                settings = reminderSettings,
                                schedules = schedules,
                                staffAccounts = staffAccounts,
                                hasNotifPermission = hasNotifPermission,
                                isSaving = isSaving,
                                onRequestNotifPermission = requestNotifPermission,
                                onSaveSettings = onSaveReminderSettings,
                                onSendPhoneNotif = triggerPhoneNotification,
                                onSendWhatsApp = triggerWhatsAppReminder
                            )
                        }
                    }

                    AdminTab.ACCOUNTS -> {
                        item(key = "cloud_sync_status_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_cloud_sync_status")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                imageVector = if (cloudSyncStatus.isSyncing) Icons.Filled.CloudSync else Icons.Filled.CloudDone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Sinkronisasi Cloud Antar-HP",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (cloudSyncStatus.userEmail.isNotBlank()) {
                                                    "Terhubung: ${cloudSyncStatus.userEmail}"
                                                } else {
                                                    "Terhubung ke Firebase Cloud Firestore"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = if (cloudSyncStatus.isOnlineSynced) StatusVerifiedBg else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (cloudSyncStatus.isSyncing) "Menyinkron..." else if (cloudSyncStatus.isOnlineSynced) "Online" else "Siap",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cloudSyncStatus.isOnlineSynced) StatusVerifiedFg else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = if (cloudSyncStatus.lastSyncedAtMillis != null) {
                                            "Sinkron terakhir: ${formatShortTimestamp(cloudSyncStatus.lastSyncedAtMillis)} • Isi Email Google pada akun Penghulu/Modin agar data otomatis tersinkron ke HP mereka."
                                        } else {
                                            "Isi Email Google pada setiap akun Penghulu dan Modin di bawah agar jadwal nikah otomatis tampil real-time di HP mereka."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    OutlinedButton(
                                        onClick = onTriggerCloudSync,
                                        enabled = !cloudSyncStatus.isSyncing,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("btn_trigger_cloud_sync")
                                    ) {
                                        if (cloudSyncStatus.isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Menyinkronkan Data...")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Sync,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Sinkronkan Cloud Sekarang")
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "backup_restore_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_backup_restore")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Cadangan & Pemulihan Data (JSON)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Simpan arsip lengkap jadwal nikah, akun petugas, dan pengaturan KUA ke file JSON atau pulihkan kapan saja.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                createBackupLauncher.launch(
                                                    KuaDataTransferManager.suggestedBackupFileName()
                                                )
                                            },
                                            enabled = !isProcessingTransfer && !isSaving,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("btn_backup_json")
                                        ) {
                                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.action_backup_json),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                openRestoreLauncher.launch(
                                                    arrayOf("application/json", "text/plain", "*/*")
                                                )
                                            },
                                            enabled = !isProcessingTransfer && !isSaving,
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("btn_restore_json")
                                        ) {
                                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.action_restore_json),
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "head_of_kua_settings_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_kepala_kua_settings")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Badge,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Pengaturan Kepala KUA Donorojo",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Digunakan otomatis pada tanda tangan Laporan Bulanan A4.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = reminderSettings.headOfKuaTitle.ifBlank {
                                                    ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = reminderSettings.headOfKuaName.ifBlank {
                                                    "Nama Kepala KUA belum diatur"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (reminderSettings.headOfKuaNip.isNotBlank()) {
                                                    "NIP. ${reminderSettings.headOfKuaNip}"
                                                } else {
                                                    "NIP. Belum diatur"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { showHeadOfKuaDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("btn_open_kepala_kua_settings")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Atur Nama & NIP Kepala KUA")
                                    }
                                }
                            }
                        }

                        item(key = "wali_hakim_settings_card") {
                            val penghuluList = staffAccounts.filter { it.resolvedRole() == UserRole.PENGHULU }
                            val waliHakimList = penghuluList.filter {
                                it.isWaliHakim || it.id == reminderSettings.defaultWaliHakimAccountId
                            }
                            val defaultWaliHakimAccount = penghuluList.firstOrNull {
                                it.id == reminderSettings.defaultWaliHakimAccountId
                            }
                            val defaultDisplay = when {
                                defaultWaliHakimAccount != null ->
                                    "${defaultWaliHakimAccount.officerName} (@${defaultWaliHakimAccount.username})"
                                reminderSettings.defaultWaliHakimName.isNotBlank() ->
                                    reminderSettings.defaultWaliHakimName
                                waliHakimList.isNotEmpty() ->
                                    waliHakimList.first().officerName
                                else -> "Belum ditentukan"
                            }

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.2.dp,
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_wali_hakim_settings")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Gavel,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier
                                                    .padding(10.dp)
                                                    .size(22.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Pengaturan Penghulu Wali Hakim",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Tentukan Penghulu yang bertugas menjadi Wali Hakim pada pendaftaran nikah.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = "Wali Hakim Utama (Otomatis di Form)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = defaultDisplay,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (waliHakimList.isNotEmpty()) {
                                                    "${waliHakimList.size} Penghulu ditugaskan sebagai Wali Hakim: ${
                                                        waliHakimList.joinToString(", ") { it.officerName }
                                                    }"
                                                } else {
                                                    "Belum ada akun Penghulu ditandai sebagai Wali Hakim."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { showWaliHakimSettingsDialog = true },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("btn_open_wali_hakim_settings")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Gavel,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Atur Penghulu Wali Hakim")
                                    }
                                }
                            }
                        }

                        item(key = "accounts_intro") {
                            val totalPenghuluCount = staffAccounts.count { it.resolvedRole() == UserRole.PENGHULU }
                            val totalModinCount = staffAccounts.count { it.resolvedRole() == UserRole.MODIN }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Pengaturan Akun Admin, Penghulu & Modin",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Kelola akun login dan nomor WA petugas KUA Donorojo ($totalPenghuluCount Penghulu & $totalModinCount Modin terdaftar). Penghulu dan Modin dapat menautkan jadwal tugasnya langsung ke Kalender HP.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showAddPenghuluDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("btn_add_penghulu_header")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PersonAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tambah Penghulu",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { showAddModinDialog = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("btn_add_modin_header")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PersonAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tambah Modin",
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "accounts_role_filter") {
                            val totalPenghulu = staffAccounts.count { it.resolvedRole() == UserRole.PENGHULU }
                            val totalModin = staffAccounts.count { it.resolvedRole() == UserRole.MODIN }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedAccountRoleFilter == null && selectedAccountVillageFilter == null,
                                    onClick = {
                                        selectedAccountRoleFilter = null
                                        selectedAccountVillageFilter = null
                                    },
                                    label = { Text("Semua (${staffAccounts.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("filter_role_all")
                                )
                                FilterChip(
                                    selected = selectedAccountRoleFilter == UserRole.PENGHULU,
                                    onClick = {
                                        selectedAccountRoleFilter =
                                            if (selectedAccountRoleFilter == UserRole.PENGHULU) null else UserRole.PENGHULU
                                        selectedAccountVillageFilter = null
                                    },
                                    label = { Text("Penghulu ($totalPenghulu)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("filter_role_penghulu")
                                )
                                FilterChip(
                                    selected = selectedAccountRoleFilter == UserRole.MODIN && selectedAccountVillageFilter == null,
                                    onClick = {
                                        selectedAccountRoleFilter =
                                            if (selectedAccountRoleFilter == UserRole.MODIN) null else UserRole.MODIN
                                        selectedAccountVillageFilter = null
                                    },
                                    label = { Text("Modin Desa ($totalModin)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("filter_role_modin")
                                )
                            }
                        }

                        if (villages.isNotEmpty() && selectedAccountRoleFilter != UserRole.PENGHULU) {
                            item(key = "accounts_village_filter") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    villages.forEach { village ->
                                        val countInVillage = staffAccounts.count {
                                            it.resolvedRole() == UserRole.MODIN && it.villageId == village.code
                                        }
                                        FilterChip(
                                            selected = selectedAccountVillageFilter == village.code,
                                            onClick = {
                                                if (selectedAccountVillageFilter == village.code) {
                                                    selectedAccountVillageFilter = null
                                                } else {
                                                    selectedAccountRoleFilter = UserRole.MODIN
                                                    selectedAccountVillageFilter = village.code
                                                }
                                            },
                                            label = { Text("${village.name.removePrefix("Desa ")} ($countInVillage)") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        when (staffAccountsState) {
                            is UiState.Loading -> {
                                item(key = "loading_accounts") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is UiState.Error -> {
                                item(key = "error_accounts") {
                                    ErrorStateCard(message = staffAccountsState.message)
                                }
                            }

                            is UiState.Success -> {
                                if (filteredStaffAccounts.isEmpty()) {
                                    item(key = "empty_accounts") {
                                        val isPenghuluFilter = selectedAccountRoleFilter == UserRole.PENGHULU
                                        EmptyStateCard(
                                            icon = Icons.Filled.Groups,
                                            title = if (isPenghuluFilter) "Belum Ada Akun Penghulu" else "Belum Ada Modin di Desa Ini",
                                            description = if (isPenghuluFilter) {
                                                "Tekan tombol Tambah Penghulu untuk membuat username dan password Penghulu KUA Donorojo."
                                            } else {
                                                "Tekan tombol Tambah Modin untuk membuat username dan password Modin baru untuk desa ini."
                                            },
                                            actionLabel = if (isPenghuluFilter) "Tambah Penghulu Baru" else "Tambah Modin Baru",
                                            onAction = {
                                                if (isPenghuluFilter) {
                                                    showAddPenghuluDialog = true
                                                } else {
                                                    showAddModinDialog = true
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    items(filteredStaffAccounts, key = { it.id }) { account ->
                                        val scheduleCount = when (account.resolvedRole()) {
                                            UserRole.ADMIN -> schedules.size
                                            UserRole.PENGHULU -> schedules.count {
                                                it.penghuluAccountId == account.id ||
                                                    it.waliHakimAccountId == account.id ||
                                                    (it.penghuluAccountId.isBlank() &&
                                                        it.penghuluName.equals(account.officerName, ignoreCase = true))
                                            }
                                            UserRole.MODIN -> schedules.count {
                                                it.modinAccountId == account.id ||
                                                    (it.modinAccountId.isBlank() && it.villageId == account.villageId)
                                            }
                                        }
                                        StaffAccountAdminCard(
                                            account = account,
                                            scheduleCount = scheduleCount,
                                            isDefaultWaliHakim = account.resolvedRole() == UserRole.PENGHULU &&
                                                account.id == reminderSettings.defaultWaliHakimAccountId,
                                            onEdit = { editingAccount = account },
                                            onDelete = if (account.resolvedRole() != UserRole.ADMIN) {
                                                { deletingAccount = account }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheets & dialogs
    detailSchedule?.let { schedule ->
        ScheduleDetailSheet(
            schedule = schedule,
            isAdminMode = true,
            onDismiss = { detailSchedule = null },
            onVerifyClick = {
                detailSchedule = null
                verifyingSchedule = schedule
            },
            onEditClick = {
                detailSchedule = null
                editingSchedule = schedule
                showScheduleForm = true
            },
            onDeleteClick = {
                detailSchedule = null
                deletingSchedule = schedule
            },
            onSendWhatsApp = {
                triggerWhatsAppReminder(
                    schedule,
                    ReminderRecipientType.MODIN,
                    reminderSettings.whatsappTemplate,
                    SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts),
                    reminderSettings.resolvedAdminWhatsappApp()
                )
            },
            onSendWhatsAppPenghulu = {
                triggerWhatsAppReminder(
                    schedule,
                    ReminderRecipientType.PENGHULU,
                    reminderSettings.whatsappPenghuluTemplate,
                    SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, staffAccounts),
                    reminderSettings.resolvedAdminWhatsappApp()
                )
            },
            onSendPhoneNotif = {
                triggerPhoneNotification(schedule)
            }
        )
    }

    verifyingSchedule?.let { schedule ->
        VerificationSheet(
            schedule = schedule,
            isSaving = isSaving,
            onDismiss = { verifyingSchedule = null },
            onConfirm = { newStatus, checklist, docsComplete, note ->
                onUpdateVerification(schedule.id, newStatus, checklist, docsComplete, note) {
                    verifyingSchedule = null
                }
            }
        )
    }

    if (showScheduleForm) {
        ScheduleFormSheet(
            existingSchedule = editingSchedule,
            villages = villages,
            staffAccounts = staffAccounts,
            reminderSettings = reminderSettings,
            allSchedules = schedules,
            isSaving = isSaving,
            onDismiss = {
                showScheduleForm = false
                editingSchedule = null
            },
            onSubmit = { draft, village ->
                onSaveSchedule(draft, village) {
                    showScheduleForm = false
                    editingSchedule = null
                }
            }
        )
    }

    deletingSchedule?.let { schedule ->
        AlertDialog(
            onDismissRequest = { deletingSchedule = null },
            title = { Text("Hapus Jadwal Nikah?") },
            text = {
                Text("Pendaftaran ${schedule.groomName} & ${schedule.brideName} (${schedule.registrationNumber}) akan dihapus secara permanen.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSchedule(schedule.id) { deletingSchedule = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSchedule = null }) { Text("Batal") }
            }
        )
    }

    if (showAddModinDialog) {
        AddModinAccountDialog(
            villages = villages,
            existingAccounts = staffAccounts,
            initialVillageCode = selectedAccountVillageFilter,
            isSaving = isSaving,
            onDismiss = { showAddModinDialog = false },
            onCreate = { village, username, password, officerName, whatsappNumber, googleEmail, active ->
                onCreateModinAccount(
                    village,
                    username,
                    password,
                    officerName,
                    whatsappNumber,
                    googleEmail,
                    active
                ) {
                    showAddModinDialog = false
                }
            }
        )
    }

    if (showAddPenghuluDialog) {
        AddPenghuluAccountDialog(
            existingAccounts = staffAccounts,
            isSaving = isSaving,
            onDismiss = { showAddPenghuluDialog = false },
            onCreate = { username, password, officerName, whatsappNumber, googleEmail, active, isWaliHakim ->
                onCreatePenghuluAccount(
                    username,
                    password,
                    officerName,
                    whatsappNumber,
                    googleEmail,
                    active,
                    isWaliHakim
                ) {
                    showAddPenghuluDialog = false
                }
            }
        )
    }

    editingAccount?.let { account ->
        StaffAccountDialog(
            account = account,
            villages = villages,
            isSaving = isSaving,
            onDismiss = { editingAccount = null },
            onSave = { newUsername, newPassword, officerName, whatsappNumber, googleEmail, active, newVillage, isWaliHakim ->
                onUpdateStaffAccount(
                    account,
                    newUsername,
                    newPassword,
                    officerName,
                    whatsappNumber,
                    googleEmail,
                    active,
                    newVillage,
                    isWaliHakim
                ) {
                    editingAccount = null
                }
            }
        )
    }

    pendingRestorePayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingRestorePayload = null },
            title = { Text("Pulihkan Data Backup?") },
            text = {
                Text(
                    "File backup berisi ${payload.schedules.size} jadwal nikah, ${payload.staffAccounts.size} akun petugas, dan ${payload.villages.size} wilayah desa. Data akan digabungkan ke perangkat ini dan disinkronkan ke cloud."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreBackup(payload) {
                            pendingRestorePayload = null
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("confirm_restore_json_button")
                ) {
                    Text("Pulihkan Data")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestorePayload = null },
                    enabled = !isSaving
                ) {
                    Text("Batal")
                }
            }
        )
    }

    deletingAccount?.let { account ->
        val isPenghulu = account.resolvedRole() == UserRole.PENGHULU
        val roleTitle = if (isPenghulu) "Penghulu" else "Modin"
        val scopeDesc = if (isPenghulu) "KUA Donorojo" else account.villageName
        AlertDialog(
            onDismissRequest = { deletingAccount = null },
            title = { Text("Hapus Akun $roleTitle?") },
            text = {
                Text("Akun $roleTitle \"${account.officerName}\" (@${account.username} • $scopeDesc) akan dihapus. Jadwal nikah yang sudah ada tidak akan terhapus.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStaffAccount(account) { deletingAccount = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAccount = null }) { Text("Batal") }
            }
        )
    }

    if (showMonthlyReportSheet) {
        MonthlyReportSheet(
            reminderSettings = reminderSettings,
            villages = villages,
            schedules = schedules,
            onOpenHeadOfKuaSettings = { showHeadOfKuaDialog = true },
            onDismiss = { showMonthlyReportSheet = false },
            onPostMessage = onPostMessage
        )
    }

    if (showHeadOfKuaDialog) {
        KepalaKuaSettingsDialog(
            settings = reminderSettings,
            isSaving = isSaving,
            onDismiss = { showHeadOfKuaDialog = false },
            onSave = { title, name, nip ->
                onSaveHeadOfKuaSettings(title, name, nip) {
                    showHeadOfKuaDialog = false
                }
            }
        )
    }

    if (showWaliHakimSettingsDialog) {
        WaliHakimSettingsDialog(
            penghuluAccounts = staffAccounts.filter { it.resolvedRole() == UserRole.PENGHULU },
            settings = reminderSettings,
            isSaving = isSaving,
            onDismiss = { showWaliHakimSettingsDialog = false },
            onSave = { authorizedIds, defaultId, defaultName ->
                onSaveWaliHakimSettings(authorizedIds, defaultId, defaultName) {
                    showWaliHakimSettingsDialog = false
                }
            }
        )
    }
}

@Composable
private fun StaffAccountAdminCard(
    account: StaffAccount,
    scheduleCount: Int,
    isDefaultWaliHakim: Boolean = false,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val role = account.resolvedRole()
    val isAdmin = role == UserRole.ADMIN
    val isPenghulu = role == UserRole.PENGHULU
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("staff_account_card_${account.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isAdmin || isPenghulu) 1.5.dp else 1.dp,
            color = when {
                isAdmin -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                isPenghulu -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            isAdmin -> Icons.Filled.AdminPanelSettings
                            isPenghulu -> Icons.Filled.Badge
                            else -> Icons.Filled.Person
                        },
                        contentDescription = null,
                        tint = if (isPenghulu) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = if (isAdmin) "Admin KUA Donorojo" else account.officerName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isAdmin -> "Akses Penuh • $scheduleCount total jadwal"
                                isPenghulu -> buildString {
                                    append("Penghulu KUA Donorojo")
                                    if (isDefaultWaliHakim) {
                                        append(" • Wali Hakim Utama")
                                    } else if (account.isWaliHakim) {
                                        append(" • Wali Hakim")
                                    }
                                    append(" • $scheduleCount jadwal")
                                }
                                else -> "${account.villageName} • $scheduleCount jadwal"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (account.active) StatusVerifiedBg else StatusCancelledBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (account.active) "Aktif" else "Nonaktif",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (account.active) StatusVerifiedFg else StatusCancelledFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Username: ${account.username}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (account.whatsappNumber.isNotBlank()) {
                        if (isAdmin) {
                            "WA Pengirim Smart Reminder: ${account.whatsappNumber}"
                        } else {
                            "WA Reminder: ${account.whatsappNumber}"
                        }
                    } else {
                        if (isAdmin) {
                            "WA Pengirim Smart Reminder: Belum diisi"
                        } else {
                            "WA Reminder: Belum diisi"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (account.whatsappNumber.isNotBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }

            if (account.googleEmail.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Cloud Sync: ${account.googleEmail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("delete_account_btn_${account.id}")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus")
                    }
                }
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_account_btn_${account.id}")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Atur Akun")
                }
            }
        }
    }
}
