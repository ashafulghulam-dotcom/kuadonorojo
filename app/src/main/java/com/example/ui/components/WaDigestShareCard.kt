package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MarriageSchedule
import com.example.data.Village
import com.example.data.WhatsAppAppPreference
import com.example.util.DigestPeriod
import com.example.util.WaScheduleDigestFormatter

@Composable
fun WaDigestQuickCard(
    schedules: List<MarriageSchedule>,
    scopeLabel: String,
    adminWhatsappNumber: String = "",
    appPreference: WhatsAppAppPreference = WhatsAppAppPreference.AUTO,
    villages: List<Village> = emptyList(),
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(DigestPeriod.TODAY) }
    var showPreviewSheet by remember { mutableStateOf(false) }

    val todayCount = remember(schedules) {
        WaScheduleDigestFormatter.filterSchedulesForPeriod(schedules, DigestPeriod.TODAY).size
    }
    val tomorrowCount = remember(schedules) {
        WaScheduleDigestFormatter.filterSchedulesForPeriod(schedules, DigestPeriod.TOMORROW).size
    }
    val weekCount = remember(schedules) {
        WaScheduleDigestFormatter.filterSchedulesForPeriod(schedules, DigestPeriod.THIS_WEEK).size
    }

    val activeCount = when (selectedPeriod) {
        DigestPeriod.TODAY -> todayCount
        DigestPeriod.TOMORROW -> tomorrowCount
        DigestPeriod.THIS_WEEK -> weekCount
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_wa_digest_quick")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wa_digest_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.wa_digest_card_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { showPreviewSheet = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_wa_digest_preview")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = stringResource(R.string.cd_preview_wa_digest),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Pratinjau",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Period selector chips with live schedule counts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DigestPeriod.entries.forEach { period ->
                    val count = when (period) {
                        DigestPeriod.TODAY -> todayCount
                        DigestPeriod.TOMORROW -> tomorrowCount
                        DigestPeriod.THIS_WEEK -> weekCount
                    }
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = {
                            Text(
                                text = "${period.shortLabel} ($count)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("chip_wa_digest_${period.name.lowercase()}")
                    )
                }
            }

            // Direct 1-click actions: Kirim WA Grup & Salin Teks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val message = WaScheduleDigestFormatter.formatWhatsAppDigest(
                            schedules = schedules,
                            period = selectedPeriod,
                            scopeLabel = scopeLabel,
                            adminWhatsappNumber = adminWhatsappNumber
                        )
                        val launched = WaScheduleDigestFormatter.shareToWhatsAppGroup(
                            context = context,
                            message = message,
                            appPreference = appPreference
                        )
                        onFeedback(
                            "Membuka $launched untuk bagikan rekap ${selectedPeriod.shortLabel} ($activeCount jadwal) ke grup."
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1.15f)
                        .height(48.dp)
                        .testTag("btn_wa_digest_share_group")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_share_wa_group),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val message = WaScheduleDigestFormatter.formatWhatsAppDigest(
                            schedules = schedules,
                            period = selectedPeriod,
                            scopeLabel = scopeLabel,
                            adminWhatsappNumber = adminWhatsappNumber
                        )
                        val copied = WaScheduleDigestFormatter.copyToClipboard(context, message)
                        if (copied) {
                            onFeedback(
                                "Teks rekap WA ${selectedPeriod.shortLabel} ($activeCount jadwal) disalin. Tinggal tempel di grup WA."
                            )
                        } else {
                            onFeedback("Gagal menyalin teks ke clipboard.")
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(48.dp)
                        .testTag("btn_wa_digest_copy")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_copy_wa_text),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showPreviewSheet) {
        WaDigestPreviewSheet(
            schedules = schedules,
            initialPeriod = selectedPeriod,
            baseScopeLabel = scopeLabel,
            adminWhatsappNumber = adminWhatsappNumber,
            appPreference = appPreference,
            villages = villages,
            onDismiss = { showPreviewSheet = false },
            onFeedback = onFeedback
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaDigestPreviewSheet(
    schedules: List<MarriageSchedule>,
    initialPeriod: DigestPeriod,
    baseScopeLabel: String,
    adminWhatsappNumber: String,
    appPreference: WhatsAppAppPreference,
    villages: List<Village>,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedPeriod by remember { mutableStateOf(initialPeriod) }
    var selectedVillageCode by remember { mutableStateOf<String?>(null) }

    val resolvedScopeLabel = remember(selectedVillageCode, villages, baseScopeLabel) {
        val v = villages.firstOrNull { it.code == selectedVillageCode }
        v?.name ?: baseScopeLabel
    }

    val filteredCount = remember(schedules, selectedPeriod, selectedVillageCode) {
        WaScheduleDigestFormatter.filterSchedulesForPeriod(
            schedules = schedules,
            period = selectedPeriod,
            villageCodeFilter = selectedVillageCode
        ).size
    }

    val previewText = remember(
        schedules,
        selectedPeriod,
        selectedVillageCode,
        resolvedScopeLabel,
        adminWhatsappNumber
    ) {
        WaScheduleDigestFormatter.formatWhatsAppDigest(
            schedules = schedules,
            period = selectedPeriod,
            scopeLabel = resolvedScopeLabel,
            adminWhatsappNumber = adminWhatsappNumber,
            villageCodeFilter = selectedVillageCode
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pratinjau Rekap WA Grup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Format teks WhatsApp siap kirim ke grup koordinasi KUA Donorojo, Penghulu, dan Modin ($filteredCount jadwal).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Period filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DigestPeriod.entries.forEach { period ->
                    val count = WaScheduleDigestFormatter.filterSchedulesForPeriod(
                        schedules = schedules,
                        period = period,
                        villageCodeFilter = selectedVillageCode
                    ).size
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text("${period.shortLabel} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("sheet_wa_digest_${period.name.lowercase()}")
                    )
                }
            }

            // Optional village filter (Admin mode when villages list is supplied)
            if (villages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedVillageCode == null,
                        onClick = { selectedVillageCode = null },
                        label = { Text("Semua 8 Desa") },
                        modifier = Modifier.testTag("sheet_wa_village_all")
                    )
                    villages.forEach { village ->
                        FilterChip(
                            selected = selectedVillageCode == village.code,
                            onClick = { selectedVillageCode = village.code },
                            label = { Text(village.name.removePrefix("Desa ")) },
                            modifier = Modifier.testTag("sheet_wa_village_${village.code}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // WhatsApp message preview box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val launched = WaScheduleDigestFormatter.shareToWhatsAppGroup(
                            context = context,
                            message = previewText,
                            appPreference = appPreference
                        )
                        onFeedback("Membuka $launched untuk bagikan rekap ${selectedPeriod.shortLabel} ($filteredCount jadwal).")
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_btn_share_wa")
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_share_wa_group), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        val copied = WaScheduleDigestFormatter.copyToClipboard(context, previewText)
                        if (copied) {
                            onFeedback("Rekap WA ${selectedPeriod.shortLabel} disalin ke clipboard.")
                        } else {
                            onFeedback("Gagal menyalin teks ke clipboard.")
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.85f)
                        .height(48.dp)
                        .testTag("sheet_btn_copy_wa")
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_copy_wa_text), fontWeight = FontWeight.SemiBold)
                }
            }

            TextButton(
                onClick = {
                    WaScheduleDigestFormatter.shareViaSystemChooser(context, previewText)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_btn_share_other")
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bagikan ke Aplikasi Lain")
            }
        }
    }
}
