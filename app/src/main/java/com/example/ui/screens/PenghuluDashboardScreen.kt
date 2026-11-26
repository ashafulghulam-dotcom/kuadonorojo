package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.data.CloudSyncStatus
import com.example.data.DashboardMode
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.todayIsoDate
import com.example.ui.components.DeviceCalendarSyncCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.KpiSummaryStrip
import com.example.ui.components.KuaHeroHeader
import com.example.ui.components.MonthlyCalendarSection
import com.example.ui.components.PenghuluMonthlyRecapChartCard
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailSheet
import com.example.ui.components.SearchAndStatusFilterBar
import com.example.ui.components.SelfProfileSettingsSection
import com.example.ui.components.TodayWeddingsSpotlightSection
import com.example.ui.components.WaDigestQuickCard
import com.example.util.DeviceCalendarSyncManager
import com.example.util.SmartReminderManager
import kotlinx.coroutines.launch

private enum class PenghuluTab(val label: String) {
    SCHEDULES("Jadwal Tugas"),
    CALCULATOR("Kalkulator"),
    PROFILE("Akun Saya")
}

@Composable
fun PenghuluDashboardScreen(
    activeAccount: StaffAccount,
    isAdminPreview: Boolean,
    staffAccountsState: UiState<List<StaffAccount>>,
    schedulesState: UiState<List<MarriageSchedule>>,
    reminderSettingsState: UiState<ReminderSettings>,
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus(),
    previewPenghuluId: String?,
    isSaving: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onSelectPreviewPenghulu: (String?) -> Unit,
    onUpdateOwnProfile: (
        officerName: String,
        whatsappNumber: String,
        username: String,
        currentPassword: String,
        newPassword: String?,
        onSuccess: () -> Unit
    ) -> Unit,
    onSwitchMode: (DashboardMode) -> Unit,
    onLogoutStaff: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(PenghuluTab.SCHEDULES) }
    var showCalendarView by remember { mutableStateOf(false) }
    var selectedCalendarDate by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<ValidationStatus?>(null) }
    var onlyMySchedules by remember { mutableStateOf(true) }
    var detailSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var hasNotifPermission by remember {
        mutableStateOf(SmartReminderManager.hasNotificationPermission(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    LaunchedEffect(actionMessage) {
        if (actionMessage != null) {
            snackbarHostState.showSnackbar(actionMessage)
            onClearMessage()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = selectedTab != PenghuluTab.SCHEDULES || isAdminPreview) {
        if (selectedTab != PenghuluTab.SCHEDULES) {
            selectedTab = PenghuluTab.SCHEDULES
        } else if (isAdminPreview) {
            onSwitchMode(DashboardMode.ADMIN_KUA)
        }
    }

    val allAccounts = (staffAccountsState as? UiState.Success)?.data.orEmpty()
    val penghuluAccounts = remember(allAccounts) {
        allAccounts.filter { it.resolvedRole() == UserRole.PENGHULU }
    }
    val allSchedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val reminderSettings = (reminderSettingsState as? UiState.Success)?.data ?: ReminderSettings()

    val targetPenghulu: StaffAccount? = remember(isAdminPreview, activeAccount, previewPenghuluId, penghuluAccounts) {
        if (isAdminPreview) {
            penghuluAccounts.firstOrNull { it.id == previewPenghuluId } ?: penghuluAccounts.firstOrNull()
        } else {
            activeAccount
        }
    }

    val myAssignedSchedules = remember(allSchedules, targetPenghulu) {
        if (targetPenghulu == null) {
            emptyList()
        } else {
            allSchedules.filter { item ->
                item.penghuluAccountId == targetPenghulu.id ||
                    item.waliHakimAccountId == targetPenghulu.id ||
                    (item.penghuluAccountId.isBlank() &&
                        item.penghuluName.isNotBlank() &&
                        item.penghuluName.equals(targetPenghulu.officerName, ignoreCase = true))
            }
        }
    }

    val activeScopeSchedules = remember(allSchedules, myAssignedSchedules, onlyMySchedules, targetPenghulu) {
        if (onlyMySchedules && targetPenghulu != null) myAssignedSchedules else allSchedules
    }

    val todayIso = remember { todayIsoDate() }
    val todaySchedules = remember(activeScopeSchedules, todayIso) {
        activeScopeSchedules.filter {
            it.weddingDate == todayIso && it.resolvedStatus() != ValidationStatus.DIBATALKAN
        }.sortedBy { it.weddingTime }
    }

    // Automatic HP notification trigger for Penghulu's assigned weddings
    LaunchedEffect(myAssignedSchedules, reminderSettings, hasNotifPermission) {
        if (hasNotifPermission && myAssignedSchedules.isNotEmpty()) {
            val sentCount = SmartReminderManager.checkAndTriggerAutoNotifications(
                context = context,
                schedules = myAssignedSchedules,
                settings = reminderSettings
            )
            if (sentCount > 0) {
                snackbarHostState.showSnackbar(
                    "Notifikasi otomatis terkirim untuk $sentCount jadwal tugas Penghulu."
                )
            }
        }
    }

    val filteredSchedules = remember(
        activeScopeSchedules,
        searchQuery,
        selectedStatusFilter,
        showCalendarView,
        selectedCalendarDate
    ) {
        activeScopeSchedules.filter { item ->
            val status = item.resolvedStatus()
            val matchesStatus = selectedStatusFilter == null || status == selectedStatusFilter
            val matchesDate = if (showCalendarView && !selectedCalendarDate.isNullOrBlank()) {
                item.weddingDate == selectedCalendarDate
            } else {
                true
            }
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                item.groomName.lowercase().contains(q) ||
                item.brideName.lowercase().contains(q) ||
                item.villageName.lowercase().contains(q) ||
                item.modinName.lowercase().contains(q) ||
                item.registrationNumber.lowercase().contains(q) ||
                item.penghuluName.lowercase().contains(q)
            matchesStatus && matchesDate && matchesSearch
        }
    }

    val pendingCount = remember(activeScopeSchedules) {
        activeScopeSchedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.MENUNGGU ||
                st == ValidationStatus.VERIFIKASI_BERKAS ||
                st == ValidationStatus.PERLU_REVISI
        }
    }
    val verifiedCount = remember(activeScopeSchedules) {
        activeScopeSchedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.TERVERIFIKASI || st == ValidationStatus.SELESAI
        }
    }

    val activePenghuluDisplay = targetPenghulu?.officerName ?: "Penghulu KUA Donorojo"

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == PenghuluTab.SCHEDULES,
                        onClick = { selectedTab = PenghuluTab.SCHEDULES },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PenghuluTab.SCHEDULES) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                                contentDescription = null
                            )
                        },
                        label = { Text(PenghuluTab.SCHEDULES.label, maxLines = 1) },
                        modifier = Modifier.testTag("penghulu_tab_schedules")
                    )
                    NavigationBarItem(
                        selected = selectedTab == PenghuluTab.CALCULATOR,
                        onClick = { selectedTab = PenghuluTab.CALCULATOR },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PenghuluTab.CALCULATOR) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                contentDescription = null
                            )
                        },
                        label = { Text(PenghuluTab.CALCULATOR.label, maxLines = 1) },
                        modifier = Modifier.testTag("penghulu_tab_calculator")
                    )
                    NavigationBarItem(
                        selected = selectedTab == PenghuluTab.PROFILE,
                        onClick = { selectedTab = PenghuluTab.PROFILE },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PenghuluTab.PROFILE) Icons.Filled.ManageAccounts else Icons.Outlined.ManageAccounts,
                                contentDescription = null
                            )
                        },
                        label = { Text(PenghuluTab.PROFILE.label, maxLines = 1) },
                        modifier = Modifier.testTag("penghulu_tab_profile")
                    )
                }
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "penghulu_hero_header") {
                    KuaHeroHeader(
                        title = "Dasbor Penghulu KUA",
                        subtitle = "$activePenghuluDisplay • Jadwal Akad & Kalender HP",
                        officerName = "${activeAccount.officerName} (@${activeAccount.username})",
                        roleBadge = if (isAdminPreview) "Pratinjau Penghulu • Baca Saja" else "Penghulu KUA • Real-Time",
                        isAdminAccount = isAdminPreview,
                        currentMode = DashboardMode.PENGHULU,
                        onSwitchMode = onSwitchMode,
                        onSignOut = onLogoutStaff
                    )
                }

                when (selectedTab) {
                    PenghuluTab.CALCULATOR -> {
                        item(key = "penghulu_kua_calculators") {
                            KuaCalculatorsSection()
                        }
                    }

                    PenghuluTab.PROFILE -> {
                        item(key = "penghulu_self_profile") {
                            val profileAccount = targetPenghulu ?: activeAccount
                            SelfProfileSettingsSection(
                                account = profileAccount,
                                isAdminPreview = isAdminPreview,
                                isSaving = isSaving,
                                onSaveProfile = onUpdateOwnProfile
                            )
                        }
                    }

                    PenghuluTab.SCHEDULES -> {
                    // Calendar Sync Card directly on Penghulu dashboard
                    item(key = "penghulu_calendar_sync") {
                        DeviceCalendarSyncCard(
                            accountId = targetPenghulu?.id ?: activeAccount.id,
                            roleLabel = "Penghulu KUA",
                            schedulesToSync = myAssignedSchedules,
                            onFeedback = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    // 1-click WA Group Digest share/copy for Penghulu
                    item(key = "penghulu_wa_digest_card") {
                        val resolvedAdminWa = remember(reminderSettings, allAccounts) {
                            SmartReminderManager.resolveAdminWhatsappNumber(reminderSettings, allAccounts)
                        }
                        val digestScope = if (onlyMySchedules && targetPenghulu != null) {
                            "Penghulu: ${targetPenghulu.officerName}"
                        } else {
                            "Semua Wilayah (8 Desa)"
                        }
                        WaDigestQuickCard(
                            schedules = activeScopeSchedules,
                            scopeLabel = digestScope,
                            adminWhatsappNumber = resolvedAdminWa,
                            appPreference = reminderSettings.resolvedAdminWhatsappApp(),
                            onFeedback = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    // Notification status banner
                    item(key = "penghulu_notif_banner") {
                        if (!hasNotifPermission) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.NotificationsOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Aktifkan izin notifikasi HP agar pengingat jadwal tugas akad muncul otomatis.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("penghulu_enable_notif_button")
                                    ) {
                                        Text("Aktifkan", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (reminderSettings.autoPhoneNotifEnabled) {
                                            Icons.Filled.NotificationsActive
                                        } else {
                                            Icons.Filled.Lock
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            append(
                                                if (reminderSettings.autoPhoneNotifEnabled) {
                                                    "Notif HP Otomatis Aktif. Pengingat akad untuk tugas $activePenghuluDisplay aktif otomatis."
                                                } else {
                                                    "Mode Pantau Penghulu (Read-Only): Perubahan jadwal dikelola oleh Admin KUA Donorojo."
                                                }
                                            )
                                            if (cloudSyncStatus.isOnlineSynced) {
                                                append(" • Cloud Online")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Admin Preview Penghulu Selector
                    if (isAdminPreview && penghuluAccounts.isNotEmpty()) {
                        item(key = "penghulu_preview_selector") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Pilih Akun Penghulu untuk Pratinjau:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    penghuluAccounts.forEach { penghulu ->
                                        val selected = targetPenghulu?.id == penghulu.id
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectPreviewPenghulu(penghulu.id) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(penghulu.officerName) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("penghulu_preview_chip_${penghulu.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Scope toggle: Jadwal Tugas Saya vs Semua Jadwal KUA
                    item(key = "penghulu_scope_toggle") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = onlyMySchedules,
                                onClick = { onlyMySchedules = true },
                                label = { Text("Jadwal Tugas Saya (${myAssignedSchedules.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("penghulu_filter_mine")
                            )
                            FilterChip(
                                selected = !onlyMySchedules,
                                onClick = { onlyMySchedules = false },
                                label = { Text("Semua Jadwal KUA (${allSchedules.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("penghulu_filter_all")
                            )
                        }
                    }

                    item(key = "penghulu_view_mode_toggle") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !showCalendarView,
                                onClick = { showCalendarView = false },
                                leadingIcon = {
                                    Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Daftar Jadwal") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("penghulu_view_list")
                            )
                            FilterChip(
                                selected = showCalendarView,
                                onClick = { showCalendarView = true },
                                leadingIcon = {
                                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Kalender Bulanan") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("penghulu_view_calendar")
                            )
                        }
                    }

                    if (showCalendarView) {
                        item(key = "penghulu_monthly_calendar") {
                            MonthlyCalendarSection(
                                schedules = activeScopeSchedules,
                                selectedIsoDate = selectedCalendarDate,
                                onSelectDate = { selectedCalendarDate = it }
                            )
                        }
                    } else {
                        item(key = "penghulu_today_spotlight") {
                            TodayWeddingsSpotlightSection(
                                todaySchedules = todaySchedules,
                                isAdminMode = false,
                                onScheduleClick = { detailSchedule = it }
                            )
                        }
                    }

                    item(key = "penghulu_kpi_strip") {
                        KpiSummaryStrip(
                            totalCount = activeScopeSchedules.size,
                            pendingCount = pendingCount,
                            verifiedCount = verifiedCount
                        )
                    }

                    item(key = "penghulu_monthly_recap_chart") {
                        PenghuluMonthlyRecapChartCard(
                            allSchedules = allSchedules,
                            penghuluAccounts = penghuluAccounts,
                            defaultPenghuluId = targetPenghulu?.id
                        )
                    }

                    item(key = "penghulu_search_filter") {
                        SearchAndStatusFilterBar(
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            selectedStatus = selectedStatusFilter,
                            onSelectStatus = { selectedStatusFilter = it }
                        )
                    }

                    when (schedulesState) {
                        is UiState.Loading -> {
                            item(key = "penghulu_loading") {
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
                            item(key = "penghulu_error") {
                                ErrorStateCard(message = schedulesState.message)
                            }
                        }

                        is UiState.Success -> {
                            if (filteredSchedules.isEmpty()) {
                                item(key = "penghulu_empty") {
                                    EmptyStateCard(
                                        icon = Icons.Filled.EventNote,
                                        title = if (onlyMySchedules) {
                                            "Belum Ada Jadwal Tugas Penghulu"
                                        } else {
                                            "Belum Ada Jadwal Nikah"
                                        },
                                        description = if (onlyMySchedules) {
                                            "Saat Admin KUA Donorojo menunjuk $activePenghuluDisplay pada jadwal akad, jadwal akan otomatis tampil di sini dan siap ditautkan ke Kalender HP."
                                        } else {
                                            "Belum ada pendaftaran nikah tercatat di KUA Donorojo."
                                        }
                                    )
                                }
                            } else {
                                items(filteredSchedules, key = { it.id }) { schedule ->
                                    ScheduleCard(
                                        schedule = schedule,
                                        isAdminMode = false,
                                        onClick = { detailSchedule = schedule }
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

    detailSchedule?.let { schedule ->
        ScheduleDetailSheet(
            schedule = schedule,
            isAdminMode = false,
            readOnlyRoleLabel = "Penghulu",
            onDismiss = { detailSchedule = null },
            onAddToCalendar = {
                val opened = DeviceCalendarSyncManager.openSingleScheduleInCalendarIntent(
                    context = context,
                    schedule = schedule,
                    roleLabel = "Penghulu KUA"
                )
                if (!opened) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Aplikasi Kalender HP tidak ditemukan.")
                    }
                }
            }
        )
    }
}
