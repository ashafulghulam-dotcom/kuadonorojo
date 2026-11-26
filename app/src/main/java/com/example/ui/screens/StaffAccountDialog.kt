package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StaffAccount
import com.example.data.UserRole
import com.example.data.Village

@Composable
fun AddModinAccountDialog(
    villages: List<Village>,
    existingAccounts: List<StaffAccount>,
    initialVillageCode: String? = null,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        village: Village,
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        active: Boolean
    ) -> Unit
) {
    val defaultVillage = remember(villages, initialVillageCode) {
        villages.firstOrNull { it.code == initialVillageCode } ?: villages.firstOrNull()
    }

    fun suggestUsername(village: Village?): String {
        if (village == null) return ""
        val countInVillage = existingAccounts.count {
            it.resolvedRole() == UserRole.MODIN && it.villageId == village.code
        }
        var suffix = countInVillage + 1
        var candidate = "modin.${village.code}$suffix"
        while (existingAccounts.any { it.username.equals(candidate, ignoreCase = true) }) {
            suffix++
            candidate = "modin.${village.code}$suffix"
        }
        return candidate
    }

    var selectedVillage by remember { mutableStateOf(defaultVillage) }
    var villageMenuExpanded by remember { mutableStateOf(false) }
    var officerName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(suggestUsername(defaultVillage)) }
    var usernameEditedManually by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var whatsappNumber by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tambah Akun Modin Desa",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambahkan Modin baru secara manual untuk desa tertentu lengkap dengan username dan password login.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Village picker
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedVillage?.name ?: "Pilih Wilayah Desa *",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Wilayah Desa *") },
                        trailingIcon = {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_modin_village_picker")
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { villageMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = villageMenuExpanded,
                        onDismissRequest = { villageMenuExpanded = false }
                    ) {
                        villages.forEach { village ->
                            val existingCount = existingAccounts.count {
                                it.resolvedRole() == UserRole.MODIN && it.villageId == village.code
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(village.name)
                                        Text(
                                            text = "Saat ini: $existingCount Modin",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedVillage = village
                                    if (!usernameEditedManually) {
                                        username = suggestUsername(village)
                                    }
                                    villageMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = officerName,
                    onValueChange = { officerName = it.take(100) },
                    label = { Text("Nama Lengkap Modin *") },
                    placeholder = { Text("Contoh: H. Ahmad Suparno") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_modin_officer_name")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        usernameEditedManually = true
                        username = it.trim().lowercase().take(50)
                    },
                    label = { Text("Username Login *") },
                    placeholder = { Text("Contoh: modin.blingoh2") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_modin_username")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("Password Login *") },
                    placeholder = { Text("Minimal 4 karakter") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_modin_password")
                )

                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = { whatsappNumber = it.take(30) },
                    label = { Text("Nomor WhatsApp (Opsional)") },
                    placeholder = { Text("08xxxxxxxxxx") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_modin_whatsapp")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status Akun Aktif", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aktifkan agar Modin langsung bisa login dan dipilih di jadwal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val village = selectedVillage
                    val cleanUser = username.trim().lowercase()
                    val cleanName = officerName.trim()
                    val duplicateExists = existingAccounts.any {
                        it.username.equals(cleanUser, ignoreCase = true)
                    }
                    when {
                        village == null -> errorMsg = "Pilih wilayah desa terlebih dahulu."
                        cleanName.length < 2 -> errorMsg = "Nama Modin minimal 2 karakter."
                        cleanUser.length < 3 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUser) ->
                            errorMsg = "Username minimal 3 karakter (huruf kecil, angka, titik, _ atau -)."
                        duplicateExists ->
                            errorMsg = "Username \"$cleanUser\" sudah digunakan petugas lain."
                        password.length < 4 ->
                            errorMsg = "Password minimal 4 karakter."
                        else -> {
                            errorMsg = null
                            onCreate(
                                village,
                                cleanUser,
                                password,
                                cleanName,
                                whatsappNumber.trim(),
                                active
                            )
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_add_modin_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Tambah Modin")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun AddPenghuluAccountDialog(
    existingAccounts: List<StaffAccount>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        username: String,
        password: String,
        officerName: String,
        whatsappNumber: String,
        active: Boolean
    ) -> Unit
) {
    fun suggestUsername(): String {
        val countPenghulu = existingAccounts.count { it.resolvedRole() == UserRole.PENGHULU }
        var suffix = countPenghulu + 1
        var candidate = "penghulu$suffix"
        while (existingAccounts.any { it.username.equals(candidate, ignoreCase = true) }) {
            suffix++
            candidate = "penghulu$suffix"
        }
        return candidate
    }

    var officerName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(suggestUsername()) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var whatsappNumber by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Tambah Akun Penghulu KUA",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambahkan akun Penghulu KUA Donorojo agar dapat login ke Dasbor Penghulu, melihat jadwal tugas akad, dan menautkannya ke Kalender HP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = officerName,
                    onValueChange = { officerName = it.take(100) },
                    label = { Text("Nama Lengkap Penghulu *") },
                    placeholder = { Text("Contoh: H. Muhammad Luthfi, S.Ag.") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_penghulu_officer_name")
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().lowercase().take(50) },
                    label = { Text("Username Login *") },
                    placeholder = { Text("Contoh: penghulu2") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_penghulu_username")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(64) },
                    label = { Text("Password Login *") },
                    placeholder = { Text("Minimal 4 karakter") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_penghulu_password")
                )

                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = { whatsappNumber = it.take(30) },
                    label = { Text("Nomor WhatsApp (Opsional)") },
                    placeholder = { Text("08xxxxxxxxxx") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_penghulu_whatsapp")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Status Akun Aktif", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aktifkan agar Penghulu bisa login dan dipilih saat input jadwal nikah",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUser = username.trim().lowercase()
                    val cleanName = officerName.trim()
                    val duplicateExists = existingAccounts.any {
                        it.username.equals(cleanUser, ignoreCase = true)
                    }
                    when {
                        cleanName.length < 2 -> errorMsg = "Nama Penghulu minimal 2 karakter."
                        cleanUser.length < 3 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUser) ->
                            errorMsg = "Username minimal 3 karakter (huruf kecil, angka, titik, _ atau -)."
                        duplicateExists ->
                            errorMsg = "Username \"$cleanUser\" sudah digunakan petugas lain."
                        password.length < 4 ->
                            errorMsg = "Password minimal 4 karakter."
                        else -> {
                            errorMsg = null
                            onCreate(
                                cleanUser,
                                password,
                                cleanName,
                                whatsappNumber.trim(),
                                active
                            )
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("confirm_add_penghulu_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Tambah Penghulu")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}

@Composable
fun StaffAccountDialog(
    account: StaffAccount,
    villages: List<Village>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        username: String,
        newPassword: String?,
        officerName: String,
        whatsappNumber: String,
        active: Boolean,
        newVillage: Village?
    ) -> Unit
) {
    val role = account.resolvedRole()
    val isAdminRole = role == UserRole.ADMIN
    val isModinRole = role == UserRole.MODIN
    val isPenghuluRole = role == UserRole.PENGHULU
    val initialVillage = remember(account, villages) {
        villages.firstOrNull { it.code == account.villageId }
    }

    var selectedVillage by remember { mutableStateOf(initialVillage) }
    var villageMenuExpanded by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(account.username) }
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var officerName by remember { mutableStateOf(account.officerName) }
    var whatsappNumber by remember { mutableStateOf(account.whatsappNumber) }
    var active by remember { mutableStateOf(account.active) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isAdminRole -> "Pengaturan Akun Admin KUA"
                    isPenghuluRole -> "Ubah Akun Penghulu KUA"
                    else -> "Ubah Akun Modin"
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isModinRole && villages.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedVillage?.name ?: account.villageName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Wilayah Desa") },
                            trailingIcon = {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { villageMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = villageMenuExpanded,
                            onDismissRequest = { villageMenuExpanded = false }
                        ) {
                            villages.forEach { village ->
                                DropdownMenuItem(
                                    text = { Text(village.name) },
                                    onClick = {
                                        selectedVillage = village
                                        villageMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = officerName,
                    onValueChange = { officerName = it.take(100) },
                    label = {
                        Text(
                            when {
                                isAdminRole -> "Nama Admin KUA *"
                                isPenghuluRole -> "Nama Penghulu KUA *"
                                else -> "Nama Modin Desa *"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim().lowercase().take(50) },
                    label = { Text("Username Login *") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account_username")
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it.take(64) },
                    label = { Text("Password Baru (Kosongkan jika tetap)") },
                    placeholder = { Text("Minimal 4 karakter") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account_password")
                )

                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = { whatsappNumber = it.take(30) },
                    label = { Text("Nomor WhatsApp (Smart Reminder WA)") },
                    placeholder = { Text("08xxxxxxxxxx") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account_whatsapp")
                )

                if (!isAdminRole) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Status Akun Aktif", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Nonaktifkan jika ingin membekukan akses login sementara",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUser = username.trim().lowercase()
                    val cleanName = officerName.trim()
                    when {
                        cleanName.length < 2 -> errorMsg = "Nama petugas minimal 2 karakter."
                        cleanUser.length < 3 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUser) ->
                            errorMsg = "Username minimal 3 karakter (huruf kecil, angka, titik, _ atau -)."
                        newPassword.isNotBlank() && newPassword.length < 4 ->
                            errorMsg = "Password baru minimal 4 karakter."
                        else -> {
                            errorMsg = null
                            onSave(
                                cleanUser,
                                newPassword.takeIf { it.isNotBlank() },
                                cleanName,
                                whatsappNumber.trim(),
                                if (isAdminRole) true else active,
                                if (isModinRole) selectedVillage else null
                            )
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_account_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Simpan")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal")
            }
        }
    )
}
