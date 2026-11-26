package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ReminderSettings
import com.example.data.StaffAccount
import com.example.data.UserRole

@Composable
fun WaliHakimSettingsDialog(
    penghuluAccounts: List<StaffAccount>,
    settings: ReminderSettings,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        authorizedPenghuluIds: Set<String>,
        defaultWaliHakimAccountId: String,
        defaultWaliHakimName: String
    ) -> Unit
) {
    val activePenghulus = remember(penghuluAccounts) {
        penghuluAccounts.filter { it.resolvedRole() == UserRole.PENGHULU }
    }

    var authorizedIds by remember(activePenghulus) {
        mutableStateOf(
            activePenghulus
                .filter { it.isWaliHakim || it.id == settings.defaultWaliHakimAccountId }
                .map { it.id }
                .toSet()
        )
    }
    var defaultPenghuluId by remember(settings.defaultWaliHakimAccountId, activePenghulus) {
        mutableStateOf(
            if (activePenghulus.any { it.id == settings.defaultWaliHakimAccountId }) {
                settings.defaultWaliHakimAccountId
            } else {
                ""
            }
        )
    }
    var fallbackWaliHakimName by remember(settings.defaultWaliHakimName, defaultPenghuluId) {
        mutableStateOf(
            if (defaultPenghuluId.isBlank()) settings.defaultWaliHakimName else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Pengaturan Penghulu Wali Hakim",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Centang Penghulu KUA yang ditugaskan menjadi Wali Hakim dan pilih satu sebagai Wali Hakim Utama (terisi otomatis saat input nikah Wali Hakim).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activePenghulus.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Belum ada akun Penghulu terdaftar. Tambahkan akun Penghulu terlebih dahulu atau isi nama Wali Hakim manual di bawah.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activePenghulus.forEach { penghulu ->
                            val isChecked = penghulu.id in authorizedIds
                            val isDefault = defaultPenghuluId == penghulu.id
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDefault) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = BorderStroke(
                                    width = if (isDefault) 1.5.dp else 1.dp,
                                    color = if (isDefault) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wali_hakim_penghulu_row_${penghulu.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isChecked) {
                                                    authorizedIds = authorizedIds - penghulu.id
                                                    if (isDefault) {
                                                        defaultPenghuluId = ""
                                                    }
                                                } else {
                                                    authorizedIds = authorizedIds + penghulu.id
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    authorizedIds = authorizedIds + penghulu.id
                                                } else {
                                                    authorizedIds = authorizedIds - penghulu.id
                                                    if (isDefault) {
                                                        defaultPenghuluId = ""
                                                    }
                                                }
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = penghulu.officerName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "@${penghulu.username}${if (!penghulu.active) " • Nonaktif" else ""}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (isDefault) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier.clickable { defaultPenghuluId = "" }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Star,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "Wali Hakim Utama",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    authorizedIds = authorizedIds + penghulu.id
                                                    defaultPenghuluId = penghulu.id
                                                    fallbackWaliHakimName = ""
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(50),
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .testTag("btn_set_default_wali_hakim_${penghulu.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.StarBorder,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Jadikan Utama",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Nama Wali Hakim Cadangan / Kepala KUA (Opsional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Digunakan sebagai default jika belum memilih akun Penghulu sebagai Wali Hakim Utama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = fallbackWaliHakimName,
                        onValueChange = {
                            fallbackWaliHakimName = it.take(120)
                            if (it.isNotBlank()) {
                                defaultPenghuluId = ""
                            }
                        },
                        label = { Text("Nama Penghulu / Kepala KUA Wali Hakim") },
                        placeholder = { Text("Contoh: H. Ahmad Fauzi, S.Ag., M.H.") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_fallback_wali_hakim_name")
                    )
                    if (settings.headOfKuaName.isNotBlank()) {
                        TextButton(
                            onClick = {
                                defaultPenghuluId = ""
                                fallbackWaliHakimName = settings.headOfKuaName
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("btn_use_head_of_kua_as_wali_hakim")
                        ) {
                            Text(
                                text = "Gunakan Nama Kepala KUA (${settings.headOfKuaName})",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val matchedDefault = activePenghulus.firstOrNull { it.id == defaultPenghuluId }
                    val resolvedName = matchedDefault?.officerName ?: fallbackWaliHakimName.trim()
                    onSave(
                        authorizedIds,
                        matchedDefault?.id.orEmpty(),
                        resolvedName
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("btn_save_wali_hakim_settings")
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
