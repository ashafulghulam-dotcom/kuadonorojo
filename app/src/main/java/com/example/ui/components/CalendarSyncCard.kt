package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.data.formatShortTimestamp
import com.example.util.CalendarSyncResult
import com.example.util.DeviceCalendarSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceCalendarSyncCard(
    accountId: String,
    roleLabel: String,
    schedulesToSync: List<MarriageSchedule>,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(DeviceCalendarSyncManager.hasCalendarPermissions(context))
    }
    var autoSyncEnabled by remember(accountId) {
        mutableStateOf(DeviceCalendarSyncManager.isAutoSyncEnabled(context, accountId))
    }
    var lastSyncMillis by remember(accountId) {
        mutableLongStateOf(DeviceCalendarSyncManager.getLastSyncTimeMillis(context, accountId))
    }
    var lastCalendarName by remember(accountId) {
        mutableStateOf(DeviceCalendarSyncManager.getLastCalendarName(context, accountId))
    }
    var isSyncing by remember { mutableStateOf(false) }

    val activeScheduleCount = remember(schedulesToSync) {
        schedulesToSync.count { it.resolvedStatus() != ValidationStatus.DIBATALKAN }
    }

    fun runCalendarSync(showMessage: Boolean) {
        if (isSyncing) return
        isSyncing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                DeviceCalendarSyncManager.syncSchedulesToDeviceCalendar(
                    context = context,
                    schedules = schedulesToSync,
                    roleLabel = roleLabel,
                    accountId = accountId
                )
            }
            isSyncing = false
            when (result) {
                is CalendarSyncResult.Success -> {
                    lastSyncMillis = DeviceCalendarSyncManager.getLastSyncTimeMillis(context, accountId)
                    lastCalendarName = result.calendarName
                    if (showMessage) {
                        val summary = if (result.totalActive == 0) {
                            "Tersambung ke ${result.calendarName}. Belum ada jadwal aktif untuk ditambahkan."
                        } else {
                            "${result.totalActive} jadwal ditautkan ke ${result.calendarName} (${result.inserted} baru, ${result.updated} diperbarui)."
                        }
                        onFeedback(summary)
                    }
                }

                CalendarSyncResult.NoWritableCalendar -> {
                    if (showMessage) {
                        onFeedback("Tidak ditemukan akun Kalender HP yang aktif. Buka aplikasi Google Calendar/Kalender HP terlebih dahulu.")
                    }
                }

                is CalendarSyncResult.Error -> {
                    if (showMessage) {
                        onFeedback(result.message)
                    }
                }
            }
        }
    }

    var pendingAutoToggle by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        hasPermission = granted || DeviceCalendarSyncManager.hasCalendarPermissions(context)
        if (hasPermission) {
            if (pendingAutoToggle) {
                autoSyncEnabled = true
                DeviceCalendarSyncManager.setAutoSyncEnabled(context, accountId, true)
                pendingAutoToggle = false
            }
            runCalendarSync(showMessage = true)
        } else {
            pendingAutoToggle = false
            onFeedback("Izin Kalender HP ditolak. Anda tetap bisa menambah jadwal satu per satu dari detail jadwal.")
        }
    }

    // Auto-sync whenever assigned schedules change and auto-sync is enabled with permission
    val scheduleFingerprint = remember(schedulesToSync) {
        schedulesToSync.joinToString("|") {
            "${it.id}:${it.weddingDate}:${it.weddingTime}:${it.validationStatus}:${it.updatedAt}"
        }
    }
    LaunchedEffect(autoSyncEnabled, hasPermission, scheduleFingerprint) {
        if (autoSyncEnabled && hasPermission && schedulesToSync.isNotEmpty()) {
            runCalendarSync(showMessage = false)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_calendar_sync_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                        imageVector = Icons.Filled.EventAvailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tautkan ke Kalender HP",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (lastSyncMillis > 0L) {
                            val calLabel = lastCalendarName.ifBlank { "Kalender HP" }
                            "Tersambung ke $calLabel • Terakhir: ${formatShortTimestamp(lastSyncMillis)}"
                        } else {
                            "Tampilkan $activeScheduleCount jadwal tugas Anda langsung di aplikasi Kalender HP lengkap dengan pengingat."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (hasPermission) {
                            runCalendarSync(showMessage = true)
                        } else {
                            pendingAutoToggle = false
                            permissionLauncher.launch(DeviceCalendarSyncManager.REQUIRED_PERMISSIONS)
                        }
                    },
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(46.dp)
                        .testTag("btn_sync_device_calendar")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Menautkan...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (lastSyncMillis > 0L) "Sinkronkan ($activeScheduleCount)" else "Tautkan ($activeScheduleCount)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val opened = DeviceCalendarSyncManager.openDeviceCalendarApp(context)
                        if (!opened) {
                            onFeedback("Aplikasi Kalender HP tidak ditemukan di perangkat ini.")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("btn_open_device_calendar")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Buka Kalender",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sinkron Otomatis ke Kalender HP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Otomatis perbarui Kalender HP saat ada jadwal baru atau perubahan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = autoSyncEnabled && hasPermission,
                    onCheckedChange = { enable ->
                        if (enable) {
                            if (hasPermission) {
                                autoSyncEnabled = true
                                DeviceCalendarSyncManager.setAutoSyncEnabled(context, accountId, true)
                                runCalendarSync(showMessage = true)
                            } else {
                                pendingAutoToggle = true
                                permissionLauncher.launch(DeviceCalendarSyncManager.REQUIRED_PERMISSIONS)
                            }
                        } else {
                            autoSyncEnabled = false
                            DeviceCalendarSyncManager.setAutoSyncEnabled(context, accountId, false)
                            onFeedback("Sinkronisasi otomatis Kalender HP dimatikan.")
                        }
                    },
                    modifier = Modifier.testTag("switch_auto_calendar_sync")
                )
            }
        }
    }
}
