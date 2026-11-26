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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.data.DashboardMode
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.UiState
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.data.todayIsoDate
import com.example.ui.components.DeviceCalendarSyncCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.KpiSummaryStrip
import com.example.ui.components.KuaHeroHeader
import com.example.ui.components.ScheduleCard
import com.example.ui.components.ScheduleDetailSheet
import com.example.ui.components.SearchAndStatusFilterBar
import com.example.ui.components.TodayWeddingsSpotlightSection
import com.example.util.DeviceCalendarSyncManager
import com.example.util.SmartReminderManager
import kotlinx.coroutines.launch

private enum class ModinTab(val label: String) {
    SCHEDULES("Jadwal Desa"),
    CALCULATOR("Kalkulator KUA")
}

@Composable
fun ModinDashboardScreen(
    activeAccount: StaffAccount,
    isAdminPreview: Boolean,
    villagesState: UiState<List<Village>>,
    schedulesState: UiState<List<MarriageSchedule>>,
    reminderSettingsState: UiState<ReminderSettings>,
    previewVillageId: String?,
    onSelectPreviewVillage: (String?) -> Unit,
    onSwitchMode: (DashboardMode) -> Unit,
    onLogoutStaff: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(ModinTab.SCHEDULES) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf<ValidationStatus?>(null) }
    var onlyMySchedules by remember { mutableStateOf(!isAdminPreview) }
    var detailSchedule by remember { mutableStateOf<MarriageSchedule?>(null) }
    var hasNotifPermission by remember {
        mutableStateOf(SmartReminderManager.hasNotificationPermission(context))
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
    }

    // Request notification permission automatically on Android 13+ when Modin opens dashboard
    LaunchedEffect(Unit) {
        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Back returns from Calculator tab to Jadwal Desa, or from Admin Preview back to Admin KUA
    BackHandler(enabled = selectedTab != ModinTab.SCHEDULES || isAdminPreview) {
        if (selectedTab != ModinTab.SCHEDULES) {
            selectedTab = ModinTab.SCHEDULES
        } else if (isAdminPreview) {
            onSwitchMode(DashboardMode.ADMIN_KUA)
        }
    }

    val villages = (villagesState as? UiState.Success)?.data.orEmpty()
    val allSchedules = (schedulesState as? UiState.Success)?.data.orEmpty()
    val reminderSettings = (reminderSettingsState as? UiState.Success)?.data ?: ReminderSettings()

    // Determine active village scope:
    // - For logged-in Modin: strictly locked to activeAccount.villageId
    // - For Admin previewing Modin Dashboard: selected previewVillageId (or first of the 8 villages)
    val targetVillageCode = remember(isAdminPreview, activeAccount.villageId, previewVillageId, villages) {
        if (isAdminPreview) {
            previewVillageId ?: villages.firstOrNull()?.code.orEmpty()
        } else {
            activeAccount.villageId
        }
    }

    val activeVillageName = remember(isAdminPreview, activeAccount.villageName, targetVillageCode, villages) {
        villages.firstOrNull { it.code == targetVillageCode }?.name
            ?: activeAccount.villageName.ifBlank { "Wilayah Desa" }
    }

    val villageScopedSchedules = remember(allSchedules, targetVillageCode) {
        if (targetVillageCode.isNotBlank()) {
            allSchedules.filter { it.villageId == targetVillageCode }
        } else {
            emptyList()
        }
    }

    val myAssignedSchedules = remember(villageScopedSchedules, activeAccount.id, activeAccount.officerName) {
        villageScopedSchedules.filter { item ->
            item.modinAccountId == activeAccount.id ||
                (item.modinAccountId.isBlank() &&
                    (item.modinName.isBlank() || item.modinName.equals(activeAccount.officerName, ignoreCase = true)))
        }
    }

    val activeScopeSchedules = remember(villageScopedSchedules, myAssignedSchedules, isAdminPreview, onlyMySchedules) {
        if (!isAdminPreview && onlyMySchedules) myAssignedSchedules else villageScopedSchedules
    }

    val todayIso = remember { todayIsoDate() }
    val todaySchedules = remember(activeScopeSchedules, todayIso) {
        activeScopeSchedules.filter {
            it.weddingDate == todayIso && it.resolvedStatus() != ValidationStatus.DIBATALKAN
        }.sortedBy { it.weddingTime }
    }

    // Automatic HP notification trigger for Modin on wedding day (H-0) & configured reminder days (H-1/H-3)
    val notifTargetSchedules = if (isAdminPreview) villageScopedSchedules else myAssignedSchedules
    LaunchedEffect(notifTargetSchedules, reminderSettings, hasNotifPermission) {
        if (hasNotifPermission && notifTargetSchedules.isNotEmpty()) {
            val sentCount = SmartReminderManager.checkAndTriggerAutoNotifications(
                context = context,
                schedules = notifTargetSchedules,
                settings = reminderSettings
            )
            if (sentCount > 0) {
                snackbarHostState.showSnackbar(
                    "Notifikasi otomatis terkirim untuk $sentCount jadwal akad di $activeVillageName."
                )
            }
        }
    }

    val filteredSchedules = remember(activeScopeSchedules, searchQuery, selectedStatusFilter) {
        activeScopeSchedules.filter { item ->
            val status = item.resolvedStatus()
            val matchesStatus = selectedStatusFilter == null || status == selectedStatusFilter
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                item.groomName.lowercase().contains(q) ||
                item.brideName.lowercase().contains(q) ||
                item.modinName.lowercase().contains(q) ||
                item.registrationNumber.lowercase().contains(q) ||
                item.penghuluName.lowercase().contains(q)
            matchesStatus && matchesSearch
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
                    selected = selectedTab == ModinTab.SCHEDULES,
                    onClick = { selectedTab = ModinTab.SCHEDULES },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == ModinTab.SCHEDULES) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = null
                        )
                    },
                    label = { Text(ModinTab.SCHEDULES.label) },
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
                    label = { Text(ModinTab.CALCULATOR.label) },
                    modifier = Modifier.testTag("modin_tab_calculator")
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
                        subtitle = "$activeVillageName • Pantau Status Real-Time",
                        officerName = "${activeAccount.officerName} (@${activeAccount.username})",
                        roleBadge = if (isAdminPreview) "Pratinjau Modin • Baca Saja" else "Modin Desa • Real-Time",
                        isAdminAccount = isAdminPreview,
                        currentMode = DashboardMode.MODIN_DESA,
                        onSwitchMode = onSwitchMode,
                        onSignOut = onLogoutStaff
                    )
                }

                if (selectedTab == ModinTab.CALCULATOR) {
                    item(key = "modin_kua_calculators") {
                        KuaCalculatorsSection()
                    }
                } else {
                    item(key = "modin_calendar_sync") {
                        DeviceCalendarSyncCard(
                            accountId = if (isAdminPreview) "preview_modin_$targetVillageCode" else activeAccount.id,
                            roleLabel = "Modin Desa",
                            schedulesToSync = if (isAdminPreview) villageScopedSchedules else myAssignedSchedules,
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
                                            text = "Aktifkan izin notifikasi HP agar pengingat hari nikah di $activeVillageName muncul otomatis.",
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
                                        text = if (reminderSettings.autoPhoneNotifEnabled) {
                                            "Notif HP Otomatis Aktif (Mode Pantau Baca Saja). Pengingat hari nikah untuk $activeVillageName akan muncul otomatis."
                                        } else {
                                            "Akses Pantau Wilayah (Read-Only): Perubahan data & verifikasi akhir dikelola oleh Admin KUA Donorojo."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Village selector only shown when Admin previews the 8 Donorojo villages
                    if (isAdminPreview && villages.isNotEmpty()) {
                        item(key = "modin_village_selector") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Pilih Wilayah Desa Donorojo untuk Pratinjau:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    villages.forEach { village ->
                                        val selected = targetVillageCode == village.code
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onSelectPreviewVillage(village.code) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.LocationOn,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text(village.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                            ),
                                            modifier = Modifier.testTag("modin_village_chip_${village.code}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!isAdminPreview) {
                        item(key = "modin_scope_toggle") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = onlyMySchedules,
                                    onClick = { onlyMySchedules = true },
                                    label = { Text("Jadwal Saya (${myAssignedSchedules.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("modin_filter_mine")
                                )
                                FilterChip(
                                    selected = !onlyMySchedules,
                                    onClick = { onlyMySchedules = false },
                                    label = { Text("Semua Desa (${villageScopedSchedules.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.testTag("modin_filter_village")
                                )
                            }
                        }
                    }

                    // Today's Weddings Spotlight prominently on Main Dashboard
                    item(key = "modin_today_spotlight") {
                        TodayWeddingsSpotlightSection(
                            todaySchedules = todaySchedules,
                            isAdminMode = false,
                            onScheduleClick = { detailSchedule = it }
                        )
                    }

                    item(key = "modin_kpi_strip") {
                        KpiSummaryStrip(
                            totalCount = activeScopeSchedules.size,
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
                                        title = "Belum Ada Pendaftar di $activeVillageName",
                                        description = "Saat Admin KUA Donorojo menginput pendaftaran nikah untuk $activeVillageName, data dan status validasinya akan muncul real-time di sini."
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
