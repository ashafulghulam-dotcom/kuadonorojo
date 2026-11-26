package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.DocumentChecklist
import com.example.data.MarriageSchedule
import com.example.data.ValidationStatus
import com.example.ui.components.DispensasiKawinAlertCard
import com.example.ui.components.DocumentChecklistEditor
import com.example.ui.components.visuals
import com.example.util.NikAgeValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationSheet(
    schedule: MarriageSchedule,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ValidationStatus, DocumentChecklist, Boolean, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedStatus by remember { mutableStateOf(schedule.resolvedStatus()) }
    var checklist by remember { mutableStateOf(schedule.resolvedChecklist()) }
    var documentsComplete by remember {
        mutableStateOf(schedule.documentsComplete || checklist.isAllMandatoryComplete)
    }
    var note by remember { mutableStateOf(schedule.verificationNote) }

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
                text = "Verifikasi Akhir KUA Donorojo",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${schedule.groomName} & ${schedule.brideName} • ${schedule.villageName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            val coupleAgeCheck = remember(schedule.groomNik, schedule.brideNik, schedule.weddingDate) {
                NikAgeValidator.analyzeCouple(
                    groomNik = schedule.groomNik,
                    brideNik = schedule.brideNik,
                    weddingDateIso = schedule.weddingDate
                )
            }
            if (coupleAgeCheck.requiresDispensasiKawin) {
                DispensasiKawinAlertCard(
                    coupleAgeCheck = coupleAgeCheck,
                    onApplyNote = {
                        val rec = coupleAgeCheck.recommendedNoteText()
                        note = when {
                            note.isBlank() -> rec
                            note.contains("Dispensasi Kawin", ignoreCase = true) -> note
                            else -> "${note.trim()}\n$rec".take(500)
                        }
                    }
                )
            }

            DocumentChecklistEditor(
                checklist = checklist,
                onChange = { updated ->
                    checklist = updated
                    documentsComplete = updated.isAllMandatoryComplete
                }
            )

            Text(
                text = "Pilih Status Validasi",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ValidationStatus.entries.forEach { status ->
                    val isSelected = selectedStatus == status
                    val visuals = status.visuals()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) visuals.bgColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) visuals.fgColor else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedStatus = status
                                if (status == ValidationStatus.TERVERIFIKASI || status == ValidationStatus.SELESAI) {
                                    if (!checklist.isAllMandatoryComplete) {
                                        checklist = DocumentChecklist.allComplete(includeOptional = checklist.tambahan)
                                    }
                                    documentsComplete = true
                                }
                            }
                            .testTag("verify_option_${status.code}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) visuals.fgColor else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = status.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) visuals.fgColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = status.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) visuals.fgColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            val missingLabels = remember(checklist) { checklist.missingMandatoryLabels() }
            if (missingLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            note = "Berkas belum lengkap: ${missingLabels.joinToString(", ")}. Mohon segera dilengkapi sebelum hari akad."
                        },
                        modifier = Modifier.testTag("verify_fill_missing_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Isi Catatan Berkas Kurang (${missingLabels.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("Catatan Validasi (Tampil Real-Time di Dasbor Modin)") },
                placeholder = { Text("Contoh: Berkas lengkap, siap akad di KUA pukul 09.00 WIB.") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("verify_note_input")
            )

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
                        onConfirm(
                            selectedStatus,
                            checklist,
                            documentsComplete || checklist.isAllMandatoryComplete,
                            note
                        )
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(50.dp)
                        .testTag("confirm_verification_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simpan Verifikasi")
                    }
                }
            }
        }
    }
}
