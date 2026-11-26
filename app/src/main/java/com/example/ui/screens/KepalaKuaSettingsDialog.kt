package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun KepalaKuaSettingsDialog(
    settings: ReminderSettings,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, name: String, nip: String) -> Unit
) {
    var title by remember(settings.headOfKuaTitle) {
        mutableStateOf(settings.headOfKuaTitle.ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE })
    }
    var name by remember(settings.headOfKuaName) {
        mutableStateOf(settings.headOfKuaName)
    }
    var nip by remember(settings.headOfKuaNip) {
        mutableStateOf(settings.headOfKuaNip)
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Atur Kepala KUA Donorojo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Nama dan NIP Kepala KUA yang disimpan akan otomatis muncul pada kolom tanda tangan setiap kali mengunduh Laporan Bulanan A4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text("Jabatan") },
                    placeholder = { Text("Kepala KUA Donorojo") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.WorkOutline, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_kepala_kua_title")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        validationError = null
                    },
                    label = { Text("Nama Lengkap Kepala KUA (beserta gelar)") },
                    placeholder = { Text("Contoh: H. Ahmad Fauzi, S.Ag., M.H.") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                    },
                    singleLine = true,
                    isError = validationError != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_kepala_kua_name")
                )

                OutlinedTextField(
                    value = nip,
                    onValueChange = {
                        nip = it
                        validationError = null
                    },
                    label = { Text("NIP Kepala KUA") },
                    placeholder = { Text("Contoh: 19750412 200501 1 003") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Badge, contentDescription = null)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_kepala_kua_nip")
                )

                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().length < 2) {
                        validationError = "Masukkan nama lengkap Kepala KUA terlebih dahulu."
                        return@Button
                    }
                    onSave(
                        title.trim().ifBlank { ReminderSettings.DEFAULT_HEAD_OF_KUA_TITLE },
                        name.trim(),
                        nip.trim()
                    )
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("btn_save_kepala_kua")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.testTag("btn_cancel_kepala_kua")
            ) {
                Text("Batal")
            }
        }
    )
}
