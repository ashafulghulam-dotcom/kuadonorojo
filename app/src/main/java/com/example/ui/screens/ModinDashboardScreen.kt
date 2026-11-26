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
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailSheet
import com.example.ui.components.SearchAndStatusFilterBar
import com.example.ui.components.SelfProfileSettingsSection
import com.example.ui.components.TodayWeddingsSpotlightSection
import com.example.util.DeviceCalendarSyncManager
import com.example.util.SmartReminderManager
import kotlinx.coroutines.launch

private enum class ModinTab(val label: String) {
    SCHEDULES("Jadwal Modin"),
    CALCULATOR("Kalkulator"),
    PROFILE("Akun Saya")
}

@Composable
fun ModinDashboardScreen(
    activeAccount: StaffAccount,
    isAdminPreview: Boolean,
    staffAccountsState: UiState<List<StaffAccount>>,
    schedulesState: UiState<List<MarriageSchedule>>,
    reminderSettingsState: UiState<ReminderSettings>,
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus(),
    previewModinId: String?,
    isSaving: Boolean,
    actionMessage: String?,
    onClearMessage: () -> Unit,
    onSelectPreviewModin: (String?) -> Unit,
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

    var selectedTab by remember { mutableStateOf(ModinTab.SCHEDULES) }
    var showCalendarView by remember { mutableStateOf(false) }
    var selectedCalendarDate by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<ValidationStatus?>(null) }
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

    // Request notification permission automatically on Android 13+ when Modin opens dashboard
    LaunchedEffect(Unit) {
        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Back returns to Jadwal Modin tab, or from Admin Preview back to Admin KUA
    BackHandler(enabled = selectedTab != ModinTab.SCHEDULES || isAdminPreview) {
        if (selectedTab != ModinTab.SCHEDULES) {
            selectedTab = ModinTab.SCHEDULES
        } else if (isAdminPreview) {
            onSwitchMode(DashboardMode.ADMIN_KUA)
        }
    }

    val allAccounts = (staffAccountsState as? UiState.Success)?.data.orEmpty()
    val modinAccounts = remember(allAccounts) {
        allAccounts.filter { it.resolvedRole() == UserRole.MODIN }
    }
    val allSchedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val reminderSettings = (reminderSettingsState as? UiState.Success)?.data ?: ReminderSettings()

    // Determine active Modin scope (strictly per-Modin, NOT per-village):
    // - Logged-in Modin: strictly locked to activeAccount
    // - Admin Preview: selected Modin account from modinAccounts
    val targetModin: StaffAccount? = remember(isAdminPreview, activeAccount, previewModinId, modinAccounts) {
        if (isAdminPreview) {
            modinAccounts.firstOrNull { it.id == previewModinId } ?: modinAccounts.firstOrNull()
        } else {
            activeAccount
        }
    }

    val myAssignedSchedules = remember(allSchedules, targetModin) {
        if (targetModin == null) {
            emptyList()
        } else {
            allSchedules.filter { item ->
                item.modinAccountId == targetModin.id ||
                    (item.modinAccountId.isBlank() &&
                        item.modinName.isNotBlank() &&
                        item.modinName.equals(targetModin.officerName, ignoreCase = true))
            }
        }
    }

    val todayIso = remember { todayIsoDate() }
    val todaySchedules = remember(myAssignedSchedules, todayIso) {
        myAssignedSchedules.filter {
            it.weddingDate == todayIso && it.resolvedStatus() != ValidationStatus.DIBATALKAN
        }.sortedBy { it.weddingTime }
    }

    val activeModinDisplay = targetModin?.officerName ?: "Modin KUA Donorojo"
    val activeModinSubtitle = remember(targetModin) {
        if (targetModin == null) {
            "Jadwal Tugas Modin & Kalender HP"
        } else if (targetModin.villageName.isNotBlank()) {
            "${targetModin.officerName} (${targetModin.villageName}) • Jadwal Tugas Modin"
        } else {
            "${targetModin.officerName} • Jadwal Tugas Modin"
        }
    }

    // Automatic HP notification trigger strictly for this Modin's assigned schedules
    LaunchedEffect(myAssignedSchedules, reminderSettings, hasNotifPermission) {
        if (hasNotifPermission && myAssignedSchedules.isNotEmpty()) {
            val sentCount = SmartReminderManager.checkAndTriggerAutoNotifications(
                context = context,
                schedules = myAssignedSchedules,
                settings = reminderSettings
            )
            if (sentCount > 0) {
                snackbarHostState.showSnackbar(
                    "Notifikasi otomatis terkirim untuk $sentCount jadwal tugas $activeModinDisplay."
                )
            }
        }
    }

    val filteredSchedules = remember(
        myAssignedSchedules,
        searchQuery,
        selectedStatusFilter,
        showCalendarView,
        selectedCalendarDate
    ) {
        myAssignedSchedules.filter { item ->
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

    val pendingCount = remember(myAssignedSchedules) {
        myAssignedSchedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.MENUNGGU ||
                st == ValidationStatus.VERIFIKASI_BERKAS ||
                st == ValidationStatus.PERLU_REVISI
        }
    }
    val verifiedCount = remember(myAssignedSchedules) {
        myAssignedSchedules.count {
            val st = it.resolvedStatus()
            st == ValidationStatus.TERVERIFIKASI || st == ValidationStatus.SELESAI
        }
    }

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
                        selected = selectedTab == ModinTab.SCHEDULES,
                        onClick = { selectedTab = ModinTab.SCHEDULES },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == ModinTab.SCHEDULES) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                                contentDescription = null
                            )
                        },
                        label = { Text(ModinTab.SCHEDULES.label, maxLines = 1) },
                        modifier = Modifier.testTag("modin_tab_schedules")
                    )
                    NavigationBarItem(
                        selected = selectedTab == ModinTab.CALCULATOR,
                        onClick = { selectedTab = ModinTab.CALCULATOR },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == ModinTab.CALCULATOR) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                                contentDescription = null
                            )
                        },
                        label = { Text(ModinTab.CALCULATOR.label, maxLines = 1) },
                        modifier = Modifier.testTag("modin_tab_calculator")
                    )
                    NavigationBarItem(
                        selected = selectedTab == ModinTab.PROFILE,
                        onClick = { selectedTab = ModinTab.PROFILE },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == ModinTab.PROFILE) Icons.Filled.ManageAccounts else Icons.Outlined.ManageAccounts,
                                contentDescription = null
                            )
                        },
                        label = { Text(ModinTab.PROFILE.label, maxLines = 1) },
                        modifier = Modifier.testTag("modin_tab_profile")
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
                item(key = "modin_hero_header") {
                    KuaHeroHeader(
                        title = "Dasbor Modin Donorojo",
                        subtitle = activeModinSubtitle,
                        officerName = "${activeAccount.officerName} (@${activeAccount.username})",
                        roleBadge = if (isAdminPreview) "Pratinjau Modin • Baca Saja" else "Modin Desa • Jadwal Pribadi",
                        isAdminAccount = isAdminPreview,
                        currentMode = DashboardMode.MODIN_DESA,
                        onSwitchMode = onSwitchMode,
                        onSignOut = onLogoutStaff
                    )
                }

                when (selectedTab) {
                    ModinTab.CALCULATOR -> {
                        item(key = "modin_kua_calculators") {
                            KuaCalculatorsSection()
                        }
                    }

                    ModinTab.PROFILE -> {
                        item(key = "modin_self_profile") {
                            val profileAccount = targetModin ?: activeAccount
                            SelfProfileSettingsSection(
                                account = profileAccount,
                                isAdminPreview = isAdminPreview,
                                isSaving = isSaving,
                                onSaveProfile = onUpdateOwnProfile
                            )
                        }
                    }

                    ModinTab.SCHEDULES -> {
                        // Admin Preview Modin Account Selector (per-Modin, NOT per-village)
                        if (isAdminPreview && modinAccounts.isNotEmpty()) {
                            item(key = "modin_preview_selector") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Pilih Akun Modin untuk Pratinjau Jadwal:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        modinAccounts.forEach { modin ->
                                            val selected = targetModin?.id == modin.id
                                            val villageShort = modin.villageName.removePrefix("Desa ")
                                            val chipText = if (villageShort.isNotBlank()) {
                                                "${modin.officerName} ($villageShort)"
                                            } else {
                                                modin.officerName
                                            }
                                            FilterChip(
                                                selected = selected,
                                                onClick = { onSelectPreviewModin(modin.id) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Filled.Person,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                label = { Text(chipText) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                ),
                                                modifier = Modifier.testTag("modin_preview_chip_${modin.id}")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "modin_calendar_sync") {
                            DeviceCalendarSyncCard(
                                accountId = targetModin?.id ?: activeAccount.id,
                                roleLabel = "Modin Desa",
                                schedulesToSync = myAssignedSchedules,
                                onFeedback = { message ->
                                    scope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            )
                        }

                        // Notification permission prompt or active indicator
                        item(key = "modin_notif_banner") {
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
                                                text = "Aktifkan izin notifikasi HP agar pengingat jadwal tugas $activeModinDisplay muncul otomatis.",
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
                                            modifier = Modifier.testTag("modin_enable_notif_button")
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
                                                        "Menampilkan jadwal khusus $activeModinDisplay. Notif HP otomatis aktif pada hari akad."
                                                    } else {
                                                        "Menampilkan jadwal khusus $activeModinDisplay (Mode Baca Saja). Perubahan jadwal dikelola Admin KUA Donorojo."
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

                        item(key = "modin_view_mode_toggle") {
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
                                    modifier = Modifier.testTag("modin_view_list")
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
                                    modifier = Modifier.testTag("modin_view_calendar")
                                )
                            }
                        }

                        if (showCalendarView) {
                            item(key = "modin_monthly_calendar") {
                                MonthlyCalendarSection(
                                    schedules = myAssignedSchedules,
                                    selectedIsoDate = selectedCalendarDate,
                                    onSelectDate = { selectedCalendarDate = it }
                                )
                            }
                        } else {
                            // Today's Weddings Spotlight strictly for this Modin
                            item(key = "modin_today_spotlight") {
                                TodayWeddingsSpotlightSection(
                                    todaySchedules = todaySchedules,
                                    isAdminMode = false,
                                    onScheduleClick = { detailSchedule = it }
                                )
                            }
                        }

                        item(key = "modin_kpi_strip") {
                            KpiSummaryStrip(
                                totalCount = myAssignedSchedules.size,
                                pendingCount = pendingCount,
                                verifiedCount = verifiedCount
                            )
                        }

                        item(key = "modin_search_filter") {
                            SearchAndStatusFilterBar(
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                selectedStatus = selectedStatusFilter,
                                onSelectStatus = { selectedStatusFilter = it }
                            )
                        }

                        when (schedulesState) {
                            is UiState.Loading -> {
                                item(key = "modin_loading") {
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
                                item(key = "modin_error") {
                                    ErrorStateCard(message = schedulesState.message)
                                }
                            }

                            is UiState.Success -> {
                                if (filteredSchedules.isEmpty()) {
                                    item(key = "modin_empty") {
                                        EmptyStateCard(
                                            icon = Icons.Filled.EventNote,
                                            title = "Belum Ada Jadwal Tugas Modin",
                                            description = "Saat Admin KUA Donorojo menunjuk $activeModinDisplay sebagai Modin pendamping pada jadwal akad nikah, datanya akan tampil otomatis di sini."
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

    // Strictly read-only detail sheet for Modin with direct calendar export
    detailSchedule?.let { schedule ->
        ScheduleDetailSheet(
            schedule = schedule,
            isAdminMode = false,
            readOnlyRoleLabel = "Modin",
            onDismiss = { detailSchedule = null },
            onAddToCalendar = {
                val opened = DeviceCalendarSyncManager.openSingleScheduleInCalendarIntent(
                    context = context,
                    schedule = schedule,
                    roleLabel = "Modin Desa"
                )
                if (!opened) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Aplikasi Kalender HP tidak ditemukan di perangkat ini.")
                    }
                }
            }
        )
    }
}
