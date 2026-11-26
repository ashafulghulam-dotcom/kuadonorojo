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
import com.example.data.LocationType
import com.example.data.MarriageSchedule
import com.example.data.ScheduleDraft
import com.example.data.StaffAccount
import com.example.data.UserRole
import com.example.data.ValidationStatus
import com.example.data.Village
import com.example.data.formatIndonesianDate
import com.example.data.generateRegistrationNumber
import com.example.data.todayIsoDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleFormSheet(
    existingSchedule: MarriageSchedule?,
    villages: List<Village>,
    staffAccounts: List<StaffAccount>,
    existingScheduleCount: Int,
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
        mutableStateOf(existingSchedule?.locationAddress ?: "Balai Nikah KUA Donorojo")
    }
    var penghuluName by remember { mutableStateOf(existingSchedule?.penghuluName ?: "") }
    var validationStatus by remember {
        mutableStateOf(existingSchedule?.resolvedStatus() ?: ValidationStatus.MENUNGGU)
    }
    var documentsComplete by remember { mutableStateOf(existingSchedule?.documentsComplete ?: false) }
    var verificationNote by remember { mutableStateOf(existingSchedule?.verificationNote ?: "") }

    var villageDropdownExpanded by remember { mutableStateOf(false) }
    var modinDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val selectedModinAccount = remember(selectedModinId, staffAccounts) {
        staffAccounts.firstOrNull { it.id == selectedModinId }
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
                                selectedVillage = village
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

            OutlinedTextField(
                value = groomNik,
                onValueChange = { groomNik = it.filter { c -> c.isDigit() }.take(16) },
                label = { Text("NIK Calon Suami (Opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

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

            OutlinedTextField(
                value = brideNik,
                onValueChange = { brideNik = it.filter { c -> c.isDigit() }.take(16) },
                label = { Text("NIK Calon Istri (Opsional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

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
                                if (type == LocationType.KUA && locationAddress.isBlank()) {
                                    locationAddress = "Balai Nikah KUA Donorojo"
                                }
                            }
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

            OutlinedTextField(
                value = penghuluName,
                onValueChange = { penghuluName = it.take(100) },
                label = { Text("Nama Penghulu KUA") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

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

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Berkas N1–N4 Lengkap",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tandai jika persyaratan administrasi nikah sudah terpenuhi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = documentsComplete,
                        onCheckedChange = { documentsComplete = it }
                    )
                }
            }

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
                        when {
                            village == null -> validationError = "Pilih wilayah desa terlebih dahulu."
                            resolvedModinName.length < 2 -> validationError = "Pilih atau isi nama Modin pendamping terlebih dahulu."
                            regNumber.trim().length < 3 -> validationError = "Nomor pendaftaran minimal 3 karakter."
                            groomName.trim().length < 2 -> validationError = "Nama calon suami wajib diisi."
                            brideName.trim().length < 2 -> validationError = "Nama calon istri wajib diisi."
                            locationAddress.trim().length < 2 -> validationError = "Alamat lokasi akad wajib diisi."
                            else -> {
                                validationError = null
                                onSubmit(
                                    ScheduleDraft(
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
                                        penghuluName = penghuluName.trim(),
                                        validationStatus = validationStatus,
                                        documentsComplete = documentsComplete,
                                        verificationNote = verificationNote.trim()
                                    ),
                                    village
                                )
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
}
