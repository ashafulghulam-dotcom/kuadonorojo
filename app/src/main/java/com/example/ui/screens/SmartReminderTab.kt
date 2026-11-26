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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.ValidationStatus
import com.example.data.WhatsAppAppPreference
import com.example.data.daysUntilWedding
import com.example.data.formatIndonesianDate
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ValidationStatusBadge
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg
import com.example.util.BackgroundReminderScheduler
import com.example.util.SmartReminderManager
import com.example.util.SmartReminderManager.ReminderRecipientType

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
    onSendWhatsApp: (
        schedule: MarriageSchedule,
        recipientType: ReminderRecipientType,
        template: String,
        adminWaNumber: String,
        appPreference: WhatsAppAppPreference
    ) -> Unit
) {
    val context = LocalContext.current
    val resolvedInitialAdminWa = remember(settings, staffAccounts) {
        SmartReminderManager.resolveAdminWhatsappNumber(settings, staffAccounts)
    }

    var autoPhoneEnabled by remember(settings) { mutableStateOf(settings.autoPhoneNotifEnabled) }
    var waEnabled by remember(settings) { mutableStateOf(settings.whatsappReminderEnabled) }
    var remindH0 by remember(settings) { mutableStateOf(settings.remindOnWeddingDay) }
    var remindH1 by remember(settings) { mutableStateOf(settings.remindOneDayBefore) }
    var remindH3 by remember(settings) { mutableStateOf(settings.remindThreeDaysBefore) }
    var reminderTime by remember(settings) { mutableStateOf(settings.reminderTime) }
    var adminWaNumber by remember(resolvedInitialAdminWa) { mutableStateOf(resolvedInitialAdminWa) }
    var adminWaApp by remember(settings) { mutableStateOf(settings.resolvedAdminWhatsappApp()) }
    var waModinTemplate by remember(settings) { mutableStateOf(settings.whatsappTemplate) }
    var waPenghuluTemplate by remember(settings) { mutableStateOf(settings.whatsappPenghuluTemplate) }
    var selectedTemplateTab by remember { mutableStateOf(ReminderRecipientType.MODIN) }
    var showTimePicker by remember { mutableStateOf(false) }

    val hasStandardWa = remember(context) {
        SmartReminderManager.isPackageInstalled(context, "com.whatsapp")
    }
    val hasBusinessWa = remember(context) {
        SmartReminderManager.isPackageInstalled(context, "com.whatsapp.w4b")
    }

    LaunchedEffect(settings, resolvedInitialAdminWa) {
        autoPhoneEnabled = settings.autoPhoneNotifEnabled
        waEnabled = settings.whatsappReminderEnabled
        remindH0 = settings.remindOnWeddingDay
        remindH1 = settings.remindOneDayBefore
        remindH3 = settings.remindThreeDaysBefore
        reminderTime = settings.reminderTime
        adminWaNumber = resolvedInitialAdminWa
        adminWaApp = settings.resolvedAdminWhatsappApp()
        waModinTemplate = settings.whatsappTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE }
        waPenghuluTemplate = settings.whatsappPenghuluTemplate.ifBlank {
            ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
        }
    }

    val upcomingSchedules = remember(schedules) {
        schedules.filter { item ->
            val st = item.resolvedStatus()
            val diff = daysUntilWedding(item.weddingDate) ?: -1
            st != ValidationStatus.DIBATALKAN && st != ValidationStatus.SELESAI && diff >= 0
        }.sortedWith(compareBy<MarriageSchedule> { it.weddingDate }.thenBy { it.weddingTime })
    }

    val effectiveAdminWa = adminWaNumber.trim()

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

        // Dedicated Official Admin KUA WhatsApp Sender Configuration Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.2.dp,
                if (effectiveAdminWa.isNotBlank()) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                } else {
                    StatusPendingFg.copy(alpha = 0.45f)
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_wa_sender_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Nomor WA Pengirim (Admin KUA)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Dipakai untuk Smart Reminder ke WA Modin & Penghulu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        color = if (effectiveAdminWa.isNotBlank()) StatusVerifiedBg else StatusPendingBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (effectiveAdminWa.isNotBlank()) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.WarningAmber
                                },
                                contentDescription = null,
                                tint = if (effectiveAdminWa.isNotBlank()) StatusVerifiedFg else StatusPendingFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (effectiveAdminWa.isNotBlank()) "Aktif" else "Atur",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (effectiveAdminWa.isNotBlank()) StatusVerifiedFg else StatusPendingFg
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = adminWaNumber,
                    onValueChange = { adminWaNumber = it.take(30) },
                    label = { Text("Nomor WA Resmi Admin KUA Donorojo") },
                    placeholder = { Text("Contoh: 081234567890") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_wa_sender_input")
                )

                Text(
                    text = "Aplikasi WhatsApp di HP Admin KUA:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WhatsAppAppPreference.entries.forEach { pref ->
                        val installedSuffix = when (pref) {
                            WhatsAppAppPreference.WHATSAPP -> if (hasStandardWa) " ✓" else ""
                            WhatsAppAppPreference.WHATSAPP_BUSINESS -> if (hasBusinessWa) " ✓" else ""
                            WhatsAppAppPreference.AUTO -> ""
                        }
                        FilterChip(
                            selected = adminWaApp == pref,
                            onClick = { adminWaApp = pref },
                            label = { Text("${pref.label}$installedSuffix") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("admin_wa_app_${pref.code.lowercase()}")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pastikan nomor WA Admin KUA di atas aktif di aplikasi ${adminWaApp.label} pada HP ini. Nomor WA ini otomatis tersinkron dengan akun Admin KUA dan dicantumkan sebagai identitas pengirim resmi di setiap pengingat ke Modin & Penghulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
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
                    text = "Atur pengingat otomatis lewat Notifikasi HP Modin & Penghulu pada hari nikah serta pengingat cepat via WA Admin KUA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Notifikasi HP Otomatis Petugas", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Otomatis memunculkan notif HP saat Modin atau Penghulu memiliki jadwal akad.",
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
                        Text("Pengingat WA Admin KUA", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aktifkan kirim pengingat WA dari nomor Admin KUA ke WA Modin & WA Penghulu.",
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

                if (autoPhoneEnabled) {
                    val nextTriggerText = remember(reminderTime) {
                        BackgroundReminderScheduler.formatNextTriggerLabel(reminderTime)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusVerifiedBg.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("background_reminder_status_strip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = StatusVerifiedFg,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pengingat latar belakang aktif meski aplikasi ditutup • Jadwal berikutnya: $nextTriggerText",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusVerifiedFg
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Text(
                    text = "Template Pesan WhatsApp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTemplateTab == ReminderRecipientType.MODIN,
                        onClick = { selectedTemplateTab = ReminderRecipientType.MODIN },
                        leadingIcon = {
                            Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Template WA Modin") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("tab_template_modin")
                    )
                    FilterChip(
                        selected = selectedTemplateTab == ReminderRecipientType.PENGHULU,
                        onClick = { selectedTemplateTab = ReminderRecipientType.PENGHULU },
                        leadingIcon = {
                            Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Template WA Penghulu") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("tab_template_penghulu")
                    )
                }

                val activeTemplateValue = if (selectedTemplateTab == ReminderRecipientType.MODIN) {
                    waModinTemplate
                } else {
                    waPenghuluTemplate
                }

                OutlinedTextField(
                    value = activeTemplateValue,
                    onValueChange = { updated ->
                        val trimmedLen = updated.take(800)
                        if (selectedTemplateTab == ReminderRecipientType.MODIN) {
                            waModinTemplate = trimmedLen
                        } else {
                            waPenghuluTemplate = trimmedLen
                        }
                    },
                    label = {
                        Text(
                            if (selectedTemplateTab == ReminderRecipientType.MODIN) {
                                "Template Pesan WA ke Modin Desa"
                            } else {
                                "Template Pesan WA ke Penghulu KUA"
                            }
                        )
                    },
                    minLines = 4,
                    maxLines = 7,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(
                            if (selectedTemplateTab == ReminderRecipientType.MODIN) {
                                "wa_template_input"
                            } else {
                                "wa_penghulu_template_input"
                            }
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "{pengantin}",
                        "{desa}",
                        "{modin}",
                        "{penghulu}",
                        "{wali}",
                        "{tanggal}",
                        "{jam}",
                        "{lokasi}",
                        "{status}",
                        "{wa_admin}"
                    ).forEach { tag ->
                        AssistChip(
                            onClick = {
                                if (selectedTemplateTab == ReminderRecipientType.MODIN) {
                                    waModinTemplate = "$waModinTemplate $tag".take(800)
                                } else {
                                    waPenghuluTemplate = "$waPenghuluTemplate $tag".take(800)
                                }
                            },
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
                                adminWhatsappNumber = effectiveAdminWa,
                                adminWhatsappApp = adminWaApp.code,
                                whatsappTemplate = waModinTemplate.ifBlank { ReminderSettings.DEFAULT_WA_TEMPLATE },
                                whatsappPenghuluTemplate = waPenghuluTemplate.ifBlank {
                                    ReminderSettings.DEFAULT_WA_PENGHULU_TEMPLATE
                                }
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

        // Queue of Upcoming Reminders (1-Tap WA Modin, WA Penghulu & Phone Notification)
        Text(
            text = "Antrean Pengingat Akad (${upcomingSchedules.size} Jadwal)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (effectiveAdminWa.isNotBlank()) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                StatusPendingBg.copy(alpha = 0.7f)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = if (effectiveAdminWa.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        StatusPendingFg
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (effectiveAdminWa.isNotBlank()) {
                        "Pengirim WA: Admin KUA ($effectiveAdminWa • ${adminWaApp.label})"
                    } else {
                        "Nomor WA Admin KUA belum diisi di atas. Isi nomor WA Admin KUA lalu klik Simpan."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (effectiveAdminWa.isNotBlank()) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        StatusPendingFg
                    }
                )
            }
        }

        if (upcomingSchedules.isEmpty()) {
            EmptyStateCard(
                icon = Icons.Filled.NotificationsActive,
                title = "Belum Ada Antrean Pengingat",
                description = "Jadwal akad hari ini dan mendatang yang aktif akan muncul di sini agar mudah dikirim dari WA Admin KUA ke Modin & Penghulu."
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

                val penghuluAccount = SmartReminderManager.resolveAssignedPenghulu(schedule, staffAccounts)
                val penghuluPhone = penghuluAccount?.whatsappNumber.orEmpty()
                val penghuluLabel = schedule.penghuluName.ifBlank { penghuluAccount?.officerName.orEmpty() }

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

                        // Target recipients summary (Modin & Penghulu)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (modinPhone.isNotBlank()) {
                                        "WA Modin: $modinPhone (${modinLabel.ifBlank { "Modin Desa" }})"
                                    } else {
                                        val targetText = if (modinLabel.isNotBlank()) " ($modinLabel)" else ""
                                        "WA Modin$targetText: Belum diisi di tab Akun"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (modinPhone.isNotBlank()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (penghuluPhone.isNotBlank()) {
                                        "WA Penghulu: $penghuluPhone (${penghuluLabel.ifBlank { "Penghulu KUA" }})"
                                    } else {
                                        val targetText = if (penghuluLabel.isNotBlank()) " ($penghuluLabel)" else ""
                                        "WA Penghulu$targetText: Belum diisi di tab Akun"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (penghuluPhone.isNotBlank()) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onSendWhatsApp(
                                        schedule,
                                        ReminderRecipientType.MODIN,
                                        waModinTemplate,
                                        effectiveAdminWa,
                                        adminWaApp
                                    )
                                },
                                enabled = waEnabled,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("reminder_wa_modin_${schedule.id}")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("WA Modin", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = {
                                    onSendWhatsApp(
                                        schedule,
                                        ReminderRecipientType.PENGHULU,
                                        waPenghuluTemplate,
                                        effectiveAdminWa,
                                        adminWaApp
                                    )
                                },
                                enabled = waEnabled,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("reminder_wa_penghulu_${schedule.id}")
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("WA Penghulu", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = { onSendPhoneNotif(schedule) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("reminder_phone_${schedule.id}")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Kirim Notif HP", style = MaterialTheme.typography.labelMedium)
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
