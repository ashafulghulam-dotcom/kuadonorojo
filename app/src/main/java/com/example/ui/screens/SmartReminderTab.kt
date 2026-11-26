package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.ValidationStatus
import com.example.data.daysUntilWedding
import com.example.data.formatIndonesianDate
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ValidationStatusBadge
import com.example.util.SmartReminderManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartReminderSection(
    settings: ReminderSettings,
    schedules: List<MarriageSchedule>,
    staffAccounts: List<StaffAccount>,
    hasNotifPermission: Boolean,
    isSaving: Boolean,
    onRequestNotifPermission: () -> Unit,
    onSaveSettings: (ReminderSettings) -> Unit,
    onSendPhoneNotif: (MarriageSchedule) -> Unit,
    onSendWhatsApp: (MarriageSchedule, String) -> Unit
) {
    var autoPhoneEnabled by remember(settings) { mutableStateOf(settings.autoPhoneNotifEnabled) }
    var waEnabled by remember(settings) { mutableStateOf(settings.whatsappReminderEnabled) }
    var remindH0 by remember(settings) { mutableStateOf(settings.remindOnWeddingDay) }
    var remindH1 by remember(settings) { mutableStateOf(settings.remindOneDayBefore) }
    var remindH3 by remember(settings) { mutableStateOf(settings.remindThreeDaysBefore) }
    var reminderTime by remember(settings) { mutableStateOf(settings.reminderTime) }
    var waTemplate by remember(settings) { mutableStateOf(settings.whatsappTemplate) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        autoPhoneEnabled = settings.autoPhoneNotifEnabled
        waEnabled = settings.whatsappReminderEnabled
        remindH0 = settings.remindOnWeddingDay
        remindH1 = settings.remindOneDayBefore
        remindH3 = settings.remindThreeDaysBefore
        reminderTime = settings.reminderTime
        waTemplate = settings.whatsappTemplate
    }

    val upcomingSchedules = remember(schedules) {
        schedules.filter { item ->
            val st = item.resolvedStatus()
            val diff = daysUntilWedding(item.weddingDate) ?: -1
            st != ValidationStatus.DIBATALKAN && st != ValidationStatus.SELESAI && diff >= 0
        }.sortedWith(compareBy<MarriageSchedule> { it.weddingDate }.thenBy { it.weddingTime })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasNotifPermission) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Izin notifikasi HP belum aktif. Aktifkan agar pengingat akad muncul di perangkat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onRequestNotifPermission,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Aktifkan", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Smart Reminder Configuration Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pengaturan Smart Reminder",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Atur pengingat otomatis lewat Notifikasi HP Modin pada hari nikah dan pengingat cepat via WhatsApp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifikasi HP Otomatis Modin", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Otomatis memunculkan notif HP saat desa Modin memiliki jadwal akad.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoPhoneEnabled,
                        onCheckedChange = { autoPhoneEnabled = it },
                        modifier = Modifier.testTag("switch_auto_phone_notif")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pengingat WhatsApp (WA)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aktifkan tombol kirim pesan WA otomatis ke nomor Modin desa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = waEnabled,
                        onCheckedChange = { waEnabled = it },
                        modifier = Modifier.testTag("switch_wa_reminder")
                    )
                }

                Text(
                    text = "Jadwal Pemicu Pengingat",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = remindH0,
                        onClick = { remindH0 = !remindH0 },
                        label = { Text("Hari H (H-0)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = remindH1,
                        onClick = { remindH1 = !remindH1 },
                        label = { Text("H-1") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = remindH3,
                        onClick = { remindH3 = !remindH3 },
                        label = { Text("H-3") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Jam Pengingat Harian",
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("$reminderTime WIB")
                    }
                }

                OutlinedTextField(
                    value = waTemplate,
                    onValueChange = { waTemplate = it.take(800) },
                    label = { Text("Template Pesan WhatsApp") },
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wa_template_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("{pengantin}", "{desa}", "{modin}", "{tanggal}", "{jam}", "{lokasi}", "{penghulu}", "{status}").forEach { tag ->
                        AssistChip(
                            onClick = { waTemplate = "$waTemplate $tag".take(800) },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Button(
                    onClick = {
                        onSaveSettings(
                            settings.copy(
                                autoPhoneNotifEnabled = autoPhoneEnabled,
                                whatsappReminderEnabled = waEnabled,
                                remindOnWeddingDay = remindH0,
                                remindOneDayBefore = remindH1,
                                remindThreeDaysBefore = remindH3,
                                reminderTime = reminderTime,
                                whatsappTemplate = waTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE }
                            )
                        )
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_reminder_settings_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Pengaturan Smart Reminder")
                    }
                }
            }
        }

        // Queue of Upcoming Reminders (1-Tap WA & Phone Notification)
        Text(
            text = "Antrean Pengingat Akad (${upcomingSchedules.size} Jadwal)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (upcomingSchedules.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.NotificationsActive,
                title = "Belum Ada Antrean Pengingat",
                description = "Jadwal akad hari ini dan mendatang yang aktif akan muncul di sini agar mudah dikirim ke Modin lewat Notif HP atau WA."
            )
        } else {
            upcomingSchedules.forEach { schedule ->
                val diff = daysUntilWedding(schedule.weddingDate) ?: 0
                val dayBadge = when (diff) {
                    0 -> "HARI INI (H-0)"
                    1 -> "BESOK (H-1)"
                    else -> "H-$diff"
                }
                val modinAccount = SmartReminderManager.resolveAssignedModin(schedule, staffAccounts)
                val modinPhone = modinAccount?.whatsappNumber.orEmpty()
                val modinLabel = schedule.modinName.ifBlank { modinAccount?.officerName.orEmpty() }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (diff == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = dayBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            ValidationStatusBadge(status = schedule.resolvedStatus())
                        }

                        Text(
                            text = "${schedule.groomName} & ${schedule.brideName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${schedule.villageName} • ${formatIndonesianDate(schedule.weddingDate)} (${schedule.weddingTime} WIB)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (modinPhone.isNotBlank()) {
                                "WA Modin: $modinPhone (${modinLabel.ifBlank { "Modin Desa" }})"
                            } else {
                                val targetText = if (modinLabel.isNotBlank()) " ($modinLabel)" else ""
                                "Nomor WA Modin$targetText belum diisi (atur di tab Akun)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (modinPhone.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSendPhoneNotif(schedule) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("reminder_phone_${schedule.id}")
                            ) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Notif HP", style = MaterialTheme.typography.labelMedium)
                            }
                            Button(
                                onClick = { onSendWhatsApp(schedule, waTemplate) },
                                enabled = waEnabled,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("reminder_wa_${schedule.id}")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Kirim WA", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        val parts = reminderTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
        val initMin = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pilih Jam Pengingat (WIB)") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hh = timePickerState.hour.toString().padStart(2, '0')
                        val mm = timePickerState.minute.toString().padStart(2, '0')
                        reminderTime = "$hh:$mm"
                        showTimePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Batal") }
            }
        )
    }
}
