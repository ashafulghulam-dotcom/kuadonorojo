package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ErrorContainerLight
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.OnErrorContainerLight
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusVerifiedBg
import com.example.ui.theme.StatusVerifiedFg
import com.example.util.CoupleAgeCheck
import com.example.util.NikAgeAnalysis

@Composable
fun NikAgeInlineBadge(
    analysis: NikAgeAnalysis?,
    roleLabel: String,
    modifier: Modifier = Modifier
) {
    if (analysis == null) return

    if (!analysis.isCompleteNik) {
        val remaining = 16 - analysis.nik.length
        Text(
            text = "NIK $roleLabel: ${analysis.nik.length}/16 digit (kurang $remaining digit untuk hitung usia otomatis)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(start = 4.dp, top = 2.dp)
        )
        return
    }

    if (!analysis.isValidDate) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = StatusPendingBg,
            border = BorderStroke(1.dp, StatusPendingFg.copy(alpha = 0.35f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = StatusPendingFg,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = analysis.errorMessage ?: "Format tanggal lahir pada NIK tidak valid.",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusPendingFg
                )
            }
        }
        return
    }

    val isUnderage = analysis.requiresDispensasiKawin
    val bgColor = if (isUnderage) ErrorContainerLight else StatusVerifiedBg
    val fgColor = if (isUnderage) OnErrorContainerLight else StatusVerifiedFg
    val accentColor = if (isUnderage) ErrorCrimson else StatusVerifiedFg

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag(if (isUnderage) "nik_age_underage_$roleLabel" else "nik_age_valid_$roleLabel")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isUnderage) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isUnderage) {
                        "Usia saat akad: ${analysis.formattedAge()} (< 19 thn) — Wajib Lampirkan Dispensasi Kawin PA"
                    } else {
                        "Usia saat akad: ${analysis.formattedAge()} • Memenuhi batas usia ≥ 19 thn (UU No. 16/2019)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isUnderage) FontWeight.Bold else FontWeight.SemiBold,
                    color = fgColor
                )
            }
            Text(
                text = "Lahir: ${analysis.birthDateFormatted ?: "-"} • ${analysis.genderLabel ?: "-"}",
                style = MaterialTheme.typography.labelSmall,
                color = fgColor.copy(alpha = 0.82f)
            )
            if (!analysis.genderMismatchWarning.isNullOrBlank()) {
                Text(
                    text = "Catatan: ${analysis.genderMismatchWarning}",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusPendingFg,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DispensasiKawinAlertCard(
    coupleAgeCheck: CoupleAgeCheck,
    onApplyNote: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!coupleAgeCheck.requiresDispensasiKawin) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ErrorContainerLight,
        border = BorderStroke(1.3.dp, ErrorCrimson.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_dispensasi_kawin_warning")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ErrorCrimson.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = ErrorCrimson,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wajib Lampirkan Dispensasi Kawin PA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = OnErrorContainerLight
                    )
                    Text(
                        text = "Deteksi otomatis NIK — UU No. 16 Tahun 2019 (minimal usia 19 tahun)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnErrorContainerLight.copy(alpha = 0.85f)
                    )
                }
            }

            coupleAgeCheck.underageDetails().forEach { detail ->
                Text(
                    text = "• $detail",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = OnErrorContainerLight
                )
            }

            Text(
                text = "Pastikan berkas Penetapan Dispensasi Kawin dari Pengadilan Agama (PA) dilampirkan sebelum verifikasi akhir.",
                style = MaterialTheme.typography.bodySmall,
                color = OnErrorContainerLight.copy(alpha = 0.9f)
            )

            if (onApplyNote != null) {
                OutlinedButton(
                    onClick = onApplyNote,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.55f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_append_dispensasi_note")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        tint = ErrorCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tambahkan ke Catatan Berkas",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnErrorContainerLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DispensasiKawinCompactChip(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ErrorContainerLight,
        border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("chip_dispensasi_kawin_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = ErrorCrimson,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Wajib Lampirkan Dispensasi Kawin PA (< 19 thn • UU 16/2019)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = OnErrorContainerLight
            )
        }
    }
}
