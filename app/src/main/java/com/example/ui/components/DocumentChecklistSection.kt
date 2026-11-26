package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.DocumentChecklist
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryContainer
import com.example.ui.theme.OutlineVariantSage
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg

private data class ChecklistRowItem(
    val code: String,
    val title: String,
    val subtitle: String,
    val isMandatory: Boolean,
    val checked: Boolean,
    val onToggle: (Boolean) -> Unit
)

@Composable
fun DocumentChecklistEditor(
    checklist: DocumentChecklist,
    onChange: (DocumentChecklist) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        ChecklistRowItem(
            code = DocumentChecklist.CODE_N1,
            title = "Pengantar Nikah (Model N1)",
            subtitle = "Surat pengantar resmi dari Kepala Desa / Kelurahan",
            isMandatory = true,
            checked = checklist.n1,
            onToggle = { onChange(checklist.copy(n1 = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_N2,
            title = "Permohonan Kehendak Nikah (Model N2)",
            subtitle = "Formulir permohonan pencatatan akad nikah",
            isMandatory = true,
            checked = checklist.n2,
            onToggle = { onChange(checklist.copy(n2 = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_N4,
            title = "Persetujuan Calon Pengantin (Model N4)",
            subtitle = "Ditandatangani kedua calon pengantin",
            isMandatory = true,
            checked = checklist.n4,
            onToggle = { onChange(checklist.copy(n4 = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_KTP,
            title = "Fotokopi KTP & Kartu Keluarga (KK)",
            subtitle = "Calon suami, calon istri, orang tua, dan wali nikah",
            isMandatory = true,
            checked = checklist.ktp,
            onToggle = { onChange(checklist.copy(ktp = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_AKTA,
            title = "Akta Kelahiran / Ijazah Terakhir",
            subtitle = "Verifikasi identitas & usia minimal nikah (19 tahun)",
            isMandatory = true,
            checked = checklist.akta,
            onToggle = { onChange(checklist.copy(akta = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_SEHAT,
            title = "Surat Keterangan Sehat / Imunisasi TT",
            subtitle = "Dari Puskesmas Donorojo / fasilitas kesehatan resmi",
            isMandatory = true,
            checked = checklist.sehat,
            onToggle = { onChange(checklist.copy(sehat = it)) }
        ),
        ChecklistRowItem(
            code = DocumentChecklist.CODE_TAMBAHAN,
            title = "Berkas Tambahan (N5 / Dispensasi / Akta Cerai)",
            subtitle = "Opsional bila usia < 21 th, duda/janda, atau wali hakim",
            isMandatory = false,
            checked = checklist.tambahan,
            onToggle = { onChange(checklist.copy(tambahan = it)) }
        )
    )

    val progress = checklist.completedCount.toFloat() / checklist.totalCount.toFloat()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(
            1.dp,
            if (checklist.isAllMandatoryComplete) EmeraldPrimary.copy(alpha = 0.35f) else OutlineVariantSage
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_checklist_editor")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rincian Checklist Berkas Nikah",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (checklist.isAllMandatoryComplete) {
                            "Seluruh 6 berkas wajib lengkap (${checklist.completedCount}/${checklist.totalCount})"
                        } else {
                            "${checklist.mandatoryCompletedCount}/6 berkas wajib terpenuhi"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checklist.isAllMandatoryComplete) EmeraldPrimary else StatusPendingFg
                    )
                }

                TextButton(
                    onClick = {
                        if (checklist.isAllMandatoryComplete) {
                            onChange(DocumentChecklist())
                        } else {
                            onChange(DocumentChecklist.allComplete(includeOptional = checklist.tambahan))
                        }
                    },
                    modifier = Modifier.testTag("checklist_toggle_all_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (checklist.isAllMandatoryComplete) "Reset" else "Lengkap Wajib",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                color = if (checklist.isAllMandatoryComplete) EmeraldPrimary else StatusPendingFg,
                trackColor = OutlineVariantSage,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
            )

            rows.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = OutlineVariantSage.copy(alpha = 0.65f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { item.onToggle(!item.checked) }
                        .padding(vertical = 4.dp)
                        .testTag("checklist_item_${item.code.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = item.onToggle,
                        colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!item.isMandatory) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = EmeraldPrimaryContainer
                                ) {
                                    Text(
                                        text = "Opsional",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldPrimary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentChecklistReadOnlyCard(
    checklist: DocumentChecklist,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        "N1" to checklist.n1,
        "N2" to checklist.n2,
        "N4" to checklist.n4,
        "KTP & KK" to checklist.ktp,
        "Akta/Ijazah" to checklist.akta,
        "Sehat/TT" to checklist.sehat,
        "Tambahan" to checklist.tambahan
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF9FBFA),
        border = BorderStroke(1.dp, OutlineVariantSage),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Checklist Berkas N1–N4",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = checklist.summaryText(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (checklist.isAllMandatoryComplete) StatusVerifiedFg else StatusPendingFg
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                entries.forEach { (label, done) ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (done) StatusVerifiedBg else Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (done) StatusVerifiedFg.copy(alpha = 0.25f) else OutlineVariantSage
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (done) StatusVerifiedFg else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = if (done) StatusVerifiedFg else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentChecklistCompactPill(
    checklist: DocumentChecklist,
    documentsComplete: Boolean,
    modifier: Modifier = Modifier
) {
    val complete = documentsComplete || checklist.isAllMandatoryComplete
    val bg = if (complete) StatusVerifiedBg else StatusPendingBg
    val fg = if (complete) StatusVerifiedFg else StatusPendingFg
    val label = if (checklist.completedCount > 0) {
        "${checklist.completedCount}/${checklist.totalCount} Berkas"
    } else if (complete) {
        "Berkas Lengkap"
    } else {
        "0/${checklist.totalCount} Berkas"
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
