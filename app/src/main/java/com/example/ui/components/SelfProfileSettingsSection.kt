package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StaffAccount
import com.example.data.UserRole
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg

@Composable
fun SelfProfileSettingsSection(
    account: StaffAccount,
    isAdminPreview: Boolean,
    isSaving: Boolean,
    onSaveProfile: (
        officerName: String,
        whatsappNumber: String,
        username: String,
        currentPassword: String,
        newPassword: String?,
        onSuccess: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val role = account.resolvedRole()
    val roleDisplay = when (role) {
        UserRole.PENGHULU -> if (account.isWaliHakim) {
            "Penghulu KUA Donorojo • Wali Hakim"
        } else {
            "Penghulu KUA Donorojo"
        }
        UserRole.MODIN -> if (account.villageName.isNotBlank()) {
            "Modin • ${account.villageName}"
        } else {
            "Modin Desa KUA Donorojo"
        }
        UserRole.ADMIN -> "Admin KUA Donorojo"
    }

    var officerName by remember(account.id, account.officerName) { mutableStateOf(account.officerName) }
    var whatsappNumber by remember(account.id, account.whatsappNumber) { mutableStateOf(account.whatsappNumber) }
    var username by remember(account.id, account.username) { mutableStateOf(account.username) }
    var currentPassword by remember(account.id) { mutableStateOf("") }
    var newPassword by remember(account.id) { mutableStateOf("") }
    var confirmPassword by remember(account.id) { mutableStateOf("") }

    var showCurrentPass by remember { mutableStateOf(false) }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(account.id, account.officerName, account.whatsappNumber, account.username) {
        officerName = account.officerName
        whatsappNumber = account.whatsappNumber
        username = account.username
    }

    val cleanOfficer = officerName.trim()
    val cleanPhone = whatsappNumber.trim()
    val cleanUsername = username.trim().lowercase()
    val cleanNewPass = newPassword.trim()
    val usernameChanged = !account.username.equals(cleanUsername, ignoreCase = true)
    val passwordChanged = cleanNewPass.isNotEmpty()
    val needsCurrentPassword = usernameChanged || passwordChanged
    val hasChanges = cleanOfficer != account.officerName ||
        cleanPhone != account.whatsappNumber ||
        usernameChanged ||
        passwordChanged

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Active Account Summary Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("self_profile_summary_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (role == UserRole.PENGHULU) Icons.Filled.Badge else Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.officerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = roleDisplay,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = StatusVerifiedBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusVerifiedFg,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Aktif",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusVerifiedFg
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            text = "@${account.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = if (account.whatsappNumber.isNotBlank()) StatusVerifiedBg else StatusPendingBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = if (account.whatsappNumber.isNotBlank()) {
                                "WA: ${account.whatsappNumber}"
                            } else {
                                "WA belum diisi"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (account.whatsappNumber.isNotBlank()) StatusVerifiedFg else StatusPendingFg,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (isAdminPreview) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Anda sedang dalam mode Pratinjau Admin KUA. Petugas yang login langsung dengan akun ini dapat mengubah nama, nomor WA, username, dan password secara mandiri di halaman ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            return
        }

        // Self-Service Account Edit Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Pengaturan Profil & Akun Mandiri",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Perbarui nama lengkap, nomor WhatsApp, username login, atau ganti password akun Anda kapan saja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = officerName,
                    onValueChange = {
                        officerName = it.take(100)
                        validationError = null
                    },
                    label = {
                        Text(
                            if (role == UserRole.PENGHULU) "Nama Lengkap Penghulu *" else "Nama Lengkap Modin *"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_profile_name_input")
                )

                OutlinedTextField(
                    value = whatsappNumber,
                    onValueChange = {
                        whatsappNumber = it.take(30)
                        validationError = null
                    },
                    label = { Text("Nomor WhatsApp (Smart Reminder)") },
                    placeholder = { Text("08xxxxxxxxxx") },
                    supportingText = {
                        Text("Digunakan Admin KUA untuk mengirim pengingat jadwal akad ke WhatsApp Anda.")
                    },
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
                        .testTag("self_profile_wa_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Keamanan Login (Username & Password)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.trim().lowercase().take(50)
                        validationError = null
                    },
                    label = { Text("Username Login *") },
                    supportingText = {
                        Text("3–50 karakter (huruf kecil, angka, titik, _ atau -).")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_profile_username_input")
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it.take(64)
                        validationError = null
                    },
                    label = { Text("Password Baru (Kosongkan jika tetap)") },
                    placeholder = { Text("Minimal 4 karakter") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showNewPass = !showNewPass },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (showNewPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.cd_toggle_password)
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_profile_new_password_input")
                )

                AnimatedVisibility(visible = passwordChanged) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it.take(64)
                            validationError = null
                        },
                        label = { Text("Konfirmasi Password Baru *") },
                        placeholder = { Text("Ketik ulang password baru") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { showConfirmPass = !showConfirmPass },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = if (showConfirmPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.cd_toggle_password)
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("self_profile_confirm_password_input")
                    )
                }

                AnimatedVisibility(visible = needsCurrentPassword) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = {
                                currentPassword = it.take(64)
                                validationError = null
                            },
                            label = { Text("Password Saat Ini *") },
                            placeholder = { Text("Masukkan password Anda saat ini") },
                            supportingText = {
                                Text("Wajib diisi sebagai verifikasi keamanan saat mengubah username atau password.")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showCurrentPass = !showCurrentPass },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (showCurrentPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.cd_toggle_password)
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (showCurrentPass) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("self_profile_current_password_input")
                        )
                    }
                }

                AnimatedVisibility(visible = validationError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = validationError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        when {
                            cleanOfficer.length < 2 -> {
                                validationError = "Nama lengkap minimal 2 karakter."
                            }
                            cleanUsername.length < 3 || !Regex("^[a-z0-9._\\-]+$").matches(cleanUsername) -> {
                                validationError = "Username harus 3–50 karakter (huruf kecil, angka, titik, _ atau -)."
                            }
                            passwordChanged && cleanNewPass.length < 4 -> {
                                validationError = "Password baru minimal 4 karakter."
                            }
                            passwordChanged && cleanNewPass != confirmPassword.trim() -> {
                                validationError = "Konfirmasi password baru tidak cocok."
                            }
                            needsCurrentPassword && currentPassword.isBlank() -> {
                                validationError = "Masukkan password saat ini untuk mengonfirmasi perubahan username atau password."
                            }
                            else -> {
                                validationError = null
                                onSaveProfile(
                                    cleanOfficer,
                                    cleanPhone,
                                    cleanUsername,
                                    currentPassword,
                                    cleanNewPass.takeIf { it.isNotEmpty() }
                                ) {
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            }
                        }
                    },
                    enabled = !isSaving && hasChanges,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_self_profile_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Perubahan Akun")
                    }
                }
            }
        }
    }
}
