package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.DocumentChecklist
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ReminderSettings
import com.example.data.ScheduleDraft
import com.example.data.StaffAccount
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.data.WaliReferenceData
import com.example.data.WaliStatus
import com.example.data.formatIndonesianDate
import com.example.data.generateRegistrationNumber
import com.example.data.todayIsoDate
import com.example.ui.components.DispensasiKawinAlertCard
import com.example.ui.components.DocumentChecklistEditor
import com.example.ui.components.NikAgeInlineBadge
import com.example.ui.theme.ErrorContainerLight
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.OnErrorContainerLight
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.util.ConflictSeverity
import com.example.util.NikAgeValidator
import com.example.util.ScheduleConflictChecker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val BALAI_NIKAH_KUA_ADDRESS = "Balai Nikah KUA Donorojo"

private fun defaultLocationAddress(type: LocationType, village: Village?): String = when (type) {
    LocationType.KUA -> BALAI_NIKAH_KUA_ADDRESS
    LocationType.LUAR_KUA -> village?.name.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormSheet(
    existingSchedule: MarriageSchedule?,
    villages: List<Village>,
    staffAccounts: List<StaffAccount>,
    reminderSettings: ReminderSettings,
    allSchedules: List<MarriageSchedule>,
    existingScheduleCount: Int = allSchedules.size,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ScheduleDraft, Village) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialVillage = remember(existingSchedule, villages) {
        villages.firstOrNull { it.code == existingSchedule?.villageId } ?: villages.firstOrNull()
    }

    var regNumber by remember {
        mutableStateOf(existingSchedule?.registrationNumber ?: generateRegistrationNumber(existingScheduleCount))
    }
    var groomName by remember { mutableStateOf(existingSchedule?.groomName ?: "") }
    var groomNik by remember { mutableStateOf(existingSchedule?.groomNik ?: "") }
    var brideName by remember { mutableStateOf(existingSchedule?.brideName ?: "") }
    var brideNik by remember { mutableStateOf(existingSchedule?.brideNik ?: "") }
    var selectedVillage by remember { mutableStateOf(initialVillage) }

    val villageModins = remember(staffAccounts, selectedVillage) {
        val code = selectedVillage?.code.orEmpty()
        staffAccounts.filter {
            it.resolvedRole() == UserRole.MODIN && it.villageId == code && it.active
        }
    }

    val initialModinAccount = remember(existingSchedule, staffAccounts, initialVillage) {
        val modinId = existingSchedule?.modinAccountId.orEmpty()
        val code = initialVillage?.code.orEmpty()
        val activeForVillage = staffAccounts.filter {
            it.resolvedRole() == UserRole.MODIN && it.villageId == code && it.active
        }
        when {
            modinId.isNotBlank() -> staffAccounts.firstOrNull { it.id == modinId }
            existingSchedule != null && existingSchedule.modinName.isNotBlank() ->
                activeForVillage.firstOrNull { it.officerName.equals(existingSchedule.modinName, ignoreCase = true) }
            existingSchedule == null -> activeForVillage.firstOrNull()
            else -> null
        }
    }

    var selectedModinId by remember {
        mutableStateOf(initialModinAccount?.id ?: existingSchedule?.modinAccountId.orEmpty())
    }
    var useManualModin by remember {
        mutableStateOf(
            existingSchedule != null &&
                existingSchedule.modinName.isNotBlank() &&
                initialModinAccount == null
        )
    }
    var manualModinName by remember {
        mutableStateOf(
            if (existingSchedule != null && initialModinAccount == null) {
                existingSchedule.modinName
            } else {
                ""
            }
        )
    }

    var weddingDate by remember { mutableStateOf(existingSchedule?.weddingDate ?: todayIsoDate()) }
    var weddingTime by remember { mutableStateOf(existingSchedule?.weddingTime ?: "09:00") }
    var locationType by remember {
        mutableStateOf(existingSchedule?.resolvedLocationType() ?: LocationType.KUA)
    }
    var locationAddress by remember {
        mutableStateOf(
            existingSchedule?.locationAddress?.takeIf { it.isNotBlank() }
                ?: defaultLocationAddress(
                    existingSchedule?.resolvedLocationType() ?: LocationType.KUA,
                    initialVillage
                )
        )
    }
    val activePenghulus = remember(staffAccounts) {
        staffAccounts.filter { it.resolvedRole() == UserRole.PENGHULU && it.active }
    }

    val initialPenghuluAccount = remember(existingSchedule, staffAccounts) {
        val pId = existingSchedule?.penghuluAccountId.orEmpty()
        val pName = existingSchedule?.penghuluName.orEmpty()
        when {
            pId.isNotBlank() -> staffAccounts.firstOrNull { it.id == pId }
            pName.isNotBlank() -> staffAccounts.firstOrNull {
                it.resolvedRole() == UserRole.PENGHULU && it.officerName.equals(pName, ignoreCase = true)
            }
            else -> null
        }
    }

    var selectedPenghuluId by remember {
        mutableStateOf(initialPenghuluAccount?.id ?: existingSchedule?.penghuluAccountId.orEmpty())
    }
    var useManualPenghulu by remember {
        mutableStateOf(
            existingSchedule != null &&
                existingSchedule.penghuluName.isNotBlank() &&
                initialPenghuluAccount == null
        )
    }
    var manualPenghuluName by remember {
        mutableStateOf(
            if (existingSchedule != null && initialPenghuluAccount == null) {
                existingSchedule.penghuluName
            } else {
                ""
            }
        )
    }

    // Wali Nikah state (Wali Nasab / Wali Hakim)
    val authorizedWaliHakimPenghulus = remember(activePenghulus, reminderSettings.defaultWaliHakimAccountId) {
        val authorized = activePenghulus.filter {
            it.isWaliHakim || it.id == reminderSettings.defaultWaliHakimAccountId
        }
        authorized.sortedByDescending { it.id == reminderSettings.defaultWaliHakimAccountId }
    }
    val otherActivePenghulus = remember(activePenghulus, authorizedWaliHakimPenghulus) {
        val authIds = authorizedWaliHakimPenghulus.map { it.id }.toSet()
        activePenghulus.filter { it.id !in authIds }
    }
    val defaultWaliHakimAccount = remember(authorizedWaliHakimPenghulus, reminderSettings.defaultWaliHakimAccountId) {
        authorizedWaliHakimPenghulus.firstOrNull { it.id == reminderSettings.defaultWaliHakimAccountId }
            ?: authorizedWaliHakimPenghulus.firstOrNull()
    }

    var waliStatus by remember {
        mutableStateOf(existingSchedule?.resolvedWaliStatus() ?: WaliStatus.WALI_NASAB)
    }

    // Wali Nasab fields
    val existingRel = existingSchedule?.waliRelationship.orEmpty()
    val existingRelIsPreset = existingRel.isBlank() || WaliReferenceData.nasabRelationships.any {
        it.equals(existingRel, ignoreCase = true)
    }
    var selectedWaliRelationship by remember {
        mutableStateOf(
            when {
                existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB && existingRel.isNotBlank() && existingRelIsPreset ->
                    WaliReferenceData.nasabRelationships.first { it.equals(existingRel, ignoreCase = true) }
                existingSchedule == null -> WaliReferenceData.nasabRelationships.first()
                else -> WaliReferenceData.nasabRelationships.first()
            }
        )
    }
    var useManualWaliRelationship by remember {
        mutableStateOf(
            existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB &&
                existingRel.isNotBlank() &&
                !existingRelIsPreset
        )
    }
    var manualWaliRelationship by remember {
        mutableStateOf(if (useManualWaliRelationship) existingRel else "")
    }
    var waliNasabName by remember {
        mutableStateOf(
            if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB) existingSchedule.waliName else ""
        )
    }
    var waliBin by remember {
        mutableStateOf(
            if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB) existingSchedule.waliBin else ""
        )
    }
    var waliNik by remember {
        mutableStateOf(
            if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB) existingSchedule.waliNik else ""
        )
    }
    var waliNasabAddress by remember {
        mutableStateOf(
            if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_NASAB) existingSchedule.waliAddress else ""
        )
    }

    // Wali Hakim fields
    val initialWaliHakimAccount = remember(existingSchedule, staffAccounts, defaultWaliHakimAccount) {
        if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_HAKIM) {
            val whId = existingSchedule.waliHakimAccountId
            val whName = existingSchedule.waliName
            when {
                whId.isNotBlank() -> staffAccounts.firstOrNull { it.id == whId }
                whName.isNotBlank() -> staffAccounts.firstOrNull {
                    it.resolvedRole() == UserRole.PENGHULU && it.officerName.equals(whName, ignoreCase = true)
                }
                else -> null
            }
        } else {
            defaultWaliHakimAccount
        }
    }
    var selectedWaliHakimId by remember {
        mutableStateOf(initialWaliHakimAccount?.id.orEmpty())
    }
    var useManualWaliHakim by remember {
        mutableStateOf(
            when {
                existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_HAKIM ->
                    existingSchedule.waliName.isNotBlank() && initialWaliHakimAccount == null
                else -> defaultWaliHakimAccount == null && reminderSettings.defaultWaliHakimName.isNotBlank()
            }
        )
    }
    var manualWaliHakimName by remember {
        mutableStateOf(
            when {
                existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_HAKIM && initialWaliHakimAccount == null ->
                    existingSchedule.waliName
                defaultWaliHakimAccount == null -> reminderSettings.defaultWaliHakimName
                else -> ""
            }
        )
    }
    val existingReason = existingSchedule?.waliHakimReason.orEmpty()
    val existingReasonIsPreset = existingReason.isBlank() || WaliReferenceData.waliHakimReasons.any {
        it.equals(existingReason, ignoreCase = true)
    }
    var selectedWaliHakimReason by remember {
        mutableStateOf(
            if (existingReason.isNotBlank() && existingReasonIsPreset) {
                WaliReferenceData.waliHakimReasons.first { it.equals(existingReason, ignoreCase = true) }
            } else {
                WaliReferenceData.waliHakimReasons.first()
            }
        )
    }
    var useManualWaliHakimReason by remember {
        mutableStateOf(existingReason.isNotBlank() && !existingReasonIsPreset)
    }
    var manualWaliHakimReason by remember {
        mutableStateOf(if (useManualWaliHakimReason) existingReason else "")
    }
    var waliHakimNote by remember {
        mutableStateOf(
            if (existingSchedule?.resolvedWaliStatus() == WaliStatus.WALI_HAKIM) existingSchedule.waliAddress else ""
        )
    }

    var validationStatus by remember {
        mutableStateOf(existingSchedule?.resolvedStatus() ?: ValidationStatus.MENUNGGU)
    }
    var documentChecklist by remember {
        mutableStateOf(existingSchedule?.resolvedChecklist() ?: DocumentChecklist())
    }
    var documentsComplete by remember {
        mutableStateOf(existingSchedule?.documentsComplete ?: documentChecklist.isAllMandatoryComplete)
    }
    var verificationNote by remember { mutableStateOf(existingSchedule?.verificationNote ?: "") }

    var villageDropdownExpanded by remember { mutableStateOf(false) }
    var modinDropdownExpanded by remember { mutableStateOf(false) }
    var penghuluDropdownExpanded by remember { mutableStateOf(false) }
    var waliRelDropdownExpanded by remember { mutableStateOf(false) }
    var waliHakimDropdownExpanded by remember { mutableStateOf(false) }
    var waliHakimReasonDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var pendingConflictSubmit by remember { mutableStateOf<Pair<ScheduleDraft, Village>?>(null) }

    val selectedModinAccount = remember(selectedModinId, staffAccounts) {
        staffAccounts.firstOrNull { it.id == selectedModinId }
    }
    val selectedPenghuluAccount = remember(selectedPenghuluId, staffAccounts) {
        staffAccounts.firstOrNull { it.id == selectedPenghuluId }
    }
    val selectedWaliHakimAccount = remember(selectedWaliHakimId, staffAccounts) {
        staffAccounts.firstOrNull { it.id == selectedWaliHakimId }
    }

    val liveConflicts = remember(
        existingSchedule?.id,
        weddingDate,
        weddingTime,
        locationType,
        selectedPenghuluId,
        selectedPenghuluAccount,
        useManualPenghulu,
        manualPenghuluName,
        waliStatus,
        selectedWaliHakimId,
        selectedWaliHakimAccount,
        useManualWaliHakim,
        manualWaliHakimName,
        allSchedules
    ) {
        val currentPenghuluId = if (useManualPenghulu) "" else selectedPenghuluAccount?.id.orEmpty()
        val currentPenghuluName = if (useManualPenghulu) manualPenghuluName.trim() else selectedPenghuluAccount?.officerName.orEmpty()
        val currentWaliHakimId = if (waliStatus == WaliStatus.WALI_HAKIM && !useManualWaliHakim) {
            selectedWaliHakimAccount?.id.orEmpty()
        } else {
            ""
        }
        val currentWaliHakimName = if (waliStatus == WaliStatus.WALI_HAKIM) {
            if (useManualWaliHakim) manualWaliHakimName.trim() else selectedWaliHakimAccount?.officerName.orEmpty()
        } else {
            ""
        }
        ScheduleConflictChecker.detectConflicts(
            editingScheduleId = existingSchedule?.id,
            weddingDate = weddingDate,
            weddingTime = weddingTime,
            locationType = locationType,
            penghuluAccountId = currentPenghuluId,
            penghuluName = currentPenghuluName,
            waliStatus = waliStatus,
            waliHakimAccountId = currentWaliHakimId,
            waliHakimName = currentWaliHakimName,
            existingSchedules = allSchedules
        )
    }

    val coupleAgeCheck = remember(groomNik, brideNik, weddingDate) {
        NikAgeValidator.analyzeCouple(
            groomNik = groomNik,
            brideNik = brideNik,
            weddingDateIso = weddingDate
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingSchedule == null) "Input Pendaftaran Nikah" else "Ubah Jadwal Nikah",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Data tersimpan otomatis dan langsung tampil real-time di dasbor Modin desa terkait.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = regNumber,
                onValueChange = { regNumber = it.take(40) },
                label = { Text("Nomor Pendaftaran") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_reg_number")
            )

            // Village selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedVillage?.name ?: "Pilih Desa / Wilayah",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Wilayah Desa (Donorojo)") },
                    trailingIcon = {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { villageDropdownExpanded = true }
                        .testTag("form_village_picker")
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { villageDropdownExpanded = true }
                )
                DropdownMenu(
                    expanded = villageDropdownExpanded,
                    onDismissRequest = { villageDropdownExpanded = false }
                ) {
                    villages.forEach { village ->
                        val countModin = staffAccounts.count {
                            it.resolvedRole() == UserRole.MODIN && it.villageId == village.code && it.active
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(village.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "$countModin Modin aktif terdaftar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                val previousVillage = selectedVillage
                                selectedVillage = village
                                if (locationType == LocationType.LUAR_KUA) {
                                    val trimmed = locationAddress.trim()
                                    val isAutoAddress = trimmed.isEmpty() ||
                                        trimmed.equals(BALAI_NIKAH_KUA_ADDRESS, ignoreCase = true) ||
                                        villages.any { it.name.equals(trimmed, ignoreCase = true) }
                                    locationAddress = when {
                                        isAutoAddress -> village.name
                                        previousVillage != null &&
                                            trimmed.startsWith(previousVillage.name, ignoreCase = true) -> {
                                            village.name + trimmed.substring(previousVillage.name.length)
                                        }
                                        else -> village.name
                                    }
                                }
                                val firstForVillage = staffAccounts.firstOrNull {
                                    it.resolvedRole() == UserRole.MODIN && it.villageId == village.code && it.active
                                }
                                selectedModinId = firstForVillage?.id.orEmpty()
                                useManualModin = firstForVillage == null
                                if (firstForVillage != null) {
                                    manualModinName = ""
                                }
                                villageDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Modin selector (multiple Modin per village)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val modinDisplayValue = when {
                        useManualModin -> "Tulis Nama Modin Manual"
                        selectedModinAccount != null ->
                            "${selectedModinAccount.officerName} (@${selectedModinAccount.username})"
                        else -> "Pilih Modin Pendamping *"
                    }
                    OutlinedTextField(
                        value = modinDisplayValue,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Modin Pendamping *") },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_modin_picker")
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { modinDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = modinDropdownExpanded,
                        onDismissRequest = { modinDropdownExpanded = false }
                    ) {
                        villageModins.forEach { modin ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(modin.officerName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = buildString {
                                                append("@${modin.username}")
                                                if (modin.whatsappNumber.isNotBlank()) {
                                                    append(" • WA: ${modin.whatsappNumber}")
                                                }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedModinId = modin.id
                                    useManualModin = false
                                    manualModinName = ""
                                    modinDropdownExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "Tulis Nama Modin Manual...",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Jika Modin belum terdaftar di daftar akun desa",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                selectedModinId = ""
                                useManualModin = true
                                modinDropdownExpanded = false
                            }
                        )
                    }
                }

                if (useManualModin) {
                    OutlinedTextField(
                        value = manualModinName,
                        onValueChange = { manualModinName = it.take(100) },
                        label = { Text("Nama Modin Pendamping (Manual) *") },
                        placeholder = { Text("Contoh: H. Ahmad Syaiful") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_modin_manual_input")
                    )
                }

                Text(
                    text = "${villageModins.size} Modin aktif terdaftar di ${selectedVillage?.name ?: "desa ini"}. Anda bisa menambah Modin baru di menu Akun.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = groomName,
                onValueChange = { groomName = it.take(100) },
                label = { Text("Nama Calon Suami *") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_groom_name")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = groomNik,
                    onValueChange = { groomNik = it.filter { c -> c.isDigit() }.take(16) },
                    label = { Text("NIK Calon Suami (16 Digit — Hitung Usia Otomatis)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_groom_nik")
                )
                NikAgeInlineBadge(
                    analysis = coupleAgeCheck.groom,
                    roleLabel = "Calon Suami"
                )
            }

            OutlinedTextField(
                value = brideName,
                onValueChange = { brideName = it.take(100) },
                label = { Text("Nama Calon Istri *") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_bride_name")
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = brideNik,
                    onValueChange = { brideNik = it.filter { c -> c.isDigit() }.take(16) },
                    label = { Text("NIK Calon Istri (16 Digit — Hitung Usia Otomatis)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_bride_nik")
                )
                NikAgeInlineBadge(
                    analysis = coupleAgeCheck.bride,
                    roleLabel = "Calon Istri"
                )
            }

            // Date and Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.35f)
                        .height(54.dp)
                        .testTag("form_date_button")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatIndonesianDate(weddingDate),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.65f)
                        .height(54.dp)
                        .testTag("form_time_button")
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = weddingTime,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Location Type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LocationType.entries.forEach { type ->
                    val selected = locationType == type
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable {
                                locationType = type
                                locationAddress = defaultLocationAddress(type, selectedVillage)
                            }
                            .testTag("form_location_type_${type.code.lowercase()}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = locationAddress,
                onValueChange = { locationAddress = it.take(250) },
                label = { Text("Alamat Lengkap Lokasi Akad *") },
                minLines = 2,
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_location_address")
            )

            // Penghulu KUA Picker (links directly to Penghulu Dashboard & Calendar Sync)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val penghuluDisplay = when {
                        useManualPenghulu -> "Tulis Nama Penghulu Manual"
                        selectedPenghuluAccount != null ->
                            "${selectedPenghuluAccount.officerName} (@${selectedPenghuluAccount.username})"
                        else -> "Belum Ditentukan (Pilih Penghulu)"
                    }
                    OutlinedTextField(
                        value = penghuluDisplay,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Penghulu KUA Bertugas") },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_penghulu_picker")
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { penghuluDropdownExpanded = true }
                    )
                    DropdownMenu(
                        expanded = penghuluDropdownExpanded,
                        onDismissRequest = { penghuluDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Belum Ditentukan") },
                            onClick = {
                                selectedPenghuluId = ""
                                useManualPenghulu = false
                                manualPenghuluName = ""
                                penghuluDropdownExpanded = false
                            }
                        )
                        activePenghulus.forEach { penghulu ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(penghulu.officerName, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = "@${penghulu.username}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedPenghuluId = penghulu.id
                                    useManualPenghulu = false
                                    manualPenghuluName = ""
                                    penghuluDropdownExpanded = false
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Tulis Nama Penghulu Manual...",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                selectedPenghuluId = ""
                                useManualPenghulu = true
                                penghuluDropdownExpanded = false
                            }
                        )
                    }
                }

                if (useManualPenghulu) {
                    OutlinedTextField(
                        value = manualPenghuluName,
                        onValueChange = { manualPenghuluName = it.take(100) },
                        label = { Text("Nama Penghulu KUA (Manual)") },
                        placeholder = { Text("Contoh: H. Ahmad Subhan, S.Ag.") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("form_penghulu_manual_input")
                    )
                }

                Text(
                    text = "${activePenghulus.size} akun Penghulu aktif terdaftar. Pilih akun Penghulu agar jadwal tampil otomatis di Dasbor Penghulu.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status & Data Wali Nikah (Wali Nasab / Wali Hakim)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_wali_section")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Status & Data Wali Nikah *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WaliStatus.entries.forEach { option ->
                            val selected = waliStatus == option
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clickable {
                                        waliStatus = option
                                        if (option == WaliStatus.WALI_HAKIM &&
                                            selectedWaliHakimId.isBlank() &&
                                            manualWaliHakimName.isBlank()
                                        ) {
                                            if (defaultWaliHakimAccount != null) {
                                                selectedWaliHakimId = defaultWaliHakimAccount.id
                                                useManualWaliHakim = false
                                            } else if (reminderSettings.defaultWaliHakimName.isNotBlank()) {
                                                useManualWaliHakim = true
                                                manualWaliHakimName = reminderSettings.defaultWaliHakimName
                                            }
                                        }
                                    }
                                    .testTag("form_wali_status_${option.code.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (waliStatus == WaliStatus.WALI_NASAB) {
                        // Hubungan / Status Wali Nasab
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (useManualWaliRelationship) {
                                    "Lainnya (Tulis Manual)"
                                } else {
                                    selectedWaliRelationship
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Status / Hubungan Wali Nasab *") },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_relationship_picker")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { waliRelDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = waliRelDropdownExpanded,
                                onDismissRequest = { waliRelDropdownExpanded = false }
                            ) {
                                WaliReferenceData.nasabRelationships.forEach { rel ->
                                    DropdownMenuItem(
                                        text = { Text(rel) },
                                        onClick = {
                                            selectedWaliRelationship = rel
                                            useManualWaliRelationship = false
                                            manualWaliRelationship = ""
                                            waliRelDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Lainnya (Tulis Hubungan Manual)...",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        useManualWaliRelationship = true
                                        waliRelDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (useManualWaliRelationship) {
                            OutlinedTextField(
                                value = manualWaliRelationship,
                                onValueChange = { manualWaliRelationship = it.take(120) },
                                label = { Text("Hubungan Wali Nasab (Manual) *") },
                                placeholder = { Text("Contoh: Kakek dari garis ayah") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_relationship_manual")
                            )
                        }

                        OutlinedTextField(
                            value = waliNasabName,
                            onValueChange = { waliNasabName = it.take(120) },
                            label = { Text("Nama Lengkap Wali Nasab *") },
                            placeholder = { Text("Contoh: H. Supriyadi") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_wali_name")
                        )

                        OutlinedTextField(
                            value = waliBin,
                            onValueChange = { waliBin = it.take(100) },
                            label = { Text("Bin (Nama Ayah Wali)") },
                            placeholder = { Text("Contoh: Kasan / Ahmad") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_wali_bin")
                        )

                        OutlinedTextField(
                            value = waliNik,
                            onValueChange = { waliNik = it.filter { c -> c.isDigit() }.take(16) },
                            label = { Text("NIK Wali Nasab (Opsional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_wali_nik")
                        )

                        OutlinedTextField(
                            value = waliNasabAddress,
                            onValueChange = { waliNasabAddress = it.take(250) },
                            label = { Text("Alamat / Keterangan Wali (Opsional)") },
                            placeholder = { Text("Contoh: RT 02 / RW 01 Desa Tulakan") },
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_wali_address")
                        )
                    } else {
                        // Wali Hakim Picker (Penghulu yang menjadi Wali Hakim)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val waliHakimDisplay = when {
                                useManualWaliHakim -> "Tulis Nama Wali Hakim Manual"
                                selectedWaliHakimAccount != null -> {
                                    val defaultTag = if (selectedWaliHakimAccount.id == reminderSettings.defaultWaliHakimAccountId) {
                                        " • Utama"
                                    } else {
                                        ""
                                    }
                                    "${selectedWaliHakimAccount.officerName}$defaultTag"
                                }
                                else -> "Pilih Penghulu Wali Hakim *"
                            }
                            OutlinedTextField(
                                value = waliHakimDisplay,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Penghulu Wali Hakim *") },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_hakim_picker")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { waliHakimDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = waliHakimDropdownExpanded,
                                onDismissRequest = { waliHakimDropdownExpanded = false }
                            ) {
                                authorizedWaliHakimPenghulus.forEach { penghulu ->
                                    val isDefault = penghulu.id == reminderSettings.defaultWaliHakimAccountId
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(penghulu.officerName, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    text = if (isDefault) {
                                                        "Wali Hakim Utama • @${penghulu.username}"
                                                    } else {
                                                        "Penghulu Wali Hakim • @${penghulu.username}"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedWaliHakimId = penghulu.id
                                            useManualWaliHakim = false
                                            manualWaliHakimName = ""
                                            waliHakimDropdownExpanded = false
                                        }
                                    )
                                }
                                otherActivePenghulus.forEach { penghulu ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(penghulu.officerName, fontWeight = FontWeight.Medium)
                                                Text(
                                                    text = "Penghulu KUA • @${penghulu.username}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedWaliHakimId = penghulu.id
                                            useManualWaliHakim = false
                                            manualWaliHakimName = ""
                                            waliHakimDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Tulis Nama Wali Hakim Manual...",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        selectedWaliHakimId = ""
                                        useManualWaliHakim = true
                                        waliHakimDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (useManualWaliHakim) {
                            OutlinedTextField(
                                value = manualWaliHakimName,
                                onValueChange = { manualWaliHakimName = it.take(120) },
                                label = { Text("Nama Penghulu / Kepala KUA Wali Hakim *") },
                                placeholder = { Text("Contoh: H. Ahmad Fauzi, S.Ag.") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_hakim_manual_input")
                            )
                        }

                        Text(
                            text = if (authorizedWaliHakimPenghulus.isNotEmpty()) {
                                "${authorizedWaliHakimPenghulus.size} Penghulu ditugaskan sebagai Wali Hakim. Atur daftarnya di menu Akun → Pengaturan Penghulu Wali Hakim."
                            } else {
                                "Belum ada Penghulu Wali Hakim ditetapkan. Anda bisa mengaturnya di menu Akun → Pengaturan Penghulu Wali Hakim."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Sebab / Alasan Wali Hakim
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = if (useManualWaliHakimReason) {
                                    "Lainnya (Tulis Manual)"
                                } else {
                                    selectedWaliHakimReason
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sebab / Alasan Wali Hakim *") },
                                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_hakim_reason_picker")
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { waliHakimReasonDropdownExpanded = true }
                            )
                            DropdownMenu(
                                expanded = waliHakimReasonDropdownExpanded,
                                onDismissRequest = { waliHakimReasonDropdownExpanded = false }
                            ) {
                                WaliReferenceData.waliHakimReasons.forEach { reason ->
                                    DropdownMenuItem(
                                        text = { Text(reason) },
                                        onClick = {
                                            selectedWaliHakimReason = reason
                                            useManualWaliHakimReason = false
                                            manualWaliHakimReason = ""
                                            waliHakimReasonDropdownExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Lainnya (Tulis Alasan Manual)...",
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        useManualWaliHakimReason = true
                                        waliHakimReasonDropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (useManualWaliHakimReason) {
                            OutlinedTextField(
                                value = manualWaliHakimReason,
                                onValueChange = { manualWaliHakimReason = it.take(250) },
                                label = { Text("Alasan Wali Hakim (Manual) *") },
                                placeholder = { Text("Tulis alasan penggunaan Wali Hakim") },
                                minLines = 1,
                                maxLines = 3,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("form_wali_hakim_reason_manual")
                            )
                        }

                        OutlinedTextField(
                            value = waliHakimNote,
                            onValueChange = { waliHakimNote = it.take(250) },
                            label = { Text("No. Penetapan PA / Keterangan (Opsional)") },
                            placeholder = { Text("Contoh: Penetapan PA Jepara No. ...") },
                            minLines = 1,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("form_wali_hakim_note")
                        )
                    }
                }
            }

            if (liveConflicts.isNotEmpty()) {
                val hasCritical = liveConflicts.any { it.severity == ConflictSeverity.CRITICAL }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (hasCritical) ErrorContainerLight else StatusPendingBg,
                    border = BorderStroke(
                        1.2.dp,
                        if (hasCritical) ErrorCrimson.copy(alpha = 0.45f) else StatusPendingFg.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("form_conflict_warning_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = if (hasCritical) ErrorCrimson else StatusPendingFg,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Deteksi Bentrok Jadwal (${liveConflicts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasCritical) OnErrorContainerLight else StatusPendingFg
                            )
                        }
                        liveConflicts.forEach { conflict ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "• ${conflict.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (conflict.severity == ConflictSeverity.CRITICAL) {
                                        OnErrorContainerLight
                                    } else {
                                        StatusPendingFg
                                    }
                                )
                                Text(
                                    text = conflict.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (conflict.severity == ConflictSeverity.CRITICAL) {
                                        OnErrorContainerLight.copy(alpha = 0.88f)
                                    } else {
                                        StatusPendingFg.copy(alpha = 0.88f)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Validation Status selector
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = validationStatus.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status Validasi KUA") },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { statusDropdownExpanded = true }
                )
                DropdownMenu(
                    expanded = statusDropdownExpanded,
                    onDismissRequest = { statusDropdownExpanded = false }
                ) {
                    ValidationStatus.entries.forEach { st ->
                        DropdownMenuItem(
                            text = { Text(st.label) },
                            onClick = {
                                validationStatus = st
                                statusDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (coupleAgeCheck.requiresDispensasiKawin) {
                DispensasiKawinAlertCard(
                    coupleAgeCheck = coupleAgeCheck,
                    onApplyNote = {
                        val rec = coupleAgeCheck.recommendedNoteText()
                        verificationNote = when {
                            verificationNote.isBlank() -> rec
                            verificationNote.contains("Dispensasi Kawin", ignoreCase = true) -> verificationNote
                            else -> "${verificationNote.trim()}\n$rec".take(500)
                        }
                    }
                )
            }

            DocumentChecklistEditor(
                checklist = documentChecklist,
                onChange = { updated ->
                    documentChecklist = updated
                    documentsComplete = updated.isAllMandatoryComplete
                }
            )

            OutlinedTextField(
                value = verificationNote,
                onValueChange = { verificationNote = it.take(500) },
                label = { Text("Catatan KUA untuk Modin (Opsional)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Batal")
                }

                Button(
                    onClick = {
                        val village = selectedVillage
                        val resolvedModinId = if (useManualModin) "" else selectedModinAccount?.id.orEmpty()
                        val resolvedModinName = if (useManualModin) {
                            manualModinName.trim()
                        } else {
                            selectedModinAccount?.officerName.orEmpty()
                        }
                        val resolvedPenghuluId = if (useManualPenghulu) "" else selectedPenghuluAccount?.id.orEmpty()
                        val resolvedPenghuluName = if (useManualPenghulu) {
                            manualPenghuluName.trim()
                        } else {
                            selectedPenghuluAccount?.officerName.orEmpty()
                        }
                        val resolvedWaliRel = if (waliStatus == WaliStatus.WALI_NASAB) {
                            if (useManualWaliRelationship) manualWaliRelationship.trim() else selectedWaliRelationship
                        } else {
                            "Wali Hakim"
                        }
                        val resolvedWaliHakimId = if (waliStatus == WaliStatus.WALI_HAKIM && !useManualWaliHakim) {
                            selectedWaliHakimAccount?.id.orEmpty()
                        } else {
                            ""
                        }
                        val resolvedWaliName = if (waliStatus == WaliStatus.WALI_NASAB) {
                            waliNasabName.trim()
                        } else if (useManualWaliHakim) {
                            manualWaliHakimName.trim()
                        } else {
                            selectedWaliHakimAccount?.officerName.orEmpty()
                        }
                        val resolvedWaliReason = if (waliStatus == WaliStatus.WALI_HAKIM) {
                            if (useManualWaliHakimReason) manualWaliHakimReason.trim() else selectedWaliHakimReason
                        } else {
                            ""
                        }

                        when {
                            village == null -> validationError = "Pilih wilayah desa terlebih dahulu."
                            resolvedModinName.length < 2 -> validationError = "Pilih atau isi nama Modin pendamping terlebih dahulu."
                            regNumber.trim().length < 3 -> validationError = "Nomor pendaftaran minimal 3 karakter."
                            groomName.trim().length < 2 -> validationError = "Nama calon suami wajib diisi."
                            brideName.trim().length < 2 -> validationError = "Nama calon istri wajib diisi."
                            locationAddress.trim().length < 2 -> validationError = "Alamat lokasi akad wajib diisi."
                            waliStatus == WaliStatus.WALI_NASAB && resolvedWaliRel.length < 2 ->
                                validationError = "Status / hubungan wali nasab wajib diisi."
                            waliStatus == WaliStatus.WALI_NASAB && resolvedWaliName.length < 2 ->
                                validationError = "Nama lengkap wali nasab wajib diisi."
                            waliStatus == WaliStatus.WALI_HAKIM && resolvedWaliName.length < 2 ->
                                validationError = "Pilih atau isi nama Penghulu Wali Hakim terlebih dahulu."
                            waliStatus == WaliStatus.WALI_HAKIM && resolvedWaliReason.length < 2 ->
                                validationError = "Sebab / alasan Wali Hakim wajib diisi."
                            else -> {
                                validationError = null
                                val draft = ScheduleDraft(
                                    id = existingSchedule?.id,
                                    registrationNumber = regNumber.trim(),
                                    groomName = groomName.trim(),
                                    groomNik = groomNik.trim(),
                                    brideName = brideName.trim(),
                                    brideNik = brideNik.trim(),
                                    villageId = village.code,
                                    villageName = village.name,
                                    modinAccountId = resolvedModinId,
                                    modinName = resolvedModinName,
                                    weddingDate = weddingDate,
                                    weddingTime = weddingTime,
                                    locationType = locationType,
                                    locationAddress = locationAddress.trim(),
                                    penghuluAccountId = resolvedPenghuluId,
                                    penghuluName = resolvedPenghuluName,
                                    waliStatus = waliStatus,
                                    waliRelationship = resolvedWaliRel,
                                    waliName = resolvedWaliName,
                                    waliBin = if (waliStatus == WaliStatus.WALI_NASAB) waliBin.trim() else "",
                                    waliNik = if (waliStatus == WaliStatus.WALI_NASAB) waliNik.trim() else "",
                                    waliAddress = if (waliStatus == WaliStatus.WALI_NASAB) {
                                        waliNasabAddress.trim()
                                    } else {
                                        waliHakimNote.trim()
                                    },
                                    waliHakimAccountId = resolvedWaliHakimId,
                                    waliHakimReason = resolvedWaliReason,
                                    validationStatus = validationStatus,
                                    documentsComplete = documentsComplete || documentChecklist.isAllMandatoryComplete,
                                    documentChecklist = documentChecklist,
                                    verificationNote = verificationNote.trim()
                                )
                                if (liveConflicts.isNotEmpty()) {
                                    pendingConflictSubmit = draft to village
                                } else {
                                    onSubmit(draft, village)
                                }
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("form_submit_schedule_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (existingSchedule == null) "Simpan Jadwal" else "Simpan Perubahan")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            formatter.timeZone = TimeZone.getTimeZone("UTC")
                            weddingDate = formatter.format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val parts = weddingTime.split(":")
        val initHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val initMin = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Pilih Jam Akad (WIB)") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hh = timePickerState.hour.toString().padStart(2, '0')
                        val mm = timePickerState.minute.toString().padStart(2, '0')
                        weddingTime = "$hh:$mm"
                        showTimePicker = false
                    }
                ) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Batal") }
            }
        )
    }

    pendingConflictSubmit?.let { (draft, village) ->
        AlertDialog(
            onDismissRequest = { pendingConflictSubmit = null },
            icon = {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = StatusPendingFg
                )
            },
            title = { Text("Jadwal Berpotensi Bentrok") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Ditemukan ${liveConflicts.size} potensi bentrok pada tanggal ${formatIndonesianDate(draft.weddingDate)} pukul ${draft.weddingTime} WIB:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    liveConflicts.take(3).forEach { conflict ->
                        Text(
                            text = "• ${conflict.title}: ${conflict.detail}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "Apakah Anda yakin ingin tetap menyimpan jadwal ini?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConflictSubmit = null
                        onSubmit(draft, village)
                    },
                    modifier = Modifier.testTag("confirm_save_with_conflict_button")
                ) {
                    Text("Tetap Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConflictSubmit = null }) {
                    Text("Periksa Lagi")
                }
            }
        )
    }
}
